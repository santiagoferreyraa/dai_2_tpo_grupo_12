import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router'

import { CrossIcon } from '@/features/navigation/icons'

import {
  dismissArrival,
  markNotificationRead,
  useNotificationArrivals,
} from './data/notificationsStore'
import { notificationLook } from './format'
import type { Notification } from './types'

/**
 * Los recuadros que aparecen arriba a la derecha cuando llega un aviso.
 *
 * **Son lo único de esta feature que existe en el celular**, y es a propósito: sin franja no hay
 * campanita, así que el recuadro es la única forma de enterarse de algo en el momento en que pasa.
 * En PC y tablet convive con la campanita y no la duplica —uno avisa, la otra guarda—.
 *
 * **Se van de tres formas: solos a los siete segundos, con la cruz, o deslizándolos al costado.**
 * Las tres hacen lo mismo —sacarlo de la cola de anuncios— y ninguna toca el buzón: el aviso sigue
 * ahí, sin leer, hasta que se lo lea o se lo borre desde la lista. Ver `Toast`.
 *
 * **Solo anuncian lo que llegó mientras la pantalla estaba abierta.** De eso se encarga el buzón
 * (ver `notificationsStore`), que no pone en la cola el historial que trae la primera consulta:
 * abrir la aplicación y recibir cinco recuadros de cosas de ayer sería el camino más corto a que
 * alguien aprenda a ignorarlos.
 *
 * Se monta una sola vez, en `App`, y no en cada pantalla: un aviso puede llegar en cualquiera.
 */

/** Cuánto se queda un recuadro antes de irse solo. */
const DISMISS_MS = 7_000

/**
 * Cuánto hay que arrastrar para que el recuadro se vaya, en píxeles y como fracción de su ancho.
 *
 * Los dos juntos, y gana el más grande: la fracción es lo que hace que el gesto se sienta igual en
 * un teléfono angosto y en el recuadro de 352 píxeles del escritorio, y el mínimo evita que en una
 * pantalla muy chica alcance con un temblor del dedo.
 *
 * Por debajo del umbral el recuadro vuelve a su lugar, que es lo que enseña que el gesto existe sin
 * castigar al que lo empezó sin querer.
 */
const SWIPE_OUT_RATIO = 0.3
const SWIPE_OUT_MIN_PX = 56

/**
 * Cuánto se puede mover el dedo antes de que esto cuente como arrastrar y no como tocar.
 *
 * Sin esta holgura, un toque con el pulgar —que nunca es perfectamente quieto— dejaría el recuadro
 * corrido dos píxeles y, peor, se comería el clic que abre el aviso.
 */
const DRAG_SLOP_PX = 6

/** Lo que tarda en salirse de pantalla una vez soltado. Tiene que coincidir con el CSS. */
const SWIPE_EXIT_MS = 180

/**
 * Cuántos se ven a la vez.
 *
 * Los que sobran no se pierden: esperan en la cola y entran cuando se va uno. Tres es lo que entra
 * en la esquina de un teléfono sin tapar media pantalla, y de todos modos llegar cuatro avisos en
 * siete segundos es una situación que no se va a resolver leyendo los cuatro.
 */
const MAX_VISIBLE = 3

export default function NotificationToasts() {
  const arrivals = useNotificationArrivals()
  const visible = arrivals.slice(0, MAX_VISIBLE)

  if (visible.length === 0) return null

  return createPortal(
    /*
      Arriba a la derecha, que es donde se esperan. En pantalla ancha arrancan debajo de la franja
      —que mide 80 píxeles— para no taparla; en el celular no hay franja y basta con el margen.

      `pointer-events-none` en la lista y `pointer-events-auto` en cada recuadro: sin eso, la
      columna entera —incluido el aire entre los recuadros y el hueco de abajo cuando hay uno solo—
      se come los clics de lo que haya debajo, que en el mapa es media pantalla.

      1180 los deja por encima de la franja (1140) y de lo que flota sobre el mapa, y por debajo de
      las ventanas modales (1200): un aviso no puede taparle a alguien el botón de confirmar.
    */
    <ul
      aria-live="polite"
      className="pointer-events-none fixed top-4 right-3 left-3 z-[1180] flex flex-col gap-2 md:top-24 md:right-6 md:left-auto md:w-88"
    >
      {visible.map((notification) => (
        <li key={notification.id} className="pointer-events-auto">
          <Toast notification={notification} />
        </li>
      ))}
    </ul>,
    document.body,
  )
}

/**
 * Un recuadro.
 *
 * Tocarlo lleva al buzón del perfil y lo marca como leído: quien reacciona a un aviso ya lo leyó,
 * y dejarlo contando como pendiente después de haberlo abierto es mentirle a la campanita.
 *
 * **Se puede deslizar para cualquiera de los dos lados, y eso es lo mismo que cerrarlo**: sale de la
 * cola de anuncios y el aviso queda en el buzón sin leer. No borra nada —para eso está el tacho de
 * la lista— y por eso los dos lados hacen lo mismo: no hay dos acciones que repartir entre ellos, y
 * un gesto que funciona para un lado y no para el otro se siente roto sin que se entienda por qué.
 *
 * **Por qué el gesto y no solo la cruz.** El recuadro aparece en el celular sin que nadie lo haya
 * pedido y muchas veces tapando justo lo que se estaba mirando. La cruz mide 28 píxeles y está en
 * una esquina; el manotazo hacia el costado es el gesto que todo el mundo ya trae aprendido de las
 * notificaciones del sistema, y se puede hacer con el pulgar sin mirar.
 */
function Toast({ notification }: { notification: Notification }) {
  const navigate = useNavigate()
  const { Icon, tint } = notificationLook(notification.type)

  /* Dónde arrancó el dedo, y de cuál dedo se trata: ver `setPointerCapture` más abajo. */
  const startX = useRef(0)
  const pointerId = useRef<number | null>(null)

  /*
   * Si este gesto ya pasó la holgura y por lo tanto es un arrastre.
   *
   * Es un ref y no un estado porque lo lee el `onClick`, que corre DESPUÉS del `pointerup` en el
   * mismo gesto: para cuando el clic pregunta, un estado todavía podría estar en el valor viejo y
   * soltar el recuadro después de arrastrarlo abriría el aviso.
   */
  const dragged = useRef(false)

  /** Cuánto está corrido, en píxeles. Cero es en su lugar. */
  const [offset, setOffset] = useState(0)

  /** Si el dedo está apoyado: mientras lo esté, el recuadro sigue el dedo sin transición. */
  const [dragging, setDragging] = useState(false)

  /** Si ya se soltó pasado el umbral y está saliendo. Corta el reloj y evita un segundo gesto. */
  const [leaving, setLeaving] = useState(false)

  /*
   * Cuánto mide de ancho el recuadro, medido al apoyar el dedo.
   *
   * Se guarda en un estado en vez de medirlo al dibujar porque medir al dibujar es leer el DOM
   * mientras React lo está armando: en el primer dibujo el elemento todavía no existe y la medida
   * sale cero, y si después cambia —al girar el teléfono, por ejemplo— nada vuelve a dibujar. Al
   * apoyar el dedo el elemento está a la vista y con su ancho definitivo, que es justo cuando hace
   * falta saberlo.
   */
  const [width, setWidth] = useState(0)

  /*
   * Se va solo a los siete segundos.
   *
   * El reloj arranca de nuevo si cambia el aviso porque cada recuadro tiene el suyo: son
   * componentes distintos con la misma forma, no uno que cambia de contenido.
   *
   * **Con el dedo apoyado el reloj no corre, y al soltar arranca de nuevo.** Que el recuadro
   * desaparezca en medio del gesto es lo más parecido a que la aplicación te saque algo de la mano;
   * y quien lo está tocando está justamente leyéndolo, así que los siete segundos contados desde que
   * lo suelta son más fieles a la intención que los contados desde que apareció.
   */
  useEffect(() => {
    if (dragging || leaving) return
    const timer = setTimeout(() => dismissArrival(notification.id), DISMISS_MS)
    return () => clearTimeout(timer)
  }, [notification.id, dragging, leaving])

  function open() {
    /* Soltar después de arrastrar no abre el aviso: fue un gesto de descarte que no llegó. */
    if (dragged.current) return
    markNotificationRead(notification.id)
    dismissArrival(notification.id)
    void navigate('/profile/notifications')
  }

  function onPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    /* Solo el botón principal del mouse. Con el derecho se abre el menú, no se arrastra nada. */
    if (event.pointerType === 'mouse' && event.button !== 0) return
    if (leaving) return

    startX.current = event.clientX
    dragged.current = false
    pointerId.current = event.pointerId
    setWidth(event.currentTarget.offsetWidth)
    setDragging(true)

    /*
     * Capturar el puntero es lo que hace que el gesto siga vivo cuando el dedo se sale del recuadro,
     * que con un recuadro de 60 píxeles de alto y un manotazo al costado es lo que pasa siempre.
     * Sin esto, los eventos dejan de llegar a mitad de camino y el recuadro queda colgado.
     */
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function onPointerMove(event: React.PointerEvent<HTMLDivElement>) {
    if (!dragging || event.pointerId !== pointerId.current) return

    const delta = event.clientX - startX.current
    if (Math.abs(delta) > DRAG_SLOP_PX) dragged.current = true
    setOffset(dragged.current ? delta : 0)
  }

  function onPointerUp(event: React.PointerEvent<HTMLDivElement>) {
    if (!dragging || event.pointerId !== pointerId.current) return

    pointerId.current = null
    setDragging(false)

    /* Medido de nuevo y acá: es un manejador de eventos, así que el DOM se puede leer sin riesgo. */
    const current = event.currentTarget.offsetWidth
    const threshold = Math.max(SWIPE_OUT_MIN_PX, current * SWIPE_OUT_RATIO)

    if (Math.abs(offset) < threshold) {
      /* No llegó: vuelve a su lugar, con la transición que le pone `.notification-toast-swipe`. */
      setOffset(0)
      return
    }

    /*
     * Llegó. Se termina de ir solo hasta pasar el borde y recién entonces se saca de la cola: si se
     * sacara ahora, el recuadro desaparecería de golpe desde la mitad del recorrido y el gesto se
     * cortaría en el aire. Con el ancho de más, lo que sale de pantalla sale entero, sombra incluida.
     */
    setLeaving(true)
    setOffset(Math.sign(offset) * (current + 48))
    setTimeout(() => dismissArrival(notification.id), SWIPE_EXIT_MS)
  }

  /*
   * El navegador se quedó con el gesto —casi siempre porque terminó siendo un scroll vertical de la
   * página, que `touch-action: pan-y` le deja hacer—. No es un descarte: el recuadro vuelve a su
   * lugar y el reloj arranca de nuevo.
   */
  function onPointerCancel() {
    pointerId.current = null
    setDragging(false)
    setOffset(0)
  }

  /*
   * Se desvanece a medida que se va, y para el final del recorrido ya casi no está. Es lo que
   * convierte el arrastre en "esto se está yendo" en lugar de "esto se está moviendo de lugar", y de
   * paso hace evidente cuánto falta para que el gesto cuente.
   */
  const fade = width === 0 ? 0 : Math.min(1, Math.abs(offset) / width)

  return (
    /*
      Es un `div` con botones adentro y no un botón grande, porque adentro hay DOS acciones —abrir
      y descartar— y un botón dentro de otro no es HTML válido: el navegador lo desarma y el de
      adentro deja de recibir sus clics.

      **`touch-action: pan-y` es lo que reparte el gesto con el navegador**: lo horizontal es de este
      componente y lo vertical sigue siendo scroll de la página. Sin eso, arrastrar el recuadro
      scrollearía la página atrás al mismo tiempo; con `none`, un recuadro que aparece sobre el mapa
      o sobre una lista dejaría un rectángulo donde no se puede scrollear.

      El `transform` va inline porque es un número que cambia con el dedo, y la transición en el CSS
      porque no: mientras se arrastra no hay ninguna —el recuadro tiene que seguir al dedo sin ir
      atrasado— y al soltar sí, que es lo que dibuja la vuelta a su lugar o la salida.

      Con el recuadro en su lugar no se escribe `transform` ninguno: la animación de entrada usa
      `transform` también, y pisarlo desde acá le cortaría el deslizamiento con el que aparece.
    */
    <div
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      style={{
        touchAction: 'pan-y',
        ...(offset === 0
          ? {}
          : { transform: `translateX(${String(offset)}px)`, opacity: 1 - fade }),
      }}
      className={`notification-toast glass-panel flex items-start gap-3 rounded-2xl px-3.5 py-3 shadow-lg ${
        dragging ? 'notification-toast-dragging' : 'notification-toast-swipe'
      }`}
    >
      <Icon className={`${tint} mt-0.5 h-5 w-5 shrink-0`} />

      <button
        type="button"
        onClick={open}
        className="focus-visible:outline-primary min-w-0 flex-1 cursor-pointer rounded-lg text-left focus-visible:outline-2"
      >
        <span className="text-text block text-sm font-bold">{notification.title}</span>
        {/*
          El cuerpo, recortado a dos renglones. El texto completo está en el buzón, a un clic de
          acá: un recuadro que crece con lo que diga el mensaje deja de ser un aviso y pasa a ser
          una ventana que tapa la pantalla sin que nadie la haya abierto.
        */}
        <span className="text-text-muted mt-0.5 line-clamp-2 block text-xs leading-relaxed">
          {notification.body}
        </span>
      </button>

      {/*
        La cruz se queda aunque se pueda deslizar. El gesto es más cómodo pero no está escrito en
        ninguna parte: no hay forma de descubrirlo sin probarlo, y con el teclado no existe.
      */}
      <button
        type="button"
        onClick={() => dismissArrival(notification.id)}
        aria-label="Descartar el aviso"
        className="text-text-muted hover:text-text focus-visible:outline-primary -mt-0.5 -mr-1 flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-lg transition-colors focus-visible:outline-2"
      >
        <CrossIcon className="h-4 w-4" />
      </button>
    </div>
  )
}
