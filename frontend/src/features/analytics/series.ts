/**
 * Las cuentas del tablero sobre lo que está elegido.
 *
 * El backend manda las franjas de cada conector, y acá se suman solo las de los conectores
 * elegidos. Así los totales, el gráfico y el pico simultáneo siempre hablan de lo mismo que
 * muestran las listas, sin volver a pedir nada al cambiar la selección.
 */

import type { DashboardConnector } from './types'

const SLOT_MS = 15 * 60_000
/** Franjas por hora: la potencia media de una franja son sus kWh por esto. */
const SLOTS_PER_HOUR = 4
/** Más barras que esto no se distinguen en el ancho del panel: se agrupan. */
const MAX_BARS = 168
/** De cuántas franjas puede ser cada barra: 15 min, 30 min, 1 h, 2 h, 3 h, 6 h, 12 h, 1 día. */
const BUCKET_SIZES = [1, 2, 4, 8, 12, 24, 48, 96]

export interface Totals {
  energyKwh: number
  sessions: number
  peakKw: number
  peakAt: Date | null
}

export interface Bar {
  start: Date
  end: Date
  /** La franja de 15 minutos de mayor demanda dentro de la barra: así el pico no se diluye. */
  demandKw: number
  energyKwh: number
  sessions: number
}

export interface Series {
  bars: Bar[]
  bucketMinutes: number
  /** Índice de la barra que contiene el pico, o -1 si no hubo consumo. */
  peakIndex: number
}

interface SlotSum {
  energyKwh: number
  sessions: number
}

/** La energía de cada franja sumando los conectores: la base de la potencia simultánea. */
function sumBySlot(connectors: DashboardConnector[]): Map<number, SlotSum> {
  const sums = new Map<number, SlotSum>()
  for (const connector of connectors) {
    for (const slot of connector.slots) {
      const key = new Date(slot.start).getTime()
      const current = sums.get(key) ?? { energyKwh: 0, sessions: 0 }
      current.energyKwh += slot.energyKwh
      current.sessions += slot.sessionsStarted
      sums.set(key, current)
    }
  }
  return sums
}

export function totalsOf(connectors: DashboardConnector[]): Totals {
  let peakKw = 0
  let peakAt: Date | null = null
  for (const [start, sum] of sumBySlot(connectors)) {
    const demand = sum.energyKwh * SLOTS_PER_HOUR
    if (demand > peakKw) {
      peakKw = demand
      peakAt = new Date(start)
    }
  }
  return {
    energyKwh: connectors.reduce((total, c) => total + c.energyKwh, 0),
    sessions: connectors.reduce((total, c) => total + c.sessions, 0),
    peakKw,
    peakAt,
  }
}

/**
 * Las barras del gráfico, de `from` a `to`, con los huecos en cero.
 *
 * El backend solo manda las franjas con lecturas; acá se completa el eje para que el tiempo
 * corra parejo y una noche sin cargas se vea como una noche sin cargas.
 */
export function seriesOf(connectors: DashboardConnector[], from: Date, to: Date): Series {
  const sums = sumBySlot(connectors)
  const firstSlot = Math.floor(from.getTime() / SLOT_MS) * SLOT_MS
  const slotCount = Math.max(1, Math.ceil((to.getTime() - firstSlot) / SLOT_MS))
  const bucket = BUCKET_SIZES.find((size) => slotCount / size <= MAX_BARS) ?? BUCKET_SIZES.at(-1)!

  const bars: Bar[] = []
  let peakIndex = -1
  let peakKw = 0
  for (let i = 0; i < slotCount; i += bucket) {
    const start = firstSlot + i * SLOT_MS
    const bar: Bar = {
      start: new Date(start),
      end: new Date(Math.min(start + bucket * SLOT_MS, to.getTime())),
      demandKw: 0,
      energyKwh: 0,
      sessions: 0,
    }
    for (let j = 0; j < bucket && i + j < slotCount; j++) {
      const sum = sums.get(start + j * SLOT_MS)
      if (sum === undefined) continue
      bar.energyKwh += sum.energyKwh
      bar.sessions += sum.sessions
      bar.demandKw = Math.max(bar.demandKw, sum.energyKwh * SLOTS_PER_HOUR)
    }
    if (bar.demandKw > peakKw) {
      peakKw = bar.demandKw
      peakIndex = bars.length
    }
    bars.push(bar)
  }

  return { bars, bucketMinutes: bucket * 15, peakIndex }
}

/** Un tope "redondo" para el eje: 37 → 40, 180 → 200, 1.234 → 1.500. */
export function niceCeiling(value: number): number {
  if (value <= 0) return 10
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const step = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((s) => s * magnitude >= value) ?? 10
  return step * magnitude
}
