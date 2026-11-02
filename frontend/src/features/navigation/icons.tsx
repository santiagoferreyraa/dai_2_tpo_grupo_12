/**
 * Los íconos de la navegación, dibujados a mano.
 *
 * No hay librería de íconos en el proyecto y no se agrega una por cinco dibujos: sumar una
 * dependencia va en un PR aparte (regla 7 del README) y arrastra un paquete entero para algo
 * que son cinco `path`. El trazo es el mismo que ya usa la lupa de `StationSearch`, así que
 * los íconos nuevos no desentonan con los que ya están en pantalla.
 *
 * Todos comparten la misma firma: heredan el color con `currentColor` y el tamaño se decide
 * afuera, con `className`. Así el mismo ícono sirve verde sobre el círculo activo y gris
 * apagado en el resto de la barra, sin que acá haya un solo color escrito.
 */

interface IconProps {
  className?: string
}

/**
 * `aria-hidden` en los cinco: al lado de cada ícono viaja su etiqueta de texto —visible en
 * escritorio, para lector de pantalla en el celular—, así que anunciarlos sería repetir.
 */
const BASE = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.75,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

/** Home: una casa. */
export function HomeIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5.5 9.5V20a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V9.5" />
      <path d="M9.5 21v-6h5v6" />
    </svg>
  )
}

/**
 * Estaciones: un surtidor con un rayo adentro.
 *
 * Es el ícono universal de la estación de carga y por eso no es una batería ni un enchufe:
 * la pantalla lista lugares donde cargar, no conectores sueltos.
 */
export function StationsIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16" />
      <path d="M3 21h12" />
      <path d="m9.7 8.2-2.2 3.4h3l-2.2 3.4" />
      <path d="M14 12h3a2 2 0 0 1 2 2v2.5a1.5 1.5 0 0 0 3 0V9l-3-3" />
    </svg>
  )
}

/** Mapa: el pin de siempre. El mismo gesto que marca las estaciones sobre el mapa real. */
export function MapIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M12 21s7-5.6 7-11a7 7 0 1 0-14 0c0 5.4 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.6" />
    </svg>
  )
}

/** Contacto: un sobre. */
export function ContactIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="m3.8 7 7.1 5.3a2 2 0 0 0 2.2 0L20.2 7" />
    </svg>
  )
}

/** Perfil: la silueta de siempre. */
export function ProfileIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.8 20a7.2 7.2 0 0 1 14.4 0" />
    </svg>
  )
}

/**
 * El rayo del cuadrado del perfil en escritorio.
 *
 * Va relleno y sin trazo, al revés que los otros cinco: es el único que se dibuja sobre el
 * verde de la marca y a ese tamaño un contorno se ensucia.
 */
export function BoltIcon({ className }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M13.8 2 4.6 13.4a.7.7 0 0 0 .55 1.14h4.3l-1.25 7.3a.7.7 0 0 0 1.25.54l9.2-11.4a.7.7 0 0 0-.55-1.14h-4.3l1.25-7.3A.7.7 0 0 0 13.8 2Z" />
    </svg>
  )
}

/** Sol: el tema claro, en el interruptor de tema. */
export function SunIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
    </svg>
  )
}

/** Luna: el tema oscuro, en el interruptor de tema. */
export function MoonIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M20 13.4A8.2 8.2 0 1 1 10.6 4a6.6 6.6 0 0 0 9.4 9.4Z" />
    </svg>
  )
}

/**
 * Conector: un enchufe con dos patas.
 *
 * Es un enchufe y no un surtidor a propósito: al lado dice "CCS2", que es el tipo de boca del
 * auto, no el lugar donde se carga. El surtidor ya significa otra cosa en esta aplicación —la
 * sección de estaciones—, y repetirlo acá diría que el dato es dónde cargar.
 */
export function PlugIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M9 3v5" />
      <path d="M15 3v5" />
      <path d="M6 8h12v3a6 6 0 0 1-6 6 6 6 0 0 1-6-6V8Z" />
      <path d="M12 17v4" />
    </svg>
  )
}

/** Medidor: la aguja de un tablero, para la potencia máxima que el auto acepta. */
export function GaugeIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 18a8 8 0 1 1 16 0" />
      <path d="m12 18 4.5-5" />
      <path d="M12 18h.01" />
    </svg>
  )
}

/** Batería: la capacidad del paquete, que es dato de catálogo y no una lectura del auto. */
export function BatteryIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <rect x="2" y="8" width="16" height="9" rx="2" />
      <path d="M21 11v3" />
      <path d="M6 11.5v2" />
      <path d="M10 11.5v2" />
    </svg>
  )
}

/** Reloj: los tiempos y las ventanas de reserva, en la home. */
export function ClockIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 1.8" />
    </svg>
  )
}

/** Escudo con tilde: la compatibilidad del auto con el conector de una estación. */
export function ShieldCheckIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M12 2.75 5 5.5v5.2c0 4.3 2.9 8.3 7 9.55 4.1-1.25 7-5.25 7-9.55V5.5l-7-2.75Z" />
      <path d="m9 11.6 2.2 2.2L15.2 9.8" />
    </svg>
  )
}

/** Barras de un gráfico: la actividad de carga del conductor. */
export function ChartIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 20h16" />
      <path d="M7 20v-6M12 20V6M17 20v-9" />
    </svg>
  )
}

/**
 * Volante: el conductor, en su ficha de la franja de arriba.
 *
 * Reemplaza al rayo que estaba ahí. El rayo es el símbolo de la CARGA y ya aparece en la ficha
 * del vehículo, en los pines del mapa y en las tarjetas de estación: en el lugar donde va el
 * avatar decía "electricidad" cuando lo que tiene que decir es "sos vos, y manejás".
 *
 * Tres rayos a las 12, las 4 y las 8, que es el volante de tres brazos de cualquier auto. Los
 * números salen de proyectar cada ángulo entre el radio del cubo y el de la llanta, así que
 * arrancan justo donde termina el círculo del medio y no lo pisan.
 */
export function SteeringWheelIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="2.75" />
      <path d="M12 9.25V3.5M9.62 13.38 4.64 16.25M14.38 13.38 19.36 16.25" />
    </svg>
  )
}

/**
 * Campana: las notificaciones del perfil.
 *
 * Sin badge ni punto rojo. El dibujo dice de qué sección se trata; cuántos avisos hay sin leer
 * es un DATO, y ese lo pone quien lo tenga, encima del ícono y no adentro de él.
 */
export function BellIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M6 9a6 6 0 0 1 12 0c0 3.2.7 5 1.6 6.1.4.5 0 1.4-.7 1.4H5.1c-.7 0-1.1-.9-.7-1.4C5.3 14 6 12.2 6 9Z" />
      <path d="M10 19.5a2.2 2.2 0 0 0 4 0" />
    </svg>
  )
}

/** Calendario: las reservas y su historial. Un turno es una fecha antes que cualquier otra cosa. */
export function CalendarIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <rect x="3.5" y="5" width="17" height="16" rx="2.5" />
      <path d="M3.5 9.75h17" />
      <path d="M8 3.5v3M16 3.5v3" />
    </svg>
  )
}

/**
 * Tarjeta: los medios de pago.
 *
 * La franja de arriba es lo que la separa de un rectángulo cualquiera, y por eso va llena: es
 * la banda magnética, y sin ella el dibujo es el mismo que el de una ventana.
 */
export function CardIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
      <path d="M2.5 9.5h19" />
      <path d="M6 14.5h3.5" />
    </svg>
  )
}

/**
 * Deslizadores: la configuración.
 *
 * Es un panel de ajustes y no el engranaje de siempre. El engranaje dice "máquina" y esta
 * sección no configura el sistema: son preferencias de quien mira, cada una con su posición.
 */
export function SlidersIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 7h10M18 7h2M4 17h2M10 17h10" />
      <circle cx="16" cy="7" r="2.25" />
      <circle cx="8" cy="17" r="2.25" />
    </svg>
  )
}

/**
 * El doble galón del tirador del riel del perfil.
 *
 * Apunta a la izquierda, que es lo que hace el riel abierto: cerrarse. Para el otro estado se
 * lo da vuelta media vuelta desde el componente, en vez de dibujar un segundo ícono espejado.
 *
 * **Reemplaza a los tres puntos que estaban acá.** Los puntos son el símbolo de "hay más para
 * ver", no el de una dirección: decían que el tirador se podía tocar, pero no hacia dónde iba
 * a moverse el panel. El galón sí, y además es el mismo gesto en los dos sentidos.
 */
export function ChevronsLeftIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M11 6.5 5.5 12 11 17.5" />
      <path d="M17.5 6.5 12 12l5.5 5.5" />
    </svg>
  )
}

/**
 * Lápiz sobre una hoja: editar el perfil.
 *
 * El cuadrado abierto por la esquina es lo que lo separa de un lápiz suelto —que se lee como
 * "escribir"— y lo convierte en "editar esto de acá".
 */
export function EditIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M11 4.5H6a2 2 0 0 0-2 2V18a2 2 0 0 0 2 2h11.5a2 2 0 0 0 2-2v-5" />
      <path d="M17.6 3.4a1.9 1.9 0 0 1 2.7 2.7L13 13.4l-3.4.7.7-3.4Z" />
    </svg>
  )
}

/**
 * Una cruz: salir de la sesión, en el botón de abajo del riel.
 *
 * **Es una cruz y no una puerta con una flecha.** El dibujo de la puerta es el de "salir de
 * acá", y lo que hace este botón es cerrar la sesión, que es otra cosa: la pantalla se queda
 * donde está. La cruz dice cortar, que es lo que pasa.
 */
export function CrossIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </svg>
  )
}

/**
 * Un tacho: borrar un aviso del buzón.
 *
 * **Es un tacho y no la cruz de al lado, y la diferencia es el punto.** La cruz de un recuadro
 * flotante quiere decir "no me lo muestres más", y el aviso sigue estando en el buzón; esto lo
 * saca de ahí y no vuelve. Con el mismo dibujo para las dos cosas, el que cierra el recuadro de
 * la esquina creería que borró algo, y el que borra creería que solo lo escondió.
 */
export function TrashIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 7h16M10 4h4M9.5 7v12M14.5 7v12M6 7l1 13h10l1-13" />
    </svg>
  )
}

/** Flecha a la izquierda: volver al listado desde el detalle de una configuración. */
export function ArrowLeftIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M19 12H5.5" />
      <path d="M11 5.5 4.5 12l6.5 6.5" />
    </svg>
  )
}

/** Galón a la derecha: el renglón de una lista que abre algo. */
export function ChevronRightIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />
    </svg>
  )
}

/**
 * Ojo abierto: la contraseña se está viendo.
 *
 * El par con `EyeOffIcon` es el símbolo universal de "mostrar y ocultar", y por eso reemplazó al
 * botón que decía "Mostrar": un rótulo de texto al lado de un campo de contraseña se lee como
 * otra cosa más para leer, mientras que el ojo se reconoce sin leerlo.
 */
export function EyeIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M2.5 12s3.6-6 9.5-6 9.5 6 9.5 6-3.6 6-9.5 6-9.5-6-9.5-6Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

/**
 * Ojo tachado: la contraseña está oculta, que es el estado de arranque.
 *
 * La barra va en el MISMO ángulo que la que cruza cualquier símbolo de prohibido, y es lo único
 * que distingue a los dos dibujos de un vistazo: sin ella, un ojo y otro ojo son el mismo ícono.
 */
export function EyeOffIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M4 4.5l16 15" />
      <path d="M9.9 5.2A9.6 9.6 0 0 1 12 5c5.9 0 9.5 6 9.5 6a16 16 0 0 1-3.2 3.7" />
      <path d="M15.2 14.6A3 3 0 0 1 9.9 10" />
      <path d="M6.6 7.3A15.8 15.8 0 0 0 2.5 11s3.6 6 9.5 6c1.4 0 2.6-.3 3.7-.8" />
    </svg>
  )
}

/** Llave: el acceso al Backoffice de administración. */
export function KeyIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <circle cx="7.5" cy="15.5" r="4.5" />
      <path d="m10.7 12.3 8.3-8.3a2 2 0 0 1 2.8 0l.2.2a2 2 0 0 1 0 2.8L20 9l-2-2-2 2" />
      <path d="m15.5 7.5 2 2" />
    </svg>
  )
}
