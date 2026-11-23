/**
 * El modelo del asistente: Gemini, en cadena. Lo usan el arranque (`main.ts`) y el eval
 * (`eval/run.ts`), así el eval prueba exactamente lo mismo que corre en producción.
 */

import type { GoogleGenAI } from '@google/genai'
import { FallbackStrategy, ModelRouter } from '@strands-agents/sdk'
import { GoogleModel } from '@strands-agents/sdk/models/google'

/**
 * El tope de tokens de cada respuesta del modelo. Una respuesta del asistente son tres renglones de
 * estaciones y una pregunta (unos 200 tokens), y una llamada a herramienta, menos: 1024 deja margen
 * de sobra y frena una respuesta que se va de largo, que es cupo del plan gratis.
 */
export const MAX_OUTPUT_TOKENS = 1024

/**
 * Los modelos en cadena: si el primero contesta saturado, `FallbackStrategy` prueba el siguiente.
 * Se arma por pedido porque el router se engancha al agente, y hay uno por pedido.
 */
export function createGeminiModel(client: GoogleGenAI, models: string[]): ModelRouter {
  return new ModelRouter(
    models.map(
      (modelId) =>
        new GoogleModel({
          client,
          modelId,
          params: { temperature: 0.3, maxOutputTokens: MAX_OUTPUT_TOKENS },
        }),
    ),
    { strategy: new FallbackStrategy() },
  )
}
