import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import { useSession } from '@/features/auth/session'
import { BellIcon } from '@/features/navigation/icons'

import { markAllNotificationsRead, useNotifications } from './data/notificationsStore'
import NotificationList from './NotificationList'

/**
 * La campanita de la franja de arriba, entre el interruptor de tema y la ficha del perfil.
 *
 * **Es un círculo y no otra pastilla alargada, y eso es lo que la ubica.** Sus dos vecinos son
 * rectángulos de puntas redondeadas: uno con dos íconos adentro y otro con una cara y un nombre.
 * Un tercer rectángulo entre ellos se leería como parte de la misma pieza; un círculo del mismo
 * alto se lee como un botón único, que es lo que es. El alto es el de la fila —48 píxeles, el del
 * buscador— así que no desalinea nada.
 *
 * **Solo aparece en PC y tablet**, porque solo aparece la franja que la contiene. En el celular no
 * hay dónde ponerla —la barra de abajo son las secciones de la aplicación, no un lugar para un
 * buzón— y no hace falta: el aviso llega igual como recuadro flotante, y el historial vive en el
 * perfil, a un toque de la misma barra.
 *
 * **Y solo con sesión**, como la ficha de al lado: sin entrar no hay buzón, y una campanita que
 * siempre está vacía es un botón que enseña a ignorarlo.
 */

/** El alto de la franja, que acá es también el ancho. Lo fija el buscador; ver `ProfilePill`. */
const SIZE = 'h-12 w-12'

/** Cuántos avisos asoman en la ventanita. El resto está en el perfil, a un clic de abajo. */
const PREVIEW_COUNT = 6

export default function NotificationBell() {
  const session = useSession()
  const state = useNotifications()
  const [open, setOpen] = useState(false)

  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  /*
   * Escape cierra y el foco vuelve al botón. Sin eso, quien navega con teclado cierra la ventanita
   * y queda parado en un elemento que ya no existe, así que el próximo tabulador arranca desde el
   * principio de la página.
   */
  useEffect(() => {
    if (!open) return

    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      setOpen(false)
      buttonRef.current?.focus()
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  /*
   * Un clic afuera cierra. Va en `pointerdown` y no en `click` para que cerrar la ventanita y tocar
   * lo que había debajo sean el mismo gesto, sin un clic de más.
   *
   * El `contains` mira el contenedor entero —botón incluido— y no solo el panel: si mirara solo el
   * panel, tocar el botón para cerrar contaría dos veces, una acá y otra en el `onClick`, y la
   * ventanita se cerraría y se volvería a abrir en el mismo gesto.
   */
  useEffect(() => {
    if (!open) return

    function onPointerDown(event: PointerEvent) {
      const container = containerRef.current
      if (container !== null && !container.contains(event.target as Node)) setOpen(false)
    }

    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  if (session === null) return null

  const unread = state.status === 'ready' ? state.unreadCount : 0

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="dialog"
        /*
          El número va en el nombre y no solo en el punto de color: un lector de pantalla no ve el
          punto, y "Avisos" a secas no distingue un buzón vacío de uno con cinco cosas sin leer.
        */
        aria-label={unread === 0 ? 'Avisos' : `Avisos, ${String(unread)} sin leer`}
        className={`glass-panel hover:border-primary/60 focus-visible:outline-primary ${SIZE} relative flex shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2`}
      >
        <BellIcon className={`h-5 w-5 ${open ? 'text-primary' : 'text-text'}`} />

        {/*
          El contador. Se dibuja solo si hay algo sin leer: un cero en la esquina es ruido que
          además enseña a no mirar la esquina.

          De diez para arriba dice "9+". No es por lugar —entra— sino porque el número exacto deja
          de significar nada: lo que cambia una decisión es "hay varios", y un 23 en un círculo de
          dieciocho píxeles solo se lee entrecerrando los ojos.

          `aria-hidden` porque lo mismo ya está dicho en el nombre del botón, y sin eso el lector
          anuncia el número dos veces seguidas.
        */}
        {unread > 0 && (
          <span
            aria-hidden="true"
            className="brand-fill text-on-brand absolute -top-0.5 -right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full px-1 text-[10px] font-bold"
          >
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Tus avisos"
          /*
            Cuelga del botón, anclada a la derecha: la franja termina ahí, así que abrirse hacia la
            izquierda es lo único que la deja entera en pantalla. El ancho es fijo y no un
            porcentaje porque lo que tiene adentro son renglones de texto, y una ventanita que
            cambia de ancho con la pantalla parte los títulos en lugares distintos cada vez.

            1150 la deja por encima de la franja (1140), que es su propio contenedor, y por debajo
            de las ventanas modales (1200), que tienen que taparla.
          */
          className="notification-popover glass-panel absolute top-full right-0 z-[1150] mt-2 flex max-h-[min(28rem,calc(100vh-7rem))] w-96 flex-col rounded-3xl p-2.5"
        >
          <header className="flex shrink-0 items-center gap-3 px-2 pt-1.5 pb-2">
            <p className="text-text flex-1 text-base leading-tight font-extrabold tracking-tight">
              Avisos
            </p>

            {/*
              "Marcar todas" solo cuando hay algo que marcar. Deshabilitado quedaría un botón gris
              permanente al lado de un título, que es una forma de ocupar lugar con nada.
            */}
            {unread > 0 && (
              <button
                type="button"
                onClick={markAllNotificationsRead}
                className="text-text-muted hover:text-primary focus-visible:outline-primary cursor-pointer rounded-lg text-xs font-semibold transition-colors focus-visible:outline-2"
              >
                Marcar todas
              </button>
            )}
          </header>

          <div className="min-h-0 flex-1 overflow-y-auto px-0.5">
            <NotificationList limit={PREVIEW_COUNT} emptyHint="No tenés avisos todavía." />
          </div>

          {/*
            El pie lleva al historial completo. Está siempre, también con la lista vacía: es la
            respuesta a "¿y los viejos?", y esconderlo justo cuando no se ve ninguno es esconderlo
            cuando más se pregunta.
          */}
          <footer className="mt-1 shrink-0 px-0.5 pt-1">
            <Link
              to="/profile/notifications"
              onClick={() => setOpen(false)}
              className="text-text-muted hover:text-primary focus-visible:outline-primary block rounded-xl py-2 text-center text-xs font-semibold transition-colors focus-visible:outline-2"
            >
              Ver todos
            </Link>
          </footer>
        </div>
      )}
    </div>
  )
}
