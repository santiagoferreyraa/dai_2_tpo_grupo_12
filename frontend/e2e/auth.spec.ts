import { expect, newDriver, test } from './fixtures'

/**
 * Escenario 1 · Registro y login.
 *
 * Alguien se registra desde la pantalla, entra, cierra la sesión, se equivoca de contraseña y
 * vuelve a entrar. El registro no abre sesión: lleva al login con un aviso.
 */
test('registro, login fallido y login correcto', async ({ page }) => {
  const driver = newDriver()

  await page.goto('/register')
  await page.getByLabel('Nombre completo').fill(driver.fullName)
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Contraseña', { exact: true }).fill(driver.password)
  await page.getByLabel('Repetir contraseña').fill(driver.password)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()

  await expect(page).toHaveURL('/login')
  await expect(page.getByText('Tu cuenta se creó. Entrá con tu email y contraseña.')).toBeVisible()

  // Con la cuenta recién creada, entra.
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Contraseña').fill(driver.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  // La ficha del perfil se nombra con el nombre que muestra, no con el correo (QA-21).
  const profile = page.getByRole('link', { name: driver.fullName })
  await expect(profile).toBeVisible()

  // Cierra la sesión desde el perfil.
  await profile.click()
  await page.getByRole('button', { name: 'Cerrar sesión' }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cerrar sesión' }).click()
  await expect(page.getByRole('banner').getByRole('link', { name: 'Iniciar sesión' })).toBeVisible()

  // Una contraseña equivocada: el mismo mensaje que un mail inexistente, y sigue sin sesión.
  await page.goto('/login')
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Contraseña').fill(`${driver.password}-mal`)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('alert')).toHaveText('El email o la contraseña no son correctos.')
  await expect(page).toHaveURL('/login')

  // Con la correcta, entra.
  await page.getByLabel('Contraseña').fill(driver.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(profile).toBeVisible()
  await expect(page).not.toHaveURL(/\/login/)
})
