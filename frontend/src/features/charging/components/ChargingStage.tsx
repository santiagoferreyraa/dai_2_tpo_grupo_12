import type { ReactNode } from 'react'

import { BatteryIcon, BoltIcon, ClockIcon, GaugeIcon } from '@/features/navigation/icons'
import VehicleImage from '@/features/vehicles/components/VehicleImage'
import { VEHICLE_IMAGE, useDriverVehicle } from '@/features/vehicles/vehicle'

import { formatDuration, formatKw, formatKwh } from '../format'
import type { ChargingSession } from '../types'
import ChargingTank from './ChargingTank'

interface ChargingStageProps {
  session: ChargingSession
  elapsedMs: number
  /** Cada cuánto se refresca la sesión: el fluido tarda eso en llegar a cada lectura nueva. */
  refreshMs: number
  /** La sesión sigue abierta. Cerrada, el estado dice "terminada" y la potencia vuelve a cero. */
  live: boolean
  /** Lo que va debajo de las estadísticas: el botón de detener mientras carga. */
  action?: ReactNode
}

type IconComponent = (props: { className?: string }) => React.ReactElement

interface StatData {
  Icon: IconComponent
  label: string
  value: string
  unit: string
}

/**
 * La escena de la carga, adentro del recuadro que se llena de fluido.
 *
 * Es la misma escena con la carga en curso y con la carga terminada —debajo del resumen—. Lo que
 * cambia es solo si hay botón y si la potencia sigue corriendo.
 *
 * **Son dos disposiciones y no una que se acomoda.** En tablet y escritorio va la escena entera:
 * cargador, auto y las cuatro estadísticas en una grilla de dos por dos. En el celular el recuadro
 * ocupa toda la pantalla, como el mapa, y el cargador y el auto se van: en una columna angosta
 * quedaban del tamaño de una estampilla y empujaban los datos fuera de la vista. Ahí la batería pasa
 * a ser la protagonista, en un círculo en el medio, y las otras tres van en una fila debajo.
 */
export default function ChargingStage({
  session,
  elapsedMs,
  refreshMs,
  live,
  action,
}: ChargingStageProps) {
  const battery = session.batteryPercent === null ? '—' : String(Math.round(session.batteryPercent))

  const time: StatData = {
    Icon: ClockIcon,
    label: 'Tiempo',
    value: formatDuration(elapsedMs),
    unit: elapsedMs >= 3_600_000 ? 'h' : 'min',
  }
  const power: StatData = { Icon: GaugeIcon, label: 'Potencia', ...powerOf(session, live) }
  const energy: StatData = {
    Icon: BoltIcon,
    label: 'Energía',
    value: formatKwh(session.kwhDelivered),
    unit: 'kWh',
  }
  const batteryStat: StatData = { Icon: BatteryIcon, label: 'Batería', value: battery, unit: '%' }

  return (
    <ChargingTank
      percent={session.batteryPercent}
      targetPercent={session.targetBatteryPercent}
      refreshMs={refreshMs}
      /*
        En el celular, sin borde ni esquinas: el recuadro ES la pantalla, igual que el mapa. Lo
        resuelve `.charge-tank` en `charging.css`; desde tablet vuelve a ser una tarjeta de vidrio.
      */
      className="flex min-h-full flex-1 flex-col md:min-h-[38rem]"
    >
      <header className="relative z-10 flex justify-center px-6 pt-5 md:justify-start md:px-8 md:pt-6">
        {/* En el celular es una píldora de vidrio, como los filtros del mapa: flota sobre el fluido. */}
        <h1 className="glass-panel glass-over-photo text-text flex items-center gap-2.5 rounded-full px-4 py-1.5 text-sm font-semibold md:hidden">
          <Status live={live} connectorId={session.connectorId} />
        </h1>
        <h1 className="text-text hidden items-center gap-2.5 text-sm font-semibold md:flex">
          <Status live={live} connectorId={session.connectorId} />
        </h1>
      </header>

      {/* ───────────── Celular ───────────── */}
      <div className="relative z-10 flex flex-1 flex-col items-center justify-between gap-6 px-5 pt-8 pb-32 md:hidden">
        <BatteryDial value={battery} target={session.targetBatteryPercent} />

        <div className="flex w-full flex-col gap-3">
          <dl className="grid grid-cols-3 gap-3">
            <Stat {...time} size="sm" />
            <Stat {...power} size="sm" />
            <Stat {...energy} size="sm" />
          </dl>
          {action}
        </div>
      </div>

      {/* ───────────── Tablet y escritorio ───────────── */}
      <Scene stats={[time, power, energy, batteryStat]} action={action} />
    </ChargingTank>
  )
}

function Status({ live, connectorId }: { live: boolean; connectorId: number }) {
  return (
    <>
      {live && <LiveDot />}
      {live ? 'Cargando' : 'Carga terminada'}
      <span className="text-text-muted font-medium">· Conector {connectorId}</span>
    </>
  )
}

function LiveDot() {
  return (
    <span aria-hidden="true" className="relative flex h-2.5 w-2.5">
      <span className="bg-primary absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 motion-reduce:animate-none" />
      <span className="bg-primary relative inline-flex h-2.5 w-2.5 rounded-full" />
    </span>
  )
}

/**
 * Cargador, auto y las cuatro estadísticas, apoyados sobre el mismo piso.
 *
 * Las estadísticas van centradas en el alto del recuadro y no apoyadas en el piso como el cargador
 * y el auto: así quedan lejos de la línea del límite, que con un objetivo alto pasa por arriba.
 */
function Scene({ stats, action }: { stats: StatData[]; action?: ReactNode }) {
  const vehicle = useDriverVehicle()

  return (
    <div className="relative z-10 hidden flex-1 grid-cols-[minmax(0,0.8fr)_minmax(0,1.6fr)_auto] items-end gap-6 px-8 pt-16 pb-[7%] md:grid xl:px-12">
      <Charger />
      <Car>
        {vehicle === null ? (
          <img src={VEHICLE_IMAGE} alt="" className="w-full object-contain" />
        ) : (
          <VehicleImage model={vehicle} className="w-full" />
        )}
      </Car>

      <div className="flex w-[19rem] flex-col gap-4 self-center lg:w-[23rem] xl:w-[25rem]">
        <dl className="grid grid-cols-2 gap-4">
          {stats.map((stat) => (
            <Stat key={stat.label} {...stat} size="lg" />
          ))}
        </dl>
        {action}
      </div>
    </div>
  )
}

/**
 * La potencia del momento, y si el cargador todavía no la informó, el tope del conector. Con la
 * carga cerrada ya no corre nada: cero, y no el último valor como si siguiera.
 */
function powerOf(session: ChargingSession, live: boolean): { value: string; unit: string } {
  if (!live) return { value: '0', unit: 'kW' }
  if (session.currentPowerKw !== null)
    return { value: formatKw(session.currentPowerKw), unit: 'kW' }
  if (session.powerLimitKw !== null)
    return { value: formatKw(session.powerLimitKw), unit: 'kW máx' }
  return { value: '—', unit: 'kW' }
}

/**
 * Una estadística, con la anatomía del diseño: arriba a la izquierda qué es, el número grande en el
 * centro y la unidad abajo a la derecha, como en el display de un tablero.
 *
 * **Arriba va el ícono en escritorio y el nombre en el celular.** En la tarjeta grande el ícono
 * alcanza —un reloj y "min", un rayo y "kWh"— y el nombre queda para el lector de pantalla. En la
 * chica el ícono de cinco milímetros ya no se distinguía, y la palabra se lee mejor.
 *
 * **El vidrio es el de `.glass-over-photo` y no el de `.glass-panel`.** Estas tarjetas se apoyan
 * sobre el fluido, que es verde brillante y no el fondo parejo para el que está medido el 32% del
 * vidrio común: con ese, el texto claro del tema oscuro quedaba sobre verde claro y no se leía.
 *
 * **El número va en una condensada y tan alto como entre** (ver `.charge-digits`). Un valor largo
 * ("1:02:05") usa una proporción menor, porque tiene más caracteres que acomodar.
 */
function Stat({ Icon, label, value, unit, size }: StatData & { size: 'sm' | 'lg' }) {
  const long = value.length > 5
  const small = size === 'sm'

  return (
    /*
      La tarjeta es un contenedor de consultas en las dos medidas (`container-type: size`): el
      número se dimensiona con `cqh` y `cqw` contra el lugar que tiene, y se queda con lo que dé
      menos. Así ocupa casi todo el alto entre el rótulo y la unidad sin salirse a lo ancho, en
      cualquier tamaño de tarjeta.
    */
    <div
      className={`glass-panel glass-over-photo flex flex-col [container-type:size] ${
        small ? 'h-28 rounded-2xl p-2.5' : 'h-40 rounded-3xl p-4 lg:h-44'
      }`}
    >
      {small ? (
        <dt className="text-text-muted text-[11px] leading-none font-semibold">{label}</dt>
      ) : (
        <dt className="sr-only">{label}</dt>
      )}
      <dd className="contents">
        {!small && <Icon className="text-primary h-7 w-7 shrink-0" />}
        <span
          className={`charge-digits text-text flex min-h-0 flex-1 items-center justify-center ${
            small
              ? long
                ? 'text-[min(40cqh,30cqw)]'
                : 'text-[min(62cqh,46cqw)]'
              : long
                ? 'text-[min(46cqh,34cqw)]'
                : 'text-[min(62cqh,44cqw)]'
          }`}
        >
          {value}
        </span>
        <span
          className={`text-text-muted self-end leading-none font-extrabold uppercase ${
            small ? 'text-[10px]' : 'text-sm'
          }`}
        >
          {unit}
        </span>
      </dd>
    </div>
  )
}

/**
 * La batería en el celular: el número grande adentro de un círculo de vidrio.
 *
 * Va en un círculo y no suelta porque el fluido pasa por detrás: a la mitad de la carga, un número
 * claro sobre el verde del tema oscuro no se leería. El vidrio lo separa del fondo sin importar
 * por dónde ande la superficie.
 */
function BatteryDial({ value, target }: { value: string; target: number | null }) {
  return (
    <div className="glass-panel glass-over-photo my-auto flex h-60 w-60 flex-col items-center justify-center rounded-full">
      <BatteryIcon className="text-primary h-7 w-7" />
      {/*
        El "%" va colgado a la derecha del número, fuera de su ancho: así lo que queda centrado en
        el círculo es el número, y no el número más el signo.
      */}
      <p className="text-text relative mt-1 leading-none font-extrabold tracking-tight tabular-nums">
        <span className="sr-only">Batería: </span>
        {value === '—' ? (
          /*
            Sin lectura todavía va una raya dibujada y no el carácter "—": el guion largo se apoya
            a la altura de las minúsculas, bien por debajo del medio de los números, y quedaba
            caído respecto del "%". Esta va donde iría el centro de un número.
          */
          <span className="relative inline-block h-[1em] w-[0.9em] align-top text-[5.5rem]">
            <span className="sr-only">sin dato</span>
            <span
              aria-hidden="true"
              className="absolute inset-x-[0.1em] top-[0.46em] h-[0.09em] rounded-full bg-current"
            />
          </span>
        ) : (
          <span className="text-[5.5rem]">{value}</span>
        )}
        <span className="text-text-muted absolute top-2 left-full ml-1 text-2xl">%</span>
      </p>
      {target !== null && (
        <p className="text-text-muted mt-2 text-xs font-semibold">hasta el {target}%</p>
      )}
    </div>
  )
}

/**
 * El cargador, con la sombra que la foto no trae.
 *
 * La elipse no va centrada en la foto: los cables cuelgan del costado izquierdo y la manguera de la
 * derecha sobresale, así que la base del gabinete —lo que de verdad se apoya— ocupa una franja un
 * poco a la izquierda del centro. La sombra se mide contra esa base.
 */
function Charger() {
  return (
    <div className="relative w-full max-w-[18rem] justify-self-center">
      <div
        aria-hidden="true"
        className="charge-floor-shadow absolute -bottom-1.5 left-[6%] h-6 w-[78%]"
      />
      <img src="/charger.webp" alt="" className="relative h-auto w-full" />
    </div>
  )
}

/** El auto del conductor, con la misma sombra difusa debajo de las ruedas. */
function Car({ children }: { children: ReactNode }) {
  return (
    <div className="relative w-full max-w-[38rem] justify-self-center">
      <div
        aria-hidden="true"
        className="charge-floor-shadow absolute -bottom-3 left-[4%] h-9 w-[92%]"
      />
      <div className="relative">{children}</div>
    </div>
  )
}
