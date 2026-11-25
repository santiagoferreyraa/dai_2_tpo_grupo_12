import type { ReactNode } from 'react'

import { useElementSize } from '../hooks'
import { TAB_SHAPE, tabOutlinePath } from '../tabShape'

interface TabbedPanelProps {
  /** Lo que va adentro de la pestaña: el buscador. */
  tab: ReactNode
  tabWidth: number
  /** Lo que flota en el hueco a la derecha de la pestaña, sobre el fondo: el período. */
  beside: ReactNode
  children: ReactNode
}

/**
 * El panel de vidrio con pestaña de arriba a la izquierda. La silueta la arma `tabShape.ts`.
 *
 * Tres capas: el vidrio recortado con `clip-path`, el contorno en SVG encima y el contenido. La
 * sombra va en el envoltorio de las dos primeras, que es el único lugar donde `drop-shadow` sigue
 * la figura recortada y no el rectángulo.
 */
export default function TabbedPanel({ tab, tabWidth, beside, children }: TabbedPanelProps) {
  const [ref, { width, height }] = useElementSize<HTMLDivElement>()
  const outline = width > 0 && height > 0 ? tabOutlinePath(width, height, tabWidth) : ''
  const { tabHeight } = TAB_SHAPE

  return (
    <div ref={ref} className="analytics-rise relative min-h-0 min-w-0">
      {outline !== '' && (
        <div className="analytics-shape__shadow pointer-events-none absolute inset-0">
          <div
            className="analytics-shape__glass h-full w-full"
            style={{ clipPath: `path('${outline}')` }}
          />
          <svg width={width} height={height} className="absolute inset-0" aria-hidden="true">
            <path className="analytics-shape__outline" d={outline} fill="none" strokeWidth={1} />
          </svg>
        </div>
      )}

      <div
        className="absolute top-0 left-0 flex items-center px-3"
        style={{ width: tabWidth, height: tabHeight }}
      >
        {tab}
      </div>

      <div
        className="absolute top-0 right-0 z-[1100] flex items-center justify-start"
        style={{ left: tabWidth + TAB_SHAPE.fillet + 12, height: tabHeight - 8 }}
      >
        {beside}
      </div>

      <div className="absolute inset-x-0 bottom-0 flex min-h-0 p-5" style={{ top: tabHeight }}>
        {children}
      </div>
    </div>
  )
}
