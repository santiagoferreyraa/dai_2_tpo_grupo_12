import MobileNav from './MobileNav'
import TopBar from './TopBar'

/**
 * La navegación de la aplicación, en sus dos formas.
 *
 * Son **la misma navegación** —las secciones salen de `navSections.tsx`— repartida distinto
 * según el tamaño de la pantalla:
 *
 * - En escritorio y tablet es una sola franja flotante arriba: el buscador, las secciones y el
 *   perfil. Antes había además un riel de íconos a la izquierda; se fue cuando las secciones
 *   pasaron a decir su nombre en la franja, porque tener la misma navegación dos veces en
 *   pantalla es peor que tenerla una sola vez bien.
 * - En el celular es una sola barra abajo, al alcance del pulgar, con las cinco secciones
 *   —perfil incluido— porque ahí no sobra lugar para una franja aparte. El Backoffice del
 *   administrador, que antes era una burbuja flotante, ahora es una sección más de esa barra.
 *
 * Cada pieza se esconde con `md:` desde su propio archivo; acá no hay ninguna decisión, solo el
 * lugar donde `App.tsx` monta una línea en vez de dos.
 */
export default function Navbar() {
  return (
    <>
      <TopBar />
      <MobileNav />
    </>
  )
}
