/**
 * Detalle de la estación elegida en el mapa, con la elección del conector y la reserva.
 *
 * Es solo el contenido: no decide dónde se dibuja. En celular lo envuelve un BottomSheet y en
 * pantalla ancha va anclado abajo, igual que el detalle del ABM. Escribirlo una sola vez para
 * las dos formas es lo que evita que se bifurquen cuando cambie qué muestra.
 *
 * **Se elige un conector, no una estación.** RF08 reserva un conector puntual por una ventana
 * de tiempo, así que el estado que decide si se puede reservar es el del conector elegido y no
 * un resumen de la estación. Una estación con el CCS2 fuera de servicio y el Tipo 2 libre se
 * puede reservar; lo que no se puede es reservar el CCS2.
 *
 * **Sin sesión no hay botón, y eso es distinto de un botón apagado.** Reservar es a nombre de
 * alguien: sin cuenta la acción no existe todavía, no es que esté impedida por el estado del
 * conector. Un botón gris diría que el problema es la estación —que es justo lo que el panel
 * usa el gris para decir— y mandaría a probar otro conector sin que ninguno sirva. En su lugar
 * va escrito lo que falta, con el enlace para resolverlo.
 */

import { Link } from 'react-router'

import { useSession } from '@/features/auth/session'

import {
  CONNECTOR_TYPE_LABEL,
  formatDistance,
  formatDuration,
  formatPower,
  STATUS_DOT_CLASS,
  STATUS_LABEL,
  STATUS_TEXT_CLASS,
} from '../format'
import type { RouteState, RouteStatus } from '../useRoute'
import type { ConnectorSummary, StationResult } from '../types'

/**
 * El porqué de que no haya ruta, por estado. Los que faltan no dicen nada.
 *
 * Es una tabla y no tres bloques en el JSX porque los tres casos comparten el mismo titular rojo:
 * escritos por separado, el titular quedaría copiado tres veces y el día que cambie la frase
 * habría que acordarse de los tres.
 *
 * 'idle' y 'loading' quedan afuera a propósito. El primero es que no hay nada que pedir —sin
 * ubicación, sin estación elegida— y el segundo es que la consulta está en camino: anunciar un
 * fracaso en cualquiera de los dos sería mentir, y en el de carga además parpadearía un cartel
 * rojo un segundo antes de que aparezca la ruta.
 */
const NO_ROUTE_REASON: Partial<Record<RouteStatus, string>> = {
  unavailable: 'El servicio de rutas no está respondiendo.',
  inaccurate: 'No sabemos dónde estás con suficiente precisión.',
  'off-road': 'No hay ninguna calle cerca del punto de partida.',
}

interface StationDetailPanelProps {
  station: StationResult
  /** El conector elegido, o `null` si la estación no tiene ninguno tras los filtros. */
  selectedConnector: ConnectorSummary | null
  onSelectConnector: (connectorId: number) => void
  onReserve: () => void
  /**
   * El recorrido hasta la estación, tal como lo devuelve `useRoute`.
   *
   * Llega el estado entero y no solo la ruta porque los cuatro casos se dicen distinto: buscando
   * no es lo mismo que sin servicio, y sin servicio no es lo mismo que sin ubicación. Un panel
   * que solo supiera "hay ruta o no hay" tendría que elegir una sola frase para los tres.
   */
  route?: RouteState
}

export default function StationDetailPanel({
  station,
  selectedConnector,
  onSelectConnector,
  onReserve,
  route = { status: 'idle', route: null, approximate: false },
}: StationDetailPanelProps) {
  const session = useSession()
  const connectors = station.matchingConnectors

  /*
   * La única condición que bloquea la reserva. Un conector ocupado SÍ se puede reservar: la
   * reserva es para una ventana futura, y que esté cargando ahora no dice nada de las once de
   * la noche. Fuera de servicio es distinto, porque ahí no hay ventana que valga.
   */
  const reservable =
    selectedConnector !== null && selectedConnector.operationalStatus !== 'OUT_OF_SERVICE'

  const noRouteReason = NO_ROUTE_REASON[route.status] ?? null

  return (
    <div className="flex flex-col gap-5">
      <header className="flex items-baseline justify-between gap-3">
        {/*
          El nombre lleva al ABM con la estación abierta, que es donde están las fotos, la
          dirección completa y el resto de los datos. Es un enlace de verdad y no un onClick
          para que se pueda abrir en otra pestaña y se vea a dónde va antes de tocarlo.
        */}
        <Link
          to={`/stations?station=${station.stationId}`}
          className="text-text hover:text-primary focus-visible:outline-primary min-w-0 text-2xl font-semibold underline-offset-4 transition-colors hover:underline focus-visible:outline-2"
        >
          {station.name}
        </Link>

        <span className="text-text-muted shrink-0 text-xs tracking-wide uppercase">
          {connectors.length === 1 ? '1 conector' : `${connectors.length} conectores`}
        </span>
      </header>

      <p className="text-text-muted -mt-3 text-sm">{station.address}</p>

      {/*
        Cuánto hay hasta acá, por calle.

        **Es el dato que la línea del mapa no puede decir.** El recorrido dibujado muestra la
        forma del camino; el número muestra cuánto es, y el tiempo es lo que realmente decide
        entre dos estaciones que en el mapa parecen igual de cerca. Va pegado a la dirección
        porque las dos contestan lo mismo: dónde queda esto.

        El `distanceKm` que ya trae la estación NO sirve para esto: lo calcula el backend en línea
        recta desde el centro de la búsqueda, que ni siquiera es donde está el usuario. Se sigue
        mostrando en el carrusel para ordenar resultados, que es para lo que alcanza.

        Mientras busca no se escribe "buscando ruta": el renglón aparece cuando hay algo que
        decir. Un cartel de carga acá haría saltar el panel entero un renglón un segundo después
        de abrirse, justo cuando se está leyendo el nombre de la estación.
      */}
      {route.status === 'ready' && route.route !== null && (
        <div className="-mt-4 flex flex-col gap-1">
          <p className="text-text-muted flex items-center gap-2 text-sm">
            <span className="text-text font-semibold">
              {formatDistance(route.route.distanceM / 1000)}
            </span>
            <span aria-hidden="true">·</span>
            <span>{formatDuration(route.route.durationS)} en auto</span>
          </p>

          {/*
            La advertencia va PEGADA a los números y no en otro lado del panel, porque es sobre
            ellos: son correctos para el punto desde el que se calcularon, y ese punto puede no
            ser exactamente donde está el usuario.

            Se dice con palabras y no apagando la ruta. Bloquearla fue la primera versión de esto y
            resultó peor que el problema: en escritorio la ubicación sale de la red y casi siempre
            supera el umbral, así que la pantalla se quedaba sin recorrido justo donde más
            explicación hacía falta. Ver GOOD_ACCURACY_M en useRoute.
          */}
          {route.approximate && (
            <p className="text-st-warning flex items-center gap-1.5 text-xs font-semibold">
              {/*
                El punto de color antes del texto, del mismo tamaño y a la misma distancia que los
                de estado de los conectores de acá abajo. No decora: es lo que hace que la
                advertencia se lea como del mismo sistema que el resto del panel en vez de como un
                renglón suelto que se puso amarillo.
              */}
              <span
                aria-hidden="true"
                className="bg-st-warning h-1.5 w-1.5 shrink-0 rounded-full"
              />
              Desde una ubicación aproximada.
            </p>
          )}
        </div>
      )}

      {/*
        Cuando no hay recorrido, el mapa no dibuja nada, y este bloque es lo único que lo explica.
        Sin él, elegir una estación con el servicio caído se ve igual que elegirla sin haber dado
        permiso de ubicación: no pasa nada y no hay forma de saber por qué.

        **En rojo y con la misma frase para los tres motivos**, porque para quien mira los tres son
        la misma noticia: la ruta que esperaba no está. Distinguirlos en el titular obligaría a
        leerlo entero para enterarse de eso, que es lo único que necesita saber para seguir usando
        la pantalla.

        El motivo baja a un segundo renglón apagado, y no se pierde: 'unavailable' es nuestro y
        probablemente se arregle solo, 'inaccurate' y 'off-road' hablan de dónde está parado, que es
        lo único sobre lo que puede hacer algo. Quien quiera saber por qué lo tiene ahí; quien no,
        ya leyó lo que importa en el renglón de arriba.

        No es la advertencia ámbar de acá arriba y la diferencia es exactamente la que separa los
        dos colores: aquella acompaña a una ruta que SÍ está, este dice que no hay ninguna.
      */}
      {noRouteReason !== null && (
        <div className="-mt-4 flex flex-col gap-1">
          <p className="text-danger flex items-center gap-1.5 text-sm font-semibold">
            <span aria-hidden="true" className="bg-danger h-1.5 w-1.5 shrink-0 rounded-full" />
            No podemos calcular tu ruta en este momento.
          </p>
          <p className="text-text-muted text-xs">{noRouteReason}</p>
        </div>
      )}

      {connectors.length === 0 ? (
        /* Pasa cuando los filtros de la búsqueda no dejaron ninguno, no cuando no los hay. */
        <p className="text-text-muted text-sm">Ningún conector de esta estación coincide.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {connectors.map((connector) => {
            const selected = connector.connectorId === selectedConnector?.connectorId

            return (
              <li key={connector.connectorId}>
                <button
                  type="button"
                  onClick={() => onSelectConnector(connector.connectorId)}
                  aria-pressed={selected}
                  className={`focus-visible:outline-primary flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition-colors focus-visible:outline-2 ${
                    selected
                      ? 'border-primary bg-surface'
                      : 'border-border bg-surface/40 hover:bg-surface/70'
                  }`}
                >
                  <span
                    aria-hidden="true"
                    className={`h-2.5 w-2.5 shrink-0 rounded-full ${STATUS_DOT_CLASS[connector.operationalStatus]}`}
                  />

                  <span className="text-text min-w-0 flex-1 text-sm font-semibold">
                    {CONNECTOR_TYPE_LABEL[connector.connectorType]}
                  </span>

                  <span className="text-text-muted shrink-0 text-sm">
                    {formatPower(connector.maxPowerKw)}
                  </span>

                  <span
                    className={`shrink-0 text-xs tracking-wide uppercase ${STATUS_TEXT_CLASS[connector.operationalStatus]}`}
                  >
                    {STATUS_LABEL[connector.operationalStatus]}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <div className="flex flex-col gap-2">
        {session === null ? (
          /*
            El aviso ocupa exactamente el lugar del botón —mismo alto, mismo radio, mismo ancho—
            para que el panel no cambie de forma al entrar o al salir. Va sobre el vidrio de
            adentro y no en verde: no es la acción de la pantalla, es la condición para que la
            acción aparezca.

            "Iniciar sesión" es un enlace de verdad, así que se puede abrir en otra pestaña y se
            ve a dónde lleva antes de tocarlo, igual que el nombre de la estación de arriba.
          */
          <div className="border-border bg-surface/60 flex w-full flex-col items-center gap-1 rounded-2xl border px-4 py-3.5 text-center">
            <p className="text-text-muted text-sm font-semibold text-balance">
              Debés iniciar sesión para poder reservar.
            </p>
            {/*
              El enlace en un renglón aparte y no adentro de la frase: el panel mide 26rem, y las
              dos palabras metidas al final del aviso caían partidas al renglón de abajo, que se
              lee como un error de armado y no como algo para tocar.
            */}
            <Link
              to="/login"
              className="text-primary focus-visible:outline-primary text-sm font-semibold underline underline-offset-4 focus-visible:outline-2"
            >
              Iniciar sesión
            </Link>
          </div>
        ) : session.role !== 'CONDUCTOR' ? (
          /*
            Con sesión pero sin rol de conductor, que es el único que el backend deja reservar. El
            mismo recuadro que el de sin sesión y por el mismo motivo: no es el conector el que
            impide reservar sino la cuenta, y un botón gris diría lo contrario.
          */
          <div className="border-border bg-surface/60 flex w-full items-center justify-center rounded-2xl border px-4 py-3.5 text-center">
            <p className="text-text-muted text-sm font-semibold text-balance">
              Las reservas son para cuentas de conductor.
            </p>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={onReserve}
              disabled={!reservable}
              className="brand-fill text-on-primary focus-visible:outline-primary disabled:bg-none disabled:bg-surface disabled:text-text-muted w-full rounded-2xl py-4 text-sm font-bold tracking-wide uppercase focus-visible:outline-2 disabled:cursor-not-allowed"
            >
              Reservar
            </button>

            {/*
              El motivo del bloqueo va escrito, no solo insinuado por el botón apagado: un botón
              gris sin explicación deja a quien lo mira sin saber si el problema es la estación,
              su cuenta o la aplicación.
            */}
            {!reservable && selectedConnector !== null && (
              <p className="text-st-offline text-center text-xs">
                Este conector está fuera de servicio. Elegí otro para reservar.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
