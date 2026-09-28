/**
 * Mapa de estaciones para el conductor (RF07).
 *
 * Es la otra cara de StationsPage: ahí el operador administra sus estaciones, acá el
 * conductor busca dónde cargar. Comparten los tipos, el acceso a datos y los cálculos
 * derivados; no comparten la pantalla, porque no comparten ni la tarea ni el rol.
 *
 * Dueña del estado compartido: el texto buscado, cuál es la estación elegida y cuál su
 * conector. El mapa, el carrusel y el panel los reciben, ninguno de los tres los guarda — si
 * cada uno tuviera los suyos, se desincronizan.
 *
 * La pantalla tiene dos formas, y la diferencia no es de estilos sino de dónde aparece la
 * estación elegida:
 *
 * - En el celular no hay carrusel: los pines son la lista, y el detalle sube desde abajo como
 *   panel. Un carrusel al costado le comería el ancho al mapa, que es la pantalla.
 * - En pantalla ancha el carrusel queda, y el detalle se ancla abajo sin taparlo.
 *
 * Lo que NO cambia entre las dos es el estado: hay una sola estación elegida y un solo
 * conector elegido, y cada forma los dibuja donde le corresponde.
 *
 * Lo que sigue: la búsqueda por viewport y los filtros de `SearchCriteria`. Hoy pide una sola
 * vez todas las estaciones del país y filtra el texto en memoria, que con las quince del seed
 * alcanza y sobra; cuando el filtrado pase al backend, cambia esta llamada y ningún componente
 * de abajo se entera.
 */

import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import type { LatLngTuple } from 'leaflet'

import BottomSheet from '@/components/BottomSheet'
import BookingDialog from '@/features/bookings/components/BookingDialog'
import BookingPanel from '@/features/bookings/components/BookingPanel'
import { useMyBookings } from '@/features/bookings/data/myBookingsStore'
import { nextBooking } from '@/features/bookings/timeline'
import type { Booking } from '@/features/bookings/types'
import { useNow } from '@/features/bookings/useNow'
import MapScrim from './components/MapScrim'
import StationCarousel from './components/StationCarousel'
import StationDetailPanel from './components/StationDetailPanel'
import StationFilters from './components/StationFilters'
import StationMap from './components/StationMap'
import StationSearch from './components/StationSearch'
import StationSuggestions, { MAX_SUGGESTIONS } from './components/StationSuggestions'
import { searchStations } from './data/stationsRepository'
import { departFromDevice } from './departure'
import { matchesFilters, matchesQuery } from './format'
import type { ConnectorFilters } from './format'
import { COUNTRY_RADIUS_KM, DEFAULT_CENTER } from './mapConfig'
import { useDepartureOrigin } from './useDepartureOrigin'
import { useRoute } from './useRoute'
import { useMediaQuery } from '@/lib/useMediaQuery'
import { useSuggestionNav } from './useSuggestionNav'
import type { ConnectorSummary, StationResult } from './types'

/* El centro de la consulta, como par de números: es lo que espera `SearchCriteria`. */
const center = DEFAULT_CENTER as LatLngTuple

/**
 * A partir de acá entra el carrusel. Es el `lg` de Tailwind, el mismo corte que usa el ABM
 * para pasar a dos columnas: las dos pantallas de la feature entienden lo mismo por "ancha".
 */
const WIDE_QUERY = '(min-width: 1024px)'

/**
 * A partir de acá reservar abre un diálogo centrado; por debajo, el mismo contenido va adentro del
 * panel que sube desde abajo.
 *
 * **No es el mismo corte que `WIDE_QUERY`, a propósito.** La tablet todavía usa el panel de abajo
 * para el detalle de la estación, pero tiene lugar de sobra para una ventana: es el `md` de
 * Tailwind, el mismo en que aparece la franja de navegación de arriba.
 */
const DIALOG_QUERY = '(min-width: 768px)'

/**
 * Cuánto le tapa el panel al mapa en celular, aproximado.
 *
 * Es para correr el centro del mapa al elegir una estación, no para dibujar nada, así que no
 * necesita ser el alto exacto del panel —que además depende de cuántos conectores tenga—.
 * Errarle por poco deja el pin un poco más arriba o más abajo del medio del hueco; medirlo de
 * verdad obligaría a un ResizeObserver sobre un panel que entra animado.
 */
const SHEET_INSET_PX = 380

/**
 * El conector que viene elegido de arranque: el más rápido de los que están libres.
 *
 * Libre primero y potencia después, en ese orden, porque es el orden en que decide alguien que
 * quiere cargar: de nada sirve ofrecerle el de 150 kW si está fuera de servicio. Si no hay
 * ninguno libre, el más rápido a secas, que deja el panel mostrando lo mejor que hay aunque no
 * se pueda reservar ahora.
 */
function defaultConnector(station: StationResult): ConnectorSummary | null {
  const byPower = [...station.matchingConnectors].sort((a, b) => b.maxPowerKw - a.maxPowerKw)
  return byPower.find((c) => c.operationalStatus === 'AVAILABLE') ?? byPower[0] ?? null
}

export default function StationsMapPage() {
  const [allStations, setAllStations] = useState<StationResult[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  /*
   * La búsqueda puede venir de la dirección (`?q=`) o escribirse acá, y de qué pantalla se trate
   * decide cuál de las dos manda.
   *
   * De tablet para arriba esta pantalla ya NO dibuja su buscador: quedaba a cuatro centímetros
   * del de la franja de arriba, con el mismo aspecto y la misma función, y no había forma de
   * saber cuál era cuál. Manda la dirección, que ese buscador reescribe con cada tecla. En
   * celular no hay franja de arriba, así que el campo de acá es el único y escribe el estado
   * directo.
   *
   * Que la búsqueda viaje por la dirección y no por el estado del router es lo que deja el
   * resultado compartible y recargable.
   *
   * El estado se ajusta DURANTE el render y no en un efecto. Es el patrón que recomienda React
   * para el estado que se deriva de algo de afuera, y el mismo que usa `BottomSheet`: hecho en un
   * efecto, se alcanza a ver un cuadro con la lista filtrada por la búsqueda anterior.
   *
   * Se compara contra la última dirección vista y no contra `query` a secas: si se copiara
   * siempre, borrar el texto a mano lo repondría desde la dirección en el render siguiente y el
   * campo no se dejaría vaciar nunca.
   */
  const [searchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''

  /*
   * `?station=3` abre el mapa con esa estación ya elegida y el panel arriba.
   *
   * Existe para la portada: el recuadro de la estación más cercana la nombra, y tocarlo tiene
   * que llevar a ESA estación abierta. Sin esto, el conductor aterriza en el mapa teniendo que
   * buscar de nuevo la que le acaban de mostrar.
   *
   * Es el mismo mecanismo que ya usa el ABM (ver `STATION_PARAM` en `StationsPage`), con el
   * mismo nombre de parámetro a propósito: son la misma idea, y un enlace armado a mano para una
   * pantalla funciona en la otra.
   *
   * Se aplica UNA sola vez, cuando llegan las estaciones, y después se olvida. Si se aplicara en
   * cada render, cerrar el panel lo volvería a abrir en el cuadro siguiente y la estación no se
   * podría sacar de encima sin editar la dirección.
   */
  const [pendingStationId, setPendingStationId] = useState<number | null>(() => {
    const raw = searchParams.get('station')
    if (raw === null) return null

    const parsed = Number(raw)
    return Number.isInteger(parsed) ? parsed : null
  })

  const [query, setQuery] = useState(urlQuery)
  const [lastUrlQuery, setLastUrlQuery] = useState(urlQuery)

  if (urlQuery !== lastUrlQuery) {
    setLastUrlQuery(urlQuery)
    setQuery(urlQuery)
  }

  /*
   * Los filtros viven acá arriba y no adentro de StationFilters por el mismo motivo que el
   * texto buscado: el mapa, el carrusel y el encabezado tienen que estar mirando la misma
   * lista de estaciones. Con el estado adentro del componente, los filtros serían suyos y
   * nadie más se enteraría de que hay algo filtrado.
   */
  const [filters, setFilters] = useState<ConnectorFilters>({
    connectorType: null,
    minPowerKw: null,
  })

  const [selectedStationId, setSelectedStationId] = useState<number | null>(null)
  const [selectedConnectorId, setSelectedConnectorId] = useState<number | null>(null)

  /*
   * Desde dónde sale el conductor: la ubicación del dispositivo, o la dirección que haya escrito
   * en el campo de la reserva. Ver `useDepartureOrigin`, que es quien elige entre las dos.
   *
   * La ubicación se pide al entrar al mapa y no detrás de un botón: es la pantalla donde el
   * permiso se explica solo, y donde pedirlo en otro momento sería más raro que pedirlo acá.
   *
   * Que falte no rompe nada. Sin permiso, sin HTTPS o fuera del país no hay punto verde y no se
   * traza el recorrido; todo lo demás de la pantalla funciona igual. Por eso el estado sigue sin
   * mostrarse en ningún cartel del mapa: el lugar donde eso importa —y donde además hay algo que
   * hacer al respecto, que es escribir una dirección— es el campo de la reserva, que sí lo dice.
   */
  const departure = useDepartureOrigin()

  /*
   * La estación que el conductor ya tiene reservada, si es que tiene una reserva en curso o por
   * empezar. Es lo único que la pantalla necesita de Reservas, y de ahí cuelga el recorrido
   * punteado, que se dibuja SIEMPRE y no solo mientras esa estación esté elegida.
   *
   * Sale de `nextBooking`, la misma regla que usa la franja de arriba y el perfil. Que las tres
   * pantallas pregunten lo mismo es lo que evita que el mapa marque como reservada una estación
   * que la franja ya dejó de nombrar.
   *
   * El reloj corre cada medio minuto y no cada segundo como el de la franja: acá no se muestra
   * ninguna cuenta regresiva, lo único que puede cambiar es que la reserva empiece o termine, y
   * que el punteado tarde hasta treinta segundos en aparecer o irse no lo nota nadie.
   */
  const now = useNow(30_000)
  const myBookings = useMyBookings()
  const bookedStationId =
    myBookings.status === 'ready'
      ? (nextBooking(myBookings.bookings, now)?.location?.stationId ?? null)
      : null

  const wide = useMediaQuery(WIDE_QUERY)
  const dialog = useMediaQuery(DIALOG_QUERY)

  /*
   * La estación y el conector que se están reservando, o `null` si no se está reservando.
   *
   * Se guardan los dos al tocar Reservar y no se leen de la selección, porque la selección puede
   * cambiar por debajo —llega una recarga de estaciones, se mueve un filtro— y el diálogo no tiene
   * que pasar a reservar otra cosa a mitad de camino.
   */
  const [reserving, setReserving] = useState<{
    station: StationResult
    connector: ConnectorSummary
  } | null>(null)

  /*
   * La estación de la reserva recién confirmada, mientras Reservas todavía no la devuelve en su
   * lista. Es un dato de un instante y nadie lo dibuja, así que va en una referencia: guardado en
   * el estado obligaría a un repintado de toda la pantalla para no cambiar un solo píxel. Quién lo
   * lee y por qué está explicado en el efecto que olvida el punto de partida.
   */
  const justBookedRef = useRef<number | null>(null)

  useEffect(() => {
    /*
     * Si la pantalla se desmonta antes de que el backend conteste, la petición se cancela.
     * Cancelar hace que `fetch` rechace, y ese rechazo NO es un error para mostrar: nadie
     * está esperando la respuesta. De ahí el `aborted` antes de tocar el estado.
     */
    const controller = new AbortController()

    searchStations(
      {
        latitude: center[0],
        longitude: center[1],
        radiusKm: COUNTRY_RADIUS_KM,
        onlyAvailable: false,
      },
      controller.signal,
    )
      .then((rows) => setAllStations(rows))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setLoadError(
          error instanceof Error ? error.message : 'No se pudieron cargar las estaciones',
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [])

  /*
   * La estación de `?station=` se aplica en cuanto llegan los datos.
   *
   * Va DURANTE el render y no en un efecto, que es el mismo patrón que usa la búsqueda unas
   * líneas más arriba y el que React recomienda para el estado que se deriva de algo de afuera.
   * Hecho en un efecto se alcanza a ver un cuadro con el mapa sin nada elegido antes de que se
   * abra el panel, que es exactamente el parpadeo que este parámetro existe para evitar.
   *
   * `setPendingStationId(null)` primero: es lo que hace que esto pase una sola vez y que cerrar
   * el panel no lo vuelva a abrir en el render siguiente.
   */
  if (pendingStationId !== null && allStations.length > 0) {
    setPendingStationId(null)
    setSelectedStationId(pendingStationId)
    /* El conector arranca de nuevo, igual que en cualquier otra selección. */
    setSelectedConnectorId(null)
  }

  const stations = useMemo(
    () =>
      allStations.filter(
        (station) =>
          matchesQuery(station, query) && matchesFilters(station.matchingConnectors, filters),
      ),
    [allStations, query, filters],
  )

  /**
   * La selección se ignora si la estación quedó fuera de la búsqueda, pero NO se borra.
   *
   * Es a propósito: quien busca "tigre", elige esa estación y después limpia el buscador, se
   * la vuelve a encontrar elegida donde la dejó. Borrando el estado, ese ida y vuelta le
   * costaría volver a buscarla.
   */
  const selectedStation = stations.find((s) => s.stationId === selectedStationId) ?? null

  /*
   * El recorrido por calles hasta la estación elegida.
   *
   * **No es un estado de esta pantalla: se deriva de la selección.** Antes había un
   * `tracedStationId` aparte, porque la línea la disparaba la reserva y se apagaba sola a los dos
   * segundos y medio; con la ruta fija esa distinción desapareció, y con ella el estado, el
   * temporizador y las tres funciones que lo manejaban. Elegida la estación hay ruta, y deja de
   * haberla cuando se elige otra.
   *
   * Que devuelva null no es una falla: puede ser que todavía no llegó, que no hay ubicación o que
   * el servicio está caído. El mapa dibuja la recta de siempre en cualquiera de los tres casos.
   */
  /*
   * El punto de partida escrito se olvida cuando se queda sin motivo.
   *
   * Una dirección de partida no es una preferencia del conductor: es el "salgo de acá" de UN viaje
   * a UNA estación, por eso `Departure` la guarda junto al `stationId`. Mientras ese viaje existe
   * —se está reservando, o ya hay una reserva vigente para esa estación— la dirección vale y su
   * alfiler tiene que estar en el mapa. Cuando el viaje deja de existir, no queda nada que la
   * sostenga.
   *
   * Sin esto la dirección sobrevivía a la reserva entera y el alfiler se quedaba solo en el mapa:
   * sin recorrido punteado que saliera de él, sin panel abierto que lo nombrara, y sin nada en
   * pantalla que explicara por qué hay un pin ahí. Peor todavía, seguía siendo el origen de los
   * recorridos de esa estación, así que tocar su pin más tarde medía la distancia desde un lugar
   * donde el conductor ya no estaba.
   *
   * Los tres casos que esto cubre son el mismo caso: se abandonó el formulario sin confirmar, la
   * reserva se canceló, o la reserva terminó.
   *
   * **Espera a que Reservas conteste**, y de dos maneras distintas.
   *
   * Con `myBookings` a medio cargar `bookedStationId` es `null`, que es indistinguible de "no hay
   * reserva": borraría la dirección de una reserva que sí existe, justo al entrar a la pantalla.
   * De eso se encarga el `status`.
   *
   * El `status` no alcanza para el otro momento, que es el que importa. Al confirmar se pide una
   * lista nueva, pero con una lista ya a la vista `refreshMyBookings` NO pasa por `loading` —para
   * no parpadear—, así que durante ese rato el estado dice `ready` con la lista VIEJA, en la que la
   * reserva recién hecha todavía no está. Cerrar el formulario en esa ventana borraría la dirección
   * de la reserva que se acaba de confirmar. `justBookedRef` es el compás de espera: se levanta al
   * confirmar y se baja sola cuando la lista nueva llega con esa estación adentro.
   *
   * Si la lista nueva nunca llega —se cayó la red— la bandera queda levantada y el alfiler se
   * queda. Es el lado correcto para equivocarse: de más, un pin que sobra; de menos, el punto de
   * partida de una reserva vigente borrado a espaldas del conductor.
   */
  useEffect(() => {
    if (departure.departure.kind !== 'address') return
    if (myBookings.status !== 'ready') return

    const stationId = departure.departure.stationId
    if (reserving?.station.stationId === stationId) return

    if (bookedStationId === stationId) {
      justBookedRef.current = null
      return
    }

    if (justBookedRef.current === stationId) return

    departFromDevice()
  }, [departure.departure, myBookings.status, reserving, bookedStationId])

  /*
   * Desde dónde se traza ESTE recorrido, que no siempre es el punto de partida elegido.
   *
   * La dirección escrita en el formulario es el punto de partida DEL VIAJE a una estación concreta
   * —por eso `Departure` se la guarda con el `stationId`—, y vale mientras se esté mirando esa
   * estación. Tocar otra, para comparar o porque quedaba de paso, es otra pregunta: "¿cuánto tengo
   * hasta acá desde donde estoy?". Contestada desde la dirección de la otra reserva, el mapa
   * dibujaba un recorrido que salía de un lugar donde el conductor no está y que nada en pantalla
   * nombraba.
   *
   * Ojo con lo que NO hace: mirando otra estación sin ubicación del dispositivo, no hay recorrido.
   * Es a propósito y es el mismo silencio de siempre —sin saber dónde está el conductor no hay
   * nada honesto que dibujar—; el punto de partida de la otra estación no es un reemplazo.
   */
  const routeOrigin =
    departure.departure.kind === 'address' &&
    selectedStation?.stationId !== departure.departure.stationId
      ? departure.deviceOrigin
      : departure.origin

  /*
   * En el mapa hay DOS recorridos, y son dos porque contestan dos preguntas distintas.
   *
   * El de la reserva —punteado— dice "así vas a ir a donde ya te comprometiste", y por eso no
   * depende de qué pin esté tocado: se dibuja mientras la reserva esté vigente, se mire lo que se
   * mire. Antes salía del mismo `useRoute` que la selección, y ese era el error que hacía que
   * tocar otra estación lo borrara: no era un recorrido que se mantenía, era el mismo recorrido
   * apuntando a otro lado.
   *
   * El de la selección —lleno— dice "y esta otra estación, ¿a cuánto me queda desde acá?". Ese sí
   * cambia con cada pin, que es lo que se espera de él.
   *
   * La estación reservada se busca en `allStations` y no en `stations`: una búsqueda o un filtro
   * que la deje afuera esconde su pin, y está bien que lo esconda, pero el viaje comprometido
   * sigue siendo cierto y su línea se tiene que poder seguir viendo.
   */
  const bookedStation = allStations.find((s) => s.stationId === bookedStationId) ?? null
  const selectedIsBooked = selectedStation !== null && selectedStation.stationId === bookedStationId

  /*
   * El recorrido de la reserva sale de la dirección que se eligió PARA esa estación, y de la
   * ubicación del dispositivo si no se eligió ninguna. Es el punto de partida del viaje reservado,
   * y no cambia porque el conductor ande mirando otros pines.
   */
  const bookedOrigin =
    departure.departure.kind === 'address' && departure.departure.stationId === bookedStationId
      ? departure.origin
      : departure.deviceOrigin

  const bookedRouteState = useRoute(bookedOrigin, bookedStation)

  /*
   * Con la estación reservada elegida los dos recorridos serían el mismo, así que se calcula uno
   * solo: el origen en `null` deja a este hook en reposo y el panel lee el de la reserva. Sin eso
   * serían dos consultas a OSRM por la misma línea, dibujada dos veces encima de sí misma.
   */
  const selectedRouteState = useRoute(selectedIsBooked ? null : routeOrigin, selectedStation)
  const routeState = selectedIsBooked ? bookedRouteState : selectedRouteState

  /*
   * El conector elegido se resuelve contra la estación de ahora y cae en el de por omisión si
   * el guardado no le pertenece. Sin eso, cambiar de estación dejaría el panel apuntando al
   * conector de la anterior, que en esta ni existe.
   */
  const selectedConnector =
    selectedStation === null
      ? null
      : (selectedStation.matchingConnectors.find((c) => c.connectorId === selectedConnectorId) ??
        defaultConnector(selectedStation))

  /*
   * Las sugerencias del celular salen de lo que YA está filtrado, no de todas las estaciones: es
   * la misma lista que dibujan los pines, así que lo que se ofrece elegir es exactamente lo que
   * se está viendo. Elegir una la selecciona y sube el panel; no navega a ningún lado, porque ya
   * se está en el mapa.
   */
  const suggestionsId = useId()

  /*
   * Si el buscador de celular se está viendo como burbuja. Lo dice él —el estado de plegado es
   * suyo—, y acá se usa para una sola cosa: darle o no el ancho de la fila, que es lo que manda
   * a los filtros a su lado o al renglón de abajo.
   *
   * La cuenta se repite de `StationSearch` porque el aviso trae solo la mitad —si tiene el
   * foco—; la otra mitad es el texto, que vive acá: plegado es sin foco Y vacío.
   */
  const [searchExpanded, setSearchExpanded] = useState(false)
  const searchCollapsed = !searchExpanded && query === ''

  const suggestions = query.trim() === '' ? [] : stations.slice(0, MAX_SUGGESTIONS)

  const nav = useSuggestionNav(suggestions, (picked) => {
    if (picked !== null) selectStation(picked.stationId)
  })

  const handleQueryChange = (value: string) => {
    setQuery(value)
    nav.reopen()
  }

  function selectStation(stationId: number) {
    setSelectedStationId(stationId)
    /* El conector arranca de nuevo en cada estación: lo resuelve `defaultConnector`. */
    setSelectedConnectorId(null)
    /* En el celular, elegir otro pin a mitad de una reserva vuelve al detalle de la nueva. */
    setReserving(null)
  }

  function closePanel() {
    setSelectedStationId(null)
    setSelectedConnectorId(null)
    setReserving(null)
  }

  function handleReserve() {
    if (selectedStation === null || selectedConnector === null) return
    setReserving({ station: selectedStation, connector: selectedConnector })
  }

  /**
   * Termina la reserva, confirmada o no.
   *
   * **Ya no dispara nada en el mapa.** Antes encendía acá la línea hasta la estación, porque era
   * un gesto de dos segundos y había que elegirle un momento; la reserva confirmada era el mejor,
   * que es cuando el conductor se compromete a ir. Con el recorrido fijo la pregunta desapareció:
   * la ruta ya está dibujada desde que se eligió la estación, que es cuando alguien quiere saber
   * cómo llegar —antes de reservar, no después—.
   */
  function finishReserving(booking: Booking | null) {
    /*
     * Ver el efecto que olvida el punto de partida: esto es lo que le avisa que la reserva existe
     * aunque la lista todavía no la muestre.
     */
    if (booking !== null) justBookedRef.current = booking.location?.stationId ?? null
    setReserving(null)
  }

  /*
   * La ficha flotante dice lo que la pantalla no puede mostrar sola, y nada más.
   *
   * Ya no cuenta estaciones: cuántas hay se ve en el mapa, que son los pines, y repetirlo en un
   * número ocupaba la esquina con algo que el ojo ya sabía. Quedan los dos casos en los que el
   * mapa NO alcanza a explicarse: mientras carga, porque un mapa vacío parece un mapa sin
   * estaciones, y cuando la carga falla, porque si no la pantalla se rompe en silencio.
   */
  const notice = loading ? 'Cargando estaciones…' : loadError

  /*
   * El panel, escrito una sola vez para las dos formas de la pantalla.
   *
   * En el celular, reservando, el detalle deja lugar al formulario de reserva adentro del mismo
   * panel. De tablet para arriba el detalle se queda donde está y la reserva va en un diálogo.
   */
  const detail =
    selectedStation &&
    (reserving !== null && !dialog ? (
      <BookingPanel
        station={reserving.station}
        connector={reserving.connector}
        onBack={() => setReserving(null)}
        onDone={finishReserving}
      />
    ) : (
      <StationDetailPanel
        station={selectedStation}
        selectedConnector={selectedConnector}
        onSelectConnector={setSelectedConnectorId}
        onReserve={handleReserve}
        route={routeState}
      />
    ))

  return (
    /*
      De tablet para arriba el mapa vive adentro de una tarjeta redondeada, separada de los
      bordes: es la disposición que pidió el diseño y hace juego con los recuadros de la portada.

      En el celular NO: ahí sigue a sangre. El aire de los costados le come ancho a la única
      pantalla que es puro mapa, y la barra de navegación de abajo está pensada para flotar sobre
      los mosaicos, no sobre un margen.
    */
    <section className="flex min-h-0 flex-1 flex-col md:px-6 md:pb-6">
      <div className="border-border/60 relative flex min-h-0 flex-1 flex-col overflow-hidden md:rounded-3xl md:border md:shadow-xl">
        {/*
          El título queda solo para lectores de pantalla. La franja que lo mostraba se sacó porque
          le comía alto al mapa, que es toda la pantalla; el encabezado en sí no se puede borrar
          —una página sin `h1` deja a quien navega por estructura sin saber dónde está— y la barra
          de arriba ya dice "Mapa" a la vista.
        */}
        <h1 className="sr-only">Mapa</h1>

        {/*
        relative + min-h-0. El mapa, el buscador, los degradados y el panel se posicionan
        absolutos contra este div, así que necesita ser el contenedor de referencia; y min-h-0
        porque un ítem flex se niega por omisión a achicarse por debajo de su contenido, con lo
        que empujaría la página hacia abajo en vez de ocupar el hueco que queda.
      */}
        <div className="relative min-h-0 flex-1">
          <StationMap
            stations={stations}
            selectedStationId={selectedStation?.stationId ?? null}
            onSelect={selectStation}
            bottomInsetPx={!wide && selectedStation !== null ? SHEET_INSET_PX : 0}
            dimUnselected={selectedStation !== null}
            /*
              El alfiler del punto de partida se dibuja con la dirección elegida y no con el origen
              de ESTE recorrido: mientras el recorrido punteado siga en pantalla, el lugar de donde
              sale tiene que estar marcado, aunque lo que se esté mirando sea otra estación.
            */
            origin={departure.origin}
            deviceLocation={departure.device.location}
            route={selectedIsBooked ? null : selectedRouteState.route}
            bookedRoute={bookedRouteState.route}
          />

          {/*
          El degradado de abajo se agranda solo en celular. Ahí el panel ocupa el ancho entero
          y necesita apoyarse sobre algo oscuro; acá el panel es una tarjeta en la esquina, y
          un degradado de dos tercios a todo el ancho oscurece medio mapa para enmarcarla.
        */}
          <MapScrim expanded={!wide && selectedStation !== null} />

          {/*
          Centrado en celular y pegado a la izquierda de ahí para arriba.

          En celular el ancho se acota a lo disponible menos 6rem, que deja 3rem de cada lado:
          lo justo para no montarse sobre nada en una pantalla angosta. Plegado como burbuja no
          usa ese ancho, pero lo reserva, así que al desplegarse no salta.

          z-index por encima de los 1000 que usa Leaflet para sus controles; el porqué está
          explicado en StationCarousel.
        */}
          <div className="absolute top-4 left-4 z-[1120] flex w-[calc(100%-2rem)] flex-wrap items-start gap-2 lg:w-[calc(100%-23rem)]">
            {/*
            El buscador, solo en celular: de ahí para arriba lo reemplaza el de la franja de
            arriba, y desaparecer del todo es lo que deja a los filtros encabezando la fila.

            Ocupa exactamente lo que dibuja —la burbuja o el campo entero— y no un ancho fijo. El
            ancho reservado de antes evitaba un saltito al desplegarse, pero ese hueco vacío es
            justo donde ahora van los filtros, y tenerlos al lado vale más que el salto.
          */}
            {/*
              `relative` porque la lista de coincidencias se cuelga de este contenedor, y
              `onKeyDown` acá y no en el campo porque el campo lo dibuja otro componente: las
              teclas suben desde él igual.
            */}
            <div
              className={`relative shrink-0 md:hidden ${searchCollapsed ? 'w-12' : 'w-full'}`}
              onKeyDown={nav.onKeyDown}
            >
              <StationSearch
                value={query}
                onChange={handleQueryChange}
                collapsible
                onExpandedChange={setSearchExpanded}
                combobox={{
                  listboxId: suggestionsId,
                  expanded: nav.open,
                  activeOptionId:
                    nav.highlighted >= 0
                      ? `${suggestionsId}-${String(nav.highlighted)}`
                      : undefined,
                }}
              />

              {nav.open && (
                <StationSuggestions
                  listboxId={suggestionsId}
                  matches={suggestions}
                  highlighted={nav.highlighted}
                  onHighlight={nav.setHighlighted}
                  onPick={(station) => {
                    selectStation(station.stationId)
                    nav.dismiss()
                  }}
                />
              )}
            </div>

            {/*
            Los filtros. Dónde caen no lo decide una clase sino el ancho del buscador que tienen
            al lado: plegado a burbuja les deja el renglón casi entero y siguen a su derecha,
            desplegado el campo se lleva la fila completa y bajan solos al renglón de abajo. Es
            el `flex-wrap` de la fila haciendo el trabajo, sin medir nada.

            En celular scrollean de costado y en pantalla ancha envuelven, y la diferencia es
            cuánto lugar hay: envolviendo en una pantalla angosta, seis burbujas se comen tres
            renglones de mapa. La barra del scroll se esconde —`no-scrollbar`— porque flotan
            sobre los mosaicos y una barra gris ahí se lee como suciedad; lo que insinúa que hay
            más a la derecha es la burbuja cortada por el borde.

            El `py-6 -my-6` es para la sombra, no para el aire: un contenedor que scrollea de
            costado recorta también arriba y abajo —el navegador no deja pedir una sola de las
            dos— y sin lugar de sobra las burbujas quedan con la sombra cortada al ras. Son 24px
            porque eso es lo que baja `shadow-lg`: 10 de desplazamiento más 15 de difuminado,
            menos 3 que encoge. El margen negativo devuelve ese lugar prestado, así que la fila
            mide lo mismo que antes.

            Y ese lugar prestado no atrapa el dedo: la caja se estira sobre el mapa, invisible,
            y sin `pointer-events-none` se quedaría con los arrastres de una franja de mapa que
            no se ve por ningún lado. Las burbujas lo vuelven a encender para sí mismas, y el
            scroll de la fila sigue andando porque empieza en ellas.
          */}
            <div className="no-scrollbar pointer-events-none -my-6 flex min-w-0 flex-1 items-center gap-2 self-center overflow-x-auto py-6 [&>*]:pointer-events-auto md:flex-auto md:flex-wrap md:self-start md:overflow-x-visible">
              <StationFilters value={filters} onChange={setFilters} />
            </div>

            {/*
              El aviso de carga o de error, el ÚNICO lugar donde aparece un fallo del backend. Se
              pinta en rojo cuando algo se rompió, que es lo que lo saca de ser un dato al pasar.

              `md:ml-auto` lo manda al extremo de la fila, lejos del buscador y de los filtros: es
              información, no un control. En el celular no, porque ahí los filtros ocupan el
              renglón: empujado a la derecha quedaría colgando solo en el aire.
            */}
            {notice !== null && (
              <p
                className={`glass-panel shrink-0 rounded-full px-3 py-1.5 text-xs font-medium md:ml-auto ${
                  loadError !== null ? 'text-danger' : 'text-text-muted'
                }`}
                /* Los errores interrumpen; el "cargando" no. */
                role={loadError !== null ? 'alert' : undefined}
              >
                {notice}
              </p>
            )}
          </div>

          {/*
          El carrusel es de pantalla ancha nada más. En celular la lista de estaciones son los
          pines, y quien quiere ver una la toca: una segunda lista encima del mapa competiría
          por el mismo espacio con el panel que se abre justo abajo.
        */}
          {wide && (
            <StationCarousel
              stations={stations}
              selectedStationId={selectedStation?.stationId ?? null}
              onSelect={selectStation}
            />
          )}

          {/*
          Pantalla ancha: el detalle sube desde el borde de abajo, pegado a él y sin esquinas
          redondeadas. Apoya contra el borde en vez de flotar sobre el mapa, que es lo que lo
          hace leer como una parte de la pantalla y no como una tarjeta suelta. Queda a la
          izquierda para no taparle el carrusel de la derecha.

          Separado del borde izquierdo y no pegado a él: apoyado contra la esquina el panel
          se lee como un pedazo de la ventana, y separado se lee como algo apoyado sobre el
          mapa, que es lo que es.
        */}
          {wide && detail && (
            <aside className="station-panel border-border bg-surface/95 absolute bottom-0 left-12 z-[1120] flex w-[26rem] flex-col border border-b-0 shadow-lg shadow-black/40 backdrop-blur">
              {/*
              La flecha ocupa el ancho entero y no es un ícono en una esquina: apunta hacia
              abajo, que es a donde se va el panel, y esa franja es el blanco más grande que
              se puede dar para cerrarlo.
            */}
              <button
                type="button"
                onClick={closePanel}
                aria-label="Cerrar detalle"
                className="border-border text-text-muted hover:text-text hover:bg-surface focus-visible:outline-primary flex w-full shrink-0 justify-center border-b py-2 transition-colors focus-visible:-outline-offset-2 focus-visible:outline-2"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="h-5 w-5"
                  aria-hidden="true"
                >
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>

              {/*
              El contenido scrollea por su cuenta y el panel se topa contra el alto del mapa:
              una estación con muchos conectores no puede empujar el botón de reservar fuera
              de la pantalla.
            */}
              <div className="no-scrollbar max-h-[60vh] overflow-y-auto p-5">{detail}</div>
            </aside>
          )}
        </div>

        {/* Celular: lo mismo, como panel que sube desde abajo. */}
        {!wide && (
          <BottomSheet
            open={selectedStation !== null}
            onClose={closePanel}
            label="Detalle de la estación"
            /* Ver el comentario de la prop: acá atrás está el mapa, y taparlo sería esconder
             el pin que se acaba de elegir. */
            dimBackground={false}
            /*
            El mismo gris que la tarjeta de escritorio: es el mismo panel en dos formas, y con
            el fondo por omisión del ABM se veía casi negro solo en el celular.

            /95 y no opaco: las filas de conector son `bg-surface/40`, así que sobre un fondo
            del MISMO color al 100% quedarían exactamente del tono del panel y se borrarían.
            Con el panel apenas translúcido conservan el escalón que se ve en el diseño.
          */
            backgroundClass="bg-surface/95 backdrop-blur"
          >
            {detail}
          </BottomSheet>
        )}
      </div>

      {reserving !== null && dialog && (
        <BookingDialog
          station={reserving.station}
          connector={reserving.connector}
          onClose={finishReserving}
        />
      )}
    </section>
  )
}
