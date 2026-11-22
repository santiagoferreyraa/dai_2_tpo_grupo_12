/**
 * Tipos de la feature Agente: la conversación y lo que viaja hacia el backend.
 *
 * Son los del FRONT, no los del backend. El contrato HTTP vive en `agentClient.ts` y se traduce
 * ahí: si el agente termina contestando otra forma —con fuentes, con acciones sugeridas—, cambia
 * ese archivo y no las pantallas.
 */

/** Quién dijo el mensaje. `agent` es el asistente; no hay mensajes de sistema a la vista. */
export type ChatRole = 'user' | 'agent'

export interface ChatMessage {
  /** Identidad estable para la lista de React. No es el id del backend: el front lo inventa. */
  id: string
  role: ChatRole
  text: string
  /** Momento en que se agregó, para la hora al costado del mensaje. */
  at: number
  /**
   * El sello que el agente le puso a su respuesta. Solo lo tienen los mensajes del agente, y hay
   * que devolverlo tal cual con el historial: el agente descarta las respuestas que vuelven sin
   * sello válido, que es como se defiende de un historial inventado. Ver `agent/src/signature.ts`.
   */
  signature?: string
}

/**
 * En qué está la conversación.
 *
 * `sending` es lo que dibuja los tres puntitos, y `error` lo que muestra el aviso con
 * "Reintentar". Se modela como un estado y no como dos booleanos sueltos porque son
 * excluyentes: no se puede estar esperando y fallado a la vez.
 */
export type ChatStatus = 'idle' | 'sending' | 'error'
