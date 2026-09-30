import { CONNECTOR_TYPE_LABEL } from '@/features/terminals/format'

import type { VehicleModel } from '../types'
import VehicleImage from './VehicleImage'

/**
 * Un modelo abierto: el auto grande, su ficha en la barra de vidrio y el botón de elegirlo.
 *
 * **Este paso existe para que la elección sea informada, no para mostrar la foto más grande.** Lo
 * que el conductor elige acá decide qué estaciones le sirven y cuánto va a tardar cada carga, y
 * esos dos números —el conector y el tope de carga— no entran en la grilla. Sin esta pantalla, la
 * elección sería por dibujo.
 *
 * **La ficha es la MISMA barra de vidrio de la portada, puesta de pie.** No es un parecido: son
 * los mismos cuatro datos, en el mismo orden, con el mismo `.glass-panel` que `VehicleHero` usa
 * debajo del auto. Lo único que cambia es la orientación, porque acá el lugar libre está al
 * costado del auto y no abajo. Que sea la misma pieza es lo que hace que el conductor reconozca
 * su ficha la primera vez que abre la portada, en vez de tener que volver a buscar dónde estaba
 * cada número.
 *
 * **Y los cuatro van en 2×2, no en una columna de cuatro.** Apilados, la barra se estira hasta
 * más alto que el auto y el botón de elegir se va abajo del pliegue: el paso final de la elección
 * queda escondido justo en la pantalla que existe para darlo.
 *
 * **El auto pasa POR DEBAJO de la barra, con la cola tapada un toque.** Es lo que las vuelve una
 * sola composición: cortado justo antes, el auto y la ficha se leen como dos recuadros vecinos
 * que comparten fila. Como el vidrio es translúcido, esa parte del auto se sigue viendo a través
 * —igual que las ruedas delanteras en la portada—, así que no se pierde nada.
 *
 * **"Ya es tu auto" reemplaza al botón cuando el modelo es el que está puesto.** Un botón que dice
 * "Elegir este" sobre el auto que uno ya tiene promete un cambio que no va a pasar: tocarlo
 * cerraría la ventana sin que nada se mueva, y eso se lee como que no funcionó.
 */
export default function VehicleModelDetail({
  model,
  selected,
  onSelect,
}: {
  model: VehicleModel
  /** Si este modelo es el que el conductor ya tiene puesto. */
  selected: boolean
  onSelect: () => void
}) {
  const specs = [
    { label: 'Motor', value: `${String(model.motorKw)} kW` },
    { label: 'Conector', value: CONNECTOR_TYPE_LABEL[model.connectorType] },
    { label: 'Carga máx.', value: `${String(model.maxChargeKw)} kW` },
    { label: 'Batería', value: `${String(model.batteryKwh)} kWh` },
  ]

  return (
    <div className="relative flex h-full flex-col">
      {/*
        **Los dos se posicionan sueltos contra esta caja, no se reparten una fila.** Es lo que
        produce el solapado: en dos columnas cada uno termina donde empieza el otro, y no hay forma
        de que la cola quede por debajo del vidrio sin márgenes negativos peleándose con la grilla.

        Debajo de `md` el montaje se apaga y los dos se apilan: en un teléfono, una ficha encima
        del auto no deja ver ninguno de los dos.
      */}
      <div className="flex min-h-0 flex-1 flex-col gap-4 md:relative md:block">
        {/*
          El auto, centrado en la parte del panel que no tapa la ficha.

          `object-contain` sobre una caja con alto y ancho fijos hace todo el trabajo: la foto se
          ajusta sola y queda centrada dentro de ella, mida lo que mida el original. Entre el 500e
          y el Kangoo hay bastante diferencia de proporción y ninguno necesita un ajuste propio.
        */}
        <VehicleImage
          model={model}
          /*
            **Corrido a la izquierda: la caja termina antes de la ficha, no debajo de ella.** Con
            el auto centrado contra el ancho completo, la mitad derecha quedaba escondida detrás
            del vidrio y lo que se veía era un auto descentrado hacia la derecha. Acortando la caja
            —`right-14`— el centro se corre para el lado del hueco libre y la cola sigue metiéndose
            abajo del vidrio, que es lo único que del solapado tiene que verse.
          */
          className="h-32 w-full md:absolute md:inset-y-0 md:right-14 md:left-0 md:h-full md:w-auto"
        />

        {/*
          La barra de vidrio, de pie y de arriba abajo. Mismo `.glass-panel` que la ficha de la
          portada; lo único que cambia es la orientación, porque acá el lugar libre está al costado
          del auto y no abajo.

          **Va de borde a borde en vertical —`inset-y-0`— y no es un recuadro flotante.** Ocupando
          solo un pedazo, quedaba una tarjeta suelta apoyada sobre la esquina; llegando arriba y
          abajo se lee como una franja que pertenece al panel, y es lo que le da a la cola del auto
          algo detrás de lo cual meterse.

          `justify-center` mantiene los datos y el botón en el medio de esa franja: pegados arriba,
          el vidrio se vería medio vacío justo en la zona donde no hay auto que mirar a través.
        */}
        <div className="glass-panel relative z-10 flex flex-col justify-center rounded-2xl p-5 md:absolute md:inset-y-0 md:right-0 md:w-64">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
            {specs.map((spec) => (
              <div key={spec.label} className="min-w-0">
                <dt className="text-text-muted text-[10px] font-medium tracking-wide uppercase">
                  {spec.label}
                </dt>
                <dd className="text-text truncate text-base leading-tight font-extrabold tracking-tight">
                  {spec.value}
                </dd>
              </div>
            ))}
          </dl>

          {selected ? (
            <p className="text-text-muted mt-4 rounded-xl px-4 py-2.5 text-center text-sm font-semibold">
              Ya es tu auto
            </p>
          ) : (
            <button
              type="button"
              onClick={onSelect}
              className="brand-fill text-on-brand mt-4 w-full cursor-pointer rounded-xl px-4 py-2.5 text-sm font-semibold"
            >
              Elegir este
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
