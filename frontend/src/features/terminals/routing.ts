/**
 * Ruta por calles entre dos puntos, contra OSRM.
 *
 * Lo que agrega sobre la línea recta que había antes no es estética: una recta dice dirección y
 * distancia en línea de aire, y en una ciudad con un río, una autopista o una avenida de mano
 * única eso puede estar al doble de lo que realmente hay que manejar. La ruta dice por dónde se
 * va y cuánto tarda, que son las dos preguntas con las que alguien elige una estación.
 *
 * **Por qué OSRM y no otro.** Es el único de los candidatos que no pide credencial: contesta con
 * `Access-Control-Allow-Origin: *`, así que el navegador puede llamarlo directo y no hace falta
 * ni una clave en el bundle ni un proxy en el backend. Los que sí piden clave —OpenRouteService,
 * Mapbox, Google— obligan a lo segundo, porque una clave en el frontend es una clave pública.
 *
 * **Y sus condiciones, que no son menores.** El servidor de demostración es de uso no comercial,
 * pide no pasar de una consulta por segundo y no promete estar arriba: puede cortarse sin aviso
 * y sin motivo. Las dos consecuencias están tomadas en el código: el ritmo lo limita `useRoute`
 * con su umbral de movimiento, y la caída la absorbe el mapa, que vuelve a la recta de siempre
 * cuando esto falla. Nada de la pantalla puede depender de que esta respuesta llegue.
 *
 * **La licencia exige atribución** de OpenStreetMap (ODbL) y de OSRM, igual que los mosaicos.
 * Ver TILE_ATTRIBUTION en mapConfig, que ya arrastra la misma deuda: el crédito tiene que
 * aparecer en el pie o en una pantalla "acerca de".
 *
 * Si algún día el proyecto sale de la cursada, el reemplazo natural es levantar un OSRM propio
 * con el extracto de Argentina, o pasar a OpenRouteService detrás del backend. En los dos casos
 * cambia la URL de acá abajo y nada más: el resto del código habla de `Route`, no de OSRM.
 */

/**
 * El servidor de demostración del proyecto OSRM.
 *
 * El perfil va en la dirección (`/driving/`) y es el único que este servidor tiene cargado: pedir
 * `/cycling/` o `/walking/` acá devuelve igual la ruta en auto, sin avisar. La alternativa con
 * perfiles de verdad separados es el espejo de FOSSGIS
 * (`routing.openstreetmap.de/routed-car|routed-bike|routed-foot`), que sirve también de reemplazo
 * si este se cae.
 */
const OSRM_URL = 'https://router.project-osrm.org/route/v1/driving'

/**
 * `geometries=geojson` y no el `polyline` codificado que OSRM trae por omisión.
 *
 * El codificado pesa bastante menos, pero hay que decodificarlo, y eso significa sumar una
 * dependencia —o copiar el algoritmo— para ahorrar unos kilobytes en un tramo urbano que se pide
 * una vez por estación elegida. Encima OSRM codifica con precisión 5 mientras que otros usan 6,
 * y elegir mal el parámetro no falla: devuelve una ruta corrida por el planeta.
 *
 * `overview=full` es la geometría completa. Sin él viene simplificada, y una ruta simplificada
 * dibujada sobre el mapa al zoom 15 se despega visiblemente de las calles.
 *
 * `alternatives=false` y `steps=false`: no hay pantalla que muestre rutas alternativas ni
 * indicaciones giro a giro, y los pasos son la parte más pesada de la respuesta.
 */
const OSRM_QUERY = 'overview=full&geometries=geojson&alternatives=false&steps=false'

/**
 * Lo más lejos que se acepta que OSRM corra un punto para apoyarlo sobre una calle.
 *
 * **Es la protección contra la falla silenciosa de este servicio.** OSRM nunca rechaza un punto
 * que no está sobre una calle: lo pega a la más cercana y rutea desde ahí, sin avisar. Probado
 * con un punto en medio del Río de la Plata, contesta `code: "Ok"` tras correrlo 22 km, y la ruta
 * que devuelve es impecable salvo por el detalle de que arranca a 22 km de donde se pidió.
 *
 * Eso no es un caso de laboratorio: `useDeviceLocation` avisa que en una computadora de escritorio
 * la posición sale de la red y puede errarle por kilómetros. Sin este techo, el mapa dibujaría con
 * total confianza una ruta que empieza en un lugar donde el usuario nunca estuvo.
 *
 * Mil metros porque tiene que dejar pasar lo legítimo y frenar lo absurdo. Una estación al fondo
 * de un predio, o un usuario adentro de una playa de estacionamiento sin mapear, están a un par de
 * cuadras de la calle más cercana; los errores que importan —la ubicación por IP, el punto en el
 * río— son de kilómetros. El corte está en el orden de magnitud que los separa.
 */
export const MAX_SNAP_M = 1000

/** Un punto cualquiera, en el orden en que lo escribe todo el resto de la aplicación. */
export interface Point {
  latitude: number
  longitude: number
}

export interface Route {
  /** El trazado, ya en el orden que espera Leaflet. Ver la inversión de abajo. */
  coordinates: [number, number][]
  /** Metros de recorrido real, no en línea de aire. */
  distanceM: number
  /** Segundos estimados de manejo, sin tránsito: OSRM no lo modela. */
  durationS: number
  /**
   * Cuánto hubo que correr el origen para apoyarlo sobre una calle, en metros.
   *
   * Se expone aunque la ruta ya haya pasado el filtro de MAX_SNAP_M: la diferencia entre un pegado
   * de 3 m y uno de 400 m es la diferencia entre "salís de tu puerta" y "salís de la esquina", y
   * quien quiera decirlo alguna vez necesita el número.
   */
  originSnapM: number
}

/**
 * Por qué no hay ruta, cuando no la hay.
 *
 * Los tres se separan porque no se le dicen igual a quien mira la pantalla: 'service' es un
 * problema nuestro que probablemente se arregle solo, 'no-route' es una verdad sobre el mapa que
 * no se arregla nunca, y 'off-road' no habla de la ruta sino de la ubicación —es el único de los
 * tres en el que el usuario puede hacer algo—.
 */
export type RouteFailure =
  /** El servidor no contestó, contestó un error, o devolvió algo que no se entiende. */
  | 'service'
  /** Los dos puntos son válidos pero no hay camino entre ellos: una isla, la otra orilla. */
  | 'no-route'
  /** Alguna de las dos puntas quedó demasiado lejos de cualquier calle. Ver MAX_SNAP_M. */
  | 'off-road'

/**
 * Se lanza cuando OSRM no contesta o contesta algo que no es una ruta usable.
 *
 * Existe como tipo propio para que quien llame pueda distinguirla de un error de programación y
 * mostrar un estado en vez de romper la pantalla. Que no haya ruta es un caso previsto —el
 * servidor es de demostración—, no una falla de la aplicación.
 */
export class RouteUnavailableError extends Error {
  readonly reason: RouteFailure

  constructor(reason: RouteFailure, message: string) {
    super(message)
    this.name = 'RouteUnavailableError'
    this.reason = reason
  }
}

/** Lo que devuelve OSRM, recortado a lo que se usa. */
interface OsrmResponse {
  code: string
  routes?: {
    distance: number
    duration: number
    geometry: { coordinates: [number, number][] }
  }[]
  /**
   * Un punto por cada coordenada pedida, en el mismo orden, ya apoyado sobre la calle. `distance`
   * es cuánto se lo corrió para lograrlo, y es el dato que vuelve visible el pegado silencioso.
   */
  waypoints?: { distance: number }[]
}

/**
 * Pide la ruta en auto de `from` a `to`.
 *
 * **El orden de las coordenadas es la trampa de este archivo.** GeoJSON —y con él OSRM, tanto en
 * la dirección como en la respuesta— escribe `longitud,latitud`; Leaflet escribe `lat,lng`. Es la
 * clase de error que no falla: devuelve una ruta perfectamente válida entre dos puntos que no son
 * los que se pidieron. Por eso la inversión se hace acá, en la frontera, y todo lo que sale de
 * esta función ya está en el orden de Leaflet.
 *
 * `signal` no es opcional en la práctica: `watchPosition` puede disparar una consulta nueva antes
 * de que vuelva la anterior, y sin cancelar la vieja llega después y pisa a la nueva con una ruta
 * de una posición ya superada.
 */
export async function fetchRoute(from: Point, to: Point, signal?: AbortSignal): Promise<Route> {
  const origin = `${String(from.longitude)},${String(from.latitude)}`
  const destination = `${String(to.longitude)},${String(to.latitude)}`

  /*
   * `radiuses` es el techo del pegado, uno por coordenada, y es la mitad SERVIDOR de la protección
   * que explica MAX_SNAP_M: con él, OSRM devuelve `NoSegment` en vez de correr el punto 22 km y
   * contestar "Ok". Preferirlo al chequeo local tiene una ventaja concreta: nos ahorra recibir y
   * descartar la geometría completa de una ruta que ya sabemos que no sirve.
   *
   * La mitad CLIENTE —revisar `waypoints[].distance` más abajo— se hace igual, y no es redundante
   * por paranoia: si algún día este parámetro se cae al cambiar de proveedor, el chequeo local
   * sigue atajando el caso. Es el único lugar del código donde un silencio del servicio se
   * convierte en una ruta plausible y equivocada, así que vale la doble llave.
   */
  const radiuses = `${String(MAX_SNAP_M)};${String(MAX_SNAP_M)}`

  let response: Response
  try {
    response = await fetch(
      `${OSRM_URL}/${origin};${destination}?${OSRM_QUERY}&radiuses=${radiuses}`,
      { signal },
    )
  } catch (error) {
    // La cancelación no es una falla del servicio: se la deja pasar tal cual para que el hook
    // la reconozca y no la trate como "no hay ruta".
    if (signal?.aborted === true) throw error
    throw new RouteUnavailableError('service', 'No se pudo contactar al servicio de rutas')
  }

  const body = (await response.json().catch(() => null)) as OsrmResponse | null

  /*
   * **El motivo se lee del cuerpo, NO del estado HTTP.** Las dos respuestas que más nos importan
   * —`NoSegment`, que es cómo contesta a `radiuses` cuando una punta no tiene calle cerca, y
   * `NoRoute`, cuando las dos son válidas pero están incomunicadas— vienen con estado 400, igual
   * que un error de verdad. Mirando `response.ok` primero, las tres terminan indistinguibles como
   * "el servicio no anda", que es justamente lo contrario de lo que pasó: el servicio contestó, y
   * contestó con precisión.
   *
   * Verificado contra el servidor: Isla Martín García → CABA devuelve
   * `{"code":"NoRoute"}` con HTTP 400, y un punto en el Río de la Plata devuelve
   * `{"code":"NoSegment"}` también con 400.
   */
  if (body?.code === 'NoSegment') {
    throw new RouteUnavailableError('off-road', 'No hay ninguna calle cerca del punto de partida')
  }

  if (body?.code === 'NoRoute') {
    throw new RouteUnavailableError('no-route', 'No hay una ruta por calles entre los dos puntos')
  }

  const route = body?.routes?.[0]
  if (!response.ok || body === null || body.code !== 'Ok' || route === undefined) {
    throw new RouteUnavailableError(
      'service',
      `El servicio de rutas respondió ${String(response.status)}`,
    )
  }

  /*
   * La segunda llave del pegado. Se mide contra las DOS puntas y no solo contra el origen: una
   * estación mal cargada, con las coordenadas en el medio del campo, produce exactamente el mismo
   * engaño por el otro extremo —una ruta que termina en cualquier lado—.
   *
   * Si el servicio no mandó `waypoints`, se asume cero y el chequeo no frena nada: el techo del
   * servidor ya corrió, y inventar un rechazo con un dato que no llegó sería peor que confiar.
   */
  const snaps = body.waypoints?.map((w) => w.distance) ?? []
  if (snaps.some((distance) => distance > MAX_SNAP_M)) {
    throw new RouteUnavailableError('off-road', 'El recorrido no arranca donde se pidió')
  }

  return {
    coordinates: route.geometry.coordinates.map(([longitude, latitude]) => [latitude, longitude]),
    distanceM: route.distance,
    durationS: route.duration,
    originSnapM: snaps[0] ?? 0,
  }
}
