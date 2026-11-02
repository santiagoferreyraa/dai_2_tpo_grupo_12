import { Link } from 'react-router'

/**
 * Pantalla de "no encontrada".
 *
 * Sale en dos casos que, a propósito, no se distinguen: una dirección que no existe y una que
 * existe pero pide un rol que el usuario no tiene. Decirle "no tenés permiso" sería confirmarle
 * que la sección existe, que es justo lo que el backend evita desde SEC-05 respondiendo 404 en
 * vez de 403 (decidido por Manuel el 01/11; reemplaza a la pantalla de acceso denegado).
 *
 * Por eso se dibuja en el lugar y no tiene dirección propia: si el guard redirigiera, la barra
 * cambiaría solo en el caso del permiso y la diferencia quedaría a la vista.
 *
 * El botón de vuelta no es un adorno. Sin una salida a la vista, la única que queda es el
 * botón de atrás del navegador.
 */
export default function NotFoundPage() {
  return (
    <section className="flex h-full items-center justify-center p-6">
      <div className="border-border bg-surface w-full max-w-sm rounded-2xl border p-8 text-center">
        <h1 className="text-text text-2xl font-semibold">Página no encontrada</h1>
        <p className="text-text-muted mt-2 text-sm">La dirección que buscás no existe.</p>

        <Link
          className="brand-fill text-on-primary mt-6 inline-block rounded-lg px-4 py-2.5 text-sm font-semibold"
          to="/"
        >
          Volver al inicio
        </Link>
      </div>
    </section>
  )
}
