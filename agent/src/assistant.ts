/**
 * Una respuesta del asistente: arma el agente de Strands, le pasa la conversación y devuelve el
 * texto final.
 *
 * **Un agente nuevo por pedido, y no uno por conductor.** El front manda el hilo entero en cada
 * mensaje (`frontend/src/features/agent/agentClient.ts`), así que el servicio no guarda nada entre
 * llamadas: no hay sesiones que vencer ni memoria que se mezcle entre dos conductores, y reiniciar
 * el proceso no pierde ninguna conversación. Crear el agente es barato; lo caro es el modelo.
 *
 * Las dependencias entran por parámetro —el modelo, la API, el buscador de direcciones— para que
 * los tests corran con un modelo guionado y sin salir a la red.
 */

import { Agent, ModelError, ModelThrottledError } from '@strands-agents/sdk'
import type { MessageData, Model, ModelRouter } from '@strands-agents/sdk'

import type { EcopediaApi } from './ecopedia.ts'
import type { Place } from './geocoding.ts'
import { buildSystemPrompt, REMINDER } from './prompt.ts'
import { createTools } from './tools.ts'

/** Un mensaje del hilo, como lo manda el front. */
export interface HistoryEntry {
  role: 'user' | 'agent'
  text: string
}

export interface AskInput {
  message: string
  history: HistoryEntry[]
  /** El encabezado `Authorization` del pedido, tal cual. */
  authorization: string | undefined
  signal?: AbortSignal
}

export interface AskResult {
  text: string
  /** Qué herramientas usó, en orden. Solo para el log del servicio. */
  toolsUsed: string[]
}

export interface AssistantDeps {
  createModel: () => Model | ModelRouter
  createApi: (authorization: string | undefined) => EcopediaApi
  findPlaces: (query: string, signal?: AbortSignal) => Promise<Place[]>
  now?: () => Date
}

/**
 * Cuántas vueltas modelo → herramienta puede dar por pregunta. El recorrido normal son tres
 * (lugar, búsqueda, respuesta), con margen para agrandar el radio o mirar horarios; más que esto
 * es un modelo dando vueltas, y cada vuelta gasta cupo del plan gratis.
 */
const MAX_TURNS = 8

/** Por qué no hubo respuesta, para que el servidor elija el código y el mensaje. */
export class AssistantError extends Error {
  readonly reason: 'busy' | 'bad-key' | 'failed'

  constructor(reason: AssistantError['reason'], message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = 'AssistantError'
    this.reason = reason
  }
}

export function createAssistant(deps: AssistantDeps) {
  return {
    async ask(input: AskInput): Promise<AskResult> {
      const { previous, prompt } = toMessages(input.history, input.message)
      const api = deps.createApi(input.authorization)
      const agent = new Agent({
        model: deps.createModel(),
        systemPrompt: buildSystemPrompt(deps.now?.() ?? new Date(), api.hasSession),
        tools: createTools({ api, findPlaces: deps.findPlaces }),
        messages: previous,
        printer: false,
      })

      const before = agent.messages.length
      let text: string
      try {
        /* La pregunta y, en un bloque aparte, el recordatorio de las reglas. Ver `REMINDER`. */
        const result = await agent.invoke([{ text: prompt }, { text: REMINDER }], {
          cancelSignal: input.signal,
          limits: { turns: MAX_TURNS },
        })
        text = String(result).trim()
      } catch (error) {
        if (input.signal?.aborted) throw error
        throw classify(error)
      }

      if (text === '') throw new AssistantError('failed', 'El modelo contestó vacío')

      const toolsUsed = agent.messages
        .slice(before)
        .flatMap((message) => message.content)
        .flatMap((block) => (block.type === 'toolUseBlock' ? [block.name] : []))

      return { text, toolsUsed }
    },
  }
}

export type Assistant = ReturnType<typeof createAssistant>

/**
 * El hilo del front, pasado al formato de Strands.
 *
 * El front manda el hilo CON el mensaje nuevo al final, y Strands lo recibe aparte en `invoke`: si
 * quedara en los dos lados, el modelo lo leería dos veces. Por eso se separa en lo anterior
 * (`previous`) y lo que se pregunta ahora (`prompt`).
 *
 * También junta los mensajes seguidos del mismo lado —pasa cuando una respuesta falló y el
 * conductor volvió a escribir: los dos van juntos en el `prompt`— y descarta lo que haya del
 * asistente antes del primer mensaje del conductor: Gemini espera que la conversación la abra el
 * usuario.
 */
export function toMessages(
  history: HistoryEntry[],
  message: string,
): { previous: MessageData[]; prompt: string } {
  const entries = [...history]
  const last = entries.at(-1)
  if (last !== undefined && last.role === 'user' && last.text.trim() === message.trim())
    entries.pop()
  entries.push({ role: 'user', text: message })

  const firstUser = entries.findIndex((entry) => entry.role === 'user')

  const merged: { role: 'user' | 'assistant'; texts: string[] }[] = []
  for (const entry of entries.slice(firstUser)) {
    const role = entry.role === 'user' ? 'user' : 'assistant'
    const previous = merged.at(-1)
    if (previous?.role === role) previous.texts.push(entry.text)
    else merged.push({ role, texts: [entry.text] })
  }

  /* El último siempre es del conductor, porque el mensaje nuevo se agregó al final. */
  const current = merged.pop()!
  return {
    previous: merged.map(({ role, texts }) => ({ role, content: texts.map((text) => ({ text })) })),
    prompt: current.texts.join('\n'),
  }
}

/**
 * Traduce el error del modelo. Gemini gratis contesta 503 cuando está saturado y 429 cuando se
 * agotó el cupo; Strands los envuelve y el código queda en el texto, así que se lee de ahí.
 */
function classify(error: unknown): AssistantError {
  const detail = error instanceof Error ? error.message : String(error)

  if (/API[_ ]?key|API_KEY_INVALID|PERMISSION_DENIED|UNAUTHENTICATED/i.test(detail)) {
    return new AssistantError('bad-key', 'Gemini rechazó la clave', { cause: error })
  }
  if (
    error instanceof ModelThrottledError ||
    /\b(429|503)\b|RESOURCE_EXHAUSTED|UNAVAILABLE|high demand/i.test(detail)
  ) {
    return new AssistantError('busy', 'Gemini está saturado o sin cupo', { cause: error })
  }
  if (error instanceof ModelError) {
    return new AssistantError('failed', 'El modelo falló', { cause: error })
  }
  return new AssistantError('failed', 'El agente falló', { cause: error })
}
