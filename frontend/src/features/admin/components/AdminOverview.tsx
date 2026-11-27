import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'

import AnalyticsMap from '@/features/analytics/components/AnalyticsMap'
import DemandChart from '@/features/analytics/components/DemandChart'
import { formatAmount, formatCount, formatTime } from '@/features/analytics/format'
import { useCountUp } from '@/features/analytics/hooks'
import { presetPeriod } from '@/features/analytics/period'
import { seriesOf, totalsOf } from '@/features/analytics/series'
import { useAnalyticsData } from '@/features/analytics/useAnalyticsData'
import { ChevronRightIcon } from '@/features/navigation/icons'
import { fetchNetworkRates } from '@/features/pricing/data/pricingRepository'
import type { NetworkRates } from '@/features/pricing/types'

import { fetchConnectors, fetchStations } from '../data/adminRepository'
import type { AdminDashboardMetrics, ConnectorItem, StationItem } from '../types'
import { Donut, Gauge, MiniBars } from './OverviewCharts'
import { Panel } from './ui'

/**
 * El tablero del backoffice: la red entera de un vistazo, **sin scrollear**.
 *
 * Ocupa todo el ancho y todo el alto que deja la ventana, y se reparte adentro: a la izquierda,
 * tres tarjetas chicas, la demanda de las últimas 24 horas y una fila con usuarios por rol,
 * conectores y precios; a la derecha, el mapa a todo el alto. Ningún panel tiene un alto fijo:
 * crecen y se achican con la ventana, así que en una pantalla de escritorio o en una tableta todo
 * lo que importa queda a la vista. En el celular no entra, y ahí sí se apila y se scrollea.
 *
 * **Lo que ya existía se reutiliza, no se copia.** El gráfico de demanda y el mapa son los de
 * Estadísticas, alimentados por el mismo `useAnalyticsData` —para el ADMIN, la red entera—; los
 * números suben con el mismo `useCountUp` y los paneles entran con el mismo `analytics-rise`.
 *
 * Cada panel trae lo suyo y no espera a los otros: si la telemetría tarda, las tarjetas de usuarios
 * y estaciones ya están; si falla, el resto del tablero sigue.
 */

type Section = 'users' | 'stations' | 'connectors' | 'pricing'

/** Las 24 horas en doce barritas de dos horas, para la tarjeta de energía. */
const ENERGY_BUCKETS = 12

const formatInt = (value: number) => formatCount(Math.round(value))

const formatMoney = (value: number) =>
  `$${value.toLocaleString('es-AR', { maximumFractionDigits: 2 })}`

/**
 * Una de las tres tarjetas de arriba: el rótulo, el número grande y, pegadas a él, sus barras.
 * Nada más: sin ícono ni bajada, para que se lea de un golpe.
 */
function KpiCard({
  label,
  value,
  format,
  unit,
  chart,
  delayMs,
}: {
  label: string
  value: number
  format: (value: number) => string
  unit?: string
  chart: ReactNode
  delayMs: number
}) {
  const shown = useCountUp(value)
  return (
    <Panel
      as="article"
      delayMs={delayMs}
      className="flex min-h-32 min-w-0 flex-col justify-between gap-2"
    >
      <h2 className="text-text-muted text-xs font-semibold">{label}</h2>
      <div className="flex min-w-0 items-end gap-3">
        <p className="text-text flex shrink-0 items-baseline gap-1">
          <span className="charge-digits text-5xl tabular-nums">{format(shown)}</span>
          {unit !== undefined && (
            <span className="text-text-muted text-sm font-semibold">{unit}</span>
          )}
        </p>
        <div className="h-16 min-w-0 flex-1">{chart}</div>
      </div>
    </Panel>
  )
}

/** El renglón de una leyenda: punto de color, nombre, cantidad y porcentaje. */
function LegendRow({
  color,
  label,
  value,
  total,
}: {
  color: string
  label: string
  value: number
  total: number
}) {
  const share = total === 0 ? 0 : Math.round((value / total) * 100)
  return (
    <li className="flex items-center gap-2 text-xs">
      <span
        aria-hidden="true"
        className="h-2 w-2 shrink-0 rounded-full"
        style={{ background: color }}
      />
      <span className="text-text-muted min-w-0 flex-1 truncate">{label}</span>
      <span className="text-text font-bold tabular-nums">{formatCount(value)}</span>
      <span className="text-text-muted hidden w-9 text-right tabular-nums xl:inline">{share}%</span>
    </li>
  )
}

/** El título de un panel, con un acceso a su sección a la derecha si lo tiene. */
function PanelTitle({
  id,
  children,
  action,
}: {
  id: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex min-w-0 shrink-0 items-baseline justify-between gap-3">
      <h2 id={id} className="text-text truncate text-sm font-bold">
        {children}
      </h2>
      {action}
    </div>
  )
}

/** "Ver": el acceso chico de la esquina de un panel, en gris que se enciende en verde. */
function MoreButton({
  onClick,
  label,
  children,
}: {
  onClick: () => void
  /** El nombre completo para el lector: "Ver" solo no dice adónde lleva. */
  label: string
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="text-text-muted hover:text-primary focus-visible:outline-primary inline-flex shrink-0 cursor-pointer items-center gap-0.5 rounded text-xs font-semibold focus-visible:outline-2"
    >
      {children}
      <ChevronRightIcon className="h-3.5 w-3.5" />
    </button>
  )
}

/* Los colores de la dona y de las barras de usuarios: variables, porque cambian con el tema. */
const ROLE_COLOR = {
  driver: 'var(--color-st-available)',
  cpo: 'var(--admin-role-cpo)',
  admin: 'var(--admin-role-admin)',
}

export default function AdminOverview({
  metrics,
  onNavigate,
  onOpenStation,
}: {
  metrics: AdminDashboardMetrics
  onNavigate: (section: Section) => void
  /** Tocar un pin del mapa lleva a la sección de estaciones, buscando esa. */
  onOpenStation: (stationName: string) => void
}) {
  /* Las últimas 24 horas, fijas mientras el tablero está abierto: es un resumen, no un explorador. */
  const [period] = useState(() => presetPeriod('24h'))
  const analytics = useAnalyticsData(period)
  const totals = useMemo(() => totalsOf(analytics.connectors), [analytics.connectors])
  const series = useMemo(
    () => seriesOf(analytics.connectors, period.from, period.to),
    [analytics.connectors, period],
  )
  const allStationIds = useMemo(
    () => new Set(analytics.stations.map((s) => s.id)),
    [analytics.stations],
  )

  /* Las barritas de energía: la serie del gráfico, juntada en doce tramos de dos horas. */
  const energyBuckets = useMemo(() => {
    const buckets = Array.from({ length: ENERGY_BUCKETS }, () => 0)
    const span = period.to.getTime() - period.from.getTime()
    for (const bar of series.bars) {
      const at = (bar.start.getTime() - period.from.getTime()) / span
      const index = Math.min(ENERGY_BUCKETS - 1, Math.max(0, Math.floor(at * ENERGY_BUCKETS)))
      buckets[index] += bar.energyKwh
    }
    return buckets
  }, [series, period])

  const [connectors, setConnectors] = useState<ConnectorItem[] | null>(null)
  const [stations, setStations] = useState<StationItem[]>([])
  const [rates, setRates] = useState<NetworkRates | null>(null)
  const [ratesFailed, setRatesFailed] = useState(false)

  useEffect(() => {
    let alive = true
    fetchConnectors().then(
      (rows) => alive && setConnectors(rows),
      () => alive && setConnectors([]),
    )
    fetchStations().then(
      (rows) => alive && setStations(rows),
      () => undefined,
    )
    fetchNetworkRates().then(
      (value) => alive && setRates(value),
      () => alive && setRatesFailed(true),
    )
    return () => {
      alive = false
    }
  }, [])

  const status = useMemo(() => {
    const counts = { AVAILABLE: 0, OCCUPIED: 0, OUT_OF_SERVICE: 0 }
    for (const c of connectors ?? []) {
      if (c.operationalStatus in counts) counts[c.operationalStatus as keyof typeof counts]++
    }
    return counts
  }, [connectors])
  const connectorTotal = status.AVAILABLE + status.OCCUPIED + status.OUT_OF_SERVICE
  const availableShown = useCountUp(status.AVAILABLE)

  /*
    Una barrita por estación, todas del mismo alto: verde la que anda, roja la dada de baja. Mientras
    llega la lista, las cuentas del tablero dan la misma forma.
  */
  const stationBars =
    stations.length > 0
      ? stations.map((s) => (s.active ? 'var(--color-st-available)' : 'var(--color-st-offline)'))
      : [
          ...Array.from({ length: metrics.activeStations }, () => 'var(--color-st-available)'),
          ...Array.from({ length: metrics.inactiveStations }, () => 'var(--color-st-offline)'),
        ]

  const roles = [
    { label: 'Conductores', value: metrics.driverCount, color: ROLE_COLOR.driver },
    { label: 'CPO', value: metrics.cpoCount, color: ROLE_COLOR.cpo },
    { label: 'Admins', value: metrics.adminCount, color: ROLE_COLOR.admin },
  ]

  return (
    /*
      La misma disposición que Estadísticas: arriba, los números y la demanda a la izquierda y el
      mapa a la derecha, en la proporción de allá (1,75 a 1); abajo, a todo el ancho, usuarios por
      rol, conectores y precios. El mapa queda del tamaño del de Estadísticas en vez de ocupar la
      pantalla de arriba abajo.

      De `md` en adelante el tablero mide exactamente lo que deja la ventana (`h-full`) y se reparte
      adentro con `flex` y `min-h-0`: así no hay scroll. El `min-h` es el piso para una ventana muy
      baja, donde aparece el scroll en vez de aplastar los gráficos hasta no leerse. Hasta `lg` el
      mapa va debajo de la demanda.
    */
    <div className="flex flex-col gap-4 md:h-full md:min-h-[36rem]">
      <div className="grid gap-4 lg:min-h-0 lg:flex-[1.3] lg:grid-cols-[minmax(0,1.75fr)_minmax(0,1fr)]">
        <div className="flex min-h-0 min-w-0 flex-col gap-4">
          <div className="grid shrink-0 gap-4 sm:grid-cols-2">
            <KpiCard
              label="Energía · 24 h"
              value={totals.energyKwh}
              format={formatAmount}
              unit="kWh"
              chart={<MiniBars values={energyBuckets} />}
              delayMs={0}
            />
            <KpiCard
              label="Estaciones operativas"
              value={metrics.activeStations}
              format={formatInt}
              unit={`/ ${formatCount(metrics.totalStations)}`}
              chart={<MiniBars values={stationBars.map(() => 1)} colors={stationBars} />}
              delayMs={120}
            />
          </div>

          <Panel
            delayMs={160}
            labelledBy="admin-demand"
            className="flex min-h-56 flex-col gap-2 md:min-h-48 lg:min-h-40 lg:flex-1"
          >
            <PanelTitle
              id="admin-demand"
              action={
                <Link
                  to="/analytics"
                  className="text-text-muted hover:text-primary focus-visible:outline-primary inline-flex items-center gap-0.5 rounded text-xs font-semibold focus-visible:outline-2"
                >
                  Ver estadísticas
                  <ChevronRightIcon className="h-3.5 w-3.5" />
                </Link>
              }
            >
              Demanda de la red
              <span className="text-text-muted ml-2 text-xs font-normal">
                desde las {formatTime(period.from)}
              </span>
            </PanelTitle>
            <div className="relative flex min-h-0 flex-1 flex-col">
              <DemandChart
                series={series}
                from={period.from}
                to={period.to}
                animationKey={analytics.state}
                barColor="var(--color-st-available)"
              />
              {(analytics.state !== 'ready' || totals.sessions === 0) && (
                <div className="pointer-events-none absolute inset-0 grid place-items-center px-6 text-center">
                  <p
                    className={`text-text-muted text-sm ${analytics.state === 'loading' ? 'animate-pulse' : ''}`}
                  >
                    {analytics.state === 'loading'
                      ? 'Cargando la demanda…'
                      : analytics.state === 'error'
                        ? 'No pudimos traer la demanda.'
                        : 'Todavía no hubo cargas en las últimas 24 h.'}
                  </p>
                </div>
              )}
            </div>
          </Panel>
        </div>

        <Panel
          delayMs={320}
          labelledBy="admin-map"
          className="relative min-h-72 overflow-hidden !p-0 md:min-h-56 lg:min-h-0"
        >
          {/* El título flota sobre el mapa en una pastilla de vidrio. */}
          <h2
            id="admin-map"
            className="glass-panel text-text absolute top-3 left-3 z-[500] rounded-full px-3.5 py-1.5 text-sm font-bold"
          >
            Mapa de la red
          </h2>
          <div className="absolute inset-0">
            <AnalyticsMap
              stations={analytics.stations}
              selectedIds={allStationIds}
              onFocus={(id) => {
                const station = analytics.stations.find((s) => s.id === id)
                if (station !== undefined) onOpenStation(station.name)
              }}
            />
          </div>
        </Panel>
      </div>

      <div className="grid gap-4 sm:grid-cols-3 md:min-h-52 md:flex-1">
        <Panel delayMs={200} labelledBy="admin-roles" className="flex min-h-0 flex-col gap-3">
          <PanelTitle
            id="admin-roles"
            action={
              <MoreButton label="Ver usuarios" onClick={() => onNavigate('users')}>
                Ver
              </MoreButton>
            }
          >
            Usuarios por rol
          </PanelTitle>
          <div className="mx-auto aspect-square h-36 max-w-full sm:h-auto sm:min-h-0 sm:flex-1">
            <Donut slices={roles} center={formatCount(metrics.totalUsers)} caption="usuarios" />
          </div>
          <ul className="flex shrink-0 flex-col gap-1">
            {roles.map((role) => (
              <LegendRow key={role.label} {...role} total={metrics.totalUsers} />
            ))}
          </ul>
        </Panel>

        <Panel delayMs={240} labelledBy="admin-connectors" className="flex min-h-0 flex-col gap-3">
          <PanelTitle
            id="admin-connectors"
            action={
              <MoreButton label="Ver conectores" onClick={() => onNavigate('connectors')}>
                Ver
              </MoreButton>
            }
          >
            Conectores
          </PanelTitle>
          <div className="h-32 sm:h-auto sm:min-h-0 sm:flex-1">
            <Gauge
              segments={[
                { value: status.AVAILABLE, stroke: 'stroke-st-available' },
                { value: status.OCCUPIED, stroke: 'stroke-st-occupied' },
                { value: status.OUT_OF_SERVICE, stroke: 'stroke-st-offline' },
              ]}
            >
              <span className="charge-digits text-text text-5xl leading-none tabular-nums">
                {connectors === null ? '–' : formatInt(availableShown)}
              </span>
              <span className="text-text-muted mt-1 text-xs font-semibold">
                libres de {formatCount(connectorTotal)}
              </span>
            </Gauge>
          </div>
          <ul className="flex shrink-0 flex-col gap-1">
            <LegendRow
              color="var(--color-st-available)"
              label="Disponibles"
              value={status.AVAILABLE}
              total={connectorTotal}
            />
            <LegendRow
              color="var(--color-st-occupied)"
              label="Ocupados"
              value={status.OCCUPIED}
              total={connectorTotal}
            />
            <LegendRow
              color="var(--color-st-offline)"
              label="Fuera de servicio"
              value={status.OUT_OF_SERVICE}
              total={connectorTotal}
            />
          </ul>
        </Panel>

        <Panel delayMs={280} labelledBy="admin-rates" className="flex min-h-0 flex-col gap-3">
          <PanelTitle
            id="admin-rates"
            action={
              <MoreButton label="Editar precios" onClick={() => onNavigate('pricing')}>
                Editar
              </MoreButton>
            }
          >
            Precios vigentes
          </PanelTitle>
          {rates === null ? (
            <p className={`text-text-muted text-sm ${ratesFailed ? '' : 'animate-pulse'}`}>
              {ratesFailed ? 'No pudimos traer los precios.' : 'Cargando precios…'}
            </p>
          ) : (
            /*
              A todo el ancho de la tarjeta (pedido del 27/11): cada precio es un renglón que se
              estira, el rótulo a la izquierda y el monto grande contra el borde derecho, con una
              raya fina entre renglones. Los tres montos van en la misma columna: la burbuja del
              asistente ya no estorba, porque se puede arrastrar a otro lado.
            */
            <dl className="divide-border/50 flex min-h-0 flex-1 flex-col divide-y">
              {[
                ['Carga normal', formatMoney(rates.normalKwhRate), '/ kWh'],
                ['Carga rápida', formatMoney(rates.fastKwhRate), '/ kWh'],
                ['Multa por exceso', formatMoney(rates.excessPenaltyPerMin), '/ min'],
              ].map(([label, value, unit]) => (
                <div key={label} className="flex flex-1 items-center justify-between gap-3 py-1.5">
                  <dt className="text-text-muted text-sm font-semibold whitespace-nowrap">
                    {label}
                  </dt>
                  <dd className="text-text flex items-baseline gap-1.5 whitespace-nowrap">
                    <span className="charge-digits text-4xl tabular-nums">{value}</span>
                    <span className="text-text-muted text-xs font-semibold">{unit}</span>
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </Panel>
      </div>
    </div>
  )
}
