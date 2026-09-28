/**
 * Las direcciones que coinciden con lo que se está escribiendo.
 *
 * Es el pegamento entre un campo de texto, que cambia con cada tecla, y `searchAddresses`, que
 * sale a la red. Todo lo que hace es decidir CUÁNDO se consulta, que es lo único que separa un
 * autocompletado usable de un abuso de un servicio gratuito: escribir "Avenida Corrientes 348" son
 * veintiún pulsaciones, y sin nada en el medio serían veintiuna consultas para una sola respuesta
 * que sirva.
 *
 * Tres frenos, cada uno tapando un agujero distinto:
 *
 * - El **largo mínimo**, para no preguntar lo que no se puede contestar. Con una o dos letras no
 *   hay coincidencia que valga; con tres ya hay algo que buscar.
 * - La **espera**, para preguntar una vez por pausa y no una por tecla. Es el freno que hace casi
 *   todo el trabajo.
 * - La **cancelación**, para que la respuesta vieja no pise a la nueva. Sin esto, una consulta
 *   lenta seguida de una rápida deja en pantalla las coincidencias del texto anterior, que es un
 *   error que encima se ve como si la lista estuviera bien.
 *
 * Lo que este hook NO hace es elegir. La dirección queda elegida cuando alguien toca una de estas
 * sugerencias, y hasta entonces el texto escrito no vale como punto de partida: ver
 * `DepartureField`, que es donde está esa regla y por qué.
 */

import { useEffect, useState } from 'react'

import { searchAddresses, type AddressSuggestion } from './geocoding'

/**
 * Cuánto se espera sin teclear antes de consultar.
 *
 * 350 ms es el hueco entre dos teclas de alguien escribiendo de corrido: por debajo se consulta a
 * mitad de palabra, y por encima la lista llega después de que el ojo ya la estaba buscando.
 */
const DEBOUNCE_MS = 350

/**
 * Menos que esto no se consulta.
 *
 * Tres, y no dos, porque con dos letras Photon contesta cualquier cosa de todo el país: lo que
 * devuelve no es una ayuda para elegir, es ruido que encima cuesta una consulta.
 */
const MIN_QUERY_LENGTH = 3

export type AddressSearchStatus =
  /** Todavía no hay suficiente texto como para buscar nada. */
  | 'idle'
  | 'searching'
  | 'ready'
  /** El buscador de direcciones no contestó. */
  | 'unavailable'

export interface AddressSearchState {
  status: AddressSearchStatus
  /** Solo con `status === 'ready'`. Puede estar vacío: buscó y no encontró nada. */
  results: AddressSuggestion[]
}

const IDLE: AddressSearchState = { status: 'idle', results: [] }

/**
 * Busca `query` y devuelve lo que coincide, inclinado hacia `near` si se sabe dónde está el
 * conductor.
 *
 * `near` se pasa como objeto porque es lo que tiene a mano quien llama —la ubicación del
 * dispositivo—, pero adentro el efecto depende de sus NÚMEROS y no de su identidad: el GPS entrega
 * un objeto nuevo en cada arreglo, y una dependencia por identidad volvería a consultar con cada
 * uno aunque el texto no se haya tocado. Es el mismo cuidado que se toma `useRoute`.
 */
export function useAddressSearch(
  query: string,
  near: { latitude: number; longitude: number } | null,
): AddressSearchState {
  const [state, setState] = useState<AddressSearchState>(IDLE)

  const trimmed = query.trim()
  const enough = trimmed.length >= MIN_QUERY_LENGTH

  const nearLat = near?.latitude ?? null
  const nearLon = near?.longitude ?? null

  useEffect(() => {
    if (!enough) {
      /* eslint-disable-next-line react/set-state-in-effect -- sincroniza con la red; ver abajo. */
      setState(IDLE)
      return
    }

    /*
     * El "buscando" se pone acá y no adentro del temporizador, así que aparece apenas se deja de
     * escribir y no 350 ms después. Sin eso, la lista anterior se queda quieta durante la espera y
     * se lee como si el campo hubiera dejado de responder.
     *
     * El `setState` adentro de un efecto es el caso para el que existen los efectos: lo que
     * dispara el cambio no es un evento de la interfaz sino una respuesta de la red.
     */
    /* eslint-disable-next-line react/set-state-in-effect -- sincroniza con la red; ver arriba. */
    setState((current) => ({ status: 'searching', results: current.results }))

    const controller = new AbortController()
    const timer = setTimeout(() => {
      const bias =
        nearLat === null || nearLon === null ? null : { latitude: nearLat, longitude: nearLon }

      searchAddresses(trimmed, bias, controller.signal)
        .then((results) => setState({ status: 'ready', results }))
        .catch(() => {
          /*
           * Cancelar hace que `fetch` rechace, y ese rechazo no es una falla: quien canceló ya
           * pidió otra cosa, y marcar un error acá haría parpadear el aviso entre una tecla y la
           * siguiente.
           */
          if (controller.signal.aborted) return

          /*
           * Todos los motivos caen en el mismo estado, y a propósito: no hay nada que el
           * conductor pueda hacer distinto según si el servicio está caído o si contestó algo
           * ilegible. La lista anterior SÍ se borra: dejarla a la vista la haría pasar por las
           * coincidencias de este texto.
           */
          setState({ status: 'unavailable', results: [] })
        })
    }, DEBOUNCE_MS)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [trimmed, enough, nearLat, nearLon])

  return state
}
