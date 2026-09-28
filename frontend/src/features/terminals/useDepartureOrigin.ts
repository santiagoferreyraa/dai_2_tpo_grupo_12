/**
 * El punto de partida ya resuelto: de dónde sale el recorrido, venga de donde venga.
 *
 * Es la única pieza que sabe que hay dos orígenes posibles. Arriba de acá —el mapa, el panel de
 * detalle, `useRoute`— todo habla de "el origen" y no le importa si lo dijo el GPS o si lo escribió
 * el conductor; abajo, `useDeviceLocation` y `departure` no saben el uno del otro. Sin este hook
 * en el medio, cada pantalla que quisiera trazar una ruta tendría que rehacer la elección, y la
 * primera que se olvidara del caso de la dirección volvería a rutear desde el GPS sin avisar.
 *
 * La ubicación del dispositivo se sigue pidiendo aunque haya una dirección elegida, y no es un
 * descuido: el punto azul del mapa es "estás acá" y eso no deja de ser cierto porque el recorrido
 * arranque en otro lado. Además el campo de la reserva la usa para inclinar la búsqueda de
 * direcciones hacia donde está el conductor. El costo es cero: `useDeviceLocation` comparte una
 * sola suscripción entre todos los que preguntan.
 */

import { useMemo } from 'react'

import { useDeparture, type Departure } from './departure'
import { useDeviceLocation, type DeviceLocationState } from './useDeviceLocation'
import type { RouteOrigin } from './useRoute'

/**
 * El origen, con la marca de de dónde salió.
 *
 * `kind` es para el mapa y nada más: un punto elegido a mano no se dibuja con el disco que late,
 * porque ese dibujo significa "el navegador te está midiendo AHORA" y sobre una dirección escrita
 * sería mentira. Para `useRoute`, en cambio, los dos son exactamente lo mismo —un `RouteOrigin`—,
 * que es lo que deja que el recorrido no tenga que enterarse de nada de esto.
 */
export interface DeparturePoint extends RouteOrigin {
  kind: Departure['kind']
}

export interface DepartureOriginState {
  /** Qué eligió el conductor. Ver `departure`. */
  departure: Departure
  /**
   * Desde dónde se calcula el recorrido, o `null` si todavía no se sabe.
   *
   * Con una dirección elegida nunca es `null`: el punto ya está. Con el dispositivo puede serlo
   * —mientras el GPS busca, sin permiso, o fuera del país—, y ahí el recorrido no se dibuja y el
   * campo de la reserva es el lugar donde se puede hacer algo al respecto.
   */
  origin: DeparturePoint | null
  /**
   * Dónde está el dispositivo AHORA, se esté usando como punto de partida o no.
   *
   * Es el mismo punto que `origin` cuando no hay dirección elegida, y otro cuando la hay. Existe
   * aparte por dos motivos que aparecieron juntos: el mapa dibuja el "estás acá" aunque el
   * recorrido salga de otro lado, y hay recorridos que tienen que salir de acá y no del punto de
   * partida elegido —el de una estación que no es la reservada, ver `StationsMapPage`—.
   *
   * `null` con el mismo significado que en `origin`: el navegador todavía no contestó, no dio
   * permiso, o la ubicación cayó fuera del país.
   */
  deviceOrigin: DeparturePoint | null
  /**
   * Cómo viene la ubicación del dispositivo, se esté usando o no.
   *
   * Viaja aparte de `origin` porque con una dirección elegida el origen no dice nada del GPS, y
   * sin embargo el punto azul del mapa se sigue dibujando con esto.
   */
  device: DeviceLocationState
}

export function useDepartureOrigin(): DepartureOriginState {
  const departure = useDeparture()
  const device = useDeviceLocation()

  /*
   * El objeto se memoriza porque es lo que termina entrando a `useRoute`, y ahí adentro hay un
   * umbral de movimiento que decide si vale la pena volver a consultar OSRM. Uno nuevo por render
   * no rompe nada —el umbral compara números, no identidades— pero sí obliga a recorrer ese efecto
   * en cada repintado de la pantalla, que con el mapa arrastrándose son muchos.
   */
  const deviceOrigin = useMemo<DeparturePoint | null>(() => {
    if (device.location === null) return null

    const { latitude, longitude, accuracyM } = device.location
    return { kind: 'device', latitude, longitude, accuracyM }
  }, [device.location])

  const origin = useMemo<DeparturePoint | null>(() => {
    if (departure.kind === 'address') {
      const { latitude, longitude, accuracyM } = departure.address
      return { kind: 'address', latitude, longitude, accuracyM }
    }

    return deviceOrigin
  }, [departure, deviceOrigin])

  return { departure, origin, deviceOrigin, device }
}
