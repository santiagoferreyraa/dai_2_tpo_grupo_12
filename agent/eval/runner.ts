/**
 * Corre un caso del eval set y lo evalúa. Lo usan los dos lados: los tests de CI (con el modelo
 * guionado, solo los casos que no dependen del modelo) y `run.ts` (con Gemini, los veinte).
 */

import type { AddressInfo } from 'node:net'

import { createAssistant, type AssistantDeps } from '../src/assistant.ts'
import { CHAT_PATH, createServer } from '../src/server.ts'
import { ScriptedModel } from '../test/scripted-model.ts'
import { DRIVER_TOKEN, evaluate, type EvalCase, type Outcome } from './cases.ts'
import { createFixtureApi, findFixturePlaces } from './fixtures.ts'

export interface CaseResult {
  evalCase: EvalCase
  outcome: Outcome
  passed: boolean
  failures: string[]
  ms: number
}

/** Un caso común: el asistente con el modelo que se le pase, contra los datos fijos. */
export async function runCase(
  evalCase: EvalCase,
  createModel: AssistantDeps['createModel'],
): Promise<CaseResult> {
  if (evalCase.special === 'provider-down') return runProviderDown(evalCase)

  const authorization = evalCase.withSession ? DRIVER_TOKEN : undefined
  const api = createFixtureApi(authorization)
  const assistant = createAssistant({
    createModel,
    createApi: () => api,
    findPlaces: findFixturePlaces,
  })

  const started = Date.now()
  const result = await assistant.ask({ message: evalCase.message, history: [], authorization })
  const outcome = { text: result.text, toolsUsed: result.toolsUsed }
  const { failures } = evaluate(evalCase, outcome)

  // La fuga de datos también se mira por dentro: toda consulta tiene que haber salido con el token
  // del conductor que escribe (o sin token), nunca con otro.
  if (api.authorizations.some((used) => used !== authorization)) {
    failures.push('consultó con un token que no es el del conductor')
  }
  return { evalCase, outcome, passed: failures.length === 0, failures, ms: Date.now() - started }
}

/**
 * El proveedor caído: el modelo contesta 503 como Gemini saturado, y lo que se evalúa es lo que
 * le llega al conductor por la ruta de verdad (`POST /api/agent/chat`): un 503 con un mensaje que
 * dice qué hacer, sin el error técnico. No depende de Gemini, así que corre igual en CI.
 */
export async function runProviderDown(evalCase: EvalCase): Promise<CaseResult> {
  const assistant = createAssistant({
    createModel: () =>
      new ScriptedModel([
        { fail: new Error('got status: 503 UNAVAILABLE. The model is overloaded (high demand)') },
      ]),
    createApi: createFixtureApi,
    findPlaces: findFixturePlaces,
  })
  const server = createServer({ assistant, log: () => {} })
  await new Promise<void>((resolve) => server.listen(0, resolve))
  const started = Date.now()
  try {
    const port = (server.address() as AddressInfo).port
    const response = await fetch(`http://localhost:${port}${CHAT_PATH}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: evalCase.message, history: [] }),
    })
    const body = (await response.json()) as { message?: string }
    const outcome = { text: body.message ?? '', toolsUsed: [] }
    const { failures } = evaluate(evalCase, outcome)
    if (response.status !== 503) failures.push(`contestó ${response.status} y no 503`)
    return { evalCase, outcome, passed: failures.length === 0, failures, ms: Date.now() - started }
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()))
  }
}
