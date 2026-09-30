/**
 * El auto del conductor: de dónde sale y cómo se muestra su ficha.
 *
 * **Ya no es una constante escrita a mano.** Hasta que existió el catálogo, este archivo vivía en
 * `home/` y exportaba un `DRIVER_VEHICLE` de ejemplo, porque el sistema no tenía concepto de
 * vehículo: ni entidad, ni endpoint, ni pantalla para elegirlo. Ahora el conductor lo elige de
 * `GET /api/vehicle-models` y el elegido viaja en su perfil, así que lo que era un objeto fijo es
 * un dato de la sesión.
 *
 * **Y por eso se mudó de `home/` a `vehicles/`.** Cuando era un ejemplo lo usaba solo la portada;
 * ahora lo muestran también el perfil y su formulario de edición, y un archivo que tres features
 * leen no puede seguir viviendo adentro de una de ellas.
 */

import { useSession } from '@/features/auth/session'
import { CONNECTOR_TYPE_LABEL } from '@/features/terminals/format'

import type { VehicleModel } from './types'

/**
 * El auto del conductor logueado, o `null`.
 *
 * **`null` cubre dos casos y las pantallas los distinguen por la sesión, no por acá**: no hay
 * nadie logueado, o hay alguien que todavía no eligió su auto. Los dos significan "no hay ficha
 * que mostrar", pero uno se resuelve entrando y el otro eligiendo, así que cada recuadro mira
 * también la sesión antes de decidir qué ofrecer.
 *
 * Sale de la sesión y no de una llamada propia: el perfil ya la trae —ver `hydrateProfile` en
 * `auth/session.ts`—, y pedirla de nuevo en cada recuadro de la portada serían tres llamadas para
 * el mismo dato que está en memoria.
 */
export function useDriverVehicle(): VehicleModel | null {
  return useSession()?.vehicle ?? null
}

/**
 * Cuál de los cuatro dibujos acompaña al dato.
 *
 * Va como NOMBRE y no como componente porque este archivo es de datos y no dibuja nada: metiendo
 * un componente acá habría que convertirlo en `.tsx` y pasaría a depender de la biblioteca de
 * íconos. Quien lo muestra traduce el nombre.
 */
export type SpecIcon = 'motor' | 'connector' | 'power' | 'battery'

export interface VehicleSpec {
  label: string
  value: string
  icon: SpecIcon
}

/**
 * Cómo se lee el conector de un auto.
 *
 * **Sale de `terminals/format` y no de una tabla propia**, aunque una copia de tres renglones
 * sería más corta que este comentario: es la misma etiqueta que muestra cada conector en el mapa
 * y en el ABM, y si acá dijera "Tipo 2" mientras el mapa dice "TYPE 2", el conductor tendría que
 * deducir que son lo mismo para creerle al recuadro de compatibilidad.
 */
export function connectorLabelOf(vehicle: VehicleModel): string {
  return CONNECTOR_TYPE_LABEL[vehicle.connectorType]
}

/**
 * La ficha que se muestra en la barra de vidrio, debajo del auto.
 *
 * Se arma a partir del vehículo en vez de escribirse aparte: así no hay forma de que el número de
 * la ficha y el que usa la cuenta se separen con el tiempo.
 */
export function specsOf(vehicle: VehicleModel): VehicleSpec[] {
  return [
    { label: 'Motor', value: `${String(vehicle.motorKw)} kW`, icon: 'motor' },
    { label: 'Conector', value: connectorLabelOf(vehicle), icon: 'connector' },
    { label: 'Carga máx.', value: `${String(vehicle.maxChargeKw)} kW`, icon: 'power' },
    { label: 'Batería', value: `${String(vehicle.batteryKwh)} kWh`, icon: 'battery' },
  ]
}

/**
 * Cómo se nombra un auto en un renglón: "Tesla Model 3".
 *
 * Existe para que la ficha del perfil y el selector no escriban cada uno su propia
 * concatenación —una con guion, otra con coma— para el mismo dato.
 */
export function vehicleLabelOf(vehicle: VehicleModel): string {
  return `${vehicle.brand} ${vehicle.name}`
}

/**
 * Dónde vive la imagen del auto. Si el archivo no está, el recuadro se dibuja igual, sin auto.
 *
 * **Es una sola para todos los modelos, y es una limitación conocida.** El dibujo de
 * `public/car.png` es genérico y no corresponde a ninguna marca real; el catálogo, en cambio, sí
 * tiene marcas reales. Mostrarlo para cualquier modelo es lo honesto que se puede hacer con un
 * solo dibujo: la alternativa —asignarle este dibujo a un fabricante concreto— le atribuiría a
 * una empresa un auto que no hizo. El día que haya una imagen por modelo, la columna va en
 * `vehicle_models` y esta constante pasa a ser un campo más de la ficha.
 */
export const VEHICLE_IMAGE = '/car.png'
