/**
 * Los tres dibujos que usa el chat y que no existían todavía.
 *
 * El resto —la cruz de cerrar— se importa de `features/navigation/icons`, que es donde vive el
 * juego completo. Acá solo están los que faltaban, con el mismo trazo, para no repetir uno que ya
 * está dibujado. Ver el encabezado de ese archivo.
 */

interface IconProps {
  className?: string
}

const BASE = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.75,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

/**
 * El agente: dos estrellas, una grande y una chica.
 *
 * **No es un globo de diálogo.** El globo dice "hay mensajes", y acá no hay mensajes esperando:
 * hay algo que responde. La chispa es lo que se usa en todos lados para nombrar al asistente, y
 * además se lee bien a 24 píxeles, que es lo que mide adentro de la burbuja.
 *
 * **Va rellena y sin trazo**, a diferencia del resto de los íconos de este archivo: adentro de la
 * burbuja se apoya sobre el verde de la marca, y una estrella de contorno sobre un fondo lleno se
 * lee como un agujero. Rellena es una forma sólida, que es lo que se quiere a ese tamaño. Toma el
 * color de quien la usa (`currentColor`), así que la burbuja la pinta blanca y el encabezado del
 * chat la deja del color del texto.
 *
 * Son DOS y no tres: la tercera chispa no se distinguía de las otras dos a 24 píxeles y solo
 * ensuciaba la silueta. Con una grande y una chica el contraste de tamaño se lee de una.
 */
export function SparkIcon({ className }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
      <path d="M10 2.5c.55 4.6 2.35 6.4 6.95 6.95-4.6.55-6.4 2.35-6.95 6.95-.55-4.6-2.35-6.4-6.95-6.95C7.65 8.9 9.45 7.1 10 2.5Z" />
      <path d="M18 14.5c.28 2.35 1.05 3.12 3.4 3.4-2.35.28-3.12 1.05-3.4 3.4-.28-2.35-1.05-3.12-3.4-3.4 2.35-.28 3.12-1.05 3.4-3.4Z" />
    </svg>
  )
}

/** Enviar: una flecha hacia arriba, la del botón del campo de texto. */
export function SendIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M12 19V5.5" />
      <path d="M5.5 12 12 5.5 18.5 12" />
    </svg>
  )
}

/** Borrar la conversación: un papel con una vuelta, el gesto de "empezar de nuevo". */
export function RefreshIcon({ className }: IconProps) {
  return (
    <svg {...BASE} className={className}>
      <path d="M20 12a8 8 0 1 1-2.6-5.9" />
      <path d="M20 4v4.5h-4.5" />
    </svg>
  )
}
