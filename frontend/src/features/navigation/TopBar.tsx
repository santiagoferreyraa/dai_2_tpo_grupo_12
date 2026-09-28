import { useLayoutEffect, useRef } from 'react'
import { Link, NavLink, useLocation } from 'react-router'

import { useSession } from '@/features/auth/session'
import NotificationBell from '@/features/notifications/NotificationBell'
import ThemeToggle from '@/features/theme/ThemeToggle'

import { isSectionActive, MAIN_SECTIONS, visibleSections } from './navSections'
import ProfilePill from './ProfilePill'
import SearchBox from './SearchBox'

/**
 * La barra de escritorio y tablet: buscador a la izquierda, secciones al medio, tema y perfil a la
 * derecha.
 *
 * **Es una grilla de tres columnas y no un `flex` con `justify-between`.** La diferencia importa:
 * con `flex`, el bloque del medio queda centrado entre los dos extremos, así que su posición
 * depende de cuánto mida el buscador y cuánto mida la ficha del usuario. Un nombre largo corre las
 * secciones. Con `grid-cols-[1fr_auto_1fr]` el centro está centrado **contra la ventana**, que es
 * lo que el ojo espera, y los costados crecen hacia afuera.
 *
 * **El punto de la sección activa es UNO y se desliza.** Antes había uno por enlace, prendiéndose
 * y apagándose: eso dice dónde estás pero no de dónde venías. Uno solo que viaja cuenta el
 * movimiento, y es el mismo gesto que hace el círculo de la barra del celular.
 *
 * Flota sobre el contenido y por eso es `fixed` y no una fila más del layout. `App.tsx` le reserva
 * el alto al `<main>` para que nada quede tapado.
 */
export default function TopBar() {
  const { pathname } = useLocation()
  const session = useSession()
  /* Solo las que le corresponden a quien mira. Ver `visibleSections`. */
  const sections = visibleSections(MAIN_SECTIONS, session?.role ?? null)

  const listRef = useRef<HTMLElement>(null)
  const dotRef = useRef<HTMLSpanElement>(null)

  /* Si el punto ya ocupó su lugar por primera vez. Ver el final de `place`. */
  const primed = useRef(false)

  /*
   * El punto se mide, no se calcula: los cuatro rótulos tienen anchos distintos, así que no hay
   * fracción del ancho total que sirva —como sí la hay en el celular, donde las cinco secciones
   * son íconos iguales—.
   *
   * La posición se escribe directo sobre el elemento y no pasa por el estado de React. No es un
   * atajo: el punto no es información de la aplicación, es el resultado de una medición del
   * navegador. Guardarlo en estado obligaría a renderizar toda la barra de nuevo cada vez que
   * cambia el ancho de la ventana, para mover un elemento que no le importa a nadie más.
   *
   * `useLayoutEffect` y no `useEffect` para que esto ocurra antes de pintar: con el efecto normal
   * se alcanza a ver un cuadro con el punto en la sección anterior.
   */
  useLayoutEffect(() => {
    const list = listRef.current
    const dot = dotRef.current
    if (list === null || dot === null) return

    const place = () => {
      const active = list.querySelector<HTMLElement>('[data-active="true"]')

      /*
        Una ruta que no está en la barra —el perfil, o el detalle de una estación mañana— no tiene
        qué marcar. El punto se desvanece en su último lugar: no decir nada es mejor que señalar
        la sección equivocada.
      */
      if (active === null) {
        dot.style.opacity = '0'
        return
      }

      const listBox = list.getBoundingClientRect()
      const activeBox = active.getBoundingClientRect()

      /* El centro del enlace, no su borde: el punto va debajo del medio del rótulo. */
      const center = activeBox.left - listBox.left + activeBox.width / 2
      dot.style.transform = `translateX(${center}px) translateX(-50%)`
      dot.style.opacity = '1'

      /*
        La primera colocación ocurre después del primer render, así que el punto pasa de "sin
        posición" a "debajo de la sección activa". Con la transición prendida eso se ve como un
        punto que entra deslizándose desde el borde izquierdo en CADA carga de página, que parece
        una falla y no una animación.

        Por eso arranca con la transición apagada y se la prende recién ahora. La lectura del
        rectángulo en el medio no es decorativa: obliga al navegador a aplicar los estilos de
        arriba ANTES de que la transición exista, que es lo que evita que ese primer salto se
        anime.
      */
      if (!primed.current) {
        primed.current = true
        dot.getBoundingClientRect()
        dot.classList.remove('top-nav__dot--idle')
      }
    }

    place()

    /*
      El `ResizeObserver` cubre lo que un efecto sobre la ruta no ve: la ventana que cambia de
      ancho y —el caso que de verdad se nota— la tipografía que termina de cargar y corre todos
      los rótulos unos píxeles.
    */
    const observer = new ResizeObserver(place)
    observer.observe(list)
    return () => observer.disconnect()
  }, [pathname, sections.length])

  return (
    /*
      **El z-index tiene que ganarle al mapa, no solo a la página.** La franja ordena su propio
      contenido —y la lista de coincidencias del buscador cuelga de ella—, pero como capa compite
      con lo que flota sobre el mapa: los filtros de estaciones viven en 1120. Con la franja en
      1050, esa lista quedaba encerrada en un contexto que pierde entero, y las burbujas de los
      filtros se dibujaban por delante de las sugerencias. Se veía solo en pantalla ancha, porque
      en celular el buscador es el que está ADENTRO de esa misma fila.

      1140 lo deja por encima de todo lo del mapa y por debajo de las ventanas modales, que son
      1200 y tienen que tapar la navegación.
    */
    <header className="fixed inset-x-0 top-0 z-[1140] hidden md:block">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-6 px-6 py-4">
        {/*
          El logo y el buscador, juntos a la izquierda.

          El logo se dibuja como MÁSCARA y no como `<img>`, y eso es lo que le permite ser del
          verde de la marca —degradado incluido— en vez del verde con el que está guardado el
          archivo: de un `mask-image` el navegador usa la silueta, y el color lo pone el fondo del
          elemento. Ver `.brand-mark` en index.css.

          Lleva a la portada, así que es un enlace y no un dibujo suelto: es lo que cualquiera
          espera de un logo, y sin texto adentro necesita su propio nombre para el lector de
          pantalla.
        */}
        {/*
          Sin `justify-self`: la celda se estira y el buscador puede pedir su ancho contra ella.
          Con `justify-self-start` la celda se encogía hasta el contenido, y ahí el `w-full` del
          buscador se resolvía contra sí mismo y quedaba en la mitad de lo que tenía disponible.
        */}
        <div className="flex items-center gap-3">
          <Link to="/" aria-label="Ecopedia, ir a la portada">
            <span className="brand-mark brand-fill block h-9 w-9" aria-hidden="true" />
          </Link>

          <SearchBox className="w-full max-w-sm" />
        </div>

        <nav
          ref={listRef}
          aria-label="Navegación principal"
          className="relative flex items-center gap-1"
        >
          {sections.map((section) => {
            const active = isSectionActive(section, pathname)
            return (
              <NavLink
                key={section.to}
                to={section.to}
                end={section.end}
                data-active={active ? 'true' : 'false'}
                className={`rounded-lg px-3.5 py-2 text-sm transition-colors ${
                  active ? 'text-primary font-semibold' : 'text-text-muted hover:text-text'
                }`}
              >
                {section.label}
              </NavLink>
            )
          })}

          {/*
            El punto. Se posiciona con `transform` y no con `left` porque el navegador puede
            moverlo sin rehacer el layout de la fila en cada cuadro.

            Los dos `translateX` no se pueden juntar en uno: el primero está en píxeles y sale de
            la medición, y el segundo es la mitad del ANCHO DEL PUNTO, que es lo que lo centra
            sobre esa medida. Un porcentaje adentro de `translate` se resuelve contra el propio
            elemento, así que sumarlos daría cualquier cosa.
          */}
          <span
            ref={dotRef}
            aria-hidden="true"
            className="top-nav__dot top-nav__dot--idle brand-fill absolute bottom-0.5 left-0 h-1 w-1 rounded-full opacity-0"
          />
        </nav>

        {/*
          Tema, avisos y perfil. La campanita va **en el medio y no en una punta**, y no es un
          orden arbitrario: los tres son controles del usuario y no de la aplicación, pero solo el
          del medio cambia con lo que pasa afuera. Entre dos piezas quietas, el círculo con el
          número es lo único que se mueve, y ahí es donde el ojo lo encuentra sin buscarlo.

          Sin sesión no se dibuja: ver `NotificationBell`.
        */}
        <div className="flex items-center gap-3 justify-self-end">
          <ThemeToggle />
          <NotificationBell />
          <ProfilePill />
        </div>
      </div>
    </header>
  )
}
