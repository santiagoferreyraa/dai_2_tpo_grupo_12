/**
 * "Tu auto tarda X en cargar": cuánto lleva llenar la batería en la estación más cercana.
 *
 * **Es una cuenta de ficha técnica, no una medición**, y por eso se puede mostrar sin inventar
 * nada: la capacidad de la batería y el tope de carga salen del modelo que el conductor eligió del
 * catálogo, y la potencia del conector la devuelve el backend. En ningún momento entra el estado
 * ACTUAL de la batería, que sería telemetría y esta aplicación no habla con el auto (ver
 * `vehicles/vehicle.ts`).
 *
 * **Sin auto elegido no hay cuenta que hacer, y el recuadro lo dice en vez de esconderse.** Es la
 * mejor oportunidad de pedir el dato: quien está mirando justo acá quiere saber cuánto tarda su
 * carga, y ése es el momento de contarle que falta un paso para poder contestarle.
 *
 * **La potencia que manda es la menor de las dos**, y ese es el dato que el recuadro existe para
 * hacer visible: en una estación de 150 kW un auto que acepta 80 carga a 80. El surtidor más
 * rápido no siempre sirve de más, y sin esta cuenta nadie lo sabría.
 *
 * **Es un estimado y lo dice en la tarjeta, no en un tooltip.** La carga real no es lineal: por
 * encima del 80% la potencia cae bastante, así que el número sirve para comparar estaciones
 * entre sí —que es para lo que está— y no para saber a qué hora uno se va.
 */

import { Link } from 'react-router'

import { ClockIcon } from '@/features/navigation/icons'
import { useDriverVehicle } from '@/features/vehicles/vehicle'
import type { VehicleModel } from '@/features/vehicles/types'

import type { StationWithDistance } from '../data/homeStations'

interface ChargeTimeCardProps {
  /** La estación de referencia. Null mientras no haya ninguna compatible y libre. */
  nearest: StationWithDistance | null
  loading: boolean
  className?: string
}

export default function ChargeTimeCard({ nearest, loading, className = '' }: ChargeTimeCardProps) {
  const vehicle = useDriverVehicle()
  const power = nearest === null || vehicle === null ? null : effectivePowerKw(nearest, vehicle)

  return (
    <article className={`glass-panel flex items-center gap-5 rounded-3xl p-6 ${className}`}>
      {/* Suelto y grande, igual que el escudo de la tarjeta de al lado. */}
      <ClockIcon className="text-primary h-16 w-16 shrink-0" />

      {/*
        Las dos ramas se comparan contra `nearest` Y contra `power` aunque la segunda implique la
        primera: TypeScript no deduce esa relación a través de la variable, y escribirlo así evita
        tener que afirmarle algo que no puede comprobar solo.
      */}
      <div className="min-w-0">
        {vehicle === null ? (
          /*
            El caso que va PRIMERO: sin auto no hay ficha de la que sacar los kWh, así que ni
            siquiera se puede escribir el renglón de abajo del caso "sin estación". Preguntarlo
            después obligaría a que esa rama supiera qué hacer sin vehículo.
          */
          <>
            <p className="text-text text-lg leading-snug font-bold text-balance">
              Elegí tu auto para estimar la carga
            </p>
            <p className="text-text-muted mt-1 text-sm">
              Con el modelo elegido calculamos cuánto tarda en la estación más cercana.
            </p>
            <Link
              to="/profile/edit"
              className="text-primary mt-2 inline-block text-sm font-semibold hover:underline"
            >
              Elegir mi auto
            </Link>
          </>
        ) : nearest === null || power === null ? (
          <>
            <p className="text-text text-lg leading-snug font-bold text-balance">
              {loading ? 'Calculando…' : 'Todavía no hay una estación para estimar'}
            </p>
            <p className="text-text-muted mt-1 text-sm">
              Batería de {vehicle.batteryKwh} kWh, hasta {vehicle.maxChargeKw} kW.
            </p>
          </>
        ) : (
          <>
            <p className="text-text text-lg leading-snug font-bold text-balance">
              Tu auto tarda{' '}
              <span className="text-primary">{formatDuration(vehicle.batteryKwh / power)}</span> en
              cargar
            </p>
            <p className="text-text-muted mt-1 text-sm">
              {vehicle.batteryKwh} kWh a {power} kW en {nearest.station.name}.
            </p>
            <p className="text-text-muted mt-1 text-xs">
              Estimado sobre la ficha del auto: no incluye la curva real de carga.
            </p>
          </>
        )}
      </div>
    </article>
  )
}

/**
 * A qué potencia carga realmente este auto en esta estación: la menor entre lo que da el
 * conector más rápido de SU tipo y lo que el auto acepta.
 *
 * Se mira solo entre los conectores del tipo que usa el vehículo, y no entre todos: el CHAdeMO
 * de 150 kW de al lado no le sirve a un auto con CCS2, así que contarlo daría un tiempo que
 * nunca va a pasar.
 */
function effectivePowerKw({ station }: StationWithDistance, vehicle: VehicleModel): number | null {
  const mine = station.connectors.filter((c) => c.connectorType === vehicle.connectorType)

  if (mine.length === 0) return null

  const fastest = Math.max(...mine.map((c) => c.maxPowerKw))
  return Math.min(fastest, vehicle.maxChargeKw)
}

/**
 * Horas decimales a algo que se lee: "45 min", "1 h 20 min", "2 h".
 *
 * Los minutos se redondean a los 5 más cercanos, y no es por pereza: el número es un estimado
 * con un margen de bastante más que un minuto, y escribir "43 min" le daría una precisión que
 * no tiene. Un valor redondeado se lee como lo que es, una referencia.
 */
function formatDuration(hours: number): string {
  const totalMinutes = Math.max(5, Math.round((hours * 60) / 5) * 5)

  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60

  if (h === 0) return `${String(m)} min`
  if (m === 0) return `${String(h)} h`
  return `${String(h)} h ${String(m)} min`
}
