import { useRef, useState } from 'react'

import { formatAmount, formatCount, formatMoment } from '../format'
import { useCountUp } from '../hooks'
import { bucketCaption, LIST_TEMPLATE } from '../layout'
import type { DashboardModel } from '../useDashboardModel'
import { ChartStatus, ConnectorList, SearchField, StationList } from './DashboardParts'
import DemandChart from './DemandChart'
import PeriodPicker from './PeriodPicker'

/**
 * Una cifra del celular: la misma tarjeta que la pantalla de carga (`Stat` en `ChargingStage`),
 * con el rótulo arriba, el número grande al medio y la unidad abajo a la derecha.
 *
 * El número se dimensiona contra la tarjeta (`container-type: size`), así "1.255" y "24" llenan el
 * mismo lugar sin salirse. Llega contando, como en escritorio.
 */
function MobileStat({
  label,
  value,
  unit,
  format,
  delayMs,
}: {
  label: string
  value: number
  unit: string
  format: (value: number) => string
  delayMs: number
}) {
  const text = format(useCountUp(value))
  const long = format(value).length > 4

  return (
    <div
      className="glass-panel analytics-rise flex h-28 flex-col rounded-2xl p-2.5 [container-type:size]"
      style={{ ['--rise-delay' as string]: `${delayMs}ms` }}
    >
      <dt className="text-text-muted text-[11px] leading-none font-semibold">{label}</dt>
      <dd className="contents">
        <span
          className={`charge-digits text-text flex min-h-0 flex-1 items-center justify-center ${
            long ? 'text-[min(40cqh,26cqw)]' : 'text-[min(62cqh,46cqw)]'
          }`}
        >
          {text}
        </span>
        <span className="text-text-muted self-end text-[10px] leading-none font-extrabold uppercase">
          {unit}
        </span>
      </dd>
    </div>
  )
}

const LISTS = ['Estaciones', 'Conectores'] as const

/**
 * Las dos listas, una al lado de la otra, que se pasan deslizando.
 *
 * El selector de arriba es el interruptor del tema (`ThemeToggle`) con dos rótulos: el mismo
 * vidrio, la misma perilla verde y la misma curva. **La perilla sigue al dedo**: su posición sale
 * del scroll, no del índice, así que acompaña el deslizamiento en vez de saltar al soltar.
 */
function ListsSlider({ model }: { model: DashboardModel }) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [progress, setProgress] = useState(0)
  const active = Math.round(progress)

  function go(index: number) {
    const track = trackRef.current
    if (track === null) return
    track.scrollTo({ left: index * track.clientWidth, behavior: 'smooth' })
  }

  return (
    <section
      className="glass-panel analytics-rise flex flex-col gap-3 rounded-3xl p-3"
      style={{ ['--rise-delay' as string]: '200ms' }}
    >
      <div
        role="tablist"
        aria-label="Listas del tablero"
        className="glass-inset relative grid grid-cols-2 rounded-full p-1"
      >
        <span
          aria-hidden="true"
          className="brand-fill absolute top-1 bottom-1 left-1 w-[calc(50%-0.25rem)] rounded-full shadow-sm"
          style={{ transform: `translateX(${progress * 100}%)` }}
        />
        {LISTS.map((label, index) => (
          <button
            key={label}
            type="button"
            role="tab"
            aria-selected={active === index}
            onClick={() => go(index)}
            className={`relative z-10 rounded-full py-2 text-sm font-semibold transition-colors duration-300 ${
              active === index ? 'text-on-primary' : 'text-text-muted'
            }`}
          >
            {label}
            <span className="ml-1.5 text-xs opacity-70">
              {index === 0 ? model.visibleStations.length : model.visibleConnectors.length}
            </span>
          </button>
        ))}
      </div>

      <div
        ref={trackRef}
        onScroll={(event) => {
          const track = event.currentTarget
          setProgress(track.clientWidth === 0 ? 0 : track.scrollLeft / track.clientWidth)
        }}
        className="no-scrollbar flex snap-x snap-mandatory overflow-x-auto overscroll-x-contain"
      >
        <div className="flex h-[26rem] w-full shrink-0 snap-start flex-col gap-3">
          <SearchField model={model} />
          <StationList model={model} template={LIST_TEMPLATE.narrow} />
        </div>
        <div className="flex h-[26rem] w-full shrink-0 snap-start flex-col pt-1">
          <ConnectorList model={model} template={LIST_TEMPLATE.narrow} />
        </div>
      </div>
    </section>
  )
}

/**
 * El tablero en el celular, con la forma de la portada: un saludo arriba con el título grande y,
 * debajo, bloques de vidrio que se recorren con el scroll de la página.
 *
 * **Sin mapa.** En el ancho de un teléfono el mapa queda chico para leer los pines y se queda con
 * el dedo que quería scrollear la página. Lo que el mapa resuelve en escritorio —ver qué está
 * elegido— acá lo dice la lista.
 *
 * Orden: período, las tres cifras, el gráfico y las listas. Es el orden en que se lee: primero el
 * cuándo, después el cuánto y por último el detalle de dónde.
 */
export default function MobileDashboard({ model }: { model: DashboardModel }) {
  const { totals, series, period } = model

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="flex flex-col gap-6 px-5 pt-6 pb-36">
        <header>
          <p className="text-text-muted text-sm">{model.scope}</p>
          <h1 className="text-text mt-1 text-3xl leading-tight font-extrabold tracking-tight">
            Estadísticas
          </h1>
        </header>

        <PeriodPicker period={period} onChange={model.setPeriod} rangeError={model.rangeError} />

        <div>
          <dl className="grid grid-cols-3 gap-3">
            <MobileStat
              label="Energía"
              value={totals.energyKwh}
              unit="kWh"
              format={formatAmount}
              delayMs={0}
            />
            <MobileStat
              label="Sesiones"
              value={totals.sessions}
              unit="cargas"
              format={(v) => formatCount(Math.round(v))}
              delayMs={60}
            />
            <MobileStat
              label="Pico simult."
              value={totals.peakKw}
              unit="kW"
              format={formatAmount}
              delayMs={120}
            />
          </dl>
          <p className="text-text-muted mt-2 text-right text-xs">
            {totals.peakAt === null
              ? 'Sin cargas en el período'
              : `Pico de potencia simultánea ${formatMoment(totals.peakAt)}`}
          </p>
        </div>

        <section>
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-text text-xl leading-tight font-extrabold tracking-tight">
              Demanda por franja
            </h2>
            <span className="text-text-muted shrink-0 text-xs">{bucketCaption(model)}</span>
          </div>
          <div
            className="glass-panel analytics-rise relative mt-3 flex h-60 flex-col rounded-3xl p-4"
            style={{ ['--rise-delay' as string]: '140ms' }}
          >
            <DemandChart
              series={series}
              from={period.from}
              to={period.to}
              animationKey={model.animationKey}
            />
            <ChartStatus model={model} />
          </div>
        </section>

        <ListsSlider model={model} />
      </div>
    </div>
  )
}
