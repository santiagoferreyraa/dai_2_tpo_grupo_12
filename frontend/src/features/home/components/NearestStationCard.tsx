/**
 * "Estación más cercana": el mapa de verdad, quieto, encuadrado sobre una estación.
 *
 * **La tarjeta ENTERA es el enlace**, no un botón adentro. El mapa chico ya se ve como algo que
 * lleva a otro lado, y obligar a apuntarle a un botón de ochenta píxeles cuando el blanco
 * natural es el recuadro completo solo agrega puntería. De ahí también el `pointer-events-none`
 * del mapa: es lo que deja que el clic llegue hasta acá. Ver `StationMiniMap`.
 *
 * El destino lleva la estación en la dirección, así que el mapa grande abre con ella elegida y
 * el panel de detalle arriba. Es el parámetro que se agregó en `StationsMapPage`.
 *
 * **Los tres estados que no son "hay una estación" se dibujan, no se esconden.** Un recuadro que
 * desaparece mientras carga deja la portada saltando, y uno que desaparece cuando no hay
 * estación compatible deja al conductor sin saber por qué.
 */

import { Link } from 'react-router'

import StationMiniMap from '@/features/terminals/components/StationMiniMap'
import { formatDistance } from '@/features/terminals/format'
import type { DeviceLocation } from '@/features/terminals/useDeviceLocation'
import type { StationDetail } from '@/features/terminals/types'

import type { StationWithDistance } from '../data/homeStations'

/**
 * Alto mínimo del mapa, no su alto.
 *
 * **El recuadro tiene que medir lo mismo que el del auto, que es su vecino de fila**, y quien
 * decide esa altura es la grilla: los dos son celdas de la misma fila, así que la más alta manda.
 * Con el mapa en un alto fijo, la tarjeta terminaba antes que el auto y quedaba un escalón.
 *
 * Por eso el mapa va `flex-1` —se come todo el sobrante entre el título y el pie verde— y esto
 * es solo el piso, para el caso en que la fila sea corta y el mapa quede tan bajo que no se
 * entienda qué se está mirando.
 *
 * Para que la cadena funcione hace falta `h-full` en cada eslabón: la celda de la grilla estira
 * al enlace, el enlace al `article`, y recién ahí el `flex-1` tiene contra qué crecer. Si falta
 * uno solo, todo vuelve a medir lo que ocupa el contenido.
 */
const MAP_MIN_HEIGHT = 'min-h-44'

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <article className="glass-panel flex h-full flex-col overflow-hidden rounded-3xl">
      {children}
    </article>
  )
}

function Title() {
  return <h2 className="text-text px-5 pt-4 pb-3 text-base font-bold">Estación más cercana</h2>
}

interface NearestStationCardProps {
  nearest: StationWithDistance | null
  stations: StationDetail[]
  deviceLocation: DeviceLocation | null
  loading: boolean
  className?: string
}

export default function NearestStationCard({
  nearest,
  stations,
  deviceLocation,
  loading,
  className = '',
}: NearestStationCardProps) {
  if (loading) {
    return (
      <div className={`h-full ${className}`}>
        <Shell>
          <Title />
          {/* Un bloque del color de la superficie que ocupa el mismo hueco que el mapa: la
              tarjeta no cambia de tamaño cuando llegan los datos y la portada no da el salto. */}
          <div className={`bg-surface/40 ${MAP_MIN_HEIGHT} flex-1 animate-pulse`} />
          <p className="text-text-muted px-5 py-3 text-sm">Buscando la más cercana…</p>
        </Shell>
      </div>
    )
  }

  if (nearest === null) {
    return (
      <div className={`h-full ${className}`}>
        <Shell>
          <Title />
          <div className="px-5 pb-5">
            <p className="text-text text-sm font-semibold">
              Ninguna estación cerca tiene tu conector libre
            </p>
            <p className="text-text-muted mt-1 text-sm">
              Puede haber alguna ocupada. Miralas todas en el mapa.
            </p>
            <Link
              to="/stations/map"
              className="text-primary mt-3 inline-block text-sm font-semibold hover:underline"
            >
              Ver el mapa
            </Link>
          </div>
        </Shell>
      </div>
    )
  }

  const { station, distanceKm } = nearest

  return (
    <Link
      to={`/stations/map?station=${String(station.id)}`}
      /*
        `group` para que el pie verde reaccione al mouse sobre CUALQUIER parte de la tarjeta y no
        solo sobre sí mismo: el blanco es el recuadro entero, así que el realce tiene que
        contarlo entero también.
      */
      className={`group focus-visible:outline-primary block h-full rounded-3xl focus-visible:outline-2 focus-visible:outline-offset-2 ${className}`}
      /*
        Sin `aria-label` (QA-21, WCAG 2.5.3 "Label in Name"): el nombre accesible sale de lo que
        está escrito en la tarjeta —estación, dirección, distancia— más el "Ver en el mapa" del
        `sr-only`. Así coincide con lo que se lee, que es lo que dice quien usa la app por voz.
        Con la etiqueta escrita a mano el nombre dejaba afuera parte del texto visible.
      */
    >
      <Shell>
        <Title />

        {/* `flex-1` para que el mapa se coma el sobrante: es lo que iguala el alto con el auto. */}
        {/*
          El minimapa es una imagen adentro del enlace: `aria-hidden` e `inert` (QA-21) para que
          sus rótulos ("150 kW") no se lean como parte del nombre del enlace y para que los
          marcadores de Leaflet —que traen `tabindex`— no sumen paradas del Tab adentro de otro
          control.
        */}
        <div aria-hidden="true" inert className={`${MAP_MIN_HEIGHT} relative flex-1`}>
          <StationMiniMap stations={stations} focus={station} deviceLocation={deviceLocation} />
        </div>

        {/*
          El pie verde: el nombre y la dirección sobre el relleno de la marca.

          Lleva el degradado entero con `brand-fill` porque es un relleno, que es para lo que esa
          utilidad existe, y la tinta va `text-on-primary` —oscura en los dos temas— porque sobre
          este verde cualquier color claro se pierde. El aclarado del hover ya viene con la
          utilidad; acá solo se dispara desde el `group`.
        */}
        <div className="brand-fill text-on-primary px-5 py-3">
          <p className="truncate text-sm font-extrabold">{station.name}</p>
          <p className="mt-0.5 flex items-baseline gap-2 text-xs font-semibold">
            <span className="truncate opacity-80">{station.address}</span>
            {/* La distancia no se trunca nunca: es el dato que contesta la pregunta. */}
            <span className="ml-auto shrink-0">{formatDistance(distanceKm)}</span>
          </p>
        </div>
        <span className="sr-only">Ver en el mapa</span>
      </Shell>
    </Link>
  )
}
