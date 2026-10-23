import { useSyncExternalStore } from 'react'

import { setToken } from '@/lib/api'

import { fetchMyProfile } from './data/userRepository'
import type { AuthResponse, Session, UserProfile } from './types'

/**
 * La sesión del usuario: dónde vive, cómo sobrevive a un F5 y quién se entera cuando cambia.
 *
 * **Por qué es un módulo y no un contexto de React.** El cliente HTTP tiene que poder cerrar
 * la sesión cuando el backend rechaza un token vencido, y eso pasa fuera de todo componente:
 * un contexto no se puede leer desde ahí. Así que el estado vive en el módulo, React lo lee
 * con `useSyncExternalStore` y el cliente HTTP lo cierra llamando a una función. La pantalla
 * se entera igual, por la suscripción.
 *
 * **Un solo lugar arma la cabecera `Authorization`.** Este archivo es el único que llama a
 * `setToken`, igual que `api.ts` es el único que escribe la dirección de la API.
 */

const STORAGE_KEY = 'ecopedia.session'

let session: Session | null = null
const listeners = new Set<() => void>()

/**
 * Convierte la respuesta del login en la sesión que se guarda.
 *
 * El backend manda `expiresInSeconds` —una duración— y acá se resuelve a un instante. Es la
 * diferencia entre poder y no poder contestar "¿este token que estaba en el navegador desde
 * anteayer sigue sirviendo?".
 */
function toSession(auth: AuthResponse): Session {
  return {
    token: auth.token,
    userId: auth.userId,
    email: auth.email,
    role: auth.role,
    expiresAt: Date.now() + auth.expiresInSeconds * 1000,
    /* El login no manda ni el nombre ni el auto. Los trae `hydrateProfile` un instante después. */
    fullName: null,
    vehicle: null,
  }
}

function isExpired(candidate: Session): boolean {
  return candidate.expiresAt <= Date.now()
}

function notify(): void {
  for (const listener of listeners) listener()
}

function persist(): void {
  if (session === null) return
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Sin persistencia la sesión vive igual, solo que no sobrevive a un F5.
  }
}

/**
 * Pregunta el perfil real —el nombre y el auto— y lo guarda en la sesión.
 *
 * **Por qué hace falta.** `AuthResponse` trae token, id, correo y rol, pero ni el nombre ni el
 * vehículo: ésos viven en `UserProfileResponse`, que es otra llamada. Sin esto, las tres
 * pantallas que saludan al usuario tienen que inventar el nombre a partir del correo —y
 * `j.perez@…` da "J.perez"—, y la portada no tendría con qué contar estaciones compatibles.
 *
 * **Por qué no se espera.** Nada de lo que hay en pantalla depende de esto: el saludo se dibuja
 * con el nombre provisorio y los recuadros del auto tienen su cara de "todavía no elegiste
 * vehículo", que es la misma que ve quien de verdad no eligió ninguno. Bloquear el login contra
 * una segunda llamada haría más lento entrar. Cuando la respuesta llega, la suscripción vuelve a
 * renderizar y las dos cosas se corrigen solas.
 *
 * **Si falla, no pasa nada.** Puede fallar por red o porque el token no sirve —y de lo segundo
 * ya se ocupa `clearSessionIfExpired` desde el cliente HTTP—. Quedarse sin el nombre real no es
 * motivo para cerrarle la sesión a nadie: se sigue con el provisorio.
 */
function hydrateProfile(): void {
  const opened = session
  if (opened === null) return

  void fetchMyProfile()
    .then((profile) => {
      /*
        La sesión pudo haberse cerrado o cambiado mientras viajaba la respuesta —alguien salió,
        o entró con otra cuenta—. Escribirle el nombre a la sesión de al lado sería peor que no
        tenerlo, así que se compara el token antes de tocar nada.
      */
      if (session === null || session.token !== opened.token) return
      session = { ...session, fullName: profile.fullName, vehicle: profile.vehicle }
      persist()
      notify()
    })
    .catch(() => {
      // Se sigue con el nombre derivado del correo. Ver el comentario de arriba.
    })
}

/**
 * Guarda en la sesión el perfil que acaba de quedar en la base.
 *
 * La usa la pantalla de perfil después de editarlo. **Es lo mismo que hace `hydrateProfile`, sin
 * la llamada**: el perfil nuevo ya volvió en la respuesta del guardado, así que volver a
 * preguntarlo sería un viaje de ida y vuelta para traer lo que está acá al lado.
 *
 * Sin esto, lo cambiado se vería solo en el perfil: la ficha de la franja de arriba, el saludo y
 * los recuadros del auto leen de la sesión, y seguirían mostrando lo anterior hasta recargar.
 * Con el vehículo se nota más que con el nombre: cambiar de auto cambia el conteo de estaciones
 * compatibles de la portada entera.
 */
export function applyProfile(profile: UserProfile): void {
  if (session === null) return
  session = { ...session, fullName: profile.fullName, vehicle: profile.vehicle }
  persist()
  notify()
}

/**
 * Lee la sesión guardada y la deja lista para usar. Se llama una sola vez, al arrancar.
 *
 * Se hace antes del primer render a propósito: si se hiciera dentro de un efecto, la primera
 * pasada del guard vería "sin sesión" y mandaría al login a alguien que sí la tenía.
 *
 * Un token vencido se descarta acá mismo. No tiene sentido rehidratarlo para que el backend
 * lo rechace en la primera llamada y recién ahí cerrar la sesión.
 */
export function restoreSession(): void {
  let stored: string | null = null
  try {
    stored = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    // Modo incógnito o almacenamiento bloqueado: se sigue sin sesión persistida.
    return
  }
  if (stored === null) return

  try {
    const candidate = JSON.parse(stored) as Session
    if (isExpired(candidate)) {
      clearSession()
      return
    }
    session = candidate
    setToken(candidate.token)
    /*
      Se vuelve a pedir aunque lo guardado ya traiga nombre y auto: el perfil pudo cambiar desde
      la última vez —desde otra máquina, incluso—, y una sesión de un formato viejo no los tiene.
    */
    hydrateProfile()
  } catch {
    // Lo guardado no es una sesión válida (versión vieja del formato, o basura). Se descarta.
    clearSession()
  }
}

/** Abre la sesión con lo que devolvió el login: la guarda, la persiste y la publica. */
export function openSession(auth: AuthResponse): Session {
  session = toSession(auth)
  setToken(session.token)
  persist()
  notify()
  hydrateProfile()
  return session
}

/** Cierra la sesión: la borra de memoria, del almacenamiento y del cliente HTTP. */
export function clearSession(): void {
  session = null
  setToken(null)
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nada que limpiar si el almacenamiento no está disponible.
  }
  notify()
}

/** La sesión actual, o `null`. Para código que no es un componente. */
export function getSession(): Session | null {
  return session
}

/**
 * Cierra la sesión solo si el token que tenemos ya venció.
 *
 * La usa el cliente HTTP cuando el backend rechaza una llamada, y la condición no es
 * cosmética: **un 403 no distingue "token vencido" de "rol equivocado"**, porque la cadena de
 * seguridad deja pasar todas las URL y quien rechaza es la anotación sobre el método, que
 * responde igual para un anónimo que para un usuario sin permiso. Si cerráramos la sesión ante
 * cualquier 403, un conductor que toca una pantalla de operador quedaría deslogueado sin
 * motivo. Mirando el vencimiento se separan los dos casos sin preguntarle nada al backend.
 */
export function clearSessionIfExpired(): void {
  if (session !== null && isExpired(session)) clearSession()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot(): Session | null {
  return session
}

/** La sesión actual, para componentes. Se vuelve a renderizar cuando cambia. */
export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}
