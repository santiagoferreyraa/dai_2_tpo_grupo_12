// Prueba de carga de los 3 endpoints críticos (QA-06, ECO-156).
//
//   k6 run qa/k6/load-critical.js
//   k6 run -e CORE_URL=https://... -e CHARGING_URL=https://... qa/k6/load-critical.js
//
// Criterio (eje C): 50 usuarios concurrentes durante 5 minutos, p95 < 500 ms y menos de 1 % de
// errores. Los umbrales están abajo en `thresholds`: si no se cumplen, k6 termina con código
// distinto de 0 y el job de CI queda en rojo.
//
// **Por qué estos tres.** Son el camino del conductor que reserva, que es el flujo principal:
//   1. GET  /api/search                 (core)      el mapa: lo que más se llama, y sin sesión
//   2. GET  /api/bookings/availability  (charging)  la agenda del conector elegido
//   3. POST /api/bookings/holds         (charging)  la retención del slot, que escribe en la base
// El login queda afuera a propósito: bcrypt es lento por diseño, y medirlo acá mediría eso.
//
// **Cada usuario virtual es un conductor distinto**, registrado en setup(). Así las retenciones
// no compiten entre sí: cada uno pide siempre su propia ventana, y repetirla no es conflicto
// (BookingServiceImplTest: "Volver a pedir el mismo horario que uno ya tenía retenido").

import http from 'k6/http'
import { check, sleep } from 'k6'
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js'

const CORE = __ENV.CORE_URL || 'http://localhost:8081'
const CHARGING = __ENV.CHARGING_URL || 'http://localhost:8082'
const VUS = Number(__ENV.VUS || 50)
const DURATION = __ENV.DURATION || '5m'

export const options = {
  scenarios: {
    conductores: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:search}': ['p(95)<500'],
    'http_req_duration{endpoint:availability}': ['p(95)<500'],
    'http_req_duration{endpoint:hold}': ['p(95)<500'],
    checks: ['rate>0.99'],
  },
  // setup() registra 50 conductores: no entra en las métricas de los endpoints medidos.
  setupTimeout: '3m',
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function setup() {
  // Un conector reservable (no fuera de servicio), tomado del catálogo real.
  const connectors = http.get(`${CORE}/api/connectors`, { tags: { endpoint: 'setup' } }).json()
  const connector = connectors.find((c) => c.operationalStatus !== 'OUT_OF_SERVICE')
  if (!connector) throw new Error('No hay conectores reservables en core')

  const run = Date.now()
  const drivers = []
  for (let i = 0; i < VUS; i++) {
    const email = `k6.${run}.${i}@ecopedia.test`
    const password = `k6-${run}-${i}`
    const body = JSON.stringify({ email, password, fullName: `Carga ${i}` })
    const registered = http.post(`${CORE}/api/auth/register`, body, {
      headers: JSON_HEADERS,
      tags: { endpoint: 'setup' },
    })
    if (registered.status !== 201) throw new Error(`Registro ${i}: ${registered.status} ${registered.body}`)

    const login = http.post(`${CORE}/api/auth/login`, JSON.stringify({ email, password }), {
      headers: JSON_HEADERS,
      tags: { endpoint: 'setup' },
    })
    if (login.status !== 200) throw new Error(`Login ${i}: ${login.status} ${login.body}`)
    drivers.push(login.json('token'))
  }
  return { connectorId: connector.id, drivers }
}

/** Ventana propia de cada conductor: día 2 + (VU × 2 h), así no se pisan entre sí. */
function windowFor(vu) {
  const start = new Date(Date.now() + 2 * 24 * 3600e3)
  start.setUTCMinutes(0, 0, 0)
  start.setTime(start.getTime() + (vu - 1) * 2 * 3600e3)
  return { start: start.toISOString(), end: new Date(start.getTime() + 3600e3).toISOString() }
}

export default function (data) {
  const token = data.drivers[(__VU - 1) % data.drivers.length]
  const auth = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }

  const search = http.get(`${CORE}/api/search?lat=-34.6037&lon=-58.3816&radiusKm=10`, {
    tags: { endpoint: 'search' },
  })
  check(search, { 'search 200': (r) => r.status === 200 })

  const from = new Date(Date.now() + 24 * 3600e3)
  from.setUTCMinutes(0, 0, 0)
  const to = new Date(from.getTime() + 4 * 3600e3)
  const availability = http.get(
    `${CHARGING}/api/bookings/availability?connectorId=${data.connectorId}` +
      `&from=${from.toISOString()}&to=${to.toISOString()}`,
    { ...auth, tags: { endpoint: 'availability' } },
  )
  check(availability, { 'availability 200': (r) => r.status === 200 })

  const window = windowFor(__VU)
  const hold = http.post(
    `${CHARGING}/api/bookings/holds`,
    JSON.stringify({ connectorId: data.connectorId, start: window.start, end: window.end }),
    { ...auth, tags: { endpoint: 'hold' } },
  )
  check(hold, { 'hold 201': (r) => r.status === 201 })

  // Un conductor real no reintenta en el mismo milisegundo: un segundo entre vueltas.
  sleep(1)
}

export function handleSummary(data) {
  return {
    'k6-summary.json': JSON.stringify(data, null, 2),
    'k6-summary.txt': textSummary(data, { indent: ' ', enableColors: false }),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  }
}
