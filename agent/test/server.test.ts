import assert from 'node:assert/strict'
import type { AddressInfo } from 'node:net'
import { after, before, describe, it } from 'node:test'

import { AssistantError, type AskInput, type AskResult } from '../src/assistant.ts'
import { CHAT_PATH, createServer, QUESTIONS_PER_MINUTE, type ServerDeps } from '../src/server.ts'

/** El token que core acepta en estos tests. Todo pedido lo lleva, salvo que se diga otra cosa. */
const TOKEN = 'Bearer valido'

/** Core de mentira: acepta `TOKEN` y rechaza cualquier otro. */
const coreAccepting: ServerDeps['checkSession'] = async (authorization) =>
  authorization === TOKEN ? 'valid' : 'invalid'

/** Levanta el servidor en un puerto libre con el asistente que se le pase. */
function serve(
  assistant: ServerDeps['assistant'],
  checkSession: ServerDeps['checkSession'] = coreAccepting,
) {
  const server = createServer({ assistant, checkSession, log: () => {} })
  let base = ''
  before(async () => {
    await new Promise<void>((resolve) => server.listen(0, resolve))
    base = `http://localhost:${(server.address() as AddressInfo).port}`
  })
  after(() => new Promise<void>((resolve) => server.close(() => resolve())))

  return (body: unknown, headers: Record<string, string> = {}, path = CHAT_PATH) =>
    fetch(`${base}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: TOKEN, ...headers },
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

  it('contesta { text, signature } con la forma que espera el front', async () => {
    const response = await post({ message: 'hola', history: [{ role: 'user', text: 'hola' }] })
    assert.equal(response.status, 200)
    const body = (await response.json()) as { text: string; signature: string }
    assert.equal(body.text, 'Andá a Palermo.')
    assert.equal(typeof body.signature, 'string')
    assert.equal(received.at(-1)?.message, 'hola')
    assert.equal(received.at(-1)?.history.length, 1)
  })

  it('le pasa el token del conductor al asistente', async () => {
    await post({ message: 'hola', history: [] })
    assert.equal(received.at(-1)?.authorization, TOKEN)
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

describe('sin una sesión que core acepte, 401 y el modelo ni se llama', () => {
  const { assistant, received } = answering(async () => ({ text: 'ok', toolsUsed: [] }))
  const checked: string[] = []
  const post = serve(assistant, async (authorization) => {
    checked.push(authorization)
    if (authorization === 'Bearer core-caido') return 'unavailable'
    return coreAccepting(authorization)
  })

  for (const [caso, authorization] of [
    ['sin token', ''],
    ['con un encabezado que no es Bearer', 'Basic xyz'],
    ['con un token que core rechaza', 'Bearer inventado'],
  ] as const) {
    it(caso, async () => {
      const before = received.length
      const response = await post(
        { message: 'hola', history: [] },
        { Authorization: authorization },
      )
      assert.equal(response.status, 401)
      assert.equal(response.headers.get('WWW-Authenticate'), 'Bearer')
      const { message } = (await response.json()) as { message: string }
      assert.match(message, /Iniciá sesión/)
      assert.equal(received.length, before)
    })
  }

  it('sin token ni siquiera le pregunta a core', async () => {
    const before = checked.length
    await post({ message: 'hola', history: [] }, { Authorization: '' })
    assert.equal(checked.length, before)
  })

  it('con core caído contesta 503, no 401: no se sabe si la sesión vale', async () => {
    const response = await post(
      { message: 'hola', history: [] },
      { Authorization: 'Bearer core-caido' },
    )
    assert.equal(response.status, 503)
    assert.match(((await response.json()) as { message: string }).message, /Probá de nuevo/)
  })

  it('con el token bueno, core lo valida y el modelo contesta', async () => {
    const response = await post({ message: 'hola', history: [] })
    assert.equal(response.status, 200)
    assert.equal(checked.at(-1), TOKEN)
  })
})

describe('las fallas del modelo llegan como mensajes para el conductor', () => {
  const cases: [AssistantError['reason'], number, RegExp][] = [
    ['busy', 503, /saturado/],
    ['too-broad', 422, /una sola estación/],
    ['bad-key', 503, /buscar estaciones en el mapa/],
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
        assert.doesNotMatch(body.message, /detalle técnico|Gemini|clave/)
      })
    })
  }

  describe('sin clave de Gemini', () => {
    const post = serve(null)

    it('contesta 503 con qué hacer, sin nombrar la variable que falta', async () => {
      const response = await post({ message: 'hola', history: [] })
      assert.equal(response.status, 503)
      const { message } = (await response.json()) as { message: string }
      assert.doesNotMatch(message, /GEMINI|\.env/)
      assert.match(message, /buscar estaciones en el mapa/)
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
