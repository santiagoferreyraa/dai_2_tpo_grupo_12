import type { VehicleModel } from '../types'
import VehicleImage from './VehicleImage'

/**
 * Los modelos de la marca abierta: foto arriba, nombre abajo.
 *
 * **Cada tarjeta muestra lo justo para RECONOCER, no para decidir.** La foto y el nombre alcanzan
 * para encontrar el auto propio; el conector y las potencias están un toque más adentro, en el
 * detalle. Puestas acá, ocho fichas técnicas juntas convierten una grilla que se mira en una tabla
 * que hay que leer, y nadie lee ocho fichas para encontrar su propio auto.
 *
 * **Las tarjetas son cuadradas.** Los modelos van de un hatchback a una camioneta, y con el alto
 * libre cada foto le daría una altura distinta a su fila: la grilla quedaría escalonada. Con la
 * caja fija, lo que cambia es cuánto ocupa el auto adentro, que es lo que uno espera al comparar
 * fotos de autos.
 *
 * **El elegido se marca con un anillo, no con un borde**, por lo mismo que los avatares del
 * perfil: un borde ocupa lugar y corre a los vecinos un par de píxeles, así que la grilla entera
 * tiembla al cambiar de elección.
 *
 * **Cada tarjeta proyecta sombra, y es lo que las vuelve objetos y no celdas.** Sobre el panel
 * claro del diálogo, un recuadro apenas más gris se lee como una división de la grilla; levantado,
 * se lee como una ficha que se puede agarrar. La sombra crece al pasar por encima, que es la forma
 * más barata de decir "esto se toca" sin ponerle un borde de color a cada una.
 *
 * **Y por eso la grilla lleva relleno propio.** Un anillo se dibuja POR FUERA de la caja del
 * elemento —dos píxeles de anillo más dos de separación—, así que no cuenta para el tamaño de la
 * tarjeta y el contenedor que scrollea lo recorta contra su borde: la fila de arriba aparecía con
 * el anillo cortado al ras, como si la tarjeta terminara ahí. Los seis píxeles de relleno son el
 * lugar donde el anillo cabe. Es el mismo arreglo que la tira de avatares del perfil, que lo
 * necesita por la misma razón.
 */
export default function VehicleModelGrid({
  models,
  currentId,
  onOpen,
}: {
  models: VehicleModel[]
  /** El modelo que ya está elegido, para marcarlo. `null` si no hay ninguno. */
  currentId: number | null
  onOpen: (model: VehicleModel) => void
}) {
  if (models.length === 0) {
    return <p className="text-text-muted p-4 text-sm">Esta marca todavía no tiene modelos.</p>
  }

  return (
    <ul className="grid grid-cols-2 gap-3 p-1.5 sm:grid-cols-3 lg:grid-cols-4">
      {models.map((model) => {
        const selected = model.id === currentId

        return (
          <li key={model.id}>
            <button
              type="button"
              onClick={() => {
                onOpen(model)
              }}
              /*
                `aria-pressed` y no `aria-current`: el botón no lleva a ningún lado, dice si ESTE
                es el auto que el conductor tiene puesto. Sin esto, quien usa lector de pantalla
                recorre ocho botones idénticos sin enterarse de cuál es el suyo.
              */
              aria-pressed={selected}
              /*
                La transición nombra fondo y sombra en vez de usar `transition-all`, que es más
                corto pero también animaría el anillo del elegido: un anillo que se desvanece al
                entrar se lee como que la elección todavía se está decidiendo.
              */
              className={`bg-surface/60 hover:bg-surface flex aspect-square w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl p-3 shadow-[0_4px_14px_-4px_rgb(0_0_0/0.18)] transition-[background-color,box-shadow] hover:shadow-[0_8px_22px_-6px_rgb(0_0_0/0.24)] ${
                selected ? 'ring-primary ring-offset-background ring-2 ring-offset-2' : ''
              }`}
            >
              <VehicleImage model={model} className="h-16 w-full" />
              <span className="text-text w-full truncate text-center text-xs font-bold tracking-tight uppercase">
                {model.name}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
