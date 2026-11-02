import { useLayoutEffect, useRef, useState } from 'react'

import { describeCard, formatExpiry, maskedFromLastFour } from '../format'
import type { PaymentMethod } from '../types'

import CardPreview from './CardPreview'
import { CARD_RATIO, PEEK_PX } from './CardStack'

interface CardPickerProps {
  cards: PaymentMethod[]
  selectedId: number | null
  onSelect: (card: PaymentMethod) => void
  disabled?: boolean
  /** El texto que nombra al grupo para el lector de pantalla. */
  label: string
}

/**
 * Cuánto pueden sumar entre todas las franjas de atrás, como mucho.
 *
 * La pila vive adentro de la revisión, y en pantalla ancha esa revisión es un diálogo: si cada
 * tarjeta sumara su franja entera, con cuatro o cinco el diálogo se estiraba hasta tener que
 * scrollear para llegar a Confirmar. Con un tope, más tarjetas las junta en vez de alargar.
 */
const PEEK_BUDGET_PX = 120

/**
 * La franja mínima. Por debajo de esto ya no se distingue una tarjeta de otra ni se le puede
 * acertar con el dedo; pasado ese punto, sí, la pila crece.
 */
const MIN_PEEK_PX = 24

/** Cuánto asoma cada tarjeta de atrás: la franja de la billetera, achicada si son muchas. */
function peekFor(count: number): number {
  if (count <= 1) return PEEK_PX
  return Math.max(MIN_PEEK_PX, Math.min(PEEK_PX, PEEK_BUDGET_PX / (count - 1)))
}

/**
 * Elegir con qué tarjeta pagar, con las tarjetas apiladas como en una billetera.
 *
 * Es la misma pila que `CardStack` —mismo asomo, misma proporción—, pero con otra regla: **la
 * elegida pasa adelante**, entera, y las demás quedan atrás mostrando la franja de arriba. Tocar
 * una de atrás la saca de la pila y la trae al frente, que es el gesto de sacar una tarjeta de la
 * billetera para pagar. La de adelante es siempre la que se va a usar: no hay un tilde aparte que
 * mirar para saberlo.
 *
 * Por eso el alto no cambia al elegir: siempre es una tarjeta entera más una franja por cada
 * otra, y las franjas se achican cuando son muchas (ver `peekFor`). En `CardStack` desplegar corre las de abajo; acá eso haría saltar los botones del
 * formulario cada vez que se cambia de tarjeta.
 *
 * **El hover levanta la tarjeta apenas**, para que se lea que se puede tocar. En el celular, que
 * no tiene hover, lo hace el toque mientras dura (`active`).
 *
 * Las vencidas aparecen apagadas y no se pueden elegir: esconderlas dejaría al conductor
 * buscando una tarjeta que sabe que cargó.
 */
export default function CardPicker({
  cards,
  selectedId,
  onSelect,
  disabled = false,
  label,
}: CardPickerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [cardHeight, setCardHeight] = useState(0)

  /* El alto se mide por lo mismo que en `CardStack`: depende del ancho disponible. */
  useLayoutEffect(() => {
    const container = containerRef.current
    if (container === null) return

    const observer = new ResizeObserver(([entry]) => {
      setCardHeight(entry.contentRect.width / CARD_RATIO)
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  /*
   * El lugar de cada tarjeta en la pila: las no elegidas en su orden, y la elegida al final, que
   * es adelante. Las claves de React no cambian, así que mover una es solo cambiarle el
   * `translateY`, y la transición hace el resto.
   */
  const order = [
    ...cards.filter((card) => card.id !== selectedId),
    ...cards.filter((card) => card.id === selectedId),
  ]
  const slotOf = (card: PaymentMethod) => order.indexOf(card)

  const peek = peekFor(cards.length)
  const stackHeight = cards.length === 0 ? 0 : (cards.length - 1) * peek + cardHeight

  return (
    <div
      ref={containerRef}
      role="radiogroup"
      aria-label={label}
      /*
        Sin transición de alto, a diferencia de `CardStack`. Acá el alto solo cambia al medirse por
        primera vez, y animado arrancaba en cero: durante esa animación las tarjetas, que van en
        absoluto, tapaban lo que viene abajo —la tolerancia y los botones—.
      */
      className="relative"
      style={{ height: stackHeight }}
    >
      {cards.map((card) => {
        const slot = slotOf(card)
        const selected = card.id === selectedId
        return (
          <div
            key={card.id}
            className="absolute inset-x-0 transition-transform duration-300 ease-out"
            style={{ transform: `translateY(${slot * peek}px)`, zIndex: slot }}
          >
            {/*
              El levantado va en un nodo propio y no en el que se posiciona, por lo mismo que el
              temblor de `CardStack`: los dos mueven la tarjeta, y en el mismo nodo uno pisaría al
              otro.
            */}
            <button
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={`${describeCard(card.brand, card.lastFour)}${card.expired ? ', vencida' : ''}`}
              disabled={disabled || card.expired}
              onClick={() => onSelect(card)}
              className="focus-visible:outline-primary block w-full cursor-pointer rounded-2xl text-left transition-transform duration-200 ease-out hover:-translate-y-1 focus-visible:outline-2 focus-visible:outline-offset-2 active:-translate-y-1 disabled:cursor-not-allowed disabled:hover:translate-y-0 motion-reduce:transition-none"
            >
              <CardPreview
                brand={card.brand}
                numberText={maskedFromLastFour(card.lastFour, card.brand)}
                holder={card.label === null ? undefined : { label: 'Etiqueta', value: card.label }}
                expiry={formatExpiry(card.expiryMonth, card.expiryYear)}
                expired={card.expired}
              />
            </button>
          </div>
        )
      })}
    </div>
  )
}
