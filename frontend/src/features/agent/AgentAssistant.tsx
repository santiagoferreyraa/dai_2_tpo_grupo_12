import { useCallback, useRef, useState } from 'react'

import AgentBubble from './AgentBubble'
import AgentChatPanel from './AgentChatPanel'
import { useAgentChat } from './useAgentChat'

/** Lo que dura la salida del panel; tiene que coincidir con `agent-panel-out` en `agent.css`. */
const CLOSE_MS = 260

/**
 * El asistente: la burbuja y el chat.
 *
 * El chat tiene tres momentos y no dos: cerrado, abierto y **cerrándose**. El tercero es lo que
 * deja que el panel se vaya con su animación —hacia la derecha en la PC— en vez de desaparecer de
 * golpe: queda montado lo que dura la salida y recién después se desmonta. Mientras tanto la
 * burbuja ya vuelve a aparecer en su lugar.
 *
 * La conversación vive acá y no en el panel, así que cerrar el chat no la borra.
 */
export default function AgentAssistant() {
  const [phase, setPhase] = useState<'closed' | 'open' | 'closing'>('closed')
  const chat = useAgentChat()

  /* Al cerrar, el foco vuelve a la burbuja: sin esto cae al principio de la página. */
  const bubbleRef = useRef<HTMLButtonElement>(null)

  const close = useCallback(() => {
    setPhase((current) => (current === 'open' ? 'closing' : current))
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.setTimeout(
      () => {
        setPhase((current) => (current === 'closing' ? 'closed' : current))
        bubbleRef.current?.focus()
      },
      reduced ? 0 : CLOSE_MS,
    )
  }, [])

  return (
    <>
      <AgentBubble ref={bubbleRef} open={phase === 'open'} onToggle={() => setPhase('open')} />
      {phase !== 'closed' && (
        <AgentChatPanel chat={chat} onClose={close} closing={phase === 'closing'} />
      )}
    </>
  )
}
