/**
 * La configuración del agente, leída una sola vez del entorno.
 *
 * Todo sale de variables, igual que en los backends de Spring: mudar de ambiente es cambiar
 * valores, no código. Los scripts de la raíz cargan el `.env` con `scripts/with-env.mjs`, así que
 * la clave de Gemini llega sin exportarla a mano.
 */

export interface Config {
  port: number
  /** Dónde está ecopedia-core: búsqueda y estaciones. */
  coreUrl: string
  /** Dónde está ecopedia-charging: la disponibilidad de un conector. */
  chargingUrl: string
  /** `undefined` si falta: el agente arranca igual y el chat avisa que no está configurado. */
  apiKey: string | undefined
  /** Modelos de Gemini en orden de preferencia. Si uno falla, se prueba el siguiente. */
  models: string[]
}

/**
 * Los modelos por omisión, elegidos probando con una clave del plan gratis el 29/09.
 *
 * Los "flash" grandes contestaban 503 por demanda en ese plan; los "flash-lite" respondían en unos
 * dos segundos y llaman bien a las herramientas. Van dos versiones fijas y no un alias como
 * `gemini-flash-latest`: un alias cambia de modelo sin avisar. El costo es que Google retira
 * versiones —el `gemini-2.5-flash` que trae Strands por omisión ya no existe para cuentas nuevas—,
 * y por eso se pueden cambiar con `GEMINI_MODELS` sin tocar código.
 */
const DEFAULT_MODELS = ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite']

export function readConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const models = (env.GEMINI_MODELS ?? '')
    .split(',')
    .map((model) => model.trim())
    .filter((model) => model !== '')

  const apiKey = env.GEMINI_API_KEY?.trim()

  return {
    port: Number(env.ECOPEDIA_AGENT_PORT ?? 8085),
    coreUrl: env.ECOPEDIA_CORE_URL ?? 'http://localhost:8081',
    chargingUrl: env.ECOPEDIA_CHARGING_URL ?? 'http://localhost:8082',
    apiKey: apiKey === '' ? undefined : apiKey,
    models: models.length > 0 ? models : DEFAULT_MODELS,
  }
}
