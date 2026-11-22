/**
 * Tipos de la feature Payments.
 *
 * Espejan los records de `com.ecopedia.integration.payment`. Los nombres de campo son los que
 * viajan en el JSON, así que van tal cual salen de Jackson.
 */

/** Las marcas que la pasarela sabe cobrar. Coincide con el enum `CardBrand`. */
export type CardBrand = 'VISA' | 'MASTERCARD' | 'AMEX'

/**
 * Una tarjeta registrada, como la devuelve el backend.
 *
 * **No tiene el número ni el token de la pasarela, y no es que falten**: el backend no los
 * manda. El número no se guarda en ninguna parte (RF02) y el token es la credencial con la
 * que se cobra, así que no baja al navegador. Para eliminar alcanza el `id`.
 */
export interface PaymentMethod {
  id: number
  brand: CardBrand
  lastFour: string
  expiryMonth: number
  expiryYear: number
  label: string | null
  /** Lo decide el servidor, no la fecha de la máquina del usuario. */
  expired: boolean
}

/**
 * Una deuda del conductor: una carga que no se pudo cobrar al cerrarla (ECO-62). Espeja
 * `DebtResponse`.
 *
 * El estado viaja, pero la pantalla no lo muestra: "se sigue reintentando" o "la cuenta quedó
 * marcada" es lo que pasa por detrás, y al conductor le alcanza con qué debe y cómo pagarlo
 * (directiva 6). Tampoco viene el motivo del rechazo, que es texto de la pasarela.
 */
export interface Debt {
  id: number
  amount: number
  /** Cuándo se cerró la carga que no se pudo cobrar. */
  incurredAt: string
  status: 'PENDING' | 'DEFAULTED' | 'SETTLED'
  nextAttemptAt: string | null
}

/**
 * Lo que se manda para dar de alta una tarjeta.
 *
 * El conductor dueño NO va acá: sale del token. Si viajara en el cuerpo, cambiar un número en
 * la petición alcanzaría para registrarle una tarjeta a otro.
 */
export interface RegisterCardInput {
  number: string
  expiryMonth: number
  /** Cuatro dígitos. El formulario pide dos y hace la cuenta antes de enviar. */
  expiryYear: number
  holderName: string
  label?: string
}
