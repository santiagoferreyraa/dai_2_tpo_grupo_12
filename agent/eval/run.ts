/**
 * Corre los 20 casos del eval set contra Gemini de verdad y deja el informe en `eval/results/`.
 *
 *   pnpm agent:eval                (desde la raíz: toma GEMINI_API_KEY del .env)
 *   pnpm --dir agent eval          (con GEMINI_API_KEY ya en el entorno)
 *
 * No corre en CI: gasta cupo del plan gratis y el modelo no contesta dos veces igual. Lo que CI sí
 * verifica en cada PR está en `test/eval-set.test.ts`. El informe guardado es la evidencia del eje
 * E: qué se preguntó, qué contestó, qué criterio falló y si se llegó a los umbrales.
 *
 * Sale con código 1 si alguna categoría queda por debajo de su umbral (`THRESHOLDS`).
 */

import { mkdirSync, writeFileSync } from 'node:fs'

import { GoogleGenAI } from '@google/genai'

import { AssistantError } from '../src/assistant.ts'
import { readConfig } from '../src/config.ts'
import { createGeminiModel } from '../src/gemini.ts'
import { CASES, THRESHOLDS, type Category } from './cases.ts'
import { runCase, type CaseResult } from './runner.ts'

/** Pausa entre casos: el plan gratis limita los pedidos por minuto. */
const PAUSE_MS = 4000
/** Si Gemini contesta saturado, un reintento después de esto. */
const BUSY_RETRY_MS = 30_000

const config = readConfig()
if (config.apiKey === undefined) {
  console.error(
    'Falta GEMINI_API_KEY: el eval corre contra Gemini de verdad. Ver el README, "El asistente".',
  )
  process.exit(2)
}
const client = new GoogleGenAI({ apiKey: config.apiKey })
const createModel = () => createGeminiModel(client, config.models)

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const results: CaseResult[] = []
for (const evalCase of CASES) {
  let result: CaseResult
  try {
    result = await runCase(evalCase, createModel)
  } catch (error) {
    if (error instanceof AssistantError && error.reason === 'busy') {
      console.log(`${evalCase.id} · Gemini saturado, reintento en ${BUSY_RETRY_MS / 1000} s`)
      await sleep(BUSY_RETRY_MS)
      result = await runCase(evalCase, createModel).catch((retryError: unknown) =>
        failed(evalCase, retryError),
      )
    } else {
      result = failed(evalCase, error)
    }
  }
  results.push(result)
  console.log(
    `${result.passed ? 'PASA ' : 'FALLA'} ${evalCase.id} · ${evalCase.title} (${result.ms} ms)`,
  )
  for (const failure of result.failures) console.log(`        ${failure}`)
  await sleep(PAUSE_MS)
}

const summary = (['dominio', 'adversarial'] as Category[]).map((category) => {
  const ofCategory = results.filter((r) => r.evalCase.category === category)
  const passed = ofCategory.filter((r) => r.passed).length
  const rate = passed / ofCategory.length
  return { category, passed, total: ofCategory.length, rate, ok: rate >= THRESHOLDS[category] }
})

const stamp = new Date().toISOString().slice(0, 16).replace(':', '')
mkdirSync(new URL('./results/', import.meta.url), { recursive: true })
const file = new URL(`./results/${stamp}.md`, import.meta.url)
writeFileSync(file, report(results, summary), 'utf-8')

console.log('')
for (const s of summary) {
  console.log(
    `${s.ok ? 'OK   ' : 'BAJO '} ${s.category}: ${s.passed} de ${s.total} (${pct(s.rate)}, umbral ${pct(THRESHOLDS[s.category])})`,
  )
}
console.log(`Informe: agent/eval/results/${stamp}.md`)
process.exit(summary.every((s) => s.ok) ? 0 : 1)

function failed(evalCase: CaseResult['evalCase'], error: unknown): CaseResult {
  const reason = error instanceof Error ? error.message : String(error)
  return {
    evalCase,
    outcome: { text: '', toolsUsed: [] },
    passed: false,
    failures: [`no contestó: ${reason}`],
    ms: 0,
  }
}

function pct(rate: number): string {
  return `${Math.round(rate * 100)} %`
}

function report(all: CaseResult[], totals: typeof summary): string {
  const lines = [
    `# Eval set del asistente — ${new Date().toISOString()}`,
    '',
    `Modelos: ${config.models.join(' → ')}. Casos y criterios: \`agent/eval/cases.ts\`; datos: \`agent/eval/fixtures.ts\`.`,
    '',
    '| Categoría | Pasan | Umbral | Resultado |',
    '|---|---|---|---|',
    ...totals.map(
      (s) =>
        `| ${s.category} | ${s.passed} de ${s.total} (${pct(s.rate)}) | ${pct(THRESHOLDS[s.category])} | ${s.ok ? 'aprobado' : 'por debajo'} |`,
    ),
    '',
    '## Casos',
    '',
  ]
  for (const r of all) {
    lines.push(
      `### ${r.evalCase.id} · ${r.evalCase.title} — ${r.passed ? 'pasa' : 'FALLA'}`,
      '',
      `- **Pregunta:** ${r.evalCase.message}`,
      `- **Se espera:** ${r.evalCase.expected}`,
      `- **Herramientas:** ${r.outcome.toolsUsed.join(', ') || 'ninguna'} · ${r.ms} ms`,
      ...(r.failures.length > 0 ? [`- **No cumplió:** ${r.failures.join('; ')}`] : []),
      '',
      '> ' + (r.outcome.text || '(sin respuesta)').replace(/\n/g, '\n> '),
      '',
    )
  }
  return lines.join('\n')
}
