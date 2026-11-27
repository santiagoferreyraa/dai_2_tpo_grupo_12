import type { CSSProperties } from 'react'

/**
 * Las clases y ayudas de estilo que comparten las secciones del backoffice. Van aparte de
 * `ui.tsx` para que ese archivo exporte solo componentes (lo pide el recargado en caliente).
 */

/*
  **Lo interactivo no usa `.glass-inset` sino su equivalente en utilidades.** Las clases de vidrio
  de index.css están fuera de las capas de Tailwind, y una regla sin capa le gana a cualquier
  utilidad: sobre `.glass-inset` no se aplicaría ni el borde del hover ni el anillo del foco —el
  anillo es una `box-shadow`, y el vidrio ya trae la suya—. Para un control, que el foco se vea no
  es opcional (WCAG 2.4.7). El cuerpo y el borde son los mismos 25% y 78% del vidrio.
*/
export const INSET = 'border border-border/80 bg-surface/25'

/** El campo de texto o la lista desplegable sobre vidrio. */
export const FIELD = `${INSET} text-text placeholder:text-text-muted focus-visible:border-primary focus-visible:ring-primary/40 w-full rounded-xl px-3.5 py-2.5 text-sm outline-none focus-visible:ring-2 disabled:opacity-50`

/** La acción principal: el degradado de la marca, como en el resto de la aplicación. */
export const BUTTON_PRIMARY =
  'brand-fill text-on-primary focus-visible:outline-primary inline-flex cursor-pointer items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold transition hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-default disabled:opacity-50'

/** La acción secundaria: vidrio con el borde que se enciende en verde. */
export const BUTTON_GHOST = `${INSET} text-text hover:bg-surface/45 hover:border-primary/60 focus-visible:outline-primary inline-flex cursor-pointer items-center justify-center gap-2 rounded-xl px-3.5 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-default disabled:opacity-50`

/** La demora de la entrada escalonada, como la escribe Estadísticas. */
export function riseDelay(ms: number): CSSProperties {
  return { ['--rise-delay' as string]: `${String(ms)}ms` }
}

export type Tone = 'ok' | 'warn' | 'off'

/** La tinta de cada estado, escrita. "Bien" va en el verde claro de la marca. Ver `admin.css`. */
export const TONE_TEXT: Record<Tone, string> = {
  ok: 'text-(--admin-ok)',
  warn: 'text-(--admin-warn)',
  off: 'text-(--admin-off)',
}

/** La misma tinta para una lista desplegable. */
export const TONE_SELECT_TEXT: Record<Tone, string> = {
  ok: 'text-(--admin-ok)',
  warn: 'text-(--admin-warn)',
  off: 'text-(--admin-off)',
}

/** El punto de cada estado: no es texto, así que usa el color de estado de siempre. */
export const TONE_DOT: Record<Tone, string> = {
  ok: 'bg-st-available',
  warn: 'bg-st-occupied',
  off: 'bg-st-offline',
}

/** El color de cada tipo de conector, escrito sin recuadro. */
export const CONNECTOR_TYPE_TEXT: Record<string, string> = {
  CCS2: 'text-(--admin-type-ccs2)',
  CHADEMO: 'text-(--admin-type-chademo)',
  TYPE_2: 'text-(--admin-type-type2)',
}

/*
  Las acciones de las filas, sin recuadro: la palabra en mayúsculas y en su color, como un enlace.
  El área que se toca sigue siendo de 32 px de alto —el padding— aunque no se vea, y el foco dibuja
  su contorno igual.
*/
const TEXT_ACTION =
  'inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-1.5 py-1.5 text-xs font-bold tracking-wide uppercase transition-opacity hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-default disabled:opacity-50'

export const ACTION_OK = `${TEXT_ACTION} text-(--admin-ok) focus-visible:outline-primary`
export const ACTION_DANGER = `${TEXT_ACTION} text-(--admin-off) focus-visible:outline-danger`
export const ACTION_NEUTRAL = `${TEXT_ACTION} text-text focus-visible:outline-primary`

/**
 * Una lista desplegable sin caja: el texto en mayúsculas y una flechita propia para cambiarla.
 *
 * `appearance-none` saca la flecha del sistema, que en Windows pinta un recuadro gris al pasar el
 * mouse —no combinaba con nada de la aplicación—. La flecha la dibuja `BareSelect` y al pasar el
 * mouse solo se aclara, como el resto de las acciones. El color lo hereda del envoltorio, así la
 * flecha y el texto van siempre juntos.
 */
export const BARE_SELECT =
  'focus-visible:ring-primary/50 cursor-pointer appearance-none rounded-lg bg-transparent py-1 pr-5 pl-1 text-xs font-bold tracking-wide uppercase [color:inherit] outline-none focus-visible:ring-2 disabled:cursor-default [&>option]:bg-surface [&>option]:text-text'
