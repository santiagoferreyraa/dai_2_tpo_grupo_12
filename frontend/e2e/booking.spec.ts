import { addCardByApi, expect, logIn, test } from './fixtures'

/**
 * Escenario 4 · Reserva con checkout.
 *
 * El flujo central: un conductor con tarjeta elige estación y conector en el mapa, el día y el
 * horario, acepta la tolerancia y paga la seña. Por detrás pasan Reservas (la retención y la
 * confirmación), Tarificación (la seña), Pagos y la pasarela simulada (el cobro). Al final la
 * reserva está en su perfil con el mismo horario y la misma seña que se le mostraron.
 *
 * Se reserva para mañana y en el horario que propone la pantalla, que es uno libre: así el
 * escenario no depende de la hora a la que corre ni de lo que reservó una corrida anterior.
 */
test('reservar un conector y pagar la seña', async ({ page, request, driver }) => {
  await addCardByApi(request, driver)
  await logIn(page, driver)

  await page.goto('/stations/map')
  await page.getByRole('main').getByRole('heading', { name: 'Recarga Flores' }).click()
  const detail = page.getByRole('complementary')
  await detail.getByRole('button', { name: /^CHADEMO 50 kW/ }).click()
  await detail.getByRole('button', { name: 'Reservar' }).click()

  // Paso 1: día, hora y duración.
  const dialog = page.getByRole('dialog', { name: 'Recarga Flores' })
  await dialog
    .getByRole('group', { name: 'Día' })
    .getByRole('button', { name: /^Mañana/ })
    .click()
  await dialog.getByRole('group', { name: 'Duración' }).getByRole('button', { name: '1 h' }).click()
  // El párrafo entero: la etiqueta y el horario van en elementos distintos.
  const proposed = dialog.getByRole('paragraph').filter({ hasText: /^Tu horario: / })
  await expect(proposed).toBeVisible()
  const schedule = (await proposed.innerText()).replace(/^Tu horario: /, '').trim()
  expect(schedule, 'el horario propuesto').toMatch(/ de \d\d:\d\d a \d\d:\d\d$/)
  await dialog.getByRole('button', { name: 'Continuar' }).click()

  // Paso 2: el horario queda retenido; se acepta la tolerancia y se paga con la tarjeta.
  await expect(dialog.getByText('Te guardamos este horario por')).toBeVisible()
  // `\s` y no un espacio: el importe sale de `Intl`, que puede separar el signo con un espacio duro.
  const depositLine = await dialog
    .getByRole('definition')
    .filter({ hasText: 'se cobra al confirmar' })
    .innerText()
  const deposit = /\$\s[\d.]+/.exec(depositLine)?.[0]
  expect(deposit, 'la seña que muestra antes de pagar').toBeDefined()

  const pay = dialog.getByRole('button', { name: 'Confirmar y pagar' })
  await expect(pay).toBeDisabled()
  await dialog
    .getByRole('checkbox', { name: /^Acepto que la reserva cuenta con una tolerancia/ })
    .check()
  await expect(dialog.getByRole('radio', { name: 'Visa •••• 4242' })).toBeChecked()
  await pay.click()

  // Confirmada: el mismo horario y la misma seña, cobrada a la tarjeta elegida.
  await expect(dialog.getByText('Reserva confirmada')).toBeVisible()
  await expect(dialog.getByText(schedule, { exact: true })).toBeVisible()
  await expect(
    dialog.getByText(`Cobramos la seña de ${deposit} a tu tarjeta terminada en 4242.`),
  ).toBeVisible()

  // Y queda en el perfil.
  await dialog.getByRole('link', { name: 'Ver mis reservas' }).click()
  await expect(page).toHaveURL('/profile/reservations')
  const active = page.getByRole('region', { name: 'Reserva activa' })
  await expect(active.getByRole('heading', { name: 'Recarga Flores' })).toBeVisible()
  await expect(active.getByText(schedule, { exact: true })).toBeVisible()
  await expect(active.getByText(`Seña ${deposit}`)).toBeVisible()
  await expect(active.getByRole('button', { name: 'Cancelar reserva' })).toBeVisible()
})
