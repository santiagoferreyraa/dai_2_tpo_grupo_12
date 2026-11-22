import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { BatteryIcon } from '@/features/navigation/icons'
import VehicleImage from '@/features/vehicles/components/VehicleImage'
import SpecIcon from '@/features/vehicles/components/SpecIcon'
import type { VehicleModel } from '@/features/vehicles/types'
import { VEHICLE_IMAGE, specsOf, useDriverVehicle } from '@/features/vehicles/vehicle'

import { formatDuration, formatKw, formatKwh } from '../format'
import type { ChargingSession } from '../types'
import ChargeGauge from './ChargeGauge'
import ChargingBattery from './ChargingBattery'
import ChargingStationPanel from './ChargingStationPanel'
import ChargingTank from './ChargingTank'

interface ChargingStageProps {
  session: ChargingSession
  elapsedMs: number
  /** Cada cuánto se refresca la sesión: el fluido tarda eso en llegar a cada lectura nueva. */
  refreshMs: number
  /** La sesión sigue abierta. Cerrada, el estado dice "terminada" y la potencia vuelve a cero. */
  live: boolean
  /** El botón de detener mientras carga: debajo de las estadísticas o colgado de la batería. */
  action?: ReactNode
}

interface StatData {
  label: string
  value: string
  unit: string
}

/**
 * La escena de la carga.
 *
 * Es la misma escena con la carga en curso y con la carga terminada —debajo del resumen—. Lo que
 * cambia es solo si hay botón y si la potencia sigue corriendo.
 *
 * **Son dos disposiciones y no una que se acomoda.** En tablet y escritorio van tres paneles de
 * vidrio: el auto con la batería, la estación en el mapa y las estadísticas (ver `Scene`). En el
 * celular el recuadro ocupa toda la pantalla, como el mapa, y se llena de fluido; el cargador y el
 * auto se van: en una columna angosta quedaban del tamaño de una estampilla y empujaban los datos
 * fuera de la vista. Ahí la batería pasa a ser la protagonista, en un círculo en el medio, y las
 * otras tres van en una fila debajo.
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
    label: 'Tiempo',
    value: formatDuration(elapsedMs),
    unit: elapsedMs >= 3_600_000 ? 'h' : 'min',
  }
  const power: StatData = { label: 'Potencia', ...powerOf(session, live) }
  const energy: StatData = {
    label: 'Energía',
    value: formatKwh(session.kwhDelivered),
    unit: 'kWh',
  }

  return (
    <ChargingTank
      percent={session.batteryPercent}
      targetPercent={session.targetBatteryPercent}
      refreshMs={refreshMs}
      /*
        En el celular el recuadro ES la pantalla, igual que el mapa. Desde tablet es solo el marco
        de los tres paneles de vidrio, sin fondo propio.
      */
      className="flex min-h-full flex-1 flex-col md:min-h-0"
    >
      {/* ───────────── Celular ───────────── */}
      <header className="relative z-10 flex justify-center px-6 pt-5 md:hidden">
        {/* Una píldora de vidrio, como los filtros del mapa: flota sobre el fluido. */}
        <h1 className="glass-panel glass-over-photo text-text flex items-center gap-2.5 rounded-full px-4 py-1.5 text-sm font-semibold">
          <Status live={live} connectorId={session.connectorId} />
        </h1>
      </header>

      <div className="relative z-10 flex flex-1 flex-col items-center justify-between gap-6 px-5 pt-8 pb-32 md:hidden">
        <BatteryDial value={battery} target={session.targetBatteryPercent} />

        <div className="flex w-full flex-col gap-3">
          <dl className="grid grid-cols-3 gap-3">
            <Stat {...time} />
            <Stat {...power} />
            <Stat {...energy} />
          </dl>
          {action}
        </div>
      </div>

      {/* ───────────── Tablet y escritorio ───────────── */}
      <Scene
        session={session}
        elapsedMs={elapsedMs}
        refreshMs={refreshMs}
        live={live}
        action={action}
        battery={battery}
        power={power}
      />
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
 * La escena de tablet y escritorio: tres paneles de vidrio, como las tarjetas de la portada.
 *
 * 1. **El auto y la batería.** Cargador y auto juntos, el auto adelante, como en una foto; al lado,
 *    la batería que se llena con su porcentaje y, colgado de su esquina, el botón de detener.
 *    Abajo, la ficha del modelo, como en la portada.
 * 2. **Dónde.** La estación, con el mapa chico encuadrado sobre ella (`ChargingStationPanel`).
 * 3. **Las estadísticas.** Tres medidores de arco iguales: la potencia y la energía llenándose
 *    contra su máximo, y en el medio el tiempo, como un reloj.
 *
 * Los tres van adentro de un marco más grande, que los agrupa como una sola pantalla.
 *
 * **Entra entera en la pantalla, sin scroll.** La escena toma el alto que queda debajo de la barra
 * de arriba y lo reparte: 55% para la fila del auto y el mapa, 45% para las estadísticas. Todo lo
 * de adentro se achica con su panel —el auto, los medidores— en vez de empujarlo hacia abajo.
 * Lo que tiene medida fija —la batería, la ficha del auto— va en su tamaño chico, y el grande
 * solo en pantallas anchas **y altas** (`xl:[@media(min-height:56rem)]:`): una notebook de 1366×768
 * es ancha pero baja, y con el tamaño grande la batería se montaba sobre la ficha.
 *
 * **El fluido acá no llena la pantalla sino la batería.** Con el auto en pantalla, un recuadro verde
 * hasta la mitad lo tapaba; la batería horizontal cuenta lo mismo —el nivel real, avanzando entre
 * lecturas— sin pisar nada.
 *
 * Desde `lg` el auto y la estación comparten la fila de arriba. En tablet vertical los tres se
 * apilan, y también entran en el alto.
 */
function Scene({
  session,
  elapsedMs,
  refreshMs,
  live,
  action,
  battery,
  power,
}: {
  session: ChargingSession
  elapsedMs: number
  refreshMs: number
  live: boolean
  action?: ReactNode
  battery: string
  power: StatData
}) {
  const vehicle = useDriverVehicle()
  const time = timeOf(elapsedMs)

  return (
    /*
      El marco: un rectángulo más grande que abraza a los tres paneles, del mismo vidrio que ellos
      —vidrio sobre vidrio, como las tarjetas de la portada— y con aire de sobra alrededor. Mide lo
      que queda de la pantalla, y los paneles se reparten lo de adentro.
    */
    <div className="glass-panel relative z-10 hidden min-h-0 flex-1 rounded-[2rem] p-5 md:flex xl:p-6">
      <div className="grid min-h-0 flex-1 gap-5 md:grid-rows-[minmax(0,1.1fr)_minmax(0,0.85fr)_minmax(0,1fr)] lg:grid-cols-[minmax(0,1.75fr)_minmax(0,1fr)] lg:grid-rows-[minmax(0,55fr)_minmax(0,45fr)]">
        <section
          className="glass-panel flex min-h-0 flex-col rounded-3xl p-5 xl:[@media(min-height:56rem)]:p-8"
          aria-label="Tu auto y su batería"
        >
          {/*
            El estado va en una píldora de vidrio arriba a la derecha, sobre la batería: la misma
            del celular. Suelto arriba a la izquierda competía con el auto y se leía como un rótulo
            perdido.
          */}
          <h1 className="glass-panel glass-over-photo text-text flex shrink-0 items-center gap-2.5 self-end rounded-full px-4 py-1.5 text-sm font-semibold">
            <Status live={live} connectorId={session.connectorId} />
          </h1>

          <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] items-center gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] xl:gap-10">
            <CarWithCharger live={live}>
              {vehicle === null ? (
                <img src={VEHICLE_IMAGE} alt="" className="w-full object-contain" />
              ) : (
                <VehicleImage model={vehicle} className="w-full" />
              )}
            </CarWithCharger>

            {/*
              La fila sigue por debajo de la ficha (ver `VehicleSpecs`), así que la batería se
              centraría más abajo de lo que se ve. El relleno de abajo la sube: un poco más que lo
              que tapa la ficha, para que no quede pegada a ella.
            */}
            <div className="min-w-0 pb-14 xl:[@media(min-height:56rem)]:pb-20">
              <BatteryBlock
                session={session}
                refreshMs={refreshMs}
                live={live}
                value={battery}
                action={action}
              />
            </div>
          </div>

          <VehicleSpecs vehicle={vehicle} />
        </section>

        <ChargingStationPanel connectorId={session.connectorId} />

        <section className="glass-panel min-h-0 rounded-3xl px-6 pt-5 pb-4 lg:col-span-2 xl:px-10">
          <dl className="grid h-full grid-cols-3 gap-6 xl:gap-12">
            <ChargeGauge
              label="Potencia"
              kind="bar"
              /*
                Sin lectura del cargador, rayas y no el tope del conector como en el celular: acá
                el arco está al lado, y un "150" con el arco vacío se contradecía.
              */
              value={live && session.currentPowerKw === null ? '--' : power.value}
              unit="kW"
              {...powerGauge(session, live, vehicle?.maxChargeKw ?? null)}
              transitionMs={refreshMs}
            />
            <ChargeGauge
              label="Tiempo"
              kind="clock"
              value={time.value}
              unit={time.unit}
              compact={time.compact}
              fraction={null}
              transitionMs={1000}
            />
            <ChargeGauge
              label="Energía"
              kind="bar"
              value={formatKwh(session.kwhDelivered)}
              unit="kWh"
              {...energyGauge(session, vehicle?.batteryKwh ?? null)}
              transitionMs={refreshMs}
            />
          </dl>
        </section>
      </div>
    </div>
  )
}

/**
 * El cargador y el auto juntos, el auto adelante y pisándole la base al cargador, como en una foto
 * de catálogo. Separados en dos columnas se leían como dos fotos sueltas.
 *
 * **Se mide contra el lugar que tiene, a lo alto y a lo ancho** (`cqw` / `cqh`), y se apoya en el
 * piso de su celda. Esa celda sigue por debajo de la ficha del auto, así que la barra de vidrio le
 * tapa la parte de abajo —las ruedas y la base del cargador—, como el auto de la portada detrás de
 * su barra.
 *
 * Las medidas de adentro van en proporción a la caja (5:4), así el montaje se mantiene igual en
 * cualquier tamaño. Las fotos de los autos tienen proporciones distintas; por eso el auto se apoya
 * abajo a la derecha, y lo que cambia según el modelo es cuánto sube.
 */
function CarWithCharger({ live, children }: { live: boolean; children: ReactNode }) {
  return (
    <div className="relative h-full min-h-0 [container-type:size]">
      <div
        className="absolute inset-x-0 bottom-0 mx-auto"
        style={{
          width: 'min(100cqw, calc(100cqh * 1.25))',
          height: 'min(100cqh, calc(100cqw * 0.8))',
        }}
      >
        <div
          aria-hidden="true"
          className={`charge-aura pointer-events-none absolute right-[-4%] bottom-[-10%] h-[70%] w-[80%] ${
            live ? 'charge-aura--live' : ''
          }`}
        />
        <div className="absolute top-0 left-0 h-[92%]">
          <div
            aria-hidden="true"
            className="charge-floor-shadow absolute -bottom-1.5 left-[6%] h-5 w-[78%]"
          />
          <img src="/charger.webp" alt="" className="relative h-full w-auto max-w-none" />
        </div>
        <div className="absolute right-0 bottom-[2%] w-[84%]">
          <div
            aria-hidden="true"
            className="charge-floor-shadow absolute -bottom-3 left-[4%] h-9 w-[92%]"
          />
          <div className="relative">{children}</div>
        </div>
      </div>
    </div>
  )
}

/**
 * La ficha del auto, igual a la de la barra de la portada: un dibujo verde y, a su lado, el rótulo
 * arriba del valor, los cuatro repartidos en columnas iguales adentro de una barra de vidrio.
 *
 * Se monta sobre la parte de abajo del auto y el cargador (el margen negativo de arriba): el vidrio
 * los desenfoca por detrás, igual que en la portada.
 *
 * **Sin auto elegido, la barra está igual**, con el mismo aviso de la portada y el botón para
 * elegirlo. Sin ella, la base del auto genérico quedaba a la vista contra el borde del panel y la
 * batería, que se acomoda contando con la barra, quedaba desbalanceada. Acá siempre hay sesión
 * —cargar la pide—, así que el botón va directo al perfil y no al ingreso.
 *
 * Es una copia de la de `VehicleHero` y no el mismo componente: allá la barra tiene además el
 * recorte hacia el borde del celular y el caso sin sesión, que acá no van. Lo compartido de verdad
 * —los datos y los dibujos— sale de `specsOf` y `SpecIcon`, así que no se pueden separar.
 */
function VehicleSpecs({ vehicle }: { vehicle: VehicleModel | null }) {
  return (
    <div className="glass-panel relative z-10 -mt-10 shrink-0 rounded-2xl px-5 py-2.5 xl:[@media(min-height:56rem)]:-mt-14 xl:[@media(min-height:56rem)]:py-4">
      {vehicle === null ? (
        <div className="flex min-h-9 items-center justify-between gap-4 xl:[@media(min-height:56rem)]:min-h-10">
          <p className="text-text text-sm font-semibold">Todavía no elegiste tu vehículo.</p>
          <Link
            to="/profile/edit"
            className="brand-fill text-on-primary shrink-0 rounded-xl px-4 py-2 text-sm font-semibold transition-colors"
          >
            Elegir mi auto
          </Link>
        </div>
      ) : (
        <dl className="grid grid-cols-4 justify-items-center gap-x-4">
          {specsOf(vehicle).map((spec) => (
            <div key={spec.label} className="flex items-center gap-2 xl:gap-3">
              <SpecIcon
                name={spec.icon}
                className="text-primary h-7 w-7 shrink-0 xl:[@media(min-height:56rem)]:h-10 xl:[@media(min-height:56rem)]:w-10"
              />
              <div>
                <dt className="text-text-muted text-[11px] font-medium">{spec.label}</dt>
                <dd className="text-text text-base leading-tight font-extrabold tracking-tight xl:[@media(min-height:56rem)]:text-lg">
                  {spec.value}
                </dd>
              </div>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}

/**
 * La batería, suelta en el panel del auto: el título grande arriba y debajo, en una fila, el
 * porcentaje y la pila con el límite marcado. El botón de detener va montado sobre la esquina de
 * abajo de la pila. Hubo una tarjeta de vidrio alrededor, pero con la ficha del auto abajo eran
 * dos cajas adentro del panel y la de la batería sobraba.
 *
 * Sin lectura todavía, el porcentaje es una raya corta y nada más: un "Esperando lectura" ahí
 * sumaba texto a una tarjeta que se tiene que leer de un vistazo.
 */
function BatteryBlock({
  session,
  refreshMs,
  live,
  value,
  action,
}: {
  session: ChargingSession
  refreshMs: number
  live: boolean
  value: string
  action?: ReactNode
}) {
  const target = session.targetBatteryPercent
  const current = session.batteryPercent

  let note: string | null = null
  if (target !== null && current !== null) {
    const missing = Math.max(0, Math.round(target - current))
    note = missing === 0 ? 'Límite alcanzado' : `Faltan ${String(missing)}% al límite`
  }

  return (
    <section className="flex min-w-0 flex-col">
      <h2 className="text-text text-2xl leading-none font-extrabold tracking-tight uppercase xl:[@media(min-height:56rem)]:text-3xl">
        Batería
      </h2>

      <div className="mt-2 flex items-center gap-4 xl:gap-5 xl:[@media(min-height:56rem)]:mt-3">
        <p className="charge-digits text-text flex shrink-0 items-start text-6xl xl:[@media(min-height:56rem)]:text-7xl">
          <span className="sr-only">Porcentaje actual: </span>
          {value === '—' ? '--' : value}
          <span className="text-text-muted mt-1 ml-0.5 text-2xl xl:[@media(min-height:56rem)]:text-3xl">
            %
          </span>
        </p>

        <div className="mt-5 h-16 min-w-0 flex-1 xl:[@media(min-height:56rem)]:mt-6 xl:[@media(min-height:56rem)]:h-24">
          <ChargingBattery
            percent={current}
            targetPercent={target}
            refreshMs={refreshMs}
            live={live}
          />
        </div>
      </div>

      <div className="mt-2 flex min-h-6 items-start justify-between gap-4">
        <p className="text-text-muted text-xs font-semibold whitespace-nowrap">{note}</p>

        {action !== undefined && (
          /*
            Montado sobre la esquina de abajo de la pila, como en el diseño: sube por encima del
            borde y se corre un poco hacia afuera, así se lee como parte de la batería. Por debajo
            de `xl` la pila es angosta y el botón la tapaba casi entera: ahí va más chico.
          */
          <div className="relative z-10 -mt-6 -mr-3 flex w-36 shrink-0 flex-col gap-2 xl:w-52 [&_button]:px-3 [&_button]:py-3 [&_button]:text-xs xl:[&_button]:px-6 xl:[&_button]:py-3.5 xl:[&_button]:text-sm">
            {action}
          </div>
        )}
      </div>
    </section>
  )
}

/**
 * El tiempo para el reloj. Hasta la hora va con segundos ("12:05"), que es lo que se mira mientras
 * se espera; desde la hora pasa a "1h 05min": con segundos serían siete cifras y el número se
 * saldría del arco.
 */
function timeOf(elapsedMs: number): { value: ReactNode; unit: string | null; compact: boolean } {
  if (elapsedMs < 3_600_000)
    return { value: formatDuration(elapsedMs), unit: 'min', compact: false }
  const totalMinutes = Math.floor(elapsedMs / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = String(totalMinutes % 60).padStart(2, '0')
  return {
    value: (
      <>
        {hours}
        <span className="text-[0.5em]">h</span> {minutes}
        <span className="text-[0.5em]">min</span>
      </>
    ),
    unit: null,
    compact: true,
  }
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

interface GaugeReading {
  fraction: number | null
  scale: { min: string; max: string } | null
}

/**
 * Dónde va el arco de la potencia: lo que entrega ahora, entre cero y el tope de esta carga.
 *
 * El tope es el del conector para esta sesión, que es lo más que puede salir de la manguera; el
 * del auto queda de respaldo si el conector no lo informó. Si una lectura lo pasa —el cargador
 * informa de más por un instante—, el tope se estira a esa lectura en vez de cortar el arco.
 * Sin lectura todavía no hay aguja; con la carga cerrada vuelve a cero.
 */
function powerGauge(
  session: ChargingSession,
  live: boolean,
  vehicleMaxKw: number | null,
): GaugeReading {
  const limit = session.powerLimitKw ?? vehicleMaxKw
  if (limit === null || limit <= 0) return { fraction: null, scale: null }
  if (live && session.currentPowerKw === null)
    return { fraction: null, scale: { min: '0', max: formatKw(limit) } }
  const current = live ? (session.currentPowerKw ?? 0) : 0
  const max = Math.max(limit, current)
  return { fraction: current / max, scale: { min: '0', max: formatKw(max) } }
}

/**
 * Dónde va el arco de la energía: lo entregado, entre cero y lo que va a haber entregado al llegar
 * al límite. Eso es lo entregado más lo que falta, y lo que falta sale de la capacidad de la
 * batería del auto y los puntos que quedan hasta el objetivo. Es una estimación: sin el auto o sin
 * lectura, el arco queda vacío y queda el número solo.
 */
function energyGauge(session: ChargingSession, batteryKwh: number | null): GaugeReading {
  const target = session.targetBatteryPercent
  const current = session.batteryPercent
  if (batteryKwh === null || target === null || current === null)
    return { fraction: null, scale: null }
  const remaining = Math.max(0, (batteryKwh * (target - current)) / 100)
  const total = session.kwhDelivered + remaining
  if (total <= 0) return { fraction: null, scale: null }
  return {
    fraction: session.kwhDelivered / total,
    scale: { min: '0', max: formatKwh(total) },
  }
}

/**
 * Una estadística del celular, con la anatomía del diseño: arriba a la izquierda qué es, el número
 * grande en el centro y la unidad abajo a la derecha, como en el display de un tablero.
 *
 * **El vidrio es el de `.glass-over-photo` y no el de `.glass-panel`.** Estas tarjetas se apoyan
 * sobre el fluido, que es verde brillante y no el fondo parejo para el que está medido el 32% del
 * vidrio común: con ese, el texto claro del tema oscuro quedaba sobre verde claro y no se leía.
 *
 * **El número va en una condensada y tan alto como entre** (ver `.charge-digits`). Un valor largo
 * ("1:02:05") usa una proporción menor, porque tiene más caracteres que acomodar.
 */
function Stat({ label, value, unit }: StatData) {
  const long = value.length > 5

  return (
    /*
      La tarjeta es un contenedor de consultas en las dos medidas (`container-type: size`): el
      número se dimensiona con `cqh` y `cqw` contra el lugar que tiene, y se queda con lo que dé
      menos. Así ocupa casi todo el alto entre el rótulo y la unidad sin salirse a lo ancho.
    */
    <div className="glass-panel glass-over-photo flex h-28 flex-col rounded-2xl p-2.5 [container-type:size]">
      <dt className="text-text-muted text-[11px] leading-none font-semibold">{label}</dt>
      <dd className="contents">
        <span
          className={`charge-digits text-text flex min-h-0 flex-1 items-center justify-center ${
            long ? 'text-[min(40cqh,30cqw)]' : 'text-[min(62cqh,46cqw)]'
          }`}
        >
          {value}
        </span>
        <span className="text-text-muted self-end text-[10px] leading-none font-extrabold uppercase">
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
 *
 * El contorno se deforma despacio, como una gota (ver `.charge-dial-blob`).
 */
function BatteryDial({ value, target }: { value: string; target: number | null }) {
  return (
    <div className="charge-dial-blob glass-panel glass-over-photo my-auto flex h-60 w-60 flex-col items-center justify-center rounded-full">
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
