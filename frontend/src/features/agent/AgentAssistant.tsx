import { useCallback, useRef, useState } from 'react'

import AgentBubble from './AgentBubble'
import AgentChatPanel from './AgentChatPanel'
import { useAgentChat } from './useAgentChat'

/**
 * El asistente entero: la burbuja que está en todas las pantallas y el chat que abre.
 *
 * Lo monta `App.tsx` una sola vez, al lado de la navegación, y por eso vive acá la conversación:
 * el panel se desmonta cada vez que el chat se cierra, así que si el hilo estuviera adentro se
 * perdería lo hablado al cerrar sin querer. Acá arriba sobrevive a todo menos a recargar.
 *
 * Es también el único archivo de la feature que conoce a los otros dos. Ver `agentClient.ts` para
 * la costura con el backend.
 */
export default function AgentAssistant() {
  const [open, setOpen] = useState(false)
  const chat = useAgentChat()

  /* Al cerrar, el foco vuelve a la burbuja: sin esto cae al principio de la página. */
  const bubbleRef = useRef<HTMLButtonElement>(null)

  const close = useCallback(() => {
    setOpen(false)
    bubbleRef.current?.focus()
  }, [])

  return (
    <>
      <AgentBubble ref={bubbleRef} open={open} onToggle={() => (open ? close() : setOpen(true))} />
      {open && <AgentChatPanel chat={chat} onClose={close} />}
    </>
  )
}
