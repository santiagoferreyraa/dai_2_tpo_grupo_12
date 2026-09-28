import { useCallback, useEffect, useRef, useState } from 'react'

import { askAgent } from './agentClient'
import type { ChatMessage, ChatStatus } from './types'

/**
 * El hilo de la conversación: los mensajes, en qué estado está y cómo se manda uno nuevo.
 *
 * Está separado del panel a propósito. El panel se monta y se desmonta cada vez que el chat se
 * abre y se cierra, y la conversación tiene que sobrevivir a eso: el hook vive en el componente
 * de afuera —`AgentAssistant`—, así que cerrar la burbuja no borra lo que se venía hablando.
 *
 * Todo lo que sabe de HTTP está en `agentClient.ts`. Acá solo se ordena qué se muestra.
 */

/** Cuántos mensajes se le mandan de contexto al agente. Los viejos dejan de viajar. */
const HISTORY_LIMIT = 20

function createMessage(role: ChatMessage['role'], text: string): ChatMessage {
  return { id: crypto.randomUUID(), role, text, at: Date.now() }
}

export interface AgentChat {
  messages: ChatMessage[]
  status: ChatStatus
  /** El texto del error, para el aviso con "Reintentar". `null` si no falló nada. */
  error: string | null
  send: (text: string) => void
  /** Vuelve a mandar el último mensaje del usuario, el que quedó sin respuesta. */
  retry: () => void
  /** Vacía la conversación. Lo usa el botón de la cabecera del panel. */
  reset: () => void
}

export function useAgentChat(): AgentChat {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [status, setStatus] = useState<ChatStatus>('idle')
  const [error, setError] = useState<string | null>(null)

  /*
   * El hilo también vive en una referencia, y no es redundante. Mandar un mensaje necesita la
   * lista de ese instante para armar el contexto, y ni `messages` —que es la del render en curso—
   * ni un actualizador funcional sirven: el actualizador puede ejecutarse dos veces en modo
   * estricto, y ahí saldrían dos peticiones por un mismo mensaje. Todo cambio pasa por `commit`,
   * así que las dos copias no se separan.
   */
  const thread = useRef<ChatMessage[]>([])

  const commit = useCallback((next: ChatMessage[]) => {
    thread.current = next
    setMessages(next)
  }, [])

  /*
   * La petición en curso. Se corta al desmontar y antes de empezar otra: sin esto, una respuesta
   * que llega tarde se agregaría a un hilo que ya se vació, o pisaría a la siguiente.
   */
  const pending = useRef<AbortController | null>(null)

  useEffect(() => () => pending.current?.abort(), [])

  const ask = useCallback(
    (text: string, history: ChatMessage[]) => {
      pending.current?.abort()
      const controller = new AbortController()
      pending.current = controller

      setStatus('sending')
      setError(null)

      askAgent({ message: text, history: history.slice(-HISTORY_LIMIT) }, controller.signal)
        .then((reply) => {
          if (controller.signal.aborted) return
          commit([...thread.current, createMessage('agent', reply.text)])
          setStatus('idle')
        })
        .catch((cause: unknown) => {
          /* Una cancelación no es una falla: la pidió la propia pantalla. */
          if (controller.signal.aborted) return
          setError(cause instanceof Error ? cause.message : 'No se pudo hablar con el agente')
          setStatus('error')
        })
    },
    [commit],
  )

  const send = useCallback(
    (raw: string) => {
      const text = raw.trim()
      if (text === '') return

      const next = [...thread.current, createMessage('user', text)]
      commit(next)
      ask(text, next)
    },
    [ask, commit],
  )

  const retry = useCallback(() => {
    const last = thread.current.findLast((message) => message.role === 'user')
    if (last !== undefined) ask(last.text, thread.current)
  }, [ask])

  const reset = useCallback(() => {
    pending.current?.abort()
    commit([])
    setStatus('idle')
    setError(null)
  }, [commit])

  return { messages, status, error, send, retry, reset }
}
