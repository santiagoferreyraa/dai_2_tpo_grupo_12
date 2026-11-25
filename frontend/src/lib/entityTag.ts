import { ApiError } from './api'

/**
 * El control de versiones de las ediciones (QA-30, ECO-180).
 *
 * El backend manda la versión de la estación y del perfil en el cuerpo (`version`), y al guardar se
 * la devuelve en `If-Match`. Si alguien guardó en el medio —otra pestaña, el `ADMIN` y el `CPO` sobre
 * la misma estación—, la respuesta es 412 y lo de esta pantalla no pisa lo del otro.
 *
 * **Se arma con la versión del cuerpo, no con la cabecera `ETag`**: el listado de estaciones no trae
 * una cabecera por estación, y Caddy puede reescribir el `ETag` de una respuesta que comprime.
 */

/** La cabecera para guardar sobre la versión que se leyó. Sin versión conocida, ninguna. */
export function ifMatch(version: number | null | undefined): Record<string, string> | undefined {
  if (version === null || version === undefined) return undefined
  return { 'If-Match': `"${String(version)}"` }
}

/** Si el rechazo es porque otro guardó en el medio. */
export function isStaleVersion(error: unknown): boolean {
  return error instanceof ApiError && error.status === 412
}

/**
 * Lo que ve el usuario ante un 412: qué hacer, sin contar qué pasó por detrás (directiva del 02/11).
 * Texto aprobado por Manuel el 25/11.
 */
export const STALE_VERSION_MESSAGE =
  'Tus cambios no se guardaron. Recargá la página y probá de nuevo.'
