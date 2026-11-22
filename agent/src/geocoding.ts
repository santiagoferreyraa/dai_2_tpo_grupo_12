/**
 * De un lugar escrito ("estoy en Plaza Italia", "Cabildo 2200") a coordenadas.
 *
 * Usa Photon, el mismo servicio que el mapa usa para el punto de partida
 * (`frontend/src/features/terminals/geocoding.ts`): no pide clave y trae datos de OpenStreetMap.
 * Es un servidor público con pedido de uso moderado, y el agente lo consulta una vez por lugar
 * mencionado, no a cada tecla como un autocompletado.
 */

const PHOTON_URL = 'https://photon.komoot.io/api'

/**
 * El recuadro de la Argentina, `minLon,minLat,maxLon,maxLat`: el mismo `ARGENTINA_BOUNDS` del
 * mapa. Encierra también pedazos de los países vecinos, por eso además se filtra por país.
 */
const BBOX = '-73.6000,-55.2000,-53.6000,-21.7000'

/**
 * Qué tan preciso es cada tipo de resultado, en metros, y a la vez cuáles se aceptan: provincia o
 * país no son un punto de partida, son una región. Son los valores que usa el mapa, más uno.
 *
 * **El agente acepta `other`, y el mapa no.** Photon marca así las plazas, los parques y buena
 * parte de los lugares conocidos, y "estoy en Plaza Italia" es justo la forma en que un conductor
 * dice dónde está; en el mapa se escribe una dirección. Se le da la precisión de una cuadra larga.
 */
const PRECISION_M: Record<string, number> = {
  house: 25,
  street: 150,
  other: 300,
  locality: 800,
  district: 1500,
  city: 5000,
}

/** Cuántos lugares se le devuelven al modelo. Con más, tiende a elegir sin preguntar. */
const MAX_PLACES = 3

const TIMEOUT_MS = 6000

export interface Place {
  label: string
  latitude: number
  longitude: number
  /** Metros, aproximados: el centro de una ciudad no es la casa de nadie. */
  precisionM: number
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: {
    type?: string
    countrycode?: string
    name?: string
    street?: string
    housenumber?: string
    district?: string
    city?: string
    state?: string
  }
}

export async function findPlaces(
  query: string,
  signal?: AbortSignal,
  doFetch: typeof fetch = fetch,
): Promise<Place[]> {
  /* Sin `lang`: Photon solo acepta de, en y fr, y con `es` contesta 400. */
  const params = new URLSearchParams({ q: query, limit: '8', bbox: BBOX })
  const timeout = AbortSignal.timeout(TIMEOUT_MS)

  const response = await doFetch(`${PHOTON_URL}?${params.toString()}`, {
    signal: signal === undefined ? timeout : AbortSignal.any([signal, timeout]),
  })
  if (!response.ok) throw new Error(`El buscador de direcciones contestó ${response.status}`)

  const body = (await response.json()) as { features?: PhotonFeature[] }
  const places: Place[] = []

  for (const feature of body.features ?? []) {
    const { type, countrycode } = feature.properties
    if (countrycode !== 'AR' || type === undefined || !(type in PRECISION_M)) continue

    /* GeoJSON escribe `longitud,latitud`; el resto de Ecopedia, `lat,lon`. */
    const [longitude, latitude] = feature.geometry.coordinates
    const label = describe(feature.properties)
    if (places.some((place) => place.label === label)) continue

    places.push({ label, latitude, longitude, precisionM: PRECISION_M[type] })
    if (places.length === MAX_PLACES) break
  }

  return places
}

/** "Avenida Cabildo 2200, Belgrano, Ciudad Autónoma de Buenos Aires". */
function describe(properties: PhotonFeature['properties']): string {
  const street =
    properties.street !== undefined
      ? [properties.street, properties.housenumber].filter(Boolean).join(' ')
      : undefined

  const parts = [properties.name, street, properties.district, properties.city, properties.state]
  return parts
    .filter((part): part is string => part !== undefined && part !== '')
    .filter((part, index, all) => all.indexOf(part) === index)
    .join(', ')
}
