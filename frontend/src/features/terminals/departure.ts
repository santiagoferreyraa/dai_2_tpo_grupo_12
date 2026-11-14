/**
 * Desde dónde sale el conductor: la ubicación del dispositivo, o una dirección que escribió.
 *
 * **Por qué es un módulo y no un estado de la pantalla del mapa.** Lo eligen y lo leen lugares que
 * no se tienen a mano entre sí: el campo de la reserva —que en pantalla ancha está adentro de un
 * diálogo montado en un portal, fuera del árbol de la pantalla— lo elige, y el mapa, el panel de
 * detalle y `useRoute` lo leen. Pasarlo por props obligaría a atravesar `BookingDialog` y
 * `BookingPanel`, que son dos envoltorios a los que el punto de partida no les importa. Es la
 * misma decisión, por el mismo motivo, que ya tomaron `session` y `myBookingsStore`.
 *
 * **Y por qué no lo guarda el backend.** Acá no se almacena el domicilio de nadie: la dirección es
 * del navegador, para calcular un recorrido, y se olvida al cerrar la pestaña. Guardarla del otro
 * lado sería empezar a tener un dato personal que la reserva no necesita —RF08 reserva un conector
 * por una ventana de tiempo, no un viaje— y que habría que cuidar.
 *
 * Por omisión es 'device', que es lo correcto y lo esperable: el caso normal es salir de donde uno
 * está. Escribir una dirección es la excepción, y existe para los tres casos en que el dispositivo
 * no sirve —permiso denegado, ubicación con kilómetros de error, o sencillamente reservar desde
 * otro lado para salir más tarde—.
 */

import { useSyncExternalStore } from 'react'

import type { AddressSuggestion } from './geocoding'

/**
 * La dirección elegida, ya sin nada de Photon encima.
 *
 * Es `AddressSuggestion` menos el `id`, que es del buscador y no del dominio: una vez elegida, de
 * qué nodo de OpenStreetMap salió no le importa a nadie. Que los campos tengan los mismos nombres
 * es lo que deja pasar una por la otra sin traducir.
 */
export interface DepartureAddress {
  label: string
  detail: string
  latitude: number
  longitude: number
  /** Ver `AddressSuggestion.accuracyM`: acá entra por la misma puerta que la precisión del GPS. */
  accuracyM: number
}

export type Departure =
  /** Donde diga el navegador, siguiendo al conductor mientras se mueve. */
  | { kind: 'device' }
  /**
   * Un punto fijo que el conductor eligió de la lista de sugerencias, para llegar a UNA estación.
   *
   * **La dirección viene atada a la estación para la que se eligió, y eso es parte del dato.** Se
   * escribe en el formulario de una reserva concreta —"salgo de la oficina para ir a ESTA
   * estación"—, así que fuera de esa estación no quiere decir nada: tocar otro pin para ver cuánto
   * queda es la pregunta "¿desde dónde estoy?", y contestada desde la dirección de la otra reserva
   * el mapa dibuja un recorrido que sale de un lugar donde el conductor no está.
   *
   * `stationId` es la estación a la que apunta. Quién decide qué hacer cuando se está mirando otra
   * es `StationsMapPage`; acá solo se guarda para quién se eligió.
   */
  | { kind: 'address'; stationId: number; address: DepartureAddress }

/** El valor por omisión, y el único que existe hasta que alguien elija una dirección. */
export const DEVICE_DEPARTURE: Departure = { kind: 'device' }

/**
 * Cómo se llama el punto de partida en pantalla cuando es el dispositivo.
 *
 * Vive acá y no adentro del campo porque lo escriben dos lugares —el campo y el mapa— y tienen que
 * decir lo mismo.
 */
export const DEVICE_DEPARTURE_LABEL = 'Tu ubicación'

const STORAGE_KEY = 'ecopedia.departure'

/** El punto de partida confirmado: el de la reserva que se hizo con él. Es el único que se guarda. */
let departure: Departure = DEVICE_DEPARTURE

/**
 * Lo que el conductor eligió en el formulario de una reserva que todavía no confirmó, o `null` si
 * no está eligiendo nada (ECO-139).
 *
 * Existe aparte de `departure` porque elegir una dirección en el formulario NO es todavía el punto
 * de partida de ningún viaje. Cuando era lo mismo, una reserva que no se terminaba de hacer —se
 * cerró el diálogo, o el backend la rechazó porque ya había otra— dejaba su dirección puesta: el
 * recorrido de esa estación salía de un punto que ya no marcaba nada, y peor, la dirección de la
 * reserva que SÍ se hizo quedaba pisada por la del intento.
 *
 * Mientras el borrador existe es lo que se ve (`useDeparture`), así el formulario y la vista previa
 * del recorrido responden a lo que se está eligiendo. Se vuelve punto de partida con
 * `commitDeparture`, al confirmar la reserva, y se tira con `discardDepartureDraft`, al salir del
 * flujo sin confirmar. Vive solo en memoria: nunca llega a `sessionStorage`.
 */
let draft: Departure | null = null

const listeners = new Set<() => void>()

/**
 * Se guarda en `sessionStorage` y no en `localStorage`, que es donde vive la sesión.
 *
 * La diferencia es cuánto dura, y acá dura poco a propósito. Sobrevivir a un F5 hace falta: el
 * mapa se recarga solo al cambiar de tema o al editar la dirección, y perder la dirección escrita
 * en cada una de esas sería insufrible. Sobrevivir una semana NO: el conductor que un martes puso
 * la dirección de la oficina y el lunes siguiente abre el mapa desde su casa vería recorridos
 * calculados desde la oficina sin que nada se lo recuerde. Cerrar la pestaña vuelve a "tu
 * ubicación", que es el valor que nunca miente.
 */
function persist(): void {
  try {
    if (departure.kind === 'device') {
      window.sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...departure.address, stationId: departure.stationId }),
    )
  } catch {
    /* Sin almacenamiento el punto de partida vive igual, solo que no sobrevive a un F5. */
  }
}

/**
 * Lee la dirección guardada, si la hay y si sigue teniendo forma de dirección.
 *
 * La validación campo por campo no es paranoia de más: `sessionStorage` lo puede editar cualquiera
 * desde las herramientas del navegador, y una latitud que sea `null` o el string "hola" no
 * rompería acá sino tres capas más abajo, adentro de Leaflet o de la dirección que se le arma a
 * OSRM, donde el error no se parece en nada a su causa.
 */
function restore(): Departure {
  if (typeof window === 'undefined') return DEVICE_DEPARTURE

  let raw: string | null
  try {
    raw = window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return DEVICE_DEPARTURE
  }

  if (raw === null) return DEVICE_DEPARTURE

  try {
    const parsed = JSON.parse(raw) as Partial<DepartureAddress & { stationId: number }>
    const { label, detail, latitude, longitude, accuracyM, stationId } = parsed

    if (typeof label !== 'string' || label === '') return DEVICE_DEPARTURE
    if (typeof detail !== 'string') return DEVICE_DEPARTURE
    if (typeof latitude !== 'number' || !Number.isFinite(latitude)) return DEVICE_DEPARTURE
    if (typeof longitude !== 'number' || !Number.isFinite(longitude)) return DEVICE_DEPARTURE
    if (typeof accuracyM !== 'number' || !Number.isFinite(accuracyM)) return DEVICE_DEPARTURE
    if (typeof stationId !== 'number' || !Number.isInteger(stationId)) return DEVICE_DEPARTURE

    return {
      kind: 'address',
      stationId,
      address: { label, detail, latitude, longitude, accuracyM },
    }
  } catch {
    return DEVICE_DEPARTURE
  }
}

departure = restore()

function notify(): void {
  for (const listener of listeners) listener()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Lo que se ve ahora: el borrador del formulario si hay uno, y si no el punto confirmado. */
export function getDeparture(): Departure {
  return draft ?? departure
}

/** Solo el punto de partida confirmado, sin el borrador del formulario. */
export function getCommittedDeparture(): Departure {
  return departure
}

/**
 * Elige una dirección como punto de partida.
 *
 * Recibe una `AddressSuggestion` y no una `DepartureAddress` suelta a propósito: es la forma de
 * que lo único que pueda entrar acá sea algo que salió del buscador y que alguien eligió de la
 * lista. Un domicilio tecleado a mano no tiene coordenadas, así que ni siquiera se puede armar.
 *
 * `stationId` es la estación para la que se está eligiendo, y es obligatorio: una dirección sin
 * destino no tiene a qué recorrido aplicarse. Ver el comentario de `Departure`.
 */
export function departFrom(suggestion: AddressSuggestion, stationId: number): void {
  const { label, detail, latitude, longitude, accuracyM } = suggestion

  draft = {
    kind: 'address',
    stationId,
    address: { label, detail, latitude, longitude, accuracyM },
  }
  notify()
}

/** Vuelve a salir desde donde esté el dispositivo. Como `departFrom`, es un borrador. */
export function departFromDevice(): void {
  if (getDeparture().kind === 'device') return

  draft = DEVICE_DEPARTURE
  notify()
}

/**
 * La reserva se confirmó: lo elegido en el formulario pasa a ser el punto de partida del viaje.
 *
 * Sin borrador no hace nada, y es lo correcto: el conductor reservó sin tocar el campo, así que el
 * punto de partida es el que ya estaba.
 */
export function commitDeparture(): void {
  if (draft === null) return

  departure = draft
  draft = null
  persist()
  notify()
}

/** Se salió de la reserva sin confirmarla: lo elegido en el formulario se olvida. */
export function discardDepartureDraft(): void {
  if (draft === null) return

  draft = null
  notify()
}

export function useDeparture(): Departure {
  return useSyncExternalStore(subscribe, getDeparture, getDeparture)
}

export function useCommittedDeparture(): Departure {
  return useSyncExternalStore(subscribe, getCommittedDeparture, getCommittedDeparture)
}
