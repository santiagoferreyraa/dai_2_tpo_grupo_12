/**
 * Cliente HTTP de la API de Ecopedia.
 *
 * Todas las llamadas del frontend pasan por acá. No es para ahorrar tipeo: es para que
 * haya dos lugares únicos en vez de veinte —la dirección de la API y el token de sesión—,
 * de modo que mudar de ambiente o agregar autenticación sea tocar un solo archivo.
 *
 * Las rutas se escriben relativas y SIN el prefijo `/api`, que lo agrega el cliente:
 *
 *     const terminals = await api.get<Terminal[]>('/terminals', { params: { city } })
 *
 * Ver el README del repo, "Direcciones y configuración".
 */

/** Prefijo que el proxy de Vite redirige al backend. Ver `vite.config.ts`. */
const ROOT = '/api'

/**
 * Error de una llamada a la API.
 *
 * `status` es el código HTTP, o `0` cuando la petición no llegó a salir (backend caído,
 * sin conexión, petición cancelada).
 */
export class ApiError extends Error {
  readonly status: number
  readonly detail: unknown

  constructor(message: string, status: number, detail?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
  }
}

let sessionToken: string | null = null

/**
 * Guarda el token que viaja en cada llamada; con `null` cierra la sesión.
 *
 * Lo va a llamar el login cuando exista. Es el único lugar del frontend que arma la
 * cabecera `Authorization`: si se agrega a mano en cada pantalla, siempre falta en alguna.
 */
export function setToken(token: string | null): void {
  sessionToken = token
}

let rejectionHandler: (() => void) | null = null

/**
 * Registra qué hacer cuando el backend rechaza una llamada por autenticación (401 o 403).
 *
 * Existe para no invertir la dependencia: este archivo no sabe qué es una sesión ni dónde se
 * guarda —si importara el módulo de sesión, que a su vez importa `setToken` de acá, quedaría
 * un ciclo—. El módulo de sesión se anota al arrancar y el cliente solo avisa.
 */
export function onAuthRejected(handler: (() => void) | null): void {
  rejectionHandler = handler
}

export type ParamValue = string | number | boolean

export interface RequestOptions {
  /** Parámetros de query. Las claves con valor `undefined` no se envían. */
  params?: Record<string, ParamValue | undefined>
  /** Para cancelar la petición si la pantalla se desmonta antes de que responda. */
  signal?: AbortSignal
  /**
   * Cabeceras propias de esa llamada, como la `Idempotency-Key` del checkout. Las de siempre
   * —`Accept`, `Content-Type` y la sesión— las pone el cliente y no se pisan desde acá.
   */
  headers?: Record<string, string>
}

function buildUrl(path: string, params: RequestOptions['params']): string {
  const url = `${ROOT}${path}`
  if (!params) return url

  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.append(key, String(value))
  }

  const queryString = query.toString()
  return queryString ? `${url}?${queryString}` : url
}

/**
 * Saca de la respuesta el texto que se le muestra al usuario.
 *
 * El backend contesta los errores de dos formas y las dos traen algo aprovechable:
 * ProblemDetail de Spring (RFC 7807) cuando el error lo arma el framework, y texto plano
 * cuando lo arma un `@ExceptionHandler` devolviendo un `String` —ahí vive el mensaje bueno,
 * el que nombra la estación que no se encontró—. Por eso el cuerpo se lee como texto una
 * sola vez y recién después se intenta interpretarlo como JSON: leerlo directo con
 * `.json()` descarta el caso de texto plano y deja al usuario con un "Error 404" pelado.
 */
async function readError(response: Response): Promise<ApiError> {
  let text = ''
  try {
    text = await response.text()
  } catch {
    // El cuerpo no se pudo leer; nos quedamos con el estado.
  }

  let body: unknown = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    // No era JSON: es el mensaje en texto plano.
  }

  const problem = body as { detail?: string; title?: string; message?: string } | null
  const message =
    problem?.detail ??
    problem?.title ??
    problem?.message ??
    (body === null && text.trim() !== '' ? text.trim() : `Error ${response.status}`)

  return new ApiError(message, response.status, body ?? text)
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = { ...options?.headers, Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (sessionToken !== null) headers['Authorization'] = `Bearer ${sessionToken}`

  let response: Response
  try {
    response = await fetch(buildUrl(path, options?.params), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: options?.signal,
    })
  } catch (error) {
    throw new ApiError('No se pudo conectar con el servidor', 0, error)
  }

  /*
   * Un 401 o un 403 pueden significar que el token venció. Se avisa antes de tirar el error
   * para que la sesión se cierre sola en vez de dejar al usuario en una pantalla que falla
   * sin decir por qué. Quién decide si de verdad hay que cerrarla no es asunto de este
   * archivo: acá solo se reporta el rechazo.
   */
  if (response.status === 401 || response.status === 403) rejectionHandler?.()

  if (!response.ok) throw await readError(response)

  // Un 204 (o un DELETE, o un POST sin cuerpo de respuesta) no trae JSON que parsear.
  if (response.status === 204 || response.status === 205) return undefined as T

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, undefined, options),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('POST', path, body, options),

  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PUT', path, body, options),

  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PATCH', path, body, options),

  delete: <T = void>(path: string, options?: RequestOptions) =>
    request<T>('DELETE', path, undefined, options),
}
