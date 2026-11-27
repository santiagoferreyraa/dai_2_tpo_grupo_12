import { expect, logIn, test } from './fixtures'

/**
 * Escenario 3 · Alta de tarjeta.
 *
 * Un conductor sin tarjetas carga una desde Medios de pago. Primero con un número que no pasa
 * la validación (el error aparece pegado al campo y no se guarda nada), después con la 4242 de
 * la pasarela simulada, que se guarda: en pantalla quedan solo la marca y los últimos cuatro.
 */
test('cargar una tarjeta desde Medios de pago', async ({ page, driver }) => {
  await logIn(page, driver)
  await page.goto('/profile/payment-methods')
  await expect(page.getByRole('heading', { name: 'Medios de pago' })).toBeVisible()

  await page.getByRole('button', { name: 'Agregar tarjeta' }).click()
  const number = page.getByRole('textbox', { name: 'Número de tarjeta' })
  await page.getByRole('textbox', { name: 'Vencimiento' }).fill('12/30')
  await page.getByRole('textbox', { name: 'Titular' }).fill(driver.fullName)

  // Un dígito cambiado: no pasa el control de Luhn.
  await number.fill('4242 4242 4242 4241')
  await page.getByRole('button', { name: 'Guardar tarjeta' }).click()
  await expect(number).toHaveAttribute('aria-invalid', 'true')
  await expect(number).toHaveAccessibleDescription(
    'Ese número no es válido. Revisá que esté bien copiado.',
  )
  await expect(page.getByText(/•••• 4241/)).toHaveCount(0)

  // El número bien copiado se guarda.
  await number.fill('4242 4242 4242 4242')
  await page.getByRole('button', { name: 'Guardar tarjeta' }).click()
  await expect(page.getByText('Visa •••• 4242')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Eliminar Visa •••• 4242' })).toBeVisible()
  // El número completo no aparece en ningún lado de la pantalla.
  await expect(page.getByText('4242 4242 4242 4242')).toHaveCount(0)
})
