import { useId } from 'react'

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
 * **El elegido se marca con un contorno dibujado encima, no con un borde ni con un anillo.** Un
 * borde ocupa lugar y corre a los vecinos un par de píxeles, así que la grilla entera tiembla al
 * cambiar de elección; un anillo no ocupa lugar pero tampoco tiene dónde poner la palabra que
 * explica qué significa el verde. El contorno va en `SelectedFrame`, acá abajo.
 *
 * **Cada tarjeta proyecta sombra, y es lo que las vuelve objetos y no celdas.** Sobre el panel
 * claro del diálogo, un recuadro apenas más gris se lee como una división de la grilla; levantado,
 * se lee como una ficha que se puede agarrar.
 *
 * **Y al pasar por encima crece.** Antes se aclaraba el fondo, y eso no se puede usar en la
 * elegida: su fondo ya es el claro —lo necesita, ver abajo—, así que el hover no hacía nada en la
 * única tarjeta que además tiene un contorno que seguir. Creciendo, las diecinueve dicen "esto se
 * toca" de la misma forma, con fondo claro o sin él.
 *
 * **Y por eso la grilla lleva relleno propio.** La sombra se dibuja POR FUERA de la caja del
 * elemento, así que no cuenta para el tamaño de la tarjeta y el contenedor que scrollea la recorta
 * contra su borde: la fila de arriba aparecía cortada al ras, como si terminara ahí. Los seis
 * píxeles de relleno son el lugar donde cae. Es el mismo arreglo que la tira de avatares del
 * perfil, que lo necesita por la misma razón.
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
  /*
    El identificador del recorte del elegido. **Generado y no una constante de módulo**: un `id`
    repetido en el documento es ambiguo, y basta con que dos grillas convivan —la del diálogo y
    cualquier otra que venga después— para que el navegador tenga que elegir una. Los dos puntos
    que mete React se sacan porque esto termina adentro de un `url(#…)` de CSS, donde no son un
    carácter válido.
  */
  const clipId = `vehicle-selected-${useId().replace(/:/g, '')}`

  if (models.length === 0) {
    return <p className="text-text-muted p-4 text-sm">Esta marca todavía no tiene modelos.</p>
  }

  return (
    <ul className="grid grid-cols-2 gap-3 p-1.5 sm:grid-cols-3 lg:grid-cols-4">
      {models.map((model) => {
        const selected = model.id === currentId

        return (
          /*
            El `pt` va en TODAS las celdas y no solo en la elegida: es el lugar donde asoma la
            pestaña del cartel, y puesto solo en una, las tarjetas de su fila quedarían corridas
            hacia arriba respecto de ella. Es un porcentaje y no una medida fija porque todo el
            dibujo del marco está en porcentajes — ver el `viewBox` de abajo.
          */
          <li
            key={model.id}
            /*
              El agrandado del hover va acá y no en el botón: la tarjeta elegida es el botón MÁS su
              contorno y su palabra, y creciendo solo el botón, el marco se queda quieto y la
              tarjeta se le sale por los cuatro costados.
            */
            className="relative pt-[7%] transition-transform duration-150 hover:scale-[1.03]"
          >
            {selected && <SelectedFrame clipId={clipId} />}

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
                El redondeo va en porcentaje y no en `rounded-2xl`: el marco de la elegida se
                dibuja con un `viewBox` en porcentajes, así que una medida fija en píxeles se le
                despegaría en las esquinas apenas la grilla cambia de columnas y las tarjetas de
                ancho. En porcentaje las dos curvas son la misma a cualquier tamaño.

                La transición nombra la sombra en vez de usar `transition-all`, que es más corto
                pero también animaría el marco del elegido: un marco que se desvanece al entrar se
                lee como que la elección todavía se está decidiendo.
              */
              /*
                La tarjeta elegida se RECORTA con la misma forma que dibuja el contorno. Sin esto
                su relleno sigue siendo un rectángulo redondeado entero y asoma por encima del
                tramo hundido del borde, que es justo donde va la palabra.

                Y por eso también cambia la sombra: un recorte se lleva puesto el `box-shadow`
                —se dibuja fuera de la caja, o sea en lo que se acaba de recortar—, así que la
                elegida la pide como filtro, que se aplica después del recorte y sigue el contorno
                de la forma nueva.
              */
              style={selected ? { clipPath: `url(#${clipId})` } : undefined}
              className={`flex aspect-square w-full cursor-pointer flex-col items-center justify-center gap-2 p-3 transition-[box-shadow] ${
                /*
                  **La elegida va con el fondo entero y las demás al 60%**, y no es un capricho:
                  sobre el panel de la ventana las dos se ven iguales, pero la elegida lleva la
                  sombra como FILTRO, y un filtro se dibuja por detrás de lo que pinta el elemento.
                  Con el fondo a medias la sombra se transparentaba a través de la tarjeta y le
                  dejaba un halo gris por dentro del contorno, que se leía como un segundo trazo.
                */
                selected
                  ? 'bg-surface drop-shadow-[0_4px_10px_rgb(0_0_0/0.18)]'
                  : 'bg-surface/60 rounded-[10%] shadow-[0_4px_14px_-4px_rgb(0_0_0/0.18)] hover:shadow-[0_8px_22px_-6px_rgb(0_0_0/0.24)]'
              }`}
            >
              {/*
                La foto se queda con TODO el alto que le sobra a la tarjeta, en vez de con una
                medida fija. Con `h-16` el auto ocupaba menos de la mitad de una tarjeta que es
                cuadrada, y el resto era aire: en un teléfono, donde la grilla es de dos columnas
                y las tarjetas son grandes, se veía una foto chiquita en el medio de un recuadro
                vacío. Creciendo con la caja, el auto se ve del mismo tamaño relativo con dos
                columnas o con cuatro.
              */}
              <VehicleImage model={model} className="min-h-0 w-full flex-1" />
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

/**
 * El marco del auto elegido: un contorno verde que baja un escalón para dejarle lugar a la palabra.
 *
 * **El recuadro solo no alcanzaba.** Un borde verde alrededor de una tarjeta dice que esa tarjeta
 * está distinguida, pero no POR QUÉ: podía leerse como "la que está bajo el puntero" o como "la
 * última que toqué". Con la palabra escrita no hay nada que interpretar, y de paso la marca deja
 * de depender del color, que es lo que alguien que no distingue el verde no puede ver.
 *
 * **El contorno es un trazo y no un `border`, y ese es todo el motivo de este componente.** Un
 * borde de CSS da la vuelta completa: para que la palabra no quede tachada por la línea hay que
 * taparla con un rectángulo del color de atrás, y ese color no existe —la ventana es vidrio, así
 * que lo que se ve ahí es una mezcla y no un token—. Con el trazo dibujado a mano no hay nada que
 * tapar: la línea se desvía, rodea la palabra y vuelve a bajar, y por el hueco se ve lo que haya
 * atrás, sea lo que sea.
 *
 * **La palabra va ARRIBA del contorno, no adentro de una pestaña.** El borde de arriba corre a su
 * altura, baja un escalón corto en el tramo del medio y vuelve a subir del otro lado; la palabra
 * se apoya en el aire que deja ese escalón. Probé encerrarla en una pestaña que sobresale —con los
 * codos para un lado y para el otro— y las dos versiones leen como una etiqueta pegada encima de
 * la tarjeta, cuando lo que tiene que parecer es la tarjeta misma haciéndole lugar.
 *
 * **El contorno cae justo sobre el borde de la tarjeta**, no un poco adentro: corrido hacia
 * adentro, las esquinas de la tarjeta asoman por afuera de la línea. Por el mismo motivo la
 * tarjeta se recorta con esta forma —ver el `clipPath`—: el relleno tiene que terminar donde
 * termina el trazo, también en el tramo hundido. Por eso el trazo va
 * por los bordes exactos del `viewBox` y el dibujo sale con `overflow-visible` — si no, el
 * navegador le corta la mitad de afuera a la línea.
 *
 * **Todo está en porcentajes del ancho de la tarjeta.** El `viewBox` mide 100 × 107 y la celda
 * mide el ancho de la tarjeta más el 7% que le agrega el `pt`, así que las dos proporciones son la
 * misma y el dibujo no se deforma. El trazo queda afuera de esa cuenta —`non-scaling-stroke`—
 * porque el grosor de una línea no es una medida de la tarjeta: en una grilla de dos columnas se
 * vería el doble de gruesa que en una de cuatro.
 */
function SelectedFrame({ clipId }: { clipId: string }) {
  return (
    <>
      {/*
        El recorte de la tarjeta: la MISMA forma que el trazo, en coordenadas de 0 a 1 sobre la
        caja del botón —`objectBoundingBox`—, que es lo que la vuelve independiente del tamaño.

        Va escrita dos veces, acá y en el trazo, y no hay forma de compartirla: una es un contorno
        sobre la celda entera —que incluye el aire de arriba, donde va la palabra— y la otra es un
        relleno sobre la tarjeta sola. Lo único que tienen que mantener igual es el escalón: el
        0,02 de acá es el salto de 2 unidades de allá, porque la tarjeta es cuadrada y mide 100.
      */}
      <svg width="0" height="0" aria-hidden="true" className="absolute">
        <clipPath id={clipId} clipPathUnits="objectBoundingBox">
          <path d="M0.1 0H0.18C0.195 0 0.195 0.02 0.21 0.02H0.79C0.805 0.02 0.805 0 0.82 0H0.9A0.1 0.1 0 0 1 1 0.1V0.9A0.1 0.1 0 0 1 0.9 1H0.1A0.1 0.1 0 0 1 0 0.9V0.1A0.1 0.1 0 0 1 0.1 0Z" />
        </clipPath>
      </svg>

      <svg
        viewBox="0 0 100 107"
        /*
          Sin esto el navegador centraría el dibujo y le dejaría barras a los costados en cuanto la
          caja no cayera exactamente en 100 × 107. No deforma nada: la caja tiene esa proporción.
        */
        preserveAspectRatio="none"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        vectorEffect="non-scaling-stroke"
        aria-hidden="true"
        className="text-selected pointer-events-none absolute inset-0 h-full w-full overflow-visible"
      >
        <path
          vectorEffect="non-scaling-stroke"
          d="M10 7H18C19.5 7 19.5 9 21 9H79C80.5 9 80.5 7 82 7H90A10 10 0 0 1 100 17V97A10 10 0 0 1 90 107H10A10 10 0 0 1 0 97V17A10 10 0 0 1 10 7Z"
        />
      </svg>

      {/*
        La palabra, apoyada sobre el escalón. El `aria-hidden` del dibujo y la ausencia de fondo
        acá son la misma decisión: lo único que hay arriba de la tarjeta es texto, y detrás se ve
        la ventana.

        Quien usa un lector de pantalla ya se entera por el `aria-pressed` del botón, así que esto
        no se anuncia aparte — sería la misma información dos veces, y además suelta, porque el
        cartel vive fuera del botón que describe.
      */}
      <span
        aria-hidden="true"
        className="text-selected pointer-events-none absolute inset-x-0 top-[0.8%] text-center text-[10px] leading-none font-extrabold tracking-wide uppercase"
      >
        Seleccionado
      </span>
    </>
  )
}
