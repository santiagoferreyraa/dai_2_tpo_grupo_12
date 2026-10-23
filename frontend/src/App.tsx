import { Outlet } from 'react-router'

import IridescentBackdrop from '@/features/backdrop/IridescentBackdrop'
import ActiveBookingBanner from '@/features/bookings/ActiveBookingBanner'
import Navbar from '@/features/navigation/Navbar'
import NotificationToasts from '@/features/notifications/NotificationToasts'

/**
 * Layout raíz de la aplicación: lo que se ve en todas las pantallas.
 *
 * El contenido de cada pantalla lo inyecta <Outlet /> según la ruta activa.
 * Las pantallas NO se agregan acá: cada feature declara sus rutas en su propio
 * archivo. Ver src/routes/routes.tsx.
 *
 * La navegación tampoco se escribe acá. Son tres piezas —la franja de arriba, el riel de la
 * izquierda y la barra del celular— y viven en `features/navigation`: este archivo monta una
 * línea y no sabe cuántas secciones hay ni cuál está activa.
 */
export default function App() {
  return (
    /*
      `h-full` y no `min-h-full`: la altura tiene que quedar DEFINIDA, porque las pantallas
      de adentro piden `h-full` para medir la ventana y un porcentaje no resuelve contra un
      `min-height`. Con `min-h-full` el contenedor terminaba midiendo su contenido, así que
      la pantalla de estaciones crecía con el formulario abierto en vez de scrollear adentro.

      `min-h-0` en el <main> es la otra mitad: sin eso, un hijo que scrollea estira al padre
      en lugar de recortarse, porque la altura mínima por defecto de un ítem flex es su
      contenido.

      `app-shell` es el degradado del fondo, que cambia con el tema. Ver index.css.
    */
    <div className="app-shell relative flex h-full flex-col">
      {/*
        El fondo animado, debajo de todo. Es el PRIMER hijo y está posicionado, así que lo pinta
        antes que el resto; el <main> lleva `relative` para quedar por encima. Sin eso el lienzo
        taparía el contenido, porque un elemento posicionado se dibuja después de uno que no lo
        está, aunque venga antes en el orden.

        El degradado de `.app-shell` sigue detrás y no es redundante: es lo que se ve mientras el
        lienzo no dibuja —en el mapa, sin WebGL 2, o durante la disolvencia de entrada—.
      */}
      <IridescentBackdrop />
      {/*
        Las dos piezas de la navegación están FUERA del flujo: la de escritorio anclada a la
        ventana y la del celular fija abajo. Ninguna ocupa lugar acá, así que el <main> mide la
        ventana entera y es él quien reserva el espacio con su propio `padding`.

        Se hace así y no dejándolas en el flujo por dos motivos. El contenido que scrollea les
        pasa por debajo, que es lo que le da algo que difuminar al vidrio; y en el celular es lo
        que deja al mapa llegar hasta el borde de abajo y verse por el hueco de la barra, en vez
        de terminar cortado contra ella.
      */}
      <Navbar />

      {/*
        Los recuadros de aviso, montados una sola vez para toda la aplicación: un aviso puede llegar
        estando en cualquier pantalla, y ponerlos en cada una sería repetir el mismo montaje cinco
        veces y olvidarse en la sexta.

        No ocupan lugar en el layout —se dibujan en un portal, anclados a la ventana— así que esta
        línea no mueve nada de lo de abajo. Ver `NotificationToasts`.
      */}
      <NotificationToasts />

      {/*
        Columna flex, no un bloque suelto: así una pantalla que quiere ocupar todo el alto
        —el mapa de estaciones— crece como ítem del flex en vez de medir su contenido. El
        ABM no lo necesita, pero tampoco le molesta: sigue scrolleando adentro.

        El `padding` de arriba es el lugar de la franja superpuesta. En el celular no hay franja,
        y la barra de abajo flota sobre el contenido a propósito.
      */}
      <main className="relative flex min-h-0 flex-1 flex-col md:pt-20">
        {/*
          La reserva activa, arriba del contenido de cualquier pantalla y DENTRO del flujo: empuja
          la pantalla hacia abajo en vez de taparle el borde de arriba. Solo en PC y tablet, y solo
          si hay una reserva. Ver `ActiveBookingBanner`.
        */}
        <ActiveBookingBanner />
        <Outlet />
      </main>
    </div>
  )
}
