import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { findPlaces } from '../src/geocoding.ts'

function feature(type: string, countrycode: string, lon: number, lat: number, name: string) {
  return {
    geometry: { coordinates: [lon, lat] },
    properties: { type, countrycode, name, city: 'Rosario', state: 'Santa Fe' },
  }
}

function photon(features: unknown[]) {
  const urls: URL[] = []
  const doFetch = (async (input: string | URL | Request) => {
    urls.push(new URL(String(input)))
    return new Response(JSON.stringify({ features }))
  }) as typeof fetch
  return { doFetch, urls }
}

describe('findPlaces', () => {
  it('se queda con los lugares de Argentina y da vuelta las coordenadas de GeoJSON', async () => {
    const { doFetch, urls } = photon([
      feature('city', 'UY', -57.84, -34.47, 'Rosario'),
      feature('city', 'AR', -60.64, -32.95, 'Rosario'),
    ])

    const places = await findPlaces('rosario', undefined, doFetch)

    assert.equal(places.length, 1)
    assert.equal(places[0].latitude, -32.95)
    assert.equal(places[0].longitude, -60.64)
    assert.equal(places[0].precisionM, 5000)
    assert.equal(urls[0].searchParams.get('q'), 'rosario')
    assert.ok(urls[0].searchParams.get('bbox'))
    /* Photon contesta 400 a `lang=es`: sin esto, ningún lugar se encuentra. */
    assert.equal(urls[0].searchParams.has('lang'), false)
  })

  it('acepta plazas y lugares conocidos, que Photon marca como "other"', async () => {
    const { doFetch } = photon([feature('other', 'AR', -58.4208, -34.5812, 'Plaza Italia')])
    const places = await findPlaces('Plaza Italia', undefined, doFetch)
    assert.equal(places.length, 1)
    assert.equal(places[0].precisionM, 300)
  })

  it('descarta provincias y países: no son un punto de partida', async () => {
    const { doFetch } = photon([
      feature('state', 'AR', -61, -31, 'Santa Fe'),
      feature('country', 'AR', -64, -34, 'Argentina'),
    ])
    assert.deepEqual(await findPlaces('santa fe', undefined, doFetch), [])
  })

  it('devuelve tres como mucho y sin repetidos', async () => {
    const { doFetch } = photon([
      feature('street', 'AR', -60.1, -32.1, 'Córdoba'),
      feature('street', 'AR', -60.1, -32.1, 'Córdoba'),
      feature('street', 'AR', -60.2, -32.2, 'San Martín'),
      feature('street', 'AR', -60.3, -32.3, 'Mitre'),
      feature('street', 'AR', -60.4, -32.4, 'Sarmiento'),
    ])
    const places = await findPlaces('calle', undefined, doFetch)
    assert.deepEqual(
      places.map((place) => place.label.split(',')[0]),
      ['Córdoba', 'San Martín', 'Mitre'],
    )
  })
})
