import { randomUUID } from 'node:crypto'

import { test as base, expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'

/**
 * Lo que comparten los escenarios de punta a punta.
 *
 * - **Nada sale de la máquina.** Los mosaicos del mapa, las fuentes de Google, la ruta de OSRM y
 *   la búsqueda de direcciones son servicios de terceros: si uno se cae o tarda, el escenario
 *   fallaría por algo que no es Ecopedia. Se cortan todos y la pantalla tiene que funcionar igual
 *   (el mapa sin fondo, el texto con la fuente del sistema).
 * - **Cada escenario tiene su conductor**, recién registrado por la API. Así no dependen del orden
 *   ni de lo que dejó otro, y se pueden correr en paralelo o repetir contra la misma base.
 */

export interface Driver {
  fullName: string
  email: string
  password: string
}

interface Fixtures {
  /** Un conductor nuevo, registrado por la API y sin sesión abierta. */
  driver: Driver
}

export const test = base.extend<Fixtures>({
  context: async ({ context, baseURL }, use) => {
    const ownHost = new URL(baseURL ?? 'http://localhost').host
    await context.route(
      (url) => url.host !== ownHost,
      (route) => route.abort(),
    )
    await use(context)
  },

  driver: async ({ request }, use) => {
    const driver = newDriver()
    const response = await request.post('/api/auth/register', {
      data: { email: driver.email, password: driver.password, fullName: driver.fullName },
    })
    expect(response.status(), 'el registro del conductor de prueba').toBe(201)
    await use(driver)
  },
})

export { expect }

/** Datos de un conductor que todavía no existe. El mail es único en cada llamada. */
export function newDriver(): Driver {
  const id = randomUUID().slice(0, 8)
  return {
    fullName: `Conductor E2E ${id}`,
    email: `e2e.${id}@ecopedia.test`,
    password: `E2e-${randomUUID()}`,
  }
}

/** Entra por la pantalla de login, como lo haría la persona. */
export async function logIn(page: Page, driver: Driver): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Contraseña').fill(driver.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  /*
    La ficha del perfil se llama con lo que muestra —el nombre y el rol—, no con el correo, que no se
    ve (QA-21, WCAG 2.5.3 "Label in Name"). Por eso se la busca por el nombre completo.
  */
  await expect(page.getByRole('link', { name: driver.fullName })).toBeVisible()
}

/**
 * Le carga una tarjeta por la API. La carga por pantalla es un escenario propio
 * (`payment-methods.spec.ts`); acá es solo una condición previa.
 */
export async function addCardByApi(request: APIRequestContext, driver: Driver): Promise<void> {
  const login = await request.post('/api/auth/login', {
    data: { email: driver.email, password: driver.password },
  })
  expect(login.ok(), 'el login del conductor de prueba').toBe(true)
  const { token } = (await login.json()) as { token: string }

  const card = await request.post('/api/payment-methods', {
    headers: { Authorization: `Bearer ${token}` },
    // La 4242 de la pasarela simulada aprueba todo (README, "Tarjetas de prueba").
    data: {
      number: '4242424242424242',
      expiryMonth: 12,
      expiryYear: new Date().getFullYear() + 3,
      holderName: driver.fullName,
    },
  })
  expect(card.status(), 'el alta de la tarjeta del conductor de prueba').toBe(201)
}
