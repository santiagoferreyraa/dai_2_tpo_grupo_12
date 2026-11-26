import type { DashboardModel } from './useDashboardModel'

/**
 * Lo que comparten las dos disposiciones del tablero y no es un componente. Va aparte de
 * `DashboardParts` porque un archivo que exporta componentes y además funciones rompe el Fast
 * Refresh de Vite.
 */

/** "Franjas de 15 min" o "barras de 6 h": qué es cada barra del gráfico. */
export function bucketCaption(model: DashboardModel): string {
  const minutes = model.series.bucketMinutes
  return minutes < 60 ? 'franjas de 15 min' : `barras de ${minutes / 60} h`
}

/** El ancho de las columnas de las listas. En el celular los números van más apretados. */
export const LIST_TEMPLATE = {
  wide: 'minmax(0,1fr) 4.5rem 4.5rem',
  narrow: 'minmax(0,1fr) 3.5rem 3.5rem',
}
