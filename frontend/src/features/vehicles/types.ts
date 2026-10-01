/**
 * Tipos de la feature Vehículos.
 *
 * Espejan los records del backend (`com.ecopedia.core.vehicle`). Los nombres de campo son los
 * que viajan en el JSON, así que van tal cual salen de Jackson.
 */

import type { ConnectorType } from '@/features/terminals/types'

/**
 * Un modelo del catálogo: la ficha técnica con la que el conductor identifica su auto.
 *
 * **Todo lo que hay acá es ficha de catálogo, no medición.** La distinción importa y es la que
 * separa lo que se puede mostrar de lo que no: la potencia del motor y el tipo de conector son
 * ciertos para cualquier unidad del modelo; la carga actual de la batería o la autonomía que
 * queda son lecturas del auto, y esta aplicación no habla con el auto. Por eso la referencia de
 * diseño muestra un 92% de batería y la portada no.
 *
 * **Los números vienen tipados y la ficha se DERIVA de ellos, no al lado.** La portada hace
 * cuentas con esto —compara el conector contra el de cada estación y estima cuánto tarda una
 * carga—, así que un texto con la unidad pegada (`'60 kWh'`) obligaría a parsear un dato que ya
 * estaba estructurado. Ver `specsOf` en `vehicle.ts`.
 *
 * `connectorType` es el MISMO tipo que el de los conectores de una estación, importado de
 * Terminales en vez de redeclarado: comparar los dos es todo el motivo por el que el catálogo
 * existe, y dos uniones con los mismos tres valores se separarían en el primer agregado.
 */
export interface VehicleModel {
  id: number
  brand: string
  name: string
  connectorType: ConnectorType
  /** Potencia del motor, en kW. Es dato de catálogo: no dice nada de la carga. */
  motorKw: number
  /**
   * Lo máximo que el auto acepta al cargar, en kW.
   *
   * NO es lo mismo que la potencia del cargador, y esa diferencia es justo lo que hace útil el
   * dato: en una estación de 150 kW un auto con tope de 80 igual carga a 80, así que el surtidor
   * más rápido no siempre le sirve de más.
   */
  maxChargeKw: number
  /** Capacidad utilizable de la batería, en kWh. */
  batteryKwh: number
  /**
   * Cómo se llama el archivo de la foto, sin carpeta ni extensión: `'tesla-model-3'`.
   *
   * Es el nombre y no la dirección, así que la carpeta y la extensión las pone `assets.ts`. Ver
   * `vehicleImageSrc`, y el comentario de la columna en el backend.
   */
  imageSlug: string
}
