import { useEffect, useLayoutEffect, useRef, useState } from 'react'

import { describeCard, formatExpiry, maskedFromLastFour } from '../format'
import type { PaymentMethod } from '../types'

import CardPreview from './CardPreview'

interface CardStackProps {
  cards: PaymentMethod[]
  onRemove: (card: PaymentMethod) => void
}

/** Cuánto de cada tarjeta asoma por debajo de la que tiene encima. Lo comparte `CardPicker`. */
export const PEEK_PX = 72

/** La proporción de una tarjeta real: 85,60 × 53,98 mm. La misma que usa `CardPreview`. */
export const CARD_RATIO = 1.586

/**
 * Cuánto hay que mantener el dedo apoyado para entrar al modo de eliminar.
 *
 * 600 ms es lo que usan los sistemas para el mismo gesto: alcanza para que un toque normal
 * —incluso uno lento— no lo dispare, y es lo bastante corto como para que no parezca que la
 * pantalla se colgó. Con dos segundos, quien no sabía cuánto había que esperar soltaba antes.
 */
const LONG_PRESS_MS = 600

/**
 * Cuánto se puede mover el dedo sin que la pulsación se cancele.
 *
 * No es cero porque un dedo apoyado dos segundos nunca queda perfectamente quieto: exigir
 * inmovilidad haría que el gesto fallara casi siempre. Pasado este margen ya no es una
 * pulsación sino un scroll, y ahí sí hay que soltar.
 */
const MOVE_TOLERANCE_PX = 12

/**
 * Un número entre 0 y 1, siempre el mismo para la misma entrada.
 *
 * Se usa para que cada tarjeta tiemble a su ritmo. Tiene que ser estable entre renders y no
 * `Math.random()`: un valor nuevo en cada render reinicia la animación desde cero, y el temblor
 * se ve como una sacudida a los saltos en vez de un movimiento continuo. Derivándolo del id, la
 * misma tarjeta tiembla siempre igual.
 *
 * El seno multiplicado por un número grande es el truco de siempre para esto: mezcla lo
 * suficiente como para que ids consecutivos —que es exactamente lo que devuelve la base— caigan
 * en lugares bien distintos del rango.
 */
function pseudoRandom(seed: number): number {
  const mixed = Math.sin(seed * 12.9898) * 43758.5453
  return mixed - Math.floor(mixed)
}

/**
 * El ritmo propio de cada tarjeta: cuánto tarda un ciclo, en qué punto arranca y para qué lado.
 *
 * **La duración distinta es lo que de verdad las despega.** Con la misma duración, dos tarjetas
 * que arrancan desfasadas mantienen ese desfase para siempre y el ojo termina leyendo el patrón;
 * con duraciones distintas se van corriendo solas y no vuelven a coincidir nunca.
 *
 * El retraso es negativo a propósito: eso no demora el arranque, sino que empieza la animación
 * ya empezada, en un punto distinto del ciclo. Con retrasos positivos las tarjetas quedarían
 * quietas un instante antes de moverse, que se ve como que la pantalla responde a destiempo.
 */
function jiggleRhythm(id: number): React.CSSProperties {
  const speed = pseudoRandom(id)
  const phase = pseudoRandom(id + 97)

  const duration = 0.22 + speed * 0.12

  return {
    animationDuration: `${duration}s`,
    animationDelay: `${(-phase * duration).toFixed(3)}s`,
    animationDirection: phase > 0.5 ? 'reverse' : 'normal',
  }
}

/**
 * Las tarjetas guardadas apiladas como en una billetera, para el celular.
 *
 * **Por qué apiladas y no una lista.** En una pantalla angosta, tres tarjetas dibujadas enteras
 * son tres pantallazos de scroll para ver algo que se resuelve mirando. Apiladas entran todas de
 * una: de cada una asoma la franja de arriba, que es justo donde están la marca y el nombre, y
 * la última se ve completa. Es cómo se ven las tarjetas en una billetera de verdad, y por eso
 * no hay que explicar el gesto.
 *
 * **Un toque despliega; una pulsación larga habilita eliminar.** Son dos gestos separados
 * porque son dos intenciones distintas y una de las dos borra datos. Mantener el dedo dos
 * segundos pone todas las tarjetas a temblar y les aparece una cruz roja arriba a la derecha,
 * igual que los íconos de un teléfono; de ahí se sale con "Listo", tocando fuera o con Escape.
 *
 * **La pulsación larga no es descubrible por sí sola**, así que la pantalla lo dice: hay un
 * renglón abajo de la pila que explica el gesto. Un gesto oculto que es la única forma de
 * borrar una tarjeta sería una función que no existe para quien no la conoce.
 *
 * **El alto se mide, no se supone.** Las posiciones son píxeles absolutos, y el alto de la
 * tarjeta depende del ancho disponible por su proporción. Se lee del contenedor con un
 * `ResizeObserver` en vez de fijar un número: escrito a mano, la pila se desarma en cuanto
 * alguien cambia un padding o gira el teléfono.
 */
export default function CardStack({ cards, onRemove }: CardStackProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [cardHeight, setCardHeight] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [removingModeAsked, setRemovingMode] = useState(false)

  /*
   * Derivado y no un estado propio: si se eliminó la última tarjeta, el modo no tiene sobre qué
   * aplicarse y se apaga solo. Apagarlo desde un efecto sería un render de más y, sobre todo,
   * un cuadro intermedio con el modo activo sin ninguna tarjeta debajo.
   */
  const removingMode = removingModeAsked && cards.length > 0

  /* La pulsación en curso: desde dónde empezó y el temporizador que la va a convertir en larga. */
  const press = useRef<{ x: number; y: number; timer: number } | null>(null)

  useLayoutEffect(() => {
    const container = containerRef.current
    if (container === null) return

    const observer = new ResizeObserver(([entry]) => {
      setCardHeight(entry.contentRect.width / CARD_RATIO)
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  /* Salir del modo con Escape, igual que de cualquier otra cosa que se abre encima. */
  useEffect(() => {
    if (!removingMode) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setRemovingMode(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [removingMode])

  function cancelPress() {
    if (press.current === null) return
    window.clearTimeout(press.current.timer)
    press.current = null
  }

  function startPress(event: React.PointerEvent) {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    cancelPress()

    const timer = window.setTimeout(() => {
      press.current = null
      setRemovingMode(true)

      /*
       * Un golpecito del motor de vibración, si el teléfono lo tiene. Es lo que confirma que el
       * gesto se completó sin obligar a mirar: la mano ya está tapando parte de la pantalla.
       * No todos los navegadores la exponen, y en ninguno es imprescindible.
       */
      navigator.vibrate?.(30)
    }, LONG_PRESS_MS)

    press.current = { x: event.clientX, y: event.clientY, timer }
  }

  function movePress(event: React.PointerEvent) {
    const start = press.current
    if (start === null) return

    const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y)
    if (moved > MOVE_TOLERANCE_PX) cancelPress()
  }

  /*
   * Un toque corto despliega la tarjeta; uno que ya disparó el modo de eliminar, no. Sin esta
   * distinción, completar la pulsación larga dejaría además una tarjeta abierta que nadie pidió.
   */
  function handleCardClick(card: PaymentMethod) {
    if (removingMode) return
    setExpandedId(card.id === expandedId ? null : card.id)
  }

  const expandedIndex = cards.findIndex((card) => card.id === expandedId)

  /*
   * Dónde arranca cada tarjeta. Apiladas es una franja por cada una que quedó atrás; con una
   * desplegada, las que están DESPUÉS se corren hacia abajo lo que falta para que se vea
   * entera. Las anteriores no se mueven: si se moviera toda la pila, la tarjeta que se acaba
   * de tocar se iría de donde estaba el dedo.
   */
  function offsetOf(index: number): number {
    const stacked = index * PEEK_PX
    if (expandedIndex === -1 || index <= expandedIndex) return stacked
    return stacked + (cardHeight - PEEK_PX)
  }

  const stackHeight = cards.length === 0 ? 0 : offsetOf(cards.length - 1) + cardHeight

  return (
    <div>
      {removingMode && (
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="text-text-muted text-sm">Tocá la cruz de la tarjeta que querés eliminar.</p>
          <button
            type="button"
            onClick={() => setRemovingMode(false)}
            className="text-primary shrink-0 text-sm font-semibold"
          >
            Listo
          </button>
        </div>
      )}

      <div
        ref={containerRef}
        className="relative transition-[height] duration-300"
        style={{ height: stackHeight }}
      >
        {cards.map((card, index) => (
          <div
            key={card.id}
            className="absolute inset-x-0 transition-transform duration-300 ease-out"
            style={{
              transform: `translateY(${offsetOf(index)}px)`,
              /*
                Las de más abajo se dibujan encima, que es como se apilan las cartas en la mano.
                Sin esto el orden lo decide el DOM y la sombra de cada una cae del lado
                equivocado.
              */
              zIndex: index,
            }}
          >
            {/*
              El temblor va en un envoltorio propio y no en el mismo nodo que se posiciona: los
              dos usan `transform`, así que compartir nodo hace que la animación pise el
              desplazamiento de la pila y las tarjetas salten al apilarse.

              Cada tarjeta tiembla a su propio ritmo —ver `jiggleRhythm`—, porque todas iguales
              se leen como una sola pieza sacudiéndose en vez de varias tarjetas sueltas.
            */}
            <div
              /* `relative` para que la cruz se ubique contra la tarjeta y tiemble con ella. */
              className={`relative ${removingMode ? 'card-jiggle' : ''}`}
              style={removingMode ? jiggleRhythm(card.id) : undefined}
            >
              <button
                type="button"
                onClick={() => handleCardClick(card)}
                onPointerDown={startPress}
                onPointerMove={movePress}
                onPointerUp={cancelPress}
                onPointerCancel={cancelPress}
                onPointerLeave={cancelPress}
                // El texto real de la tarjeta vive acá: `CardPreview` es aria-hidden.
                aria-label={`${describeCard(card.brand, card.lastFour)}${card.expired ? ', vencida' : ''}`}
                aria-expanded={card.id === expandedId}
                className="block w-full text-left"
              >
                <CardPreview
                  brand={card.brand}
                  numberText={maskedFromLastFour(card.lastFour, card.brand)}
                  holder={
                    card.label === null ? undefined : { label: 'Etiqueta', value: card.label }
                  }
                  expiry={formatExpiry(card.expiryMonth, card.expiryYear)}
                  expired={card.expired}
                />
              </button>

              {/*
                La cruz va fuera del botón de la tarjeta y no adentro: anidar un botón dentro de
                otro es HTML inválido, y el clic terminaría disparando los dos.

                Sale medio botón por afuera de la esquina, como los íconos de un teléfono. Es lo
                que hace que se lea como algo pegado encima de la tarjeta y no como parte de su
                diseño — que ya tiene un logo en la otra esquina.
              */}
              {removingMode && (
                <button
                  type="button"
                  onClick={() => onRemove(card)}
                  aria-label={`Eliminar ${describeCard(card.brand, card.lastFour)}`}
                  className="absolute -top-2 -right-2 flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-[#d7524f] text-white shadow-lg transition-transform active:scale-90"
                >
                  <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
                    <path
                      d="M6 6l12 12M18 6L6 18"
                      stroke="currentColor"
                      strokeWidth="3.2"
                      strokeLinecap="round"
                    />
                  </svg>
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* El renglón que hace descubrible la pulsación larga. Ver el comentario del componente. */}
      {!removingMode && cards.length > 0 && (
        <p className="text-text-muted mt-4 text-center text-xs">
          Mantené pulsada una tarjeta para eliminarla.
        </p>
      )}
    </div>
  )
}
