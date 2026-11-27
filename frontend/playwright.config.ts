import { defineConfig, devices } from '@playwright/test'

/**
 * Las pruebas de punta a punta (QA-20, ECO-170): el navegador contra el sistema corriendo de
 * verdad, sin nada simulado en el medio. Los escenarios están en `e2e/`.
 *
 * Los backends y los simuladores NO los levanta esta configuración: tienen que estar corriendo
 * antes (`pnpm dev` en una máquina del equipo, `qa/ci/start-stack.sh` en CI). Lo único que se
 * levanta acá es el front, y solo en CI: el que ya se armó con `pnpm build`, servido con
 * `vite preview`, que reparte `/api` con el mismo proxy que `pnpm dev`.
 *
 * Para apuntar a otro sistema (el stack de Docker, el servidor), `E2E_BASE_URL`.
 */
const CI = process.env.CI !== undefined && process.env.CI !== ''
const PREVIEW_URL = 'http://localhost:4173'
const baseURL = process.env.E2E_BASE_URL ?? (CI ? PREVIEW_URL : 'http://localhost:5173')

export default defineConfig({
  testDir: './e2e',
  // Cada escenario crea su propio conductor, así que no dependen del orden ni entre sí.
  fullyParallel: true,
  forbidOnly: CI,
  // Un reintento en CI, y Playwright lo informa como "flaky": se ve, no se esconde.
  retries: CI ? 1 : 0,
  // En CI los siete procesos comparten una máquina de dos núcleos con el navegador.
  workers: CI ? 1 : undefined,
  reporter: CI
    ? [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/junit.xml' }]]
    : [['list']],
  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL,
    // La hora de Argentina y no la de la máquina: el runner de GitHub está en UTC, y los
    // horarios de la reserva se dibujan en la hora del navegador.
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer:
    CI && process.env.E2E_BASE_URL === undefined
      ? {
          command: 'pnpm exec vite preview --port 4173 --strictPort',
          url: PREVIEW_URL,
          reuseExistingServer: false,
        }
      : undefined,
})
