/**
 * Cuántas preguntas puede hacer cada cliente por minuto.
 *
 * Existe por el cupo del plan gratis de Gemini: cada pregunta gasta de una a ocho llamadas al
 * modelo, y si alguien usa el chat de a ráfagas —o lo convierte en un Gemini gratis para otra
 * cosa— el cupo se agota para todos, justo el día de la demo.
 *
 * Es una ventana deslizante: se guardan los instantes de las preguntas del último minuto y se
 * cuentan. Vive en memoria, así que reiniciar el agente la vacía, igual que el bloqueo de login.
 */

export interface RateLimiter {
  /** Anota la pregunta si entra, o dice cuántos segundos faltan para poder hacerla. */
  take(key: string): { allowed: true } | { allowed: false; retryAfterSeconds: number }
}

export interface RateLimitOptions {
  limit: number
  windowMs: number
  /** Se inyecta en los tests para no esperar un minuto de verdad. */
  now?: () => number
}

/** Pasada esta cantidad de clientes guardados, se barren los que ya no tienen preguntas vigentes. */
const SWEEP_ABOVE = 1000

export function createRateLimiter(options: RateLimitOptions): RateLimiter {
  const now = options.now ?? Date.now
  const hits = new Map<string, number[]>()

  function recent(key: string, at: number): number[] {
    const since = at - options.windowMs
    return (hits.get(key) ?? []).filter((time) => time > since)
  }

  return {
    take(key) {
      const at = now()
      const times = recent(key, at)

      if (times.length >= options.limit) {
        hits.set(key, times)
        const retryAfterMs = times[0] + options.windowMs - at
        return { allowed: false, retryAfterSeconds: Math.max(1, Math.ceil(retryAfterMs / 1000)) }
      }

      times.push(at)
      hits.set(key, times)

      if (hits.size > SWEEP_ABOVE) {
        for (const other of hits.keys()) {
          if (recent(other, at).length === 0) hits.delete(other)
        }
      }
      return { allowed: true }
    },
  }
}

/**
 * Quién pregunta, para contar por separado.
 *
 * El front no llega directo: pasa por el proxy de Vite, así que para el agente todos los pedidos
 * vienen de la misma máquina y un solo conductor le agotaría el límite a todos. Por eso el proxy
 * manda la dirección real en `X-Forwarded-For` (`xfwd` en `vite.config.ts`).
 *
 * **Ese encabezado solo se cree si el pedido viene de la propia máquina**, que es donde corre el
 * proxy. Cualquiera puede escribir un `X-Forwarded-For` inventado; si se le creyera a un pedido
 * que llega de la red, bastaría cambiarlo en cada pregunta para esquivar el límite.
 */
export function clientKey(remoteAddress: string | undefined, forwardedFor: string | undefined) {
  const remote = remoteAddress ?? 'desconocido'
  if (!isLoopback(remote) || forwardedFor === undefined) return remote

  const first = forwardedFor.split(',')[0]?.trim()
  return first === undefined || first === '' ? remote : first
}

function isLoopback(address: string): boolean {
  return address === '127.0.0.1' || address === '::1' || address === '::ffff:127.0.0.1'
}
