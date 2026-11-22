import { summariseNetwork } from '../networkStats'
import type { StationDetail } from '../types'
import DonutChart from './DonutChart'

/**
 * El estado de la infraestructura, arriba del listado del operador.
 *
 * **Está acá y no en la portada del conductor por una razón de producto.** Saber que la red tiene
 * dieciséis CCS2 no ayuda a nadie que quiera cargar el auto —su pregunta es dónde puede hacerlo
 * ahora—, pero sí a quien administra esas estaciones: es el estado de lo que tiene a cargo, y RF05
 * le da justamente el control del tipo, la potencia y el estado operativo de cada conector.
 *
 * **No pide nada al backend.** Recibe las estaciones que la pantalla ya cargó y las cuenta. Una
 * segunda consulta por los mismos datos sería trabajo de red por nada.
 *
 * Va con los tokens `st-` y no con el vidrio del resto de la aplicación porque esta pantalla
 * todavía tiene su propio tema. Cuando RNF08 los unifique, esto se moderniza con ella.
 */

/** Un color por tipo de conector. El primero es el verde de la pantalla. */
const TYPE_COLORS = ['var(--color-st-accent)', '#8b7bf0', '#3fb8c9']

/** Un color por estado operativo. Son los tres que la feature ya tiene definidos. */
const STATUS_COLORS: Record<string, string> = {
  AVAILABLE: 'var(--color-st-available)',
  OCCUPIED: 'var(--color-st-occupied)',
  OUT_OF_SERVICE: 'var(--color-st-offline)',
}

export default function NetworkSummary({ stations }: { stations: StationDetail[] }) {
  const stats = summariseNetwork(
    stations.flatMap((station) => station.connectors),
    stations.length,
  )

  /* Sin conectores no hay nada que resumir, y un panel de ceros solo ocupa lugar. */
  if (stats.connectorCount === 0) return null

  return (
    <section className="glass-panel flex flex-col gap-6 rounded-3xl p-5 sm:flex-row sm:items-center sm:gap-8">
      {/* El anillo con el reparto por tipo, y el total adentro. */}
      <div className="flex items-center gap-4">
        <div className="text-st-muted relative h-24 w-24 shrink-0">
          <DonutChart
            className="h-full w-full"
            segments={stats.byType.map((share, index) => ({
              label: share.label,
              value: share.count,
              color: TYPE_COLORS[index % TYPE_COLORS.length],
            }))}
          />
          <span className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-st-text text-xl font-bold">{stats.connectorCount}</span>
            <span className="text-st-muted text-[10px]">conectores</span>
          </span>
        </div>

        {/*
          La lista no es una leyenda decorativa: es la versión leíble del anillo, que está
          `aria-hidden`. Un lector de pantalla salta el dibujo y lee esto.
        */}
        <ul className="flex flex-col gap-1.5">
          {stats.byType.map((share, index) => (
            <li key={share.key} className="flex items-center gap-2 text-xs">
              <span
                aria-hidden="true"
                className="h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: TYPE_COLORS[index % TYPE_COLORS.length] }}
              />
              <span className="text-st-muted w-20">{share.label}</span>
              <span className="text-st-text font-bold">{share.count}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* La disponibilidad, como barras: es lo que el operador mira primero. */}
      <div className="min-w-0 flex-1">
        <p className="text-st-muted text-xs font-medium">Disponibilidad ahora</p>

        <ul className="mt-3 flex flex-col gap-2">
          {stats.byStatus.map((share) => {
            const percent = Math.round((share.count / stats.connectorCount) * 100)
            return (
              <li key={share.key} className="flex items-center gap-3 text-xs">
                <span className="text-st-muted w-28 shrink-0">{share.label}</span>

                <span className="bg-st-border/60 h-2 min-w-0 flex-1 overflow-hidden rounded-full">
                  <span
                    className="block h-full rounded-full"
                    style={{ width: `${percent}%`, backgroundColor: STATUS_COLORS[share.key] }}
                  />
                </span>

                <span className="text-st-text w-14 shrink-0 text-right font-bold">
                  {share.count} · {percent}%
                </span>
              </li>
            )
          })}
        </ul>

        <p className="text-st-muted mt-3 text-xs">
          <span className="text-st-text font-bold">{stats.stationCount} estaciones</span> · hasta{' '}
          <span className="text-st-text font-bold">{stats.maxPowerKw} kW</span>
        </p>
      </div>
    </section>
  )
}
