import { ApiError, api } from '@/lib/api'
import type { ChatMessage } from './types'

/**
 * La costura entre el chat y el agente: lo ÚNICO que sabe de HTTP en toda la feature.
 *
 * Existe para que la lógica del agente —que la escribe otra persona, del lado del backend— entre
 * por un solo lugar. Las pantallas llaman `askAgent` y no saben si atrás hay un modelo, una
 * cadena de herramientas o el simulador de acá abajo.
 *
 * **Contesta el agente de verdad**, el servicio de `agent/` en el 8085, al que llega el proxy de
 * Vite. Con `BACKEND_READY` en `false` vuelven a contestar las respuestas fijas de `stubReply`,
 * útil para tocar la pantalla sin levantar el agente.
 */

/** En `false`, el chat usa el simulador de abajo en vez del agente. Ver el comentario de arriba. */
const BACKEND_READY = true

/** Ruta del agente en la API. Sin el prefijo `/api`, que lo agrega el cliente HTTP. */
const ENDPOINT = '/agent/chat'

/**
 * Lo que se le manda al agente.
 *
 * Viaja la conversación entera y no solo el último mensaje: el agente no guarda estado entre
 * llamadas, así que el hilo es lo que le da contexto. Si el backend termina manejando sesiones,
 * `history` se recorta acá y las pantallas no se enteran.
 */
export interface AgentRequest {
  message: string
  history: ChatMessage[]
}

export interface AgentReply {
  text: string
  /** El sello de la respuesta. Se guarda en el mensaje y vuelve con el historial. */
  signature?: string
}

/** Forma cruda del backend. Se acepta más de un nombre de campo para no atarse a uno solo. */
interface AgentResponseBody {
  text?: string
  reply?: string
  message?: string
  signature?: string
}

export async function askAgent(request: AgentRequest, signal?: AbortSignal): Promise<AgentReply> {
  if (!BACKEND_READY) return stubReply(request, signal)

  let body: AgentResponseBody
  try {
    body = await api.post<AgentResponseBody>(
      ENDPOINT,
      {
        message: request.message,
        history: request.history.map(({ role, text, signature }) => ({ role, text, signature })),
      },
      { signal },
    )
  } catch (cause) {
    throw new Error(agentErrorText(cause), { cause })
  }

  /*
   * El texto va SIN recortar: el sello se calculó sobre el texto exacto que mandó el agente, y un
   * espacio de menos lo invalidaría. El agente ya lo manda sin espacios de más.
   */
  const text = body.text ?? body.reply ?? body.message ?? ''
  if (text.trim() === '') throw new Error(UNAVAILABLE)
  return { text, signature: body.signature }
}

const UNAVAILABLE = 'El asistente no está disponible ahora. Probá de nuevo en un momento.'

/**
 * Lo que ve el conductor cuando la pregunta falla.
 *
 * El agente escribe sus rechazos para el conductor, en castellano y diciendo qué hacer (`message`
 * en `agent/src/server.ts`: el límite de preguntas, la clave inválida), y esos se muestran. Lo que
 * no viene de él —el agente caído, con el 502 de Caddy o del proxy de Vite, sin conexión— no tiene
 * ese cuerpo y lleva una frase propia, nunca el "Error 502" (directiva 6).
 */
function agentErrorText(cause: unknown): string {
  if (cause instanceof ApiError && typeof cause.detail === 'object' && cause.detail !== null) {
    const message = (cause.detail as { message?: unknown }).message
    if (typeof message === 'string' && message.trim() !== '') return message
  }
  return UNAVAILABLE
}

/*
 * ─── Simulador ────────────────────────────────────────────────────────────────────────────────
 *
 * No es un adorno: es lo que permite construir y probar la pantalla antes de que exista el
 * agente —los tres puntitos, el scroll que sigue la conversación, el aviso de error—. Se borra
 * entero el día que sobre.
 */

/** Cuánto tarda en "pensar". Suficiente para que se vean los puntitos sin aburrir. */
const STUB_DELAY_MS = 900

const STUB_REPLIES = [
  'Todavía no estoy conectado al backend, así que esto es una respuesta de prueba. Cuando el agente esté enchufado voy a poder buscarte estaciones, contarte cuánto sale una carga y reservarte un turno.',
  'Respuesta simulada: por acá va a contestar el agente de Ecopedia.',
  'Estoy de adorno hasta que me conecten, pero la pantalla ya funciona: probá escribir otra cosa.',
]

function stubReply(request: AgentRequest, signal?: AbortSignal): Promise<AgentReply> {
  return new Promise((resolve, reject) => {
    /* El índice sale del largo del hilo para que dos mensajes seguidos no den la misma respuesta. */
    const reply = STUB_REPLIES[request.history.length % STUB_REPLIES.length]

    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve({ text: reply })
    }, STUB_DELAY_MS)

    /* Si la pantalla se desmonta a mitad de la espera, se corta como lo haría una petición real. */
    function onAbort() {
      clearTimeout(timer)
      reject(new DOMException('Cancelado', 'AbortError'))
    }

    signal?.addEventListener('abort', onAbort, { once: true })
  })
}
