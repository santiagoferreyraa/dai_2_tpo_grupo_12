import { formatAmount, formatCount, formatMoment } from '../format'
import { bucketCaption, LIST_TEMPLATE } from '../layout'
import type { DashboardModel } from '../useDashboardModel'
import AnalyticsMap from './AnalyticsMap'
import { ChartStatus, ConnectorList, SearchField, StationList } from './DashboardParts'
import DemandChart from './DemandChart'
import PeriodPicker from './PeriodPicker'
import StatCard from './StatCard'
import TabbedPanel from './TabbedPanel'
import { TAB_SHAPE } from '../tabShape'

/** El ancho de la pestaña del buscador, en píxeles: lo usan la silueta y el contenido. */
const SEARCH_TAB_WIDTH = 360

/**
 * El tablero en escritorio: entra entero en la pantalla, sin scroll.
 *
 * Arriba, el panel con pestaña —totales y gráfico— y el mapa; abajo, las dos listas. Las listas
 * scrollean adentro de su panel.
 */
export default function DesktopDashboard({ model }: { model: DashboardModel }) {
  const { totals, series, period } = model

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5 px-6 pb-6">
      <div className="grid min-h-0 flex-[1.3] grid-cols-[minmax(0,1.75fr)_minmax(0,1fr)] gap-5">
        <TabbedPanel
          tabWidth={SEARCH_TAB_WIDTH}
          tab={<SearchField model={model} />}
          beside={
            <PeriodPicker
              period={period}
              onChange={model.setPeriod}
              rangeError={model.rangeError}
            />
          }
        >
          <div className="grid min-h-0 flex-1 grid-cols-[minmax(9.5rem,12rem)_minmax(0,1fr)] gap-4">
            <div className="flex min-h-0 flex-col gap-3">
              <StatCard
                label="Energía entregada"
                value={totals.energyKwh}
                unit="kWh"
                format={formatAmount}
              />
              <StatCard
                label="Sesiones"
                value={totals.sessions}
                format={(v) => formatCount(Math.round(v))}
                delayMs={60}
              />
              <StatCard
                label="Potencia simultánea"
                value={totals.peakKw}
                unit="kW"
                format={formatAmount}
                caption={
                  totals.peakAt === null
                    ? 'Sin cargas en el período'
                    : `Pico ${formatMoment(totals.peakAt)}`
                }
                delayMs={120}
              />
            </div>

            <div
              className="glass-inset analytics-rise relative flex min-h-0 flex-col rounded-2xl p-4"
              style={{ ['--rise-delay' as string]: '80ms' }}
            >
              <div className="mb-2 flex shrink-0 items-baseline justify-between gap-3">
                <h2 className="text-text text-sm font-bold">Demanda por franja</h2>
                <span className="text-text-muted truncate text-xs">
                  {model.scope} · {bucketCaption(model)}
                </span>
              </div>
              <DemandChart
                series={series}
                from={period.from}
                to={period.to}
                animationKey={model.animationKey}
              />
              <ChartStatus model={model} />
            </div>
          </div>
        </TabbedPanel>

        <div
          className="glass-panel analytics-rise relative min-h-0 overflow-hidden rounded-3xl"
          style={{ marginTop: TAB_SHAPE.tabHeight, ['--rise-delay' as string]: '100ms' }}
        >
          <AnalyticsMap
            stations={model.stations}
            selectedIds={model.selectedStationIds}
            onFocus={model.focusStation}
          />
        </div>
      </div>

      <div
        className="glass-panel analytics-rise grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)_1px_minmax(0,1fr)] gap-6 rounded-3xl p-5"
        style={{ ['--rise-delay' as string]: '160ms' }}
      >
        <StationList model={model} template={LIST_TEMPLATE.wide} />
        <div className="bg-border/70 w-px self-stretch" aria-hidden="true" />
        <ConnectorList model={model} template={LIST_TEMPLATE.wide} />
      </div>
    </div>
  )
}
