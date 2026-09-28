import NotificationList from '@/features/notifications/NotificationList'

import ProfileCard from './components/ProfileCard'

/**
 * La sección "Notificaciones": el buzón del conductor adentro de la tarjeta del perfil.
 *
 * **Es un montaje, no una copia**, igual que Reservas y Medios de pago: traer los avisos, marcarlos
 * como leídos y dibujar cada renglón vive en `features/notifications`. Acá solo se decide DÓNDE se
 * muestra.
 *
 * Es la misma lista que asoma en la ventanita de la campanita, sin recortar: allá se ven los
 * últimos y acá están todos, que es a lo que se viene cuando se entra a esta pantalla.
 *
 * **Es el único lugar donde el celular ve el historial.** En el celular no hay franja y por lo
 * tanto no hay campanita —ver `NotificationBell`—, así que el recuadro flotante avisa en el momento
 * y esta pantalla es donde queda lo que pasó.
 */
export default function NotificationsPage() {
  return (
    <ProfileCard>
      <NotificationList emptyHint="Cuando confirmes o canceles una reserva, te avisamos acá." />
    </ProfileCard>
  )
}
