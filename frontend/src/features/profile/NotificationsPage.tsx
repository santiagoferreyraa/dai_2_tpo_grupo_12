import { useState } from 'react'

import ConfirmDialog from '@/components/ConfirmDialog'
import {
  removeAllNotifications,
  useNotifications,
} from '@/features/notifications/data/notificationsStore'
import NotificationList from '@/features/notifications/NotificationList'
import { ApiError } from '@/lib/api'

import ProfileCard from './components/ProfileCard'

/**
 * La sección "Notificaciones": el buzón del conductor adentro de la tarjeta del perfil.
 *
 * **Es un montaje, no una copia**, igual que Reservas y Medios de pago: traer los avisos, marcarlos
 * como leídos, borrarlos y dibujar cada renglón vive en `features/notifications`. Acá solo se decide
 * DÓNDE se muestra, y se resuelve lo que es de esta pantalla y no de la lista: el tope de alto en el
 * celular y el "Borrar todas".
 *
 * Es la misma lista que asoma en la ventanita de la campanita, sin recortar: allá se ven los
 * últimos y acá están todos, que es a lo que se viene cuando se entra a esta pantalla.
 *
 * **Es el único lugar donde el celular ve el historial.** En el celular no hay franja y por lo
 * tanto no hay campanita —ver `NotificationBell`—, así que el recuadro flotante avisa en el momento
 * y esta pantalla es donde queda lo que pasó.
 */
export default function NotificationsPage() {
  const state = useNotifications()
  const [confirming, setConfirming] = useState(false)
  const [emptying, setEmptying] = useState(false)
  const [emptyError, setEmptyError] = useState<string | null>(null)

  /*
   * El "Borrar todas" solo con algo que borrar, por lo mismo que el "Marcar todas" de la
   * campanita: un botón gris permanente arriba de un cartel de "no tenés avisos" es ocupar lugar
   * con nada. Mientras el buzón carga tampoco aparece: todavía no se sabe si hay algo.
   */
  const hasItems = state.status === 'ready' && state.items.length > 0

  async function emptyMailbox() {
    setEmptying(true)
    setEmptyError(null)
    try {
      await removeAllNotifications()
      setConfirming(false)
    } catch (cause) {
      /*
       * El cartel se cierra igual y el error se muestra en la tarjeta, detrás: el mismo criterio
       * que al eliminar una tarjeta en Medios de pago, y por el mismo motivo —un cartel abierto
       * con el error adentro invita a reintentar la llamada que acaba de fallar—.
       *
       * El buzón sigue completo detrás, porque vaciarlo no es optimista: no hay nada que deshacer.
       * Ver `removeAllNotifications`.
       */
      setConfirming(false)
      setEmptyError(cause instanceof ApiError ? cause.message : 'No pudimos borrar tus avisos.')
    } finally {
      setEmptying(false)
    }
  }

  return (
    <ProfileCard>
      {hasItems && (
        <header className="mb-3 flex shrink-0 items-center justify-end">
          <button
            type="button"
            onClick={() => {
              setEmptyError(null)
              setConfirming(true)
            }}
            className="text-text-muted hover:text-danger focus-visible:outline-primary cursor-pointer rounded-lg text-xs font-semibold transition-colors focus-visible:outline-2"
          >
            Borrar todas
          </button>
        </header>
      )}

      {/*
        **El tope de alto es del celular y es lo que evita que el buzón estire la pantalla.**

        En PC y tablet el tope ya está puesto más arriba y no hace falta acá: `ProfileLayout` le da
        a esta misma tarjeta `max-h-full overflow-y-auto`, así que una sección larga scrollea
        adentro del vidrio y la cabecera del perfil no se va de vista. Poner un segundo contenedor
        que scrollea adentro de ése daría dos barras anidadas, y por eso el `md:` lo apaga.

        En el celular es al revés: el que scrollea es el contenedor de afuera —la página entera— y
        la tarjeta mide lo que mide su contenido. Con veinte avisos, la tarjeta crece veinte
        renglones y la pantalla se estira hasta que el buzón deja de parecer una sección del perfil
        y pasa a ser una página sin fin. Con el tope, la tarjeta siempre mide lo mismo y los avisos
        scrollean adentro.

        `55svh` y no `vh`: en el celular la barra de direcciones del navegador aparece y desaparece
        al scrollear, y `vh` mide la ventana grande, así que el tope quedaría más alto que lo que de
        verdad se ve. Junto al relleno de la tarjeta y a esta cabecera, el total queda en el
        `min-h-[60svh]` que `ProfileLayout` ya le pide a la tarjeta de una sección abierta: la
        tarjeta no cambia de tamaño por tener uno o cien avisos.

        `overscroll-contain` corta el encadenado: llegado al final de la lista, el gesto se detiene
        ahí en vez de seguir scrolleando la página de atrás, que es lo que hace que uno pierda el
        lugar donde estaba leyendo.
      */}
      <div className="max-h-[55svh] min-h-0 overflow-y-auto overscroll-contain md:max-h-none md:overflow-visible">
        <NotificationList emptyHint="Cuando confirmes o canceles una reserva, te avisamos acá." />
      </div>

      {emptyError !== null && (
        <p role="alert" className="text-danger mt-3 shrink-0 text-xs">
          {emptyError}
        </p>
      )}

      {/*
        **Éste sí pregunta, a diferencia del tacho de cada renglón.** Lo que se va no está todo a la
        vista —el buzón puede tener cincuenta avisos y en pantalla hay seis— así que el gesto es
        mucho más chico que su consecuencia, y no hay "deshacer". El tacho de un renglón borra algo
        que se está viendo, y ahí el cartel sería un estorbo.
      */}
      <ConfirmDialog
        open={confirming}
        title="Borrar todos los avisos"
        message="Se van a borrar todos tus avisos, incluidos los que no entran en la pantalla. No se puede deshacer."
        confirmLabel="Borrar todas"
        busyLabel="Borrando…"
        busy={emptying}
        onConfirm={() => void emptyMailbox()}
        onCancel={() => setConfirming(false)}
      />
    </ProfileCard>
  )
}
