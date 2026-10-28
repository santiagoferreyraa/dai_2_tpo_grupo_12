/**
 * El nodo de la distribuidora de una estación (RF15), tal como lo escribe la distribuidora:
 * mayúsculas, números y guiones, como CABA-CENTRO.
 *
 * Lo usan los dos formularios de estación, el del operador y el del administrador. Repite la
 * validación de `StationRequest` en el backend a propósito, como el resto de los formularios: no la
 * reemplaza, avisa antes de mandar.
 */

const GRID_NODE_PATTERN = /^[A-Z0-9]+(-[A-Z0-9]+)*$/
const GRID_NODE_MAX_LENGTH = 40

/** Lo que se escribe en el campo, en mayúsculas y sin espacios: así se tipea cómodo. */
export function normalizeGridNode(value: string): string {
  return value.toUpperCase().replace(/\s+/g, '')
}

/** El mensaje de error, o `null` si está bien. Vacío está bien: es "sin nodo". */
export function validateGridNode(value: string): string | null {
  if (value === '') return null
  if (value.length > GRID_NODE_MAX_LENGTH) return `Como máximo ${GRID_NODE_MAX_LENGTH} caracteres`
  if (!GRID_NODE_PATTERN.test(value)) return 'Letras, números y guiones, como CABA-CENTRO'
  return null
}

/** Lo que viaja al backend: el campo vacío es "sin nodo". */
export function toGridNodeId(value: string): string | null {
  return value === '' ? null : value
}
