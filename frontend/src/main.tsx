import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'

import { clearSessionIfExpired, restoreSession } from '@/features/auth/session'
import '@/index.css'
import { onAuthRejected } from '@/lib/api'
import { router } from '@/routes/routes'

/*
 * La sesión se rehidrata ANTES del primer render, no dentro de un efecto: si se hiciera
 * después, la primera pasada del guard vería "sin sesión" y mandaría al login a alguien que
 * la tenía guardada.
 */
restoreSession()

/*
 * Y se le enseña al cliente HTTP qué hacer cuando el backend rechaza una llamada. Cierra la
 * sesión solo si el token venció: mirar el vencimiento no depende del código que elija cada
 * módulo para rechazar. El porqué completo está en features/auth/session.ts.
 */
onAuthRejected(clearSessionIfExpired)

const container = document.getElementById('root')

if (!container) {
  throw new Error('No se encontró el elemento #root en index.html')
}

createRoot(container).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
