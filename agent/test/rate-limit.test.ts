import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { clientKey, createRateLimiter } from '../src/rate-limit.ts'

describe('createRateLimiter', () => {
  it('vuelve a dejar pasar cuando la pregunta más vieja sale de la ventana', () => {
    let now = 0
    const limiter = createRateLimiter({ limit: 2, windowMs: 60_000, now: () => now })

    assert.equal(limiter.take('a').allowed, true)
    now = 20_000
    assert.equal(limiter.take('a').allowed, true)

    now = 30_000
    assert.deepEqual(limiter.take('a'), { allowed: false, retryAfterSeconds: 30 })

    now = 60_001
    assert.equal(limiter.take('a').allowed, true, 'la del segundo 0 ya no cuenta')
    assert.equal(limiter.take('a').allowed, false, 'la del segundo 20 y la recién hecha, sí')
  })

  it('una pregunta frenada no alarga la espera', () => {
    let now = 0
    const limiter = createRateLimiter({ limit: 1, windowMs: 60_000, now: () => now })
    limiter.take('a')
    for (now = 1_000; now < 59_000; now += 1_000) limiter.take('a')
    now = 60_001
    assert.equal(limiter.take('a').allowed, true)
  })
})

describe('clientKey', () => {
  it('detrás del proxy local, usa la dirección que manda el proxy', () => {
    assert.equal(clientKey('127.0.0.1', '192.168.0.7'), '192.168.0.7')
    assert.equal(clientKey('::1', '192.168.0.7, 10.0.0.1'), '192.168.0.7')
    assert.equal(clientKey('::ffff:127.0.0.1', '192.168.0.7'), '192.168.0.7')
  })

  it('a un pedido que llega de la red no le cree el encabezado', () => {
    assert.equal(clientKey('192.168.0.9', '1.2.3.4'), '192.168.0.9')
  })

  it('sin encabezado, usa la dirección de la conexión', () => {
    assert.equal(clientKey('127.0.0.1', undefined), '127.0.0.1')
  })
})
