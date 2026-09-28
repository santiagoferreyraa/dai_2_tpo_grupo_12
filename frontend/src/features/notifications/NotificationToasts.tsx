import { useEffect } from 'react'
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
 */
function Toast({ notification }: { notification: Notification }) {
  const navigate = useNavigate()
  const { Icon, tint } = notificationLook(notification.type)

  /*
   * Se va solo a los siete segundos.
   *
   * El reloj arranca de nuevo si cambia el aviso porque cada recuadro tiene el suyo: son
   * componentes distintos con la misma forma, no uno que cambia de contenido.
   */
  useEffect(() => {
    const timer = setTimeout(() => dismissArrival(notification.id), DISMISS_MS)
    return () => clearTimeout(timer)
  }, [notification.id])

  function open() {
    markNotificationRead(notification.id)
    dismissArrival(notification.id)
    void navigate('/profile/notifications')
  }

  return (
    /*
      Es un `div` con un botón adentro y no un botón grande, porque adentro hay DOS acciones —abrir
      y descartar— y un botón dentro de otro no es HTML válido: el navegador lo desarma y el de
      adentro deja de recibir sus clics.
    */
    <div className="notification-toast glass-panel flex items-start gap-3 rounded-2xl px-3.5 py-3 shadow-lg">
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
