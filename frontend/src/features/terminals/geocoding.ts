/**
 * Buscar una dirección y obtener su punto, contra Photon.
 *
 * Existe por una sola pregunta: "¿desde dónde salís?". El mapa ya sabe calcular el recorrido hasta
 * una estación (ver `routing.ts`), pero hasta ahora el punto de partida era uno solo —donde está
 * el dispositivo— y eso alcanza mientras el conductor esté por salir. No alcanza cuando reserva
 * desde la oficina para cargar cerca de casa, cuando el navegador no da la ubicación, o cuando la
 * da con kilómetros de error, que en una computadora de escritorio es lo normal. Escribir la
 * dirección es la salida a los tres casos.
 *
 * **Por qué Photon y no Nominatim.** Nominatim es el geocodificador canónico de OpenStreetMap y
 * fue el primer candidato, pero su política de uso PROHÍBE explícitamente el autocompletado: una
 * consulta por tecla es justo el patrón que pide no usar, y la instancia pública bloquea por IP a
 * quien lo hace. Photon es de Komoot, está construido sobre los mismos datos de OpenStreetMap y
 * existe justamente para esto —búsqueda mientras se escribe—, no pide credencial y contesta con
 * `Access-Control-Allow-Origin: *`, así que el navegador lo llama directo. Es la misma decisión
 * que ya se tomó con OSRM para las rutas y por los mismos motivos: sin clave en el bundle y sin
 * proxy en el backend.
 *
 * **Y con las mismas condiciones.** La instancia pública es de cortesía, sin garantía de servicio
 * y con el pedido de no abusarla. Las dos consecuencias están tomadas: el ritmo lo limita
 * `useAddressSearch` con su espera y su largo mínimo, y la caída la absorbe el campo, que dice que
 * no se pudo buscar y deja seguir con la ubicación del dispositivo. Nada de la reserva depende de
 * que esta respuesta llegue.
 *
 * **La licencia exige atribución** de OpenStreetMap (ODbL), igual que los mosaicos y las rutas.
 * Ver `GEOCODING_ATTRIBUTION` en mapConfig, que arrastra la misma deuda que las otras dos: el
 * crédito tiene que aparecer en el pie o en una pantalla "acerca de".
 *
 * Sobre "vamos a tener que tener todas las direcciones de la Argentina": no hay que tenerlas. Las
 * tiene OpenStreetMap y las sirve Photon; acá no se guarda ni un domicilio. Si el proyecto alguna
 * vez sale de la cursada, el reemplazo natural es levantar un Photon propio con el extracto de
 * Argentina —el mismo camino que OSRM—, y entonces cambia la URL de acá abajo y nada más: el resto
 * del código habla de `AddressSuggestion`, no de Photon.
 */

import { ARGENTINA_BOUNDS } from './mapConfig'

/** La instancia pública de Komoot. Ver arriba por qué se la llama directo desde el navegador. */
const PHOTON_URL = 'https://photon.komoot.io/api'

/**
 * El recuadro del país, en el orden que espera Photon: `minLon,minLat,maxLon,maxLat`.
 *
 * Se arma desde ARGENTINA_BOUNDS y no se escribe a mano para que no se puedan separar: el mapa
 * no deja salir de ese recuadro, así que una dirección buscada afuera sería un punto al que el
 * mapa no puede ni volar.
 *
 * **Filtra, pero filtra de más.** El recuadro que encierra la Argentina encierra también pedazos
 * de Chile, Uruguay, Paraguay, Bolivia y Brasil —probado: "rosario" devuelve el de Santa Fe y el
 * de Colonia, Uruguay—. Por eso además se descarta por `countrycode` más abajo. Las dos llaves
 * hacen cosas distintas: esta le ahorra trabajo al servidor, la otra es la que de verdad decide.
 */
const BBOX = [
  ARGENTINA_BOUNDS.getWest(),
  ARGENTINA_BOUNDS.getSouth(),
  ARGENTINA_BOUNDS.getEast(),
  ARGENTINA_BOUNDS.getNorth(),
]
  .map((value) => value.toFixed(4))
  .join(',')

/**
 * Cuántas sugerencias se piden.
 *
 * Seis, el mismo número que las estaciones que coinciden en el buscador del mapa (ver
 * MAX_SUGGESTIONS en `StationSuggestions`), y por el mismo motivo: más que eso deja de ser una
 * ayuda para elegir y pasa a ser una lista para leer. Se piden más de las que se muestran porque
 * el filtro por país y el de duplicados de acá abajo descartan algunas.
 */
const REQUESTED = 10

/** Cuántas sobreviven al filtro y se ofrecen. */
export const MAX_ADDRESS_SUGGESTIONS = 6

/**
 * Una dirección elegible, ya lista para mostrar y para rutear.
 *
 * El texto viene partido en dos porque se lee en dos renglones y con dos pesos distintos: `label`
 * es lo que el conductor escribió —la calle y la altura— y `detail` es lo que la desambigua. Con
 * todo en un solo string, "Avenida Corrientes 348" en cinco provincias serían cinco renglones
 * idénticos hasta la mitad, que es exactamente el error que este campo existe para evitar.
 */
export interface AddressSuggestion {
  /** Identidad estable para la key de React. No es de nuestro dominio: es el nodo de OSM. */
  id: string
  /** La calle con la altura, o el nombre del lugar. "Avenida Corrientes 348". */
  label: string
  /** Barrio, ciudad y provincia, lo que haya. "San Nicolás, Buenos Aires, CABA". */
  detail: string
  latitude: number
  longitude: number
  /**
   * Cuánto puede estar corrido este punto, en metros.
   *
   * **No es un dato que devuelva Photon: lo deduce `accuracyFor` del tipo de resultado.** Existe
   * porque un domicilio y una ciudad no son la misma clase de respuesta —uno es una puerta, el
   * otro es el centro geométrico de cien manzanas— y el recorrido no puede tratarlos igual.
   *
   * Sale con este nombre y en metros a propósito: es exactamente lo que `useDeviceLocation`
   * informa de la ubicación del navegador, así que una dirección elegida entra en `useRoute` por
   * la misma puerta que el GPS y hereda gratis sus dos umbrales —el aviso de "aproximada" y el
   * rechazo por imprecisa—. Ver `RouteOrigin`.
   */
  accuracyM: number
}

/**
 * Se lanza cuando Photon no contesta o contesta algo que no se entiende.
 *
 * Tipo propio por el mismo motivo que `RouteUnavailableError`: que el servicio de búsqueda esté
 * caído es un caso previsto —la instancia es de cortesía—, no una falla de la aplicación, y quien
 * llama tiene que poder mostrar un estado en vez de romper la pantalla.
 */
export class GeocodingUnavailableError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'GeocodingUnavailableError'
  }
}

/** Lo que devuelve Photon, recortado a lo que se usa. Es GeoJSON. */
interface PhotonResponse {
  features?: {
    geometry?: { coordinates?: [number, number] }
    properties?: {
      osm_type?: string
      osm_id?: number
      /** house | street | locality | district | city | county | state | country | other */
      type?: string
      name?: string
      street?: string
      housenumber?: string
      district?: string
      city?: string
      county?: string
      state?: string
      countrycode?: string
    }
  }[]
}

/**
 * Cuán preciso es un resultado según qué clase de lugar es, en metros.
 *
 * Los números no salen de ninguna medición: son órdenes de magnitud, y lo único que tienen que
 * hacer es caer del lado correcto de los dos umbrales de `useRoute`. Una puerta y una calle están
 * por debajo de GOOD_ACCURACY_M (1 km) y rutean sin advertencia; un barrio o una ciudad quedan por
 * encima y el panel avisa que el punto de partida es aproximado, que es la verdad: el centro de
 * Rosario no es la casa de nadie.
 *
 * Nada llega a MAX_ACCURACY_M (25 km), y es deliberado: las clases que sí lo superarían
 * —provincia, país— no llegan hasta acá porque `usable` las descarta antes. Salir "desde Santa Fe"
 * no es un punto de partida, es una región.
 */
const ACCURACY_M: Record<string, number> = {
  house: 25,
  street: 150,
  locality: 800,
  district: 1500,
  city: 5000,
}

/**
 * Si este resultado sirve como punto de partida.
 *
 * Se cortan dos cosas distintas. Lo que está fuera del país, porque el mapa no se puede mover
 * hasta ahí (ver ARGENTINA_BOUNDS) y porque el recuadro de la consulta deja pasar las fronteras.
 * Y lo que es demasiado grande para ser un punto: una provincia entera no es un lugar del que se
 * salga, y ofrecerla sería ofrecer una ruta calculada desde un centro geométrico que no le
 * significa nada a nadie.
 */
function usable(type: string | undefined, countrycode: string | undefined): boolean {
  return countrycode === 'AR' && type !== undefined && type in ACCURACY_M
}

/**
 * El renglón principal: la calle con la altura, o el nombre propio del lugar.
 *
 * La altura va DESPUÉS de la calle porque así se escribe y se dice acá. Photon las devuelve por
 * separado justamente para que cada país las junte a su manera.
 */
function labelOf(
  properties: NonNullable<NonNullable<PhotonResponse['features']>[number]['properties']>,
): string {
  const { name, street, housenumber } = properties

  if (street !== undefined) {
    return housenumber === undefined ? street : `${street} ${housenumber}`
  }

  return name ?? ''
}

/**
 * El renglón de abajo: de lo más chico a lo más grande, sin repetir.
 *
 * El `Set` no es una optimización sino una corrección: Photon devuelve seguido la misma palabra en
 * dos campos —`district` y `city` valen los dos "Firmat", `city` y `state` valen los dos "Buenos
 * Aires"— y escrito tal cual el renglón queda "Firmat, Firmat, Santa Fe". `county` entra solo
 * cuando no hay ciudad: "Partido de Tandil" al lado de "Tandil" es la misma información dos veces.
 */
function detailOf(
  properties: NonNullable<NonNullable<PhotonResponse['features']>[number]['properties']>,
): string {
  const { district, city, county, state } = properties
  const parts = [district, city ?? county, state].filter((part) => part !== undefined)

  return [...new Set(parts)].join(', ')
}

/**
 * Busca direcciones que empiecen a parecerse a `query`, dentro de la Argentina.
 *
 * `near` inclina los resultados hacia un punto sin atarlos a él: con la ubicación del dispositivo,
 * "Corrientes 348" pone primero la de CABA y deja abajo las de Misiones y Santa Fe. Es una
 * preferencia, no un filtro —las otras siguen estando—, que es lo que corresponde cuando alguien
 * puede estar buscando a dónde ir y no dónde está.
 *
 * `signal` no es opcional en la práctica: se llama a esto por cada pausa al escribir, y sin
 * cancelar la anterior una respuesta vieja llega después y pisa la lista con las coincidencias de
 * un texto que ya no está en el campo.
 */
export async function searchAddresses(
  query: string,
  near: { latitude: number; longitude: number } | null,
  signal?: AbortSignal,
): Promise<AddressSuggestion[]> {
  const params = new URLSearchParams({
    q: query,
    limit: String(REQUESTED),
    bbox: BBOX,
  })

  if (near !== null) {
    params.set('lat', near.latitude.toFixed(5))
    params.set('lon', near.longitude.toFixed(5))
    /*
     * Cuánto pesa la cercanía contra la relevancia del texto. Photon los mezcla con estos dos
     * valores y sin ellos la posición casi no se nota —probado: con `lat`/`lon` a secas, "9 de
     * Julio 1000" seguía devolviendo Tandil primero—. `zoom` es a qué escala se considera "cerca",
     * 14 es el barrio; la escala baja de 0 a 1 y 0,6 deja la cercanía mandando sin borrar del todo
     * lo que está lejos.
     */
    params.set('zoom', '14')
    params.set('location_bias_scale', '0.6')
  }

  let response: Response
  try {
    response = await fetch(`${PHOTON_URL}?${params.toString()}`, { signal })
  } catch (error) {
    /* La cancelación no es una falla del servicio: se la deja pasar para que el hook la reconozca. */
    if (signal?.aborted === true) throw error
    throw new GeocodingUnavailableError('No se pudo contactar al buscador de direcciones')
  }

  if (!response.ok) {
    throw new GeocodingUnavailableError(
      `El buscador de direcciones respondió ${String(response.status)}`,
    )
  }

  const body = (await response.json().catch(() => null)) as PhotonResponse | null
  if (body === null) {
    throw new GeocodingUnavailableError('El buscador de direcciones respondió algo ilegible')
  }

  const suggestions: AddressSuggestion[] = []
  /*
   * Los repetidos se cortan por lo que se VE y no por el id de OSM: dos nodos distintos pueden ser
   * la misma esquina —una puerta y el portón de al lado— y ofrecerlos juntos son dos renglones
   * idénticos entre los que no hay forma de elegir.
   */
  const seen = new Set<string>()

  for (const feature of body.features ?? []) {
    const properties = feature.properties
    const coordinates = feature.geometry?.coordinates
    if (properties === undefined || coordinates === undefined) continue
    if (!usable(properties.type, properties.countrycode)) continue

    const label = labelOf(properties)
    if (label === '') continue

    const detail = detailOf(properties)
    const key = `${label}|${detail}`
    if (seen.has(key)) continue
    seen.add(key)

    /*
     * **El orden de las coordenadas es la misma trampa que en `routing.ts`.** GeoJSON escribe
     * `longitud,latitud` y todo el resto de la aplicación escribe `lat,lon`. Da la vuelta acá, en
     * la frontera, así que lo que sale de esta función ya está en el orden de adentro.
     */
    const [longitude, latitude] = coordinates

    suggestions.push({
      id: `${properties.osm_type ?? '?'}${String(properties.osm_id ?? key)}`,
      label,
      detail,
      latitude,
      longitude,
      /* El `in` de `usable` ya garantizó la clave; el `?? 0` es para el compilador. */
      accuracyM: ACCURACY_M[properties.type ?? ''] ?? 0,
    })

    if (suggestions.length === MAX_ADDRESS_SUGGESTIONS) break
  }

  return suggestions
}
