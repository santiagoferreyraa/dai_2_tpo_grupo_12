/**
 * El arranque: lee la configuración, arma el asistente con Gemini y levanta el servidor.
 *
 * Es el único archivo que conoce las implementaciones reales —Gemini, Photon, los backends—; el
 * resto las recibe por parámetro, y por eso los tests no salen a la red.
 */

import { GoogleGenAI } from '@google/genai'
import { FallbackStrategy, ModelRouter } from '@strands-agents/sdk'
import { GoogleModel } from '@strands-agents/sdk/models/google'

import { createAssistant } from './assistant.ts'
import { readConfig } from './config.ts'
import { createEcopediaApi } from './ecopedia.ts'
import { findPlaces } from './geocoding.ts'
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
    /*
     * Los modelos en cadena: si el primero contesta saturado, `FallbackStrategy` prueba el
     * siguiente. Se arma por pedido porque el router se engancha al agente, y hay uno por pedido.
     */
    createModel: () =>
      new ModelRouter(
        config.models.map(
          (modelId) => new GoogleModel({ client, modelId, params: { temperature: 0.3 } }),
        ),
        { strategy: new FallbackStrategy() },
      ),
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

createServer({ assistant }).listen(config.port, () => {
  console.log(`[agent] escuchando en http://localhost:${config.port}`)
  console.log(`[agent] modelos: ${config.models.join(' → ')}`)
  console.log(`[agent] core: ${config.coreUrl} · reservas: ${config.chargingUrl}`)
  if (assistant === null) {
    console.warn(
      '[agent] falta GEMINI_API_KEY en el .env: el chat va a avisar que no está configurado',
    )
  }
})
