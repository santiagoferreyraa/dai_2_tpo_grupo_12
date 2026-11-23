/**
 * El servidor HTTP del agente: una sola ruta de trabajo, `POST /api/agent/chat`.
 *
 * Es `node:http` a secas y no un framework: con una ruta, un framework es una dependencia más que
 * no aporta nada. El front llega por el proxy de Vite (`/api/agent` → 8085), así que no hay CORS.
 *
 * El contrato es el que ya espera `frontend/src/features/agent/agentClient.ts`:
 *
 *   pedido:    { message, history: { role: 'user' | 'agent', text, signature? }[] }
 *   respuesta: { text, signature }
 *   error:     { message }, que es lo que el cliente HTTP del front muestra.
 *
 * `signature` es el sello de cada respuesta del asistente: el front lo guarda y lo devuelve, y las
 * líneas del asistente que vuelven sin un sello válido se descartan. Ver `signature.ts`.
 *
 * **Solo con sesión, y contesta 401 si no la hay** (Manuel, 23/11). Sin token, o con uno que core
 * rechaza, el modelo ni se llama. El 401 no desloguea a nadie por un error del agente: el front,
 * ante un 401, cierra la sesión solo si el token venció según su propio reloj
 * (`clearSessionIfExpired`), y en cualquier otro caso muestra el mensaje. Hasta el 23/11 era al
 * revés ("nunca 401"): el asistente se podía usar sin sesión.
 */

import { createServer as createHttpServer } from 'node:http'
import type { IncomingMessage, Server, ServerResponse } from 'node:http'
import { z } from 'zod'

import { AssistantError, type AskInput, type AskResult } from './assistant.ts'
import type { SessionCheck } from './ecopedia.ts'
import { clientKey, createRateLimiter, type RateLimiter } from './rate-limit.ts'
import { createSigner, type Signer } from './signature.ts'

export interface ServerDeps {
  /** `null` cuando falta la clave de Gemini: el servicio arranca igual y lo avisa en el chat. */
  assistant: { ask(input: AskInput): Promise<AskResult> } | null
  /** Valida el token contra core. Ver `createSessionChecker` en `ecopedia.ts`. */
  checkSession: (authorization: string, signal?: AbortSignal) => Promise<SessionCheck>
  log?: (line: string) => void
  /** Por omisión, `QUESTIONS_PER_MINUTE` por cliente. Se inyecta en los tests. */
  rateLimiter?: RateLimiter
  /** Por omisión, uno con una clave nueva al azar. Se inyecta en los tests. */
  signer?: Signer
}

interface Context {
  assistant: ServerDeps['assistant']
  checkSession: ServerDeps['checkSession']
  limiter: RateLimiter
  signer: Signer
  log: (line: string) => void
}

export const CHAT_PATH = '/api/agent/chat'

/** Topes del pedido: un chat no manda más que esto, y lo de más solo gasta cupo del modelo. */
const MAX_BODY_BYTES = 64 * 1024
const MAX_MESSAGE_CHARS = 2000
const MAX_HISTORY = 40

/**
 * Cuánto se espera al modelo. Con el plan gratis una respuesta con tres herramientas tarda unos
 * segundos; si Gemini está saturado, Strands reintenta, y pasado este tiempo es mejor decirlo.
 */
const ANSWER_TIMEOUT_MS = 45_000

/**
 * Preguntas por minuto por cliente, para no agotar el cupo gratis de Gemini. Una conversación
 * normal no llega: cada respuesta tarda varios segundos y hay que leerla antes de seguir.
 */
export const QUESTIONS_PER_MINUTE = 10

const ChatRequest = z.object({
  message: z.string().trim().min(1).max(MAX_MESSAGE_CHARS),
  history: z
    .array(
      z.object({
        role: z.enum(['user', 'agent']),
        text: z.string().max(4 * MAX_MESSAGE_CHARS),
        signature: z.string().max(100).optional(),
      }),
    )
    .max(MAX_HISTORY)
    .default([]),
})

export function createServer(deps: ServerDeps): Server {
  const context: Context = {
    assistant: deps.assistant,
    checkSession: deps.checkSession,
    limiter:
      deps.rateLimiter ?? createRateLimiter({ limit: QUESTIONS_PER_MINUTE, windowMs: 60_000 }),
    signer: deps.signer ?? createSigner(),
    log: deps.log ?? ((line: string) => console.log(line)),
  }
  const { log } = context

  return createHttpServer((request, response) => {
    handle(request, response, context).catch((error: unknown) => {
      log(`[agent] error inesperado: ${error instanceof Error ? error.stack : String(error)}`)
      if (!response.headersSent) send(response, 500, { message: 'El asistente tuvo un error.' })
    })
  })
}

async function handle(
  request: IncomingMessage,
  response: ServerResponse,
  { assistant, checkSession, limiter, signer, log }: Context,
): Promise<void> {
  const path = new URL(request.url ?? '/', 'http://localhost').pathname

  if (path === '/api/agent/health' && request.method === 'GET') {
    return send(response, 200, { status: 'UP', configured: assistant !== null })
  }
  if (path !== CHAT_PATH) return send(response, 404, { message: 'No existe esa ruta.' })
  if (request.method !== 'POST') return send(response, 405, { message: 'Solo POST.' })

  const raw = await readBody(request)
  if (raw === null) return send(response, 413, { message: 'El mensaje es demasiado largo.' })

  let parsed: z.infer<typeof ChatRequest>
  try {
    parsed = ChatRequest.parse(JSON.parse(raw))
  } catch {
    return send(response, 400, { message: 'El pedido no tiene la forma esperada.' })
  }

  /*
   * Sin clave. Lo que ve el conductor dice qué hacer, no por qué (directiva 6): el motivo técnico
   * —falta GEMINI_API_KEY— queda en el log del arranque, que es donde lo busca quien levanta el
   * sistema (ver main.ts).
   */
  if (assistant === null) {
    return send(response, 503, {
      message:
        'El asistente no está disponible en este momento. Mientras tanto, podés buscar estaciones en el mapa.',
    })
  }

  /* Sin token no hay nada que validar: se corta antes de gastar una consulta a core. */
  const authorization = request.headers.authorization
  if (authorization === undefined || !/^Bearer \S+$/.test(authorization)) {
    return rejectSession(response)
  }

  /*
   * El límite va después de validar la forma: un pedido mal armado no llega al modelo, así que no
   * gasta cupo y no tiene por qué contar. Y va antes de consultar a core, para que mandar tokens
   * inventados en serie no se convierta en una ráfaga de consultas a core.
   */
  const forwarded = request.headers['x-forwarded-for']
  const client = clientKey(
    request.socket.remoteAddress,
    Array.isArray(forwarded) ? forwarded[0] : forwarded,
  )
  const quota = limiter.take(client)
  if (!quota.allowed) {
    log(`[agent] 429 · ${client} pasó las ${QUESTIONS_PER_MINUTE} preguntas por minuto`)
    response.setHeader('Retry-After', String(quota.retryAfterSeconds))
    return send(response, 429, {
      message: `Hiciste muchas preguntas seguidas. Esperá ${quota.retryAfterSeconds} segundos y probá de nuevo.`,
    })
  }

  const session = await checkSession(authorization)
  if (session === 'invalid') {
    log('[agent] 401 · el token no es válido para core')
    return rejectSession(response)
  }
  if (session === 'unavailable') {
    log('[agent] 503 · no se pudo validar la sesión con core')
    return send(response, 503, {
      message: 'El asistente no está disponible en este momento. Probá de nuevo en un rato.',
    })
  }

  /* Si el conductor cierra el chat o recarga, se deja de esperar al modelo. */
  const disconnected = new AbortController()
  response.on('close', () => {
    if (!response.writableFinished) disconnected.abort()
  })
  const signal = AbortSignal.any([disconnected.signal, AbortSignal.timeout(ANSWER_TIMEOUT_MS)])

  /*
   * Solo sobreviven las líneas del asistente con sello válido: las inventadas o retocadas se
   * descartan antes de que el modelo las lea. Las del conductor pasan siempre.
   */
  const history = parsed.history.filter(
    (entry) => entry.role === 'user' || signer.verify(entry.text, entry.signature),
  )
  const dropped = parsed.history.length - history.length

  const started = Date.now()

  try {
    const result = await assistant.ask({
      message: parsed.message,
      history: history.map(({ role, text }) => ({ role, text })),
      authorization,
      signal,
    })
    log(
      `[agent] 200 en ${Date.now() - started} ms` +
        ` · herramientas: ${result.toolsUsed.join(', ') || 'ninguna'}` +
        (dropped > 0 ? ` · ${dropped} respuesta(s) sin sello válido descartada(s)` : ''),
    )
    return send(response, 200, { text: result.text, signature: signer.sign(result.text) })
  } catch (error) {
    if (disconnected.signal.aborted) return
    const { status, message } = describe(error, signal)
    log(`[agent] ${status} en ${Date.now() - started} ms · ${detail(error)}`)
    return send(response, status, { message })
  }
}

/** El código y el mensaje para el conductor. El detalle técnico va al log, no al chat. */
function describe(error: unknown, signal: AbortSignal): { status: number; message: string } {
  if (signal.aborted) {
    return { status: 504, message: 'El asistente tardó demasiado. Probá de nuevo.' }
  }
  if (error instanceof AssistantError) {
    switch (error.reason) {
      case 'busy':
        return {
          status: 503,
          message: 'El asistente está saturado en este momento. Probá de nuevo en un rato.',
        }
      case 'bad-key':
        return {
          status: 503,
          message:
            'El asistente no está disponible en este momento. Mientras tanto, podés buscar estaciones en el mapa.',
        }
      case 'too-broad':
        return {
          status: 422,
          message:
            'Esa consulta es muy amplia para el asistente. Probá con una sola estación, por ejemplo: "¿qué horarios libres tiene la estación de Palermo?".',
        }
      case 'failed':
        return { status: 502, message: 'El asistente no pudo responder. Probá de nuevo.' }
    }
  }
  return { status: 500, message: 'El asistente tuvo un error.' }
}

/** El 401 de siempre: el mismo texto con o sin token, para no contar por qué se rechazó. */
function rejectSession(response: ServerResponse): void {
  response.setHeader('WWW-Authenticate', 'Bearer')
  send(response, 401, { message: 'Iniciá sesión para usar el asistente.' })
}

function detail(error: unknown): string {
  if (!(error instanceof Error)) return String(error)
  const cause = error.cause instanceof Error ? ` ← ${error.cause.message.slice(0, 300)}` : ''
  return `${error.message}${cause}`
}

/** El cuerpo como texto, o `null` si pasa el tope. */
async function readBody(request: IncomingMessage): Promise<string | null> {
  const chunks: Buffer[] = []
  let size = 0
  for await (const chunk of request) {
    size += (chunk as Buffer).length
    if (size > MAX_BODY_BYTES) return null
    chunks.push(chunk as Buffer)
  }
  return Buffer.concat(chunks).toString('utf8')
}

function send(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(body))
}
