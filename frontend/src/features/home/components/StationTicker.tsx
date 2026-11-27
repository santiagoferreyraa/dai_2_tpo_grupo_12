/**
 * La cinta de estaciones del pie de la portada: se desplaza sola, en bucle, y frena con el mouse.
 *
 * **Es una vitrina, no un listado.** Cada ficha dice nombre, dirección y el estado de sus
 * conectores como semáforo, y nada más: ni potencia, ni distancia, ni tipo de enchufe. Para eso
 * está el mapa, y cada ficha lleva ahí. Un carrusel que se mueve solo no es un lugar donde
 * comparar datos, porque lo que se está leyendo se va; sirve para mostrar que la red existe,
 * tiene lugares con nombre y ahora mismo hay enchufes libres.
 *
 * ─── Por qué estas fichas NO usan `glass-panel` ───
 *
 * Son las únicas tarjetas de la aplicación que no son de vidrio, y no es un olvido: acá el
 * vidrio NO PUEDE funcionar. `backdrop-filter` desenfoca lo que hay detrás del elemento, y para
 * eso necesita poder mirar el fondo de la página; un ancestro con `mask-image` o con un
 * `transform` animado —la cinta tiene los dos— crea un "backdrop root" que le corta esa vista.
 * El desenfoque deja de tener qué muestrear y las tarjetas salen planas y lavadas, distintas de
 * todas las demás de la portada. Con una superficie opaca se dibujan siempre igual.
 *
 * ─── Cómo se hace el bucle sin costura ───
 *
 * La lista se dibuja DOS veces seguidas y la cinta se corre hasta la mitad de su ancho. En el
 * momento en que termina el recorrido, la segunda copia está exactamente donde arrancó la
 * primera, así que el salto al reiniciar cae en un punto donde el dibujo es idéntico y no se ve.
 *
 * **Por eso la separación entre fichas va como `margin-right` de cada una y NO como `gap` del
 * contenedor.** Con `gap`, dos copias de N fichas dejan 2N−1 separaciones, no 2N: la mitad del
 * ancho cae media separación corrida y en cada vuelta se ve un tironcito. Con el margen adentro
 * de cada ficha, cada una mide siempre lo mismo y la mitad es exacta.
 *
 * La segunda copia va con `aria-hidden`: es el mismo contenido repetido por una razón visual, y
 * anunciarlo dos veces a un lector de pantalla sería duplicar la lista entera.
 */

import { Link } from 'react-router'

import { STATUS_LABEL } from '@/features/terminals/networkStats'
import type { OperationalStatus, StationDetail } from '@/features/terminals/types'

/**
 * Cuántos segundos tarda una ficha en cruzar la pantalla.
 *
 * La duración total se calcula con esto por la cantidad de fichas, en vez de fijarse: con un
 * valor fijo, la velocidad dependería de cuántas estaciones haya —cinco fichas volarían y
 * cincuenta se arrastrarían—, y es al revés de lo que uno quiere. Atada a la cantidad, la cinta
 * se mueve siempre igual de rápido.
 */
const SECONDS_PER_CARD = 6

/**
 * Un color por estado operativo, como un semáforo.
 *
 * **Las clases se escriben COMPLETAS**, por lo mismo que explica `stationPin.ts`: Tailwind no
 * ejecuta el código, busca nombres de clase como texto plano en los archivos. Un
 * `bg-st-${estado}` no aparece en ningún lado, así que el CSS no se genera y los puntos salen
 * transparentes, sin un solo error que lo delate.
 *
 * Los tres tokens ya existen en `terminals.css` y son los mismos que usa el resumen de red del
 * operador: un conector ocupado es del mismo amarillo en las dos pantallas.
 */
const STATUS_DOT: Record<OperationalStatus, string> = {
  AVAILABLE: 'bg-st-available',
  OCCUPIED: 'bg-st-occupied',
  OUT_OF_SERVICE: 'bg-st-offline',
}

/**
 * Cuántos puntos se dibujan como máximo.
 *
 * Sin tope, una estación con doce conectores estiraría su ficha, y como todas las fichas de una
 * fila flex miden lo mismo de alto, estiraría la cinta ENTERA por una sola estación. Con las del
 * seed —dos o tres conectores— el tope no llega a actuar nunca; está para que no pueda pasar.
 */
const MAX_DOTS = 5

/**
 * La columna de puntos: uno por conector, con su color de estado.
 *
 * Reemplaza al rayo que estaba antes, y dice bastante más por el mismo lugar: el rayo repetía
 * "esto es una estación de carga", que ya lo dice el nombre al lado, y esto dice cuántos
 * enchufes hay y cuáles se pueden usar ahora.
 *
 * `aria-hidden` sobre los puntos y el resumen en texto al lado: un color no se anuncia, y una
 * lista de cinco `div` vacíos no le dice nada a un lector de pantalla.
 */
function ConnectorDots({ station }: { station: StationDetail }) {
  const shown = station.connectors.slice(0, MAX_DOTS)

  return (
    <span className="flex w-11 shrink-0 flex-col items-center gap-1.5">
      {shown.length === 0 ? (
        /* Una estación sin conectores cargados es un caso real —el operador la dio de alta y
           todavía no le puso ninguno—. Un punto apagado lo dice sin dejar el hueco vacío. */
        <span className="bg-text-muted h-3 w-3 rounded-full opacity-40" aria-hidden="true" />
      ) : (
        shown.map((connector) => (
          <span
            key={connector.id}
            aria-hidden="true"
            className={`h-3 w-3 rounded-full ${STATUS_DOT[connector.operationalStatus]}`}
          />
        ))
      )}

      <span className="sr-only">{describeConnectors(station)}</span>
    </span>
  )
}

/** El resumen que escucha un lector de pantalla en lugar de los colores. */
function describeConnectors(station: StationDetail): string {
  const total = station.connectors.length
  if (total === 0) return 'Sin conectores cargados'

  const counts = station.connectors.reduce<Partial<Record<OperationalStatus, number>>>(
    (acc, connector) => ({
      ...acc,
      [connector.operationalStatus]: (acc[connector.operationalStatus] ?? 0) + 1,
    }),
    {},
  )

  const parts = Object.entries(counts).map(
    ([status, count]) =>
      `${String(count)} ${STATUS_LABEL[status as OperationalStatus].toLowerCase()}`,
  )

  return `${String(total)} conectores: ${parts.join(', ')}`
}

function StationChip({ station }: { station: StationDetail }) {
  const photo = station.photoUrls[0]

  return (
    <Link
      to={`/stations/map?station=${String(station.id)}`}
      /*
        `station-ticker__card` pone el ancho, el margen y la superficie: son las medidas de las
        que depende que la mitad del recorrido sea exacta, así que viven juntas en index.css y no
        repartidas entre una clase y una utilidad.
      */
      className="station-ticker__card focus-visible:outline-primary flex items-center gap-3 rounded-2xl p-4 focus-visible:outline-2"
    >
      {photo === undefined ? (
        <ConnectorDots station={station} />
      ) : (
        /* `alt` vacío a propósito: el nombre de la estación va escrito al lado, así que
           describir la foto sería repetirlo. */
        <img
          src={photo}
          alt=""
          loading="lazy"
          className="bg-surface h-11 w-11 shrink-0 rounded-full object-cover"
        />
      )}

      <span className="min-w-0">
        <span className="text-text block truncate text-sm font-bold">{station.name}</span>
        <span className="text-text-muted block truncate text-xs">{station.address}</span>
      </span>
    </Link>
  )
}

export default function StationTicker({
  stations,
  className = '',
}: {
  stations: StationDetail[]
  className?: string
}) {
  /* Sin estaciones no hay cinta: una franja vacía moviéndose es peor que no tener franja. */
  if (stations.length === 0) return null

  const duration = `${String(stations.length * SECONDS_PER_CARD)}s`

  return (
    <div
      className={`station-ticker ${className}`}
      style={{ '--ticker-duration': duration } as React.CSSProperties}
    >
      <div className="station-ticker__track">
        {stations.map((station) => (
          <StationChip key={station.id} station={station} />
        ))}

        {/*
          La segunda copia, la que cierra el bucle. Ver el comentario de arriba.

          `inert` además de `aria-hidden` (QA-21): las copias son enlaces, y con `aria-hidden`
          solo el lector de pantalla las salteaba pero el Tab seguía entrando en ellas —el foco
          caía en algo que el lector dice que no existe, que es la falla `aria-hidden-focus` de
          WCAG—. `inert` las saca también del recorrido del teclado.
        */}
        <div aria-hidden="true" inert className="contents">
          {stations.map((station) => (
            <StationChip key={`echo-${String(station.id)}`} station={station} />
          ))}
        </div>
      </div>
    </div>
  )
}
