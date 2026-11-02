import { useNotifications } from './data/notificationsStore'

/**
 * El punto que titila sobre el ícono de "Notificaciones" cuando hay avisos sin leer.
 *
 * **Es el hermano de `ActiveBookingDot`**, y es el mismo dibujo a propósito: en el riel del perfil
 * los dos dicen lo mismo —"acá adentro hay algo"— y dos formas distintas de decirlo obligarían a
 * aprender dos códigos para la misma idea. Lo que cambia es la condición, no la marca.
 *
 * **En el celular es lo único que avisa que hay algo pendiente**, porque ahí no hay franja y por lo
 * tanto no hay campanita: el recuadro flotante pasa y se va, y este punto es lo que queda.
 *
 * Se posiciona en la esquina de arriba a la derecha de su contenedor, que tiene que ser `relative`.
 * No dibuja nada sin avisos sin leer, ni mientras el buzón todavía no llegó: un punto que aparece
 * y desaparece mientras carga la pantalla es peor que uno que llega medio segundo tarde.
 */
export default function UnreadNotificationsDot() {
  const state = useNotifications()

  if (state.status !== 'ready' || state.unreadCount === 0) return null

  return (
    <span aria-hidden="true" className="absolute -top-0.5 -right-0.5 flex h-2.5 w-2.5">
      <span className="bg-primary absolute inline-flex h-full w-full animate-ping rounded-full opacity-70 motion-reduce:animate-none" />
      <span className="bg-primary ring-background relative inline-flex h-2.5 w-2.5 rounded-full ring-2" />
    </span>
  )
}
