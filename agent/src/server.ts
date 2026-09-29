/**
 * El servidor HTTP del agente: una sola ruta de trabajo, `POST /api/agent/chat`.
 *
 * Es `node:http` a secas y no un framework: con una ruta, un framework es una dependencia más que
 * no aporta nada. El front llega por el proxy de Vite (`/api/agent` → 8085), así que no hay CORS.
 *
 * El contrato es el que ya espera `frontend/src/features/agent/agentClient.ts`:
 *
 *   pedido:    { message: string, history: { role: 'user' | 'agent', text: string }[] }
 *   respuesta: { text: string }
 *   error:     { message: string }, que es lo que el cliente HTTP del front muestra.
 *
 * **Nunca contesta 401 ni 403.** El cliente del front cierra la sesión ante cualquiera de los dos,
 * y un problema del agente no tiene por qué desloguear a nadie. Si el token venció, lo dice el
 * agente dentro de la conversación.
 */

import { createServer as createHttpServer } from 'node:http'
import type { IncomingMessage, Server, ServerResponse } from 'node:http'
import { z } from 'zod'

import { AssistantError, type AskInput, type AskResult } from './assistant.ts'

export interface ServerDeps {
  /** `null` cuando falta la clave de Gemini: el servicio arranca igual y lo avisa en el chat. */
  assistant: { ask(input: AskInput): Promise<AskResult> } | null
  log?: (line: string) => void
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

const ChatRequest = z.object({
  message: z.string().trim().min(1).max(MAX_MESSAGE_CHARS),
  history: z
    .array(
      z.object({
        role: z.enum(['user', 'agent']),
        text: z.string().max(4 * MAX_MESSAGE_CHARS),
      }),
    )
    .max(MAX_HISTORY)
    .default([]),
})

export function createServer(deps: ServerDeps): Server {
  const log = deps.log ?? ((line: string) => console.log(line))

  return createHttpServer((request, response) => {
    handle(request, response, deps, log).catch((error: unknown) => {
      log(`[agent] error inesperado: ${error instanceof Error ? error.stack : String(error)}`)
      if (!response.headersSent) send(response, 500, { message: 'El asistente tuvo un error.' })
    })
  })
}

async function handle(
  request: IncomingMessage,
  response: ServerResponse,
  deps: ServerDeps,
  log: (line: string) => void,
): Promise<void> {
  const path = new URL(request.url ?? '/', 'http://localhost').pathname

  if (path === '/api/agent/health' && request.method === 'GET') {
    return send(response, 200, { status: 'UP', configured: deps.assistant !== null })
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

  if (deps.assistant === null) {
    return send(response, 503, {
      message: 'El asistente no está configurado: falta GEMINI_API_KEY en el .env.',
    })
  }

  /* Si el conductor cierra el chat o recarga, se deja de esperar al modelo. */
  const disconnected = new AbortController()
  response.on('close', () => {
    if (!response.writableFinished) disconnected.abort()
  })
  const signal = AbortSignal.any([disconnected.signal, AbortSignal.timeout(ANSWER_TIMEOUT_MS)])

  const authorization = request.headers.authorization
  const started = Date.now()

  try {
    const result = await deps.assistant.ask({
      message: parsed.message,
      history: parsed.history,
      authorization: authorization?.startsWith('Bearer ') ? authorization : undefined,
      signal,
    })
    log(
      `[agent] 200 en ${Date.now() - started} ms` +
        ` · ${authorization ? 'con' : 'sin'} sesión` +
        ` · herramientas: ${result.toolsUsed.join(', ') || 'ninguna'}`,
    )
    return send(response, 200, { text: result.text })
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
          message: 'El asistente no está bien configurado: revisá la clave de Gemini.',
        }
      case 'failed':
        return { status: 502, message: 'El asistente no pudo responder. Probá de nuevo.' }
    }
  }
  return { status: 500, message: 'El asistente tuvo un error.' }
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
