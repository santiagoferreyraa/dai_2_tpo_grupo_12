import type { ReactNode } from 'react'

/**
 * El tilde de las listas: solo la marca, sin caja blanca ni negra alrededor.
 *
 * Elegido, en el verde de la marca; sin elegir, la misma marca casi transparente, que deja ver
 * que ahí se puede tocar sin competir con los datos de la fila.
 */
function Check({ checked, partial = false }: { checked: boolean; partial?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={`h-4 w-4 shrink-0 transition-colors duration-200 ${
        checked || partial ? 'text-primary' : 'text-text-muted/30'
      }`}
    >
      {partial && !checked ? <path d="M6 12h12" /> : <path d="M5 12.5l4.5 4.5L19 7.5" />}
    </svg>
  )
}

export interface Column<T> {
  header: string
  /** Las columnas de números van a la derecha, como en cualquier tabla. */
  align?: 'left' | 'right'
  render: (row: T) => ReactNode
}

interface SelectionListProps<T> {
  title: string
  rows: T[]
  rowKey: (row: T) => number
  isSelected: (row: T) => boolean
  onToggle: (row: T) => void
  /** El tilde de la cabecera: elige o suelta todas las filas que se ven. */
  onToggleAll: (select: boolean) => void
  columns: Column<T>[]
  /** El ancho de cada columna, para el `grid-template-columns` de la cabecera y de las filas. */
  template: string
  empty: string
}

/**
 * Una lista con tilde a la izquierda: la de estaciones y la de conectores.
 *
 * Cada fila es un `checkbox` de verdad para el lector de pantalla. La cabecera queda fija y el
 * cuerpo scrollea adentro, sin estirar el panel.
 */
export default function SelectionList<T>({
  title,
  rows,
  rowKey,
  isSelected,
  onToggle,
  onToggleAll,
  columns,
  template,
  empty,
}: SelectionListProps<T>) {
  const selectedCount = rows.filter(isSelected).length
  const all = rows.length > 0 && selectedCount === rows.length

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div
        className="text-text-muted grid shrink-0 items-center gap-3 px-3 pb-2 text-xs font-semibold"
        style={{ gridTemplateColumns: `1rem ${template}` }}
      >
        <button
          type="button"
          role="checkbox"
          aria-checked={all ? 'true' : selectedCount > 0 ? 'mixed' : 'false'}
          aria-label={
            all ? `Quitar todas las filas de ${title}` : `Elegir todas las filas de ${title}`
          }
          onClick={() => onToggleAll(!all)}
          disabled={rows.length === 0}
          className="focus-visible:outline-primary cursor-default focus-visible:outline-2"
        >
          <Check checked={all} partial={selectedCount > 0} />
        </button>
        {columns.map((column) => (
          <span key={column.header} className={column.align === 'right' ? 'text-right' : ''}>
            {column.header}
          </span>
        ))}
      </div>

      <div
        className="analytics-scroll min-h-0 flex-1 overflow-y-auto pr-1"
        role="group"
        aria-label={title}
      >
        {rows.length === 0 && (
          <p className="text-text-muted px-3 py-6 text-center text-sm">{empty}</p>
        )}
        {rows.map((row) => {
          const selected = isSelected(row)
          return (
            <button
              key={rowKey(row)}
              type="button"
              role="checkbox"
              aria-checked={selected}
              onClick={() => onToggle(row)}
              /*
                Sin cursor de mano ni realce al pasar el mouse: la fila no cambia hasta que se elige.
                La elegida se agranda un poco y toma aire arriba y abajo, con transición, para que
                el cambio se lea como un movimiento y no como un salto.
              */
              className={`grid w-full cursor-default items-center gap-3 px-3 text-left transition-all duration-200 focus-visible:outline-primary focus-visible:outline-2 focus-visible:-outline-offset-2 ${
                selected
                  ? 'bg-primary/10 text-text my-1 py-3.5 text-[15px]'
                  : 'text-text-muted py-2.5 text-sm'
              }`}
              style={{ gridTemplateColumns: `1rem ${template}` }}
            >
              <Check checked={selected} />
              {columns.map((column) => (
                <span
                  key={column.header}
                  className={`min-w-0 truncate tabular-nums ${column.align === 'right' ? 'text-right' : ''}`}
                >
                  {column.render(row)}
                </span>
              ))}
            </button>
          )
        })}
      </div>
    </div>
  )
}
