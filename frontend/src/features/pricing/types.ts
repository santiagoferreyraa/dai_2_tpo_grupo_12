/**
 * Tipos de Tarificación, con el modelo del 31/10: los precios de la carga son de toda la red y los
 * fija el ADMIN; la seña y la hora pico son de cada estación y las fija su CPO. Espejan los DTO de
 * `com.ecopedia.core.pricing.web.dto`.
 */

/** Los precios de la red y las reglas con que se aplican. Equivale a NetworkRatesResponse. */
export interface NetworkRates {
  normalKwhRate: number
  fastKwhRate: number
  excessPenaltyPerMin: number
  /** Hasta cuántos kW una carga es normal. */
  fastChargeThresholdKw: number
  /** La franja pico, `[desde, hasta)`, sobre la hora de inicio de la carga. */
  peakStartHour: number
  peakEndHour: number
  peakMultiplier: number
  penaltyEscalationAfterMinutes: number
  penaltyEscalationMultiplier: number
}

/** Lo que se manda para cambiar los precios de la red. Equivale a NetworkRatesRequest. */
export interface NetworkRatesInput {
  normalKwhRate: number
  fastKwhRate: number
  excessPenaltyPerMin: number
}

/** La seña y la hora pico de una estación. Equivale a StationPricingResponse. */
export interface StationPricing {
  stationId: number
  depositAmount: number
  peakPricingEnabled: boolean
}

/** Lo que se manda para cambiarlas. Equivale a StationPricingRequest. */
export interface StationPricingInput {
  depositAmount: number
  peakPricingEnabled: boolean
}
