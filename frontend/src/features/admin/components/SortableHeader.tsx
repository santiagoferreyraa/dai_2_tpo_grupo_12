import type { ReactNode } from 'react'

import type { SortDirection } from '../connectorSort'

interface SortableHeaderProps {
  /** La dirección si esta columna es la que ordena; `null` si no. */
  direction: SortDirection | null
  onSort: () => void
  children: ReactNode
}

/**
 * Encabezado de tabla que ordena al tocarlo.
 *
 * El toque va en un botón dentro del `<th>` y no en el `<th>` mismo, para que se pueda usar con el
 * teclado; `aria-sort` le dice al lector de pantalla cuál columna ordena y en qué sentido. La flecha
 * tenue en las columnas inactivas avisa que también se pueden tocar.
 */
export default function SortableHeader({ direction, onSort, children }: SortableHeaderProps) {
  const ariaSort = direction === 'asc' ? 'ascending' : direction === 'desc' ? 'descending' : 'none'

  return (
    <th className="py-3 px-4" aria-sort={ariaSort}>
      <button
        type="button"
        onClick={onSort}
        className={`inline-flex items-center gap-1 uppercase hover:text-text ${
          direction ? 'text-text' : ''
        }`}
      >
        {children}
        <span aria-hidden="true" className={direction ? 'text-primary' : 'opacity-40'}>
          {direction === 'asc' ? '▲' : direction === 'desc' ? '▼' : '↕'}
        </span>
      </button>
    </th>
  )
}
