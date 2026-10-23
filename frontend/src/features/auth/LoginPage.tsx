import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'

import { ApiError } from '@/lib/api'

import AuthLayout from './components/AuthLayout'
import FormField from './components/FormField'
import { login } from './data/authRepository'
import { openSession } from './session'
import { validateEmail, validateRequiredPassword } from './validation'

/** Lo que contesta el backend cuando el email no existe o la contraseña no coincide. */
const INVALID_CREDENTIALS = 'Credenciales inválidas'

/**
 * Pantalla de login (RF01 / ECO-36).
 *
 * Al entrar abre la sesión y devuelve al usuario a donde quería ir. Ese destino lo deja el
 * guard en el estado de la navegación: si alguien pega la dirección del ABM sin sesión, entra
 * acá y termina en el ABM, no en la home. Sin eso, el redirect al login se lleva puesta la
 * intención original y hay que volver a navegar a mano.
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [attempted, setAttempted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  const state = location.state as { from?: string; notice?: string } | null
  const target = state?.from ?? '/'
  const notice = state?.notice

  const fieldErrors = {
    email: validateEmail(email),
    password: validateRequiredPassword(password),
  }
  const hasFieldErrors = Object.values(fieldErrors).some((message) => message !== undefined)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setAttempted(true)
    setError(null)
    if (hasFieldErrors) return

    setSending(true)
    try {
      openSession(await login({ email: email.trim(), password }))
      // `replace` para que el botón de atrás no vuelva al login ya resuelto.
      void navigate(target, { replace: true })
    } catch (cause) {
      setError(readLoginError(cause))
    } finally {
      setSending(false)
    }
  }

  return (
    <AuthLayout
      title="Iniciar sesión"
      subtitle="Entrá con tu cuenta de Ecopedia."
      footer={
        <>
          ¿No tenés cuenta?{' '}
          <Link className="text-primary font-medium hover:underline" to="/register">
            Registrate
          </Link>
        </>
      }
    >
      {notice !== undefined && (
        <p className="border-border bg-background text-text-muted mb-4 rounded-lg border px-3 py-2 text-sm">
          {notice}
        </p>
      )}

      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <FormField
          id="login-email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
          error={attempted ? fieldErrors.email : undefined}
          required
        />
        <FormField
          id="login-password"
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
          error={attempted ? fieldErrors.password : undefined}
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
          {sending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </AuthLayout>
  )
}

/**
 * Convierte lo que falló en el texto que ve el usuario.
 *
 * El backend contesta el mismo mensaje tanto si el mail no existe como si la contraseña no
 * coincide, y está bien que no distinga: decir cuál de las dos falló le confirma a un
 * desconocido qué direcciones están registradas. Acá se reemplaza por la frase que usa
 * cualquier login, porque "Credenciales inválidas" es vocabulario del sistema y no del que
 * está intentando entrar. Una cuenta dada de baja también recibe ese mensaje, por lo mismo.
 * Cualquier otro motivo —el bloqueo por intentos fallidos, por ejemplo— se muestra tal cual
 * viene: es información que el usuario necesita y que no tiene cómo deducir.
 */
function readLoginError(cause: unknown): string {
  if (!(cause instanceof ApiError)) return 'No se pudo iniciar sesión'
  if (cause.message === INVALID_CREDENTIALS) return 'El email o la contraseña no son correctos.'
  return cause.message
}
