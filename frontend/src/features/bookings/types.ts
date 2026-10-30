import type { ConnectorType } from '@/features/terminals/types'

/**
 * Tipos de la feature Reservas.
 *
 * Hay dos juegos, separados a propósito:
 *
 * - **Los del contrato**, que espejan los DTO de `ecopedia-charging`
 *   (com.ecopedia.charging.booking.web.dto). Las fechas viajan como texto ISO-8601 porque eso es
 *   lo que manda Jackson, y los nombres de campo son los del JSON tal cual.
 * - **Los de la pantalla**, con las fechas ya convertidas a `Date` y la estación resuelta. Son los
 *   únicos que salen del repositorio: ningún componente ve un texto ISO ni tiene que buscar a qué
 *   estación pertenece un conector.
 */

/*
 * ---------------------------------------------------------------------------
 * Contrato REST
 * ---------------------------------------------------------------------------
 */

/** Equivale al enum BookingStatus. La incomparecencia y el consumo (RF09, RF11) todavía no existen. */
export type BookingStatus = 'CONFIRMED' | 'CANCELLED'

/** Lo que se manda para retener un slot. Equivale a HoldRequest. */
export interface HoldRequest {
  connectorId: number
  start: string
  end: string
}

/** Una retención como la devuelve el backend. Equivale a HoldResponse. */
export interface HoldResponse {
  /** UUID: la retención vive en memoria del componente y no tiene id de base. */
  id: string
  connectorId: number
  start: string
  end: string
  expiresAt: string
}

/** Un hueco libre de un conector, `[start, end)`. Equivale a FreeWindowResponse (ECO-33). */
export interface FreeWindowResponse {
  start: string
  end: string
}

/** Una reserva como la devuelve el backend. Equivale a BookingResponse. */
export interface BookingResponse {
  id: number
  connectorId: number
  start: string
  end: string
  status: BookingStatus
  createdAt: string
}

/*
 * ---------------------------------------------------------------------------
 * Tipos de la pantalla
 * ---------------------------------------------------------------------------
 */

/**
 * Una retención vigente: el slot está tomado mientras el conductor confirma.
 *
 * `expiresAt` es lo que importa para la pantalla de confirmar: pasado ese instante el backend
 * contesta 410 y hay que volver a elegir el horario.
 */
export interface Hold {
  id: string
  connectorId: number
  start: Date
  end: Date
  expiresAt: Date
}

/**
 * Dónde queda el conector reservado.
 *
 * Es `null` en `Booking.location` cuando la estación no aparece en la red: la dieron de baja
 * después de la reserva, o el listado no se pudo traer. La reserva sigue existiendo igual y hay
 * que poder mostrarla y cancelarla, así que su ausencia no puede romper la pantalla.
 */
export interface BookingLocation {
  stationId: number
  stationName: string
  address: string
  latitude: number
  longitude: number
  connectorType: ConnectorType
  maxPowerKw: number
}

/** Una reserva lista para mostrar. */
export interface Booking {
  id: number
  connectorId: number
  start: Date
  end: Date
  status: BookingStatus
  createdAt: Date
  location: BookingLocation | null
  /** La seña cobrada al confirmar, en pesos. Ver `charges.ts`. */
  deposit: number
  /**
   * Lo que costó la carga, en pesos. `null` mientras no se sepa: la reserva no se usó todavía, o
   * el consumo (RF11) no está implementado y no hay de dónde sacarlo.
   */
  chargingCost: number | null
}

/** Un hueco libre de un conector, con fechas de verdad. Semiabierto, como en el backend. */
export interface FreeWindow {
  start: Date
  end: Date
}
