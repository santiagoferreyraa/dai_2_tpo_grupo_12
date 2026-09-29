import assert from 'node:assert/strict'
import type { AddressInfo } from 'node:net'
import { after, before, describe, it } from 'node:test'

import { AssistantError, type AskInput, type AskResult } from '../src/assistant.ts'
import { CHAT_PATH, createServer, QUESTIONS_PER_MINUTE, type ServerDeps } from '../src/server.ts'

/** Levanta el servidor en un puerto libre con el asistente que se le pase. */
function serve(assistant: ServerDeps['assistant']) {
  const server = createServer({ assistant, log: () => {} })
  let base = ''
  before(async () => {
    await new Promise<void>((resolve) => server.listen(0, resolve))
    base = `http://localhost:${(server.address() as AddressInfo).port}`
  })
  after(() => new Promise<void>((resolve) => server.close(() => resolve())))

  return (body: unknown, headers: Record<string, string> = {}, path = CHAT_PATH) =>
    fetch(`${base}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: typeof body === 'string' ? body : JSON.stringify(body),
    })
}

function answering(reply: (input: AskInput) => Promise<AskResult>) {
  const received: AskInput[] = []
  return {
    received,
    assistant: {
      ask(input: AskInput) {
        received.push(input)
        return reply(input)
      },
    },
  }
}

describe('POST /api/agent/chat', () => {
  const { assistant, received } = answering(async () => ({
    text: 'Andá a Palermo.',
    toolsUsed: [],
  }))
  const post = serve(assistant)

  it('contesta { text } con la forma que espera el front', async () => {
    const response = await post({ message: 'hola', history: [{ role: 'user', text: 'hola' }] })
    assert.equal(response.status, 200)
    assert.deepEqual(await response.json(), { text: 'Andá a Palermo.' })
    assert.equal(received.at(-1)?.message, 'hola')
    assert.equal(received.at(-1)?.history.length, 1)
  })

  it('le pasa el token del conductor al asistente', async () => {
    await post({ message: 'hola', history: [] }, { Authorization: 'Bearer abc' })
    assert.equal(received.at(-1)?.authorization, 'Bearer abc')
  })

  it('ignora un encabezado de autorización que no sea Bearer', async () => {
    await post({ message: 'hola', history: [] }, { Authorization: 'Basic xyz' })
    assert.equal(received.at(-1)?.authorization, undefined)
  })

  it('rechaza un pedido sin mensaje con 400', async () => {
    const response = await post({ message: '   ', history: [] })
    assert.equal(response.status, 400)
    assert.ok(((await response.json()) as { message: string }).message)
  })

  it('rechaza un cuerpo que no es JSON con 400', async () => {
    const response = await post('esto no es json')
    assert.equal(response.status, 400)
  })

  it('rechaza un mensaje demasiado largo', async () => {
    const response = await post({ message: 'x'.repeat(2001), history: [] })
    assert.equal(response.status, 400)
  })

  it('contesta 404 en otra ruta', async () => {
    const response = await post({ message: 'hola' }, {}, '/api/otra')
    assert.equal(response.status, 404)
  })
})

describe('las fallas llegan como mensajes para el conductor, nunca como 401 o 403', () => {
  const cases: [AssistantError['reason'], number, RegExp][] = [
    ['busy', 503, /saturado/],
    ['bad-key', 503, /clave de Gemini/],
    ['failed', 502, /no pudo responder/],
  ]

  for (const [reason, status, message] of cases) {
    describe(reason, () => {
      const { assistant } = answering(async () => {
        throw new AssistantError(reason, 'detalle técnico')
      })
      const post = serve(assistant)

      it(`contesta ${status} con un mensaje legible`, async () => {
        const response = await post({ message: 'hola', history: [] })
        assert.equal(response.status, status)
        const body = (await response.json()) as { message: string }
        assert.match(body.message, message)
        assert.doesNotMatch(body.message, /detalle técnico/)
      })
    })
  }

  describe('sin clave de Gemini', () => {
    const post = serve(null)

    it('contesta 503 y dice qué falta', async () => {
      const response = await post({ message: 'hola', history: [] })
      assert.equal(response.status, 503)
      assert.match(((await response.json()) as { message: string }).message, /GEMINI_API_KEY/)
    })
  })
})

describe('el límite de preguntas por minuto', () => {
  const { assistant, received } = answering(async () => ({ text: 'ok', toolsUsed: [] }))
  const post = serve(assistant)
  const ask = (ip: string) => post({ message: 'hola', history: [] }, { 'X-Forwarded-For': ip })

  it(`deja pasar ${QUESTIONS_PER_MINUTE} y frena la siguiente con 429 y Retry-After`, async () => {
    for (let i = 0; i < QUESTIONS_PER_MINUTE; i++) {
      assert.equal((await ask('10.0.0.1')).status, 200)
    }
    const before = received.length

    const blocked = await ask('10.0.0.1')
    assert.equal(blocked.status, 429)
    assert.ok(Number(blocked.headers.get('Retry-After')) >= 1)
    assert.match(((await blocked.json()) as { message: string }).message, /Esperá \d+ segundos/)
    assert.equal(received.length, before, 'la pregunta frenada no llega al modelo')
  })

  it('cuenta por separado a cada cliente que llega por el proxy', async () => {
    assert.equal((await ask('10.0.0.2')).status, 200)
  })

  it('un pedido mal armado no gasta cupo', async () => {
    for (let i = 0; i < QUESTIONS_PER_MINUTE + 2; i++) {
      await post({ message: '' }, { 'X-Forwarded-For': '10.0.0.3' })
    }
    assert.equal((await ask('10.0.0.3')).status, 200)
  })
})
