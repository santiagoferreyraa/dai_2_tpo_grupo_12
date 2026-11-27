import { expect, test } from './fixtures'

/**
 * Escenario 5 · Acceso denegado.
 *
 * Sin sesión, una pantalla privada lleva al login y, al entrar, vuelve a donde se quería ir.
 * Con sesión pero sin el rol, el backoffice se ve igual que una dirección que no existe: decir
 * "no tenés permiso" confirmaría que la sección está ahí (SEC-05). Lo de pantalla no es la
 * seguridad: el backend responde lo mismo, y eso también se comprueba.
 */
test('sin sesión lleva al login y sin el rol no muestra el backoffice', async ({
  page,
  driver,
}) => {
  // Sin sesión: al login, y después de entrar, de vuelta a la pantalla pedida.
  await page.goto('/profile/payment-methods')
  await expect(page).toHaveURL('/login')
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Contraseña').fill(driver.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page).toHaveURL('/profile/payment-methods')
  await expect(page.getByRole('heading', { name: 'Medios de pago' })).toBeVisible()

  // Un conductor en el backoffice: la misma página que una dirección inventada.
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'Página no encontrada', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: /usuarios|estaciones|tarifas/i })).toHaveCount(0)

  await page.goto('/esta-pagina-no-existe')
  await expect(page.getByRole('heading', { name: 'Página no encontrada', level: 1 })).toBeVisible()

  // Y el backend le contesta al conductor lo mismo que a una ruta inexistente: el único 404, sin
  // id ni motivo. Solo cambia `instance`, que repite la ruta pedida (RFC 9457).
  const token = await page.evaluate(() => {
    const stored = localStorage.getItem('ecopedia.session')
    return stored === null ? null : (JSON.parse(stored) as { token: string }).token
  })
  expect(token, 'el token de la sesión guardada').not.toBeNull()
  const headers = { Authorization: `Bearer ${token}` }
  const admin = await page.request.get('/api/admin/dashboard', { headers })
  const missing = await page.request.get('/api/no-existe', { headers })
  expect(admin.status()).toBe(404)
  expect(missing.status()).toBe(404)
  const withoutInstance = async (response: typeof admin) => {
    const { instance: _instance, ...body } = (await response.json()) as Record<string, unknown>
    return body
  }
  expect(await withoutInstance(admin)).toEqual(await withoutInstance(missing))
})
