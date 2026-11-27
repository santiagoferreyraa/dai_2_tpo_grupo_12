import { expect, test } from './fixtures'

/**
 * Escenario 2 · Búsqueda en el mapa.
 *
 * Sin sesión, a propósito: el mapa es público. Busca una estación por nombre, la abre, y después
 * filtra por tipo de conector. Las estaciones son las 15 de la siembra de core
 * (`V202609071900__seed_stations.sql`), que toda base nueva trae.
 */

/** Las estaciones de la siembra con al menos un conector CHADEMO. */
const WITH_CHADEMO = [
  'Recarga Obelisco',
  'Recarga Recoleta',
  'Estación Belgrano',
  'Recarga Flores',
  'Estación Vicente López',
  'Estación La Plata',
]

test('buscar una estación por nombre y filtrar por conector', async ({ page }) => {
  await page.goto('/stations/map')
  // La lista de estaciones: cada tarjeta lleva el nombre como título de nivel 2, debajo del h1
  // de la pantalla (QA-21: era un h3 y salteaba un nivel, la falla `heading-order` de axe).
  const cards = page.getByRole('main').getByRole('heading', { level: 2 })
  await expect(cards).toHaveCount(15)

  // Por nombre: el buscador sugiere la estación y, al elegirla, la lista queda solo con ella.
  await page
    .getByRole('combobox', { name: 'Buscar estación por nombre o dirección' })
    .fill('Flores')
  const suggestions = page.getByRole('listbox', { name: 'Estaciones que coinciden' })
  await expect(suggestions.getByRole('option')).toHaveCount(1)
  await suggestions.getByRole('button', { name: /^Recarga Flores/ }).click()
  await expect(cards).toHaveText(['Recarga Flores'])

  // Al tocarla se abre su detalle, con sus conectores.
  await cards.first().click()

  const detail = page.getByRole('complementary')
  await expect(detail.getByRole('link', { name: 'Recarga Flores' })).toBeVisible()
  await expect(detail.getByRole('button', { name: /^CHADEMO 50 kW/ })).toBeVisible()
  await expect(detail.getByRole('button', { name: /^TIPO 2 22 kW/ })).toBeVisible()
  await detail.getByRole('button', { name: 'Cerrar detalle' }).click()
  await page.getByRole('button', { name: 'Limpiar búsqueda' }).click()

  // Por conector: quedan solo las que tienen uno CHADEMO.
  await page.getByRole('button', { name: 'CHADEMO', exact: true }).click()
  await expect(page.getByRole('button', { name: 'CHADEMO', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  // Ordenadas por distancia: se comparan como conjunto, para no depender de dónde arranca el mapa.
  await expect
    .poll(async () => (await cards.allInnerTexts()).sort())
    .toEqual([...WITH_CHADEMO].sort())

  // "Todas" saca el filtro.
  await page.getByRole('button', { name: 'Todas' }).click()
  await expect(cards).toHaveCount(15)
})
