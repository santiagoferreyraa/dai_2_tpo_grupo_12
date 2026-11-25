import { useMediaQuery } from '@/lib/useMediaQuery'

import DesktopDashboard from './components/DesktopDashboard'
import MobileDashboard from './components/MobileDashboard'
import { useDashboardModel } from './useDashboardModel'

/**
 * El tablero de Estadísticas (ECO-301, RF16 y RF19), para el administrador y el operador.
 *
 * Un solo tablero con dos disposiciones: lo que se pide, lo que está elegido y las cuentas viven
 * en `useDashboardModel`, y cada disposición solo decide dónde va cada cosa.
 *
 * Se monta UNA de las dos, con `useMediaQuery` y no con `md:hidden`: la de escritorio lleva un mapa
 * de Leaflet y la del celular no, y dejar las dos en el DOM sería tener un mapa vivo, pidiendo
 * mosaicos, que nadie ve.
 */
export default function AnalyticsPage() {
  const model = useDashboardModel()
  const wide = useMediaQuery('(min-width: 768px)')

  return (
    <section className="flex min-h-0 flex-1 flex-col">
      {wide && <h1 className="sr-only">Estadísticas</h1>}
      {wide ? <DesktopDashboard model={model} /> : <MobileDashboard model={model} />}
    </section>
  )
}
