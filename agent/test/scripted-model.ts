/**
 * Un modelo de mentira que sigue un guion: en cada vuelta pide una herramienta o contesta un
 * texto, en el orden en que se escribió. Así los tests recorren el agente de Strands de verdad
 * —el bucle, las herramientas, los resultados— sin gastar cupo de Gemini ni depender de la red.
 *
 * Guarda lo que recibió en cada vuelta para poder afirmar sobre eso: qué le devolvió cada
 * herramienta, con qué historial arrancó.
 */

import { Model } from '@strands-agents/sdk'
import type { BaseModelConfig, Message, ModelStreamEvent, StreamOptions } from '@strands-agents/sdk'

export type Step =
  { tool: string; input: Record<string, unknown> } | { text: string } | { fail: Error }

export class ScriptedModel extends Model<BaseModelConfig> {
  readonly calls: { messages: Message[]; options: StreamOptions | undefined }[] = []
  private readonly steps: Step[]
  private config: BaseModelConfig = { modelId: 'scripted' }

  constructor(steps: Step[]) {
    super()
    this.steps = [...steps]
  }

  updateConfig(config: BaseModelConfig): void {
    this.config = { ...this.config, ...config }
  }

  getConfig(): BaseModelConfig {
    return this.config
  }

  async *stream(messages: Message[], options?: StreamOptions): AsyncIterable<ModelStreamEvent> {
    this.calls.push({ messages: [...messages], options })
    const step = this.steps.shift()
    if (step === undefined) throw new Error('El guion se terminó y el agente siguió preguntando')
    if ('fail' in step) throw step.fail

    yield { type: 'modelMessageStartEvent', role: 'assistant' }

    if ('tool' in step) {
      yield {
        type: 'modelContentBlockStartEvent',
        start: { type: 'toolUseStart', name: step.tool, toolUseId: `call-${this.calls.length}` },
      }
      yield {
        type: 'modelContentBlockDeltaEvent',
        delta: { type: 'toolUseInputDelta', input: JSON.stringify(step.input) },
      }
      yield { type: 'modelContentBlockStopEvent' }
      yield { type: 'modelMessageStopEvent', stopReason: 'toolUse' }
      return
    }

    yield { type: 'modelContentBlockStartEvent' }
    yield { type: 'modelContentBlockDeltaEvent', delta: { type: 'textDelta', text: step.text } }
    yield { type: 'modelContentBlockStopEvent' }
    yield { type: 'modelMessageStopEvent', stopReason: 'endTurn' }
  }
}

/** El resultado de herramienta que recibió el modelo en una vuelta dada, ya parseado. */
export function toolResultAt(model: ScriptedModel, call: number): unknown {
  const last = model.calls[call]?.messages.at(-1)
  for (const block of last?.content ?? []) {
    if (block.type !== 'toolResultBlock') continue
    for (const item of block.content) {
      if (item.type === 'jsonBlock') return item.json
      if (item.type === 'textBlock') return JSON.parse(item.text)
    }
  }
  throw new Error(`La vuelta ${call} no recibió un resultado de herramienta`)
}
