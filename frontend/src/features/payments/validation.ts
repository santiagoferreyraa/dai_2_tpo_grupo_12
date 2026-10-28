import { BRAND_LABEL } from './format'
import type { CardBrand } from './types'

/**
 * Reglas de validación del formulario de alta de tarjeta.
 *
 * **Repiten a propósito lo que la pasarela ya valida**, igual que `features/auth/validation.ts`
 * repite lo que valida el backend. No lo reemplazan: quien decide si la tarjeta sirve es la
 * pasarela, y hay tests que lo demuestran. Lo que aportan es el momento —avisar al escribir en
 * vez de después de una ida y vuelta por la red— y que el mensaje quede pegado al campo que
 * está mal.
 *
 * Acá el argumento pesa más que en el login: mandar un número de tarjeta a validar del otro
 * lado es exponer el dato más sensible del formulario para descubrir que hay un dígito de más.
 * Cuanto más se resuelve sin que salga del navegador, mejor.
 *
 * Si el conjunto de marcas aceptadas cambia en la pasarela simulada (`simulation/payment-gateway`),
 * cambia acá también: son dos lugares, y este archivo es el único del frontend que las menciona.
 */

/** Solo los dígitos. Es como se guarda internamente, sin los espacios que se ven en pantalla. */
export function digitsOf(value: string): string {
  return value.replace(/\D/g, '')
}

/**
 * El algoritmo de Luhn, el mismo que corre el backend.
 *
 * Atrapa el error de tipeo y la transposición de dos dígitos, que es lo que de verdad pasa al
 * copiar un número a mano. No dice nada sobre si la tarjeta existe: eso solo lo sabe el emisor.
 */
function passesLuhn(digits: string): boolean {
  let sum = 0
  let doubling = false

  for (let position = digits.length - 1; position >= 0; position--) {
    let digit = Number(digits[position])

    if (doubling) {
      digit *= 2
      if (digit > 9) digit -= 9
    }

    sum += digit
    doubling = !doubling
  }

  return sum % 10 === 0
}

/**
 * Deduce la marca por el rango del número, como hace la pasarela.
 *
 * Sirve para dos cosas: rechazar en el formulario una marca que el sistema no sabe cobrar, y
 * mostrar el nombre de la marca mientras se escribe, que es la confirmación de que el número
 * se está entendiendo bien.
 */
export function brandOf(digits: string): CardBrand | undefined {
  if (/^4/.test(digits)) return 'VISA'
  if (/^3[47]/.test(digits)) return 'AMEX'
  if (/^5[1-5]/.test(digits)) return 'MASTERCARD'

  // El rango nuevo de Mastercard: 2221 a 2720. Hay que leer cuatro dígitos para saberlo.
  if (digits.length >= 4) {
    const firstFour = Number(digits.slice(0, 4))
    if (firstFour >= 2221 && firstFour <= 2720) return 'MASTERCARD'
  }

  return undefined
}

/**
 * Cuántos dígitos tiene el número de cada marca.
 *
 * Visa admite tres largos: los 13 de las tarjetas viejas, los 16 de casi todas, y los 19 de
 * algunas emisiones nuevas. Mastercard y AMEX tienen uno solo. Es lo que permite frenar el
 * tipeo en el largo correcto en vez de dejar escribir hasta el máximo teórico de cualquier
 * tarjeta: pasarse de largo en una Mastercard es el error que este dato evita.
 */
const BRAND_LENGTHS: Record<CardBrand, number[]> = {
  VISA: [13, 16, 19],
  MASTERCARD: [16],
  AMEX: [15],
}

/** El largo mayor que admite la marca; 19 mientras no se sepa cuál es. */
export function maxDigitsFor(brand: CardBrand | undefined): number {
  if (brand === undefined) return 19
  return Math.max(...BRAND_LENGTHS[brand])
}

export function validateCardNumber(value: string): string | undefined {
  const digits = digitsOf(value)

  if (digits === '') return 'Ingresá el número de la tarjeta.'
  if (digits.length < 13 || digits.length > 19)
    return 'El número tiene que tener entre 13 y 19 dígitos.'

  const brand = brandOf(digits)
  if (brand === undefined) return 'Por ahora solo aceptamos Visa, Mastercard y American Express.'

  /*
   * El largo se revisa contra la marca ANTES que el dígito verificador, y el orden importa.
   * Un número con dígitos de más falla Luhn igual, pero contestar "no es válido, revisá que
   * esté bien copiado" deja a quien lo escribió releyendo dieciséis dígitos que están todos
   * bien: lo que sobra es la cantidad, y eso hay que decirlo.
   */
  if (!BRAND_LENGTHS[brand].includes(digits.length)) {
    const expected = BRAND_LENGTHS[brand].join(' o ')
    return `Una ${BRAND_LABEL[brand]} tiene ${expected} dígitos, y escribiste ${digits.length}.`
  }

  if (!passesLuhn(digits)) return 'Ese número no es válido. Revisá que esté bien copiado.'

  return undefined
}

/**
 * El vencimiento se escribe MM/AA, que es como está impreso en la tarjeta.
 *
 * **Los dos dígitos del año se resuelven acá y una sola vez.** El backend pide cuatro a
 * propósito: con dos, "30" hay que interpretarlo, y la regla que lo interpreta deja de valer
 * sola en algún momento. La cuenta la hace el formulario, en {@link expiryYearOf}.
 */
export function validateExpiry(value: string): string | undefined {
  const digits = digitsOf(value)

  if (digits.length !== 4) return 'Ingresá el vencimiento como MM/AA.'

  const month = Number(digits.slice(0, 2))
  if (month < 1 || month > 12) return 'El mes tiene que estar entre 01 y 12.'

  const now = new Date()
  const expiry = new Date(expiryYearOf(digits), month, 0)

  /*
   * La tarjeta sirve TODO el mes impreso: una 09/26 vale hasta el 30 de septiembre. Por eso se
   * compara contra el último día de ese mes y no contra el primero.
   *
   * Esta es una comodidad, no la regla: quien decide es el servidor, con su propio reloj. Acá
   * se avisa temprano para no gastar una llamada con una tarjeta que ya sabemos vencida.
   */
  if (expiry < now) return 'Esa tarjeta ya venció.'

  return undefined
}

/** El mes del vencimiento, a partir de los cuatro dígitos MMAA. */
export function expiryMonthOf(digits: string): number {
  return Number(digits.slice(0, 2))
}

/**
 * El año de cuatro dígitos, a partir de los dos que se escribieron.
 *
 * Se resuelve contra el siglo en curso leído del reloj, no contra un "20" fijo: escrito a mano,
 * el año 2100 lo rompe. Nadie va a estar mirando este código en 2100, pero la línea que lo
 * resuelve bien cuesta lo mismo que la que lo rompe.
 */
export function expiryYearOf(digits: string): number {
  const century = Math.floor(new Date().getFullYear() / 100) * 100
  return century + Number(digits.slice(2, 4))
}

export function validateHolderName(value: string): string | undefined {
  if (value.trim() === '') return 'Ingresá el nombre como figura en la tarjeta.'
  return undefined
}
