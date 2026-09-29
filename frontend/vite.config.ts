import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    alias: {
      // Permite importar como "@/componentes/Foo" en vez de "../../componentes/Foo".
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    port: 5173,

    /*
      Todo lo que empiece con /api se redirige al backend, así el front llama a rutas relativas
      y no hay CORS que configurar en desarrollo.

      Los backends son TRES procesos, no uno: Reservas vive en ecopedia-charging (8082), Pagos
      en ecopedia-integration (8083) y el resto en ecopedia-core (8081). Cada uno es un
      artefacto desplegable distinto. Ver ARQUITECTURA §6.4.

      El orden de estas claves importa: Vite se queda con la PRIMERA que coincida con la ruta, y
      '/api' coincide con todo. Las dos reglas específicas tienen que ir arriba o las llamadas a
      reservas y a medios de pago terminan en core, que no las conoce y contesta 404.
    */
    proxy: {
      '/api/bookings': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/payment-methods': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      /*
        El asistente del chat: un proceso en Node, fuera de los backends de Java.

        `xfwd` le pasa la dirección real del navegador en X-Forwarded-For. Sin eso, para el
        agente todos los pedidos vienen de esta máquina y el límite de preguntas por minuto sería
        uno solo para todos los que usen la app, no uno por persona.
      */
      '/api/agent': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        xfwd: true,
      },
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
