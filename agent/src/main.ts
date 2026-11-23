/**
 * El arranque: lee la configuración, arma el asistente con Gemini y levanta el servidor.
 *
 * Es el único archivo que conoce las implementaciones reales —Gemini, Photon, los backends—; el
 * resto las recibe por parámetro, y por eso los tests no salen a la red.
 */

import { GoogleGenAI } from '@google/genai'

import { createAssistant } from './assistant.ts'
import { readConfig } from './config.ts'
import { createEcopediaApi, createSessionChecker } from './ecopedia.ts'
import { findPlaces } from './geocoding.ts'
import { createGeminiModel } from './gemini.ts'
import { createServer } from './server.ts'

const config = readConfig()

/**
 * Sin clave, el servicio arranca igual: así `pnpm dev` no se cae entero porque a alguien le falta
 * el `.env`, y el chat dice qué falta en vez de mostrar un error de conexión.
 */
function buildAssistant() {
  if (config.apiKey === undefined) return null

  const client = new GoogleGenAI({ apiKey: config.apiKey })

  return createAssistant({
    createModel: () => createGeminiModel(client, config.models),
    createApi: (authorization) =>
      createEcopediaApi({
        coreUrl: config.coreUrl,
        chargingUrl: config.chargingUrl,
        authorization,
      }),
    findPlaces: (query, signal) => findPlaces(query, signal),
  })
}

const assistant = buildAssistant()

const checkSession = createSessionChecker({ coreUrl: config.coreUrl })

createServer({ assistant, checkSession }).listen(config.port, () => {
  console.log(`[agent] escuchando en http://localhost:${config.port}`)
  console.log(`[agent] modelos: ${config.models.join(' → ')}`)
  console.log(`[agent] core: ${config.coreUrl} · reservas: ${config.chargingUrl}`)
  if (assistant === null) {
    console.warn(
      '[agent] falta GEMINI_API_KEY en el .env: el chat va a avisar que no está configurado',
    )
  }
})
