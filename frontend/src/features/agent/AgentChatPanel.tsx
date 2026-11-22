import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { useSession } from '@/features/auth/session'
import { CrossIcon } from '@/features/navigation/icons'
import { displayNameOf } from '@/lib/displayName'
import { useMediaQuery } from '@/lib/useMediaQuery'
import { RefreshIcon, SendIcon, SparkIcon } from './icons'
import type { AgentChat } from './useAgentChat'
import type { ChatMessage } from './types'

/**
 * El chat: la conversación con el agente.
 *
 * Son dos piezas distintas con el mismo contenido. En el celular es una hoja que tapa la pantalla
 * —el teclado ocupa la mitad de abajo, así que una ventanita chica no serviría— y en pantalla
 * ancha una ventana apoyada sobre la burbuja, que deja seguir usando la página de atrás. Por eso
 * el oscurecedor es solo del celular: ahí el chat es lo único que se puede tocar, y acá no.
 *
 * No guarda la conversación. Eso vive en `useAgentChat`, un piso más arriba, para que cerrar el
 * panel no borre lo que se venía hablando.
 */

/** Lo que se ofrece cuando todavía no se habló. Toque y manda, no rellena el campo. */
const SUGGESTIONS = [
  '¿Dónde puedo cargar cerca?',
  '¿Cuánto tarda una carga rápida?',
  'Quiero reservar un turno',
]

/** Hasta dónde crece el campo de texto antes de empezar a scrollear adentro. */
const COMPOSER_MAX_HEIGHT_PX = 120

interface AgentChatPanelProps {
  chat: AgentChat
  onClose: () => void
}

export default function AgentChatPanel({ chat, onClose }: AgentChatPanelProps) {
  const session = useSession()
  const isWide = useMediaQuery('(min-width: 768px)')

  const [draft, setDraft] = useState('')
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  /* El foco entra al campo de texto: el chat se abre para escribir. Al cerrar vuelve a la burbuja, y de eso se encarga `AgentAssistant`. */
  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  /* Escape cierra, como cualquier panel de la aplicación. */
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  /*
   * El scroll del fondo se bloquea SOLO en el celular, donde el chat tapa la pantalla. En
   * pantalla ancha el panel es una ventanita al costado y la página de atrás se sigue usando:
   * bloquearla ahí sería romper algo que no molesta a nadie.
   */
  useEffect(() => {
    if (isWide) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [isWide])

  /*
   * La conversación queda siempre pegada abajo. Va en un efecto de layout y no en uno común
   * porque tiene que pasar ANTES de que el navegador pinte: con el efecto normal se alcanza a ver
   * el mensaje nuevo entrando desde el borde.
   */
  useLayoutEffect(() => {
    const element = scrollRef.current
    if (element !== null) element.scrollTop = element.scrollHeight
  }, [chat.messages, chat.status])

  function submit(text: string) {
    chat.send(text)
    setDraft('')
    /* El campo vuelve a una línea: el alto lo maneja `grow`, que lee el contenido. */
    if (inputRef.current !== null) inputRef.current.style.height = 'auto'
    inputRef.current?.focus()
  }

  /* El campo crece con el texto hasta el tope. `auto` primero: sin eso nunca se achica al borrar. */
  function grow(element: HTMLTextAreaElement) {
    element.style.height = 'auto'
    element.style.height = `${Math.min(element.scrollHeight, COMPOSER_MAX_HEIGHT_PX)}px`
  }

  function onComposerKeyDown(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    /* Enter manda y Shift+Enter hace un renglón: lo que ya hace todo chat. */
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit(draft)
    }
  }

  const busy = chat.status === 'sending'
  const name = session === null ? null : displayNameOf(session).split(' ')[0]

  return createPortal(
    <>
      {/*
        El oscurecedor, solo en el celular. Cierra al tocarlo y no anuncia nada: el botón de
        cerrar del encabezado es el que hace eso mismo para quien no puede tocar acá.
      */}
      <div
        aria-hidden="true"
        onClick={onClose}
        className="agent-scrim fixed inset-0 z-[1155] bg-black/50 backdrop-blur-sm md:hidden"
      />

      {/*
        El panel está armado **como el recuadro del vehículo de la home** (`VehicleHero`): un vidrio
        dentro de otro. Afuera un marco de `rounded-3xl` bien translúcido, del ancho del relleno; y
        adentro, ocupando todo lo que queda, UNA sola hoja con más cuerpo donde vive el chat entero.

        Son dos piezas y no cinco. La versión anterior dejaba el encabezado, la conversación y el
        campo de texto flotando sueltos sobre el fondo del panel, cada uno con su vidrio: el chat
        quedaba picado en franjas, con el fondo de la página asomando entre ellas. Acá el contenido
        es un bloque continuo —lo que separa el encabezado del resto es una línea, no un hueco— y el
        marco es lo único que deja pasar la página.

        Antes llevaba `glass-modal` fijo y eso lo dejaba casi opaco, como una ventana apoyada encima
        de la aplicación en vez de una pieza más de ella.

        `glass-modal` se conserva SOLO en el celular, y no es una excepción caprichosa: ahí el chat
        se apoya sobre el oscurecedor, y el 32% de `glass-panel` sobre un negro al 50% se lee gris
        sucio en el tema claro —es exactamente el caso que ese ajuste existe para resolver, ver
        `.glass-modal` en `index.css`—. En pantalla ancha no hay oscurecedor: abajo está la página,
        que es el mismo fondo contra el que están medidas las tarjetas de la home.
      */}
      <div
        role="dialog"
        /* Modal solo donde de verdad lo es: en el celular tapa todo, en pantalla ancha no. */
        aria-modal={isWide ? undefined : true}
        aria-label="Chat con EcoBot"
        className={`agent-panel glass-panel fixed inset-x-3 top-16 bottom-3 z-[1160] flex origin-bottom flex-col overflow-hidden rounded-3xl p-2.5 md:inset-auto md:right-6 md:bottom-28 md:h-[min(32rem,calc(100vh-11rem))] md:w-96 md:origin-bottom-right ${
          isWide ? '' : 'glass-modal'
        }`}
      >
        {/*
          La hoja: todo el chat en una sola pieza de vidrio, apoyada sobre el marco. `overflow-hidden`
          es lo que hace que la conversación se corte contra sus esquinas redondeadas y no contra las
          del panel.
        */}
        <div className="agent-sheet glass-panel flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl">
          {/*
          El encabezado: quién contesta y los dos botones. `shrink-0` en el encabezado y en el
          campo de abajo es lo que deja que crezca la lista del medio y no ellos.

          No lleva línea abajo: la hoja es una sola pieza de vidrio y el encabezado no es una zona
          aparte sino su primer renglón. Lo que lo despega de la conversación es el aire, que a esta
          altura alcanza —el nombre en negrita ya se lee como un techo—.

          Las estrellas van **sueltas y en verde, sin la pastilla de atrás**, por lo mismo que los
          íconos de la ficha del vehículo: adentro de algo que ya es de vidrio, un recuadro tenue es
          una caja dentro de otra caja. El dibujo grande sostiene solo el mismo peso.

          El nombre y el estado copian la pareja rótulo/valor de la ficha —el chico apagado arriba,
          el grande compacto abajo—, solo que dados vuelta: acá lo que manda es el nombre.
        */}
          <header className="flex shrink-0 items-center gap-3 px-3.5 pt-3 pb-1">
            <SparkIcon className="text-primary h-8 w-8 shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-text truncate text-base leading-tight font-extrabold tracking-tight">
                EcoBot
              </p>
              <p className="text-text-muted truncate text-[11px] font-medium">
                {busy ? 'Escribiendo…' : 'Preguntale lo que quieras'}
              </p>
            </div>

            <button
              type="button"
              onClick={chat.reset}
              disabled={chat.messages.length === 0}
              aria-label="Empezar una conversación nueva"
              className="text-text-muted hover:text-text hover:bg-surface/70 focus-visible:outline-primary flex h-9 w-9 cursor-pointer items-center justify-center rounded-xl transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-30"
            >
              <RefreshIcon className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar el chat"
              className="text-text-muted hover:text-text hover:bg-surface/70 focus-visible:outline-primary flex h-9 w-9 cursor-pointer items-center justify-center rounded-xl transition-colors focus-visible:outline-2"
            >
              <CrossIcon className="h-5 w-5" />
            </button>
          </header>

          {/*
          La conversación.

          `aria-live="polite"` es lo que hace que un lector de pantalla lea la respuesta cuando
          llega: el foco se queda en el campo de texto, así que sin esto el mensaje aparecería en
          silencio.
        */}
          <div
            ref={scrollRef}
            aria-live="polite"
            className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-3.5 py-4"
          >
            {chat.messages.length === 0 ? (
              <div className="flex flex-1 flex-col justify-end gap-4">
                {/*
                El saludo está escrito con la misma jerarquía que la portada del recuadro del
                vehículo: el renglón chico apagado arriba, el título grande y compacto abajo, y
                recién después la explicación. Es la misma pieza diciendo otra cosa.
              */}
                <div>
                  <p className="text-text-muted text-sm font-medium">
                    {name === null ? 'Hola 👋' : `Hola, ${name} 👋`}
                  </p>
                  <p className="text-text mt-1 text-2xl leading-none font-extrabold tracking-tight">
                    ¿En qué te ayudo?
                  </p>
                  <p className="text-text-muted mt-3 text-sm leading-relaxed">
                    Puedo buscarte estaciones, contarte cómo funciona una carga y ayudarte con tus
                    reservas.
                  </p>
                </div>

                {/*
                Las sugerencias mandan directo: son un atajo, no un autocompletado.

                Son el mismo botón secundario que las tarjetas de la home —vidrio y `rounded-xl`,
                ver `NextChargeCard`—, no unas pastillas propias del chat.

                El realce del mouse es la TINTA y no el borde. `.glass-panel` vive fuera de las
                capas de Tailwind y escribe `border` entero, así que le gana a cualquier
                `hover:border-*` de una clase: el borde verde no se pintaría nunca. El color del
                texto sí, porque el vidrio no lo toca.
              */}
                <ul className="flex flex-wrap gap-2">
                  {SUGGESTIONS.map((suggestion) => (
                    <li key={suggestion}>
                      <button
                        type="button"
                        onClick={() => submit(suggestion)}
                        className="glass-panel text-text hover:text-primary focus-visible:outline-primary cursor-pointer rounded-xl px-3.5 py-2 text-xs font-semibold transition-colors focus-visible:outline-2"
                      >
                        {suggestion}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              chat.messages.map((message) => <MessageRow key={message.id} message={message} />)
            )}

            {busy && <TypingIndicator />}

            {chat.status === 'error' && (
              /* El mismo vidrio que todo lo demás; lo que avisa que algo salió mal es la tinta roja.
               El borde no puede ser rojo: lo escribe `.glass-panel`, que le gana a la clase. */
              <div className="glass-panel text-danger flex items-center gap-3 rounded-2xl px-3.5 py-2.5 text-xs">
                <span className="flex-1">{chat.error}</span>
                <button
                  type="button"
                  onClick={chat.retry}
                  className="focus-visible:outline-danger cursor-pointer font-semibold underline underline-offset-2 focus-visible:outline-2"
                >
                  Reintentar
                </button>
              </div>
            )}
          </div>

          {/*
          El campo de texto.

          Es un `<form>` y no un botón suelto: en el celular, eso es lo que le pone "Enviar" a la
          tecla de acción del teclado.
        */}
          <form
            onSubmit={(event) => {
              event.preventDefault()
              submit(draft)
            }}
            className="border-border/50 flex shrink-0 items-end gap-2 border-t px-2.5 py-2.5"
          >
            <textarea
              ref={inputRef}
              value={draft}
              rows={1}
              onChange={(event) => {
                setDraft(event.target.value)
                grow(event.target)
              }}
              onKeyDown={onComposerKeyDown}
              placeholder="Escribí tu mensaje…"
              aria-label="Mensaje para el asistente"
              className="text-text placeholder:text-text-muted/70 focus-visible:outline-primary max-h-30 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm leading-snug outline-none focus-visible:rounded-xl focus-visible:outline-2"
            />
            <button
              type="submit"
              disabled={draft.trim() === '' || busy}
              aria-label="Enviar mensaje"
              className="brand-fill text-on-brand focus-visible:outline-primary flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-xl transition-transform focus-visible:outline-2 focus-visible:outline-offset-2 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <SendIcon className="h-5 w-5" />
            </button>
          </form>
        </div>
      </div>
    </>,
    document.body,
  )
}

/**
 * Un mensaje.
 *
 * El del usuario va a la derecha y relleno con el verde de la marca; el del agente a la izquierda,
 * en el mismo vidrio que el resto del panel. Son los dos rellenos que usan las tarjetas de la home
 * —`brand-fill` para lo principal, `glass-panel` para lo demás— y no una paleta propia del chat.
 *
 * La esquina que mira al costado propio queda menos redondeada: es lo que hace de "pico" y deja
 * claro de qué lado viene cada uno sin escribir un nombre arriba.
 */
function MessageRow({ message }: { message: ChatMessage }) {
  const mine = message.role === 'user'

  return (
    <div className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
      <p
        /* La hora no ocupa lugar en pantalla: aparece al apoyar el puntero encima. */
        title={new Date(message.at).toLocaleTimeString('es-AR', {
          hour: '2-digit',
          minute: '2-digit',
        })}
        className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed break-words whitespace-pre-wrap ${
          mine ? 'brand-fill text-on-brand rounded-br-md' : 'glass-panel text-text rounded-bl-md'
        }`}
      >
        {message.text}
      </p>
    </div>
  )
}

/** Los tres puntitos mientras el agente piensa. Del lado del agente, como si fuera su mensaje. */
function TypingIndicator() {
  return (
    <div className="flex justify-start">
      <span
        role="status"
        aria-label="El asistente está escribiendo"
        className="glass-panel flex items-center gap-1.5 rounded-2xl rounded-bl-md px-3.5 py-3"
      >
        <span className="agent-typing-dot bg-text-muted h-1.5 w-1.5 rounded-full" />
        <span className="agent-typing-dot bg-text-muted h-1.5 w-1.5 rounded-full" />
        <span className="agent-typing-dot bg-text-muted h-1.5 w-1.5 rounded-full" />
      </span>
    </div>
  )
}
