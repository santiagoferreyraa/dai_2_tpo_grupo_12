import { Link } from 'react-router'

import StationMiniMap from '@/features/terminals/components/StationMiniMap'
import { formatDistance } from '@/features/terminals/format'
import type { DeviceLocation } from '@/features/terminals/useDeviceLocation'
import type { StationDetail } from '@/features/terminals/types'

import NearestStationCard from './NearestStationCard'

import type { StationWithDistance } from '../data/homeStations'

/**
 * "Estación más cercana" en el celular: dos recuadros, uno atrás del otro.
 *
 * **Es la misma tarjeta de escritorio con otra disposición, no otra pantalla.** El de atrás lleva
 * los datos —el nombre y la distancia arriba, la dirección parada sobre el borde izquierdo— y el
 * de adelante, corrido hacia abajo y a la derecha, no lleva nada más que el mapa. Separarlos así
 * hace dos cosas que la tarjeta apilada no hacía: el mapa se ve entero, sin un pie verde que le
 * come el último tercio, y los datos dejan de tapar justamente la zona que uno quiere mirar.
 *
 * La dirección va girada porque es el único lugar donde entra sin robarle ancho al mapa: en un
 * teléfono, una columna de texto al costado deja el mapa en la mitad de la pantalla. Parada ocupa
 * cuarenta píxeles y se lee de un vistazo.
 *
 * **Los estados que no son "hay una estación" los sigue dibujando `NearestStationCard`.** Cargando
 * y sin estación compatible son dos recuadros de texto, y ahí la disposición en capas no aporta
 * nada: no hay mapa que destacar. Reescribirlos acá sería tener dos veces el mismo cartel.
 *
 * Todo el bloque es un solo enlace al mapa grande, con la estación ya elegida en la dirección.
 */

/**
 * Cuánto asoma el recuadro de atrás.
 *
 * Están como constantes porque son CUATRO medidas que tienen que cerrar entre sí: lo que el de
 * atrás asoma arriba es lo que el mapa baja, y lo que asoma a la izquierda es el ancho de la
 * franja donde va la dirección. Tocar una sola desalinea la pila.
 */
/**
 * Cuánto se corre el mapa, y va como RELLENO del enlace y no como margen del mapa.
 *
 * Con margen, el navegador lo saca afuera —un margen superior sin nada que lo frene se lleva a la
 * caja entera, que es el colapso de márgenes de siempre—: el enlace bajaba 28 píxeles y el
 * recuadro de atrás, que se posiciona contra él, bajaba con él. Resultado: las dos capas
 * arrancaban en la misma línea y el nombre de la estación quedaba tapado por el mapa. El relleno
 * no colapsa nunca.
 */
const MAP_OFFSET = 'pt-7 pl-10'
const BACK_INSET = 'top-0 right-5 bottom-8 left-0'
/** La franja de la dirección: el borde izquierdo del de atrás, hasta donde arranca el mapa. */
const ADDRESS_STRIP = 'top-0 bottom-8 left-0 w-10'

interface NearestStationStackProps {
  nearest: StationWithDistance | null
  stations: StationDetail[]
  deviceLocation: DeviceLocation | null
  loading: boolean
}

export default function NearestStationStack({
  nearest,
  stations,
  deviceLocation,
  loading,
}: NearestStationStackProps) {
  if (loading || nearest === null) {
    return (
      <NearestStationCard
        nearest={nearest}
        stations={stations}
        deviceLocation={deviceLocation}
        loading={loading}
      />
    )
  }

  const { station, distanceKm } = nearest

  return (
    <Link
      to={`/stations/map?station=${String(station.id)}`}
      /* `group` para que el recuadro de atrás se realce tocando cualquier parte de la pila. */
      className={`group focus-visible:outline-primary relative block ${MAP_OFFSET} rounded-3xl focus-visible:outline-2 focus-visible:outline-offset-4`}
      /*
        Sin `aria-label` (QA-21, WCAG 2.5.3 "Label in Name"): el nombre accesible sale de lo que
        está escrito en la tarjeta —estación, dirección, distancia— más el "Ver en el mapa" del
        `sr-only`. Así coincide con lo que se lee, que es lo que dice quien usa la app por voz.
        Con la etiqueta escrita a mano el nombre dejaba afuera parte del texto visible.
      */
    >
      {/* El de atrás. Va vacío: lo que lleva encima son dos textos posicionados aparte. */}
      <div
        aria-hidden="true"
        className={`glass-panel group-hover:border-primary/60 absolute ${BACK_INSET} rounded-3xl transition-colors`}
      />

      {/* La franja de arriba: el nombre a la izquierda y la distancia a la derecha. */}
      <div className="absolute top-0 right-9 left-5 flex h-7 items-center gap-3">
        <span className="text-text truncate text-sm font-extrabold">{station.name}</span>
        {/* La distancia no se trunca nunca: es el dato que contesta la pregunta. */}
        <span className="text-primary ml-auto shrink-0 text-xs font-bold">
          {formatDistance(distanceKm)}
        </span>
      </div>

      {/*
        La dirección, parada sobre el borde izquierdo y leyéndose de abajo hacia arriba.

        `vertical-rl` sola la deja de arriba hacia abajo, que en español se lee peor: la media
        vuelta es la que la pone en el sentido en que uno gira la cabeza para leer un lomo de
        libro. El recorte necesita un alto máximo —en texto parado, los puntos suspensivos
        aparecen cuando se acaba el ALTO, no el ancho—, y sin él una dirección larga se saldría
        por abajo del recuadro.
      */}
      <p className={`absolute ${ADDRESS_STRIP} flex items-center justify-center`}>
        <span className="text-text-muted max-h-full truncate text-[11px] font-semibold tracking-wide [writing-mode:vertical-rl] rotate-180">
          {station.address}
        </span>
      </p>

      {/*
        El de adelante: solo el mapa.

        La sombra es lo que lo despega del de atrás. Sin ella, dos superficies de vidrio pegadas se
        leen como una sola con un escalón raro, y la disposición entera deja de entenderse.
      */}
      {/*
        El minimapa es una imagen adentro del enlace: `aria-hidden` e `inert` (QA-21) para que
        sus rótulos ("150 kW") no se lean como parte del nombre del enlace y para que los
        marcadores de Leaflet —que traen `tabindex`— no sumen paradas del Tab adentro de otro
        control.
      */}
      <div
        aria-hidden="true"
        inert
        className="relative h-52 overflow-hidden rounded-2xl shadow-xl shadow-black/25"
      >
        <StationMiniMap stations={stations} focus={station} deviceLocation={deviceLocation} />
      </div>
      <span className="sr-only">Ver en el mapa</span>
    </Link>
  )
}
