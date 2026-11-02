import type { ConnectorItem } from './types'

/** Las columnas por las que se puede ordenar la tabla de conectores. Acciones no ordena. */
export type ConnectorSortKey = 'id' | 'station' | 'type' | 'power' | 'status'

export type SortDirection = 'asc' | 'desc'

export interface ConnectorSort {
  key: ConnectorSortKey
  direction: SortDirection
}

/** Cómo arranca la tabla: por ID, de menor a mayor. */
export const DEFAULT_CONNECTOR_SORT: ConnectorSort = { key: 'id', direction: 'asc' }

/*
 * El estado no se ordena alfabéticamente sino por lo que significa: primero lo que se puede usar,
 * después lo ocupado y al final lo roto. Alfabético, "OCCUPIED" quedaría antes que
 * "OUT_OF_SERVICE" por casualidad y "AVAILABLE" primero también por casualidad.
 */
const STATUS_ORDER: Record<string, number> = { AVAILABLE: 0, OCCUPIED: 1, OUT_OF_SERVICE: 2 }

function compareBy(key: ConnectorSortKey, a: ConnectorItem, b: ConnectorItem): number {
  switch (key) {
    case 'id':
      return a.id - b.id
    // Por número de estación, que es como la columna la muestra ("4 - Ecopedia Palermo").
    case 'station':
      return a.stationId - b.stationId
    case 'type':
      return a.connectorType.localeCompare(b.connectorType)
    case 'power':
      return a.maxPowerKw - b.maxPowerKw
    case 'status':
      return (STATUS_ORDER[a.operationalStatus] ?? 99) - (STATUS_ORDER[b.operationalStatus] ?? 99)
  }
}

/**
 * Devuelve una copia ordenada; no toca la lista que recibe.
 *
 * Los empates se desempatan siempre por ID ascendente, en las dos direcciones: ordenar por estación
 * deja los conectores de cada una en un orden fijo y no en el que haya mandado el backend.
 */
export function sortConnectors(items: ConnectorItem[], sort: ConnectorSort): ConnectorItem[] {
  const sign = sort.direction === 'asc' ? 1 : -1
  return [...items].sort((a, b) => sign * compareBy(sort.key, a, b) || a.id - b.id)
}

/** Tocar la columna activa invierte la dirección; tocar otra la ordena ascendente. */
export function nextConnectorSort(current: ConnectorSort, key: ConnectorSortKey): ConnectorSort {
  if (current.key === key) {
    return { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
  }
  return { key, direction: 'asc' }
}
