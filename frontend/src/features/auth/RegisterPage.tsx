import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { errorMessage } from '@/lib/errorMessage'

import AuthLayout from './components/AuthLayout'
import FormField from './components/FormField'
import { register } from './data/authRepository'
import {
  MINIMUM_PASSWORD_LENGTH,
  validateEmail,
  validateNewPassword,
  validatePasswordConfirmation,
} from './validation'

/**
 * Pantalla de registro (RF01 / ECO-36).
 *
 * **Al terminar manda al login, no deja la sesión abierta.** El backend responde 201 con el
 * perfil y sin token, así que entrar solo sería posible encadenando un login por detrás. Se
 * decidió no hacerlo: el usuario estrena la contraseña que acaba de elegir, y el registro
 * queda como una operación que crea la cuenta y nada más.
 *
 * El rol tampoco se pide. Todo el que se registra nace conductor; elevar a operador o
 * administrador es una operación administrativa, no una casilla de este formulario.
 *
 * **La confirmación de contraseña no viaja al backend**, y no es un olvido: no hay nada que
 * verificar del otro lado, porque el servidor recibe una sola contraseña. Existe para atajar
 * el error de tipeo en un campo que se escribe a ciegas —si se cuela, el usuario queda con una
 * cuenta cuya clave no conoce y el único camino de vuelta es registrarse de nuevo—.
 */
export default function RegisterPage() {
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [attempted, setAttempted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  const fieldErrors = {
    fullName: fullName.trim() === '' ? 'Ingresá tu nombre completo.' : undefined,
    email: validateEmail(email),
    password: validateNewPassword(password),
    confirmation: validatePasswordConfirmation(password, confirmation),
  }
  const hasFieldErrors = Object.values(fieldErrors).some((message) => message !== undefined)

  /*
   * Los errores aparecen recién al intentar enviar, para no ir señalando en rojo campos que el
   * usuario todavía no terminó de escribir. La confirmación es la excepción: en cuanto hay algo
   * escrito ahí, avisar que no coincide es útil de inmediato —es el momento en que se puede
   * corregir mirando lo que se acaba de tipear, y no dos campos después—.
   */
  function errorOf(field: keyof typeof fieldErrors): string | undefined {
    if (attempted) return fieldErrors[field]
    if (field === 'confirmation' && confirmation !== '') return fieldErrors.confirmation
    return undefined
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setAttempted(true)
    setError(null)
    if (hasFieldErrors) return

    setSending(true)
    try {
      await register({ email: email.trim(), password, fullName: fullName.trim() })
      /*
       * El mensaje viaja en el estado de la navegación y no como un cartel de esta pantalla,
       * porque esta pantalla se desmonta al navegar. Sin eso, el usuario aterriza en un login
       * sin ninguna señal de que el alta salió bien.
       */
      void navigate('/login', {
        replace: true,
        state: { notice: 'Tu cuenta se creó. Entrá con tu email y contraseña.' },
      })
    } catch (cause) {
      setError(
        errorMessage(cause, 'No se pudo crear la cuenta.', {
          byCode: {
            EMAIL_ALREADY_REGISTERED: 'Ya hay una cuenta con ese email. Iniciá sesión o usá otro.',
          },
        }),
      )
    } finally {
      setSending(false)
    }
  }

  return (
    <AuthLayout
      title="Crear cuenta"
      subtitle="Registrate para reservar y cargar."
      footer={
        <>
          ¿Ya tenés cuenta?{' '}
          <Link className="text-primary font-medium hover:underline" to="/login">
            Iniciá sesión
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <FormField
          id="register-name"
          label="Nombre completo"
          type="text"
          autoComplete="name"
          value={fullName}
          onChange={setFullName}
          error={errorOf('fullName')}
          required
        />
        <FormField
          id="register-email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
          error={errorOf('email')}
          required
        />
        <FormField
          id="register-password"
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={setPassword}
          hint={`Al menos ${MINIMUM_PASSWORD_LENGTH} caracteres.`}
          error={errorOf('password')}
          required
        />
        <FormField
          id="register-password-confirmation"
          label="Repetir contraseña"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={setConfirmation}
          error={errorOf('confirmation')}
          required
        />

        {error !== null && (
          <p className="text-sm text-red-600" role="alert">
            {error}
          </p>
        )}

        <button
          className="brand-fill text-on-primary rounded-lg px-4 py-2.5 text-sm font-semibold disabled:opacity-60"
          type="submit"
          disabled={sending}
        >
          {sending ? 'Creando…' : 'Crear cuenta'}
        </button>
      </form>
    </AuthLayout>
  )
}
