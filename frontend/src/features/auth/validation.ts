/**
 * Reglas de validación de los formularios de sesión.
 *
 * **Repiten a propósito lo que el backend ya valida.** No lo reemplazan: el backend sigue
 * siendo el que decide, y hay tests que lo demuestran. Lo que aportan es el momento — avisar
 * al escribir en vez de después de una ida y vuelta por la red— y que el mensaje quede pegado
 * al campo que está mal. Si el mínimo de la contraseña cambia en `RegisterRequest`, cambia acá
 * también: son dos lugares y este archivo es el único del frontend que lo menciona.
 */

/** Mínimo que exige `@Size` en `RegisterRequest`. */
export const MINIMUM_PASSWORD_LENGTH = 8

/*
 * Alcanza con exigir algo@algo.dominio. Validar direcciones con una expresión exhaustiva es un
 * problema conocido por no tener buena solución, y de más rechaza direcciones legítimas; el
 * formato real lo confirma el backend con `@Email`.
 */
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(email: string): string | undefined {
  if (email.trim() === '') return 'Ingresá tu email.'
  if (!EMAIL_SHAPE.test(email.trim())) return 'Ingresá un email válido, como nombre@dominio.com.'
  return undefined
}

/** Contraseña de login: solo se exige que esté. El largo lo juzga el backend al comparar. */
export function validateRequiredPassword(password: string): string | undefined {
  if (password === '') return 'Ingresá tu contraseña.'
  return undefined
}

/** Contraseña de registro: acá sí corre el mínimo, que es la regla que el alta va a aplicar. */
export function validateNewPassword(password: string): string | undefined {
  if (password === '') return 'Elegí una contraseña.'
  if (password.length < MINIMUM_PASSWORD_LENGTH) {
    return `La contraseña debe tener al menos ${MINIMUM_PASSWORD_LENGTH} caracteres.`
  }
  return undefined
}

export function validatePasswordConfirmation(
  password: string,
  confirmation: string,
): string | undefined {
  if (confirmation === '') return 'Repetí la contraseña.'
  if (confirmation !== password) return 'Las contraseñas no coinciden.'
  return undefined
}
