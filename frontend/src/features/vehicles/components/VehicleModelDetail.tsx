import type { VehicleModel } from '../types'
import { specsOf } from '../vehicle'
import SpecIcon from './SpecIcon'
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
  /*
    Los cuatro datos salen de `specsOf` y no de una lista escrita acá: son EXACTAMENTE los que
    muestra la ficha de la portada, con el mismo rótulo, el mismo redondeo y el mismo dibujo. Los
    tenía escritos aparte y eso ya había dejado una diferencia —allá "Carga máx." venía con su
    ícono y acá no—; con una sola fuente, el día que se agregue un dato aparece en los dos lados.
  */
  const specs = specsOf(model)

  return (
    <div className="relative flex h-full flex-col">
      {/*
        **Los dos se posicionan sueltos contra esta caja, no se reparten una fila.** Es lo que
        produce el solapado: en dos columnas cada uno termina donde empieza el otro, y no hay forma
        de que la cola quede por debajo del vidrio sin márgenes negativos peleándose con la grilla.

        El `justify-center` es del celular: ahora que la ventana tiene alto fijo, el detalle de un
        modelo no siempre lo llena —depende de cuánto mida la foto—, y pegado arriba dejaba un
        hueco al pie que se leía como que faltaba algo abajo.

        Debajo de `md` el montaje cambia de eje pero NO se apaga: ahí la ficha es una tarjeta ancha
        debajo del auto, y sube un poco —`-mt-10`— hasta cruzarle las ruedas. Es el mismo gesto que
        la barra de la portada, y por el mismo motivo: apoyada debajo con un espacio en el medio,
        la foto y la ficha se leen como dos tarjetas que casualmente están una arriba de la otra;
        montada, son el auto y sus datos.

        Lo que sí se apaga es el montaje LATERAL: una ficha encima del auto, en un teléfono, no
        deja ver ninguno de los dos.
      */}
      <div className="flex min-h-0 flex-1 flex-col justify-center md:relative md:block">
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
          className="h-44 w-full md:absolute md:inset-y-0 md:right-14 md:left-0 md:h-full md:w-auto"
        />

        {/*
          La barra de vidrio, de pie y de arriba abajo. Mismo `.glass-panel` que la ficha de la
          portada, con más cuerpo —`.glass-over-photo`, que explica por qué— y en la otra
          orientación, porque acá el lugar libre está al costado del auto y no abajo.

          **Va de borde a borde en vertical —`inset-y-0`— y no es un recuadro flotante.** Ocupando
          solo un pedazo, quedaba una tarjeta suelta apoyada sobre la esquina; llegando arriba y
          abajo se lee como una franja que pertenece al panel, y es lo que le da a la cola del auto
          algo detrás de lo cual meterse.

          `justify-center` mantiene los datos y el botón en el medio de esa franja: pegados arriba,
          el vidrio se vería medio vacío justo en la zona donde no hay auto que mirar a través.
        */}
        <div className="glass-panel glass-over-photo relative z-10 -mt-10 flex flex-col justify-center rounded-2xl p-5 md:absolute md:inset-y-0 md:right-0 md:mt-0 md:w-64">
          {/*
            Los datos van con `my-auto` para que el botón pueda irse al pie de la franja: el espacio
            libre se reparte arriba y abajo de la ficha, así que los datos quedan centrados —que es
            lo que pide el vidrio— y el botón apoyado contra el fondo, lejos de ellos. Pegado
            debajo del último dato se leía como un dato más de la lista.

            En el celular la franja mide lo que pide su contenido, así que no hay espacio libre que
            repartir y los márgenes automáticos no mueven nada.
          */}
          {/*
            Los cuatro datos, uno debajo del otro y no en dos columnas.

            **En dos columnas el ancho de cada celda es la mitad de una franja que ya es angosta**,
            y eso obligaba a escribirlos chicos para que "150 kW" entrara sin cortarse. Apilados,
            cada dato tiene los 16rem enteros: entran con el cuerpo que les corresponde —son LA
            información de esta pantalla, no una nota al pie— y de paso se leen en orden, que es
            como se compara una ficha técnica.

            El alto sobra: la franja va de punta a punta del panel y cuatro renglones no lo llenan.
            Es justamente el espacio que antes quedaba vacío en el medio.

            **En el celular vuelven a ser dos columnas**, y no es una contradicción: ahí la ficha no
            es una franja de pie al costado del auto sino una tarjeta ancha debajo, así que el que
            sobra es el ancho y el que falta es el alto. Cuatro renglones apilados empujaban el
            botón fuera de la pantalla.
          */}
          <dl className="my-auto grid grid-cols-2 gap-4 md:grid-cols-1 md:gap-y-4">
            {specs.map((spec) => (
              /*
                Dibujo a la izquierda y, a su derecha, el rótulo arriba del valor: la misma
                anatomía que la ficha de la portada. Son los mismos cuatro datos del mismo auto,
                así que verlos acomodados distinto en dos pantallas los haría leer como dos cosas
                distintas.
              */
              <div key={spec.label} className="flex items-center gap-3">
                <SpecIcon name={spec.icon} className="text-primary h-8 w-8 shrink-0" />

                <div className="min-w-0">
                  <dt className="text-text-muted text-[11px] font-medium">{spec.label}</dt>
                  <dd className="text-text truncate text-xl leading-tight font-extrabold tracking-tight">
                    {spec.value}
                  </dd>
                </div>
              </div>
            ))}
          </dl>

          {selected ? (
            <p className="text-text-muted mt-5 rounded-xl px-4 py-2.5 text-center text-sm font-semibold md:mt-0">
              Ya es tu auto
            </p>
          ) : (
            <button
              type="button"
              onClick={onSelect}
              /*
                El `mt` es para el celular, donde la franja mide lo que pide su contenido: ahí los
                márgenes automáticos de los datos no tienen espacio libre que repartir y el botón
                quedaba pegado al último dato. En escritorio sobra alto, el `my-auto` de los datos
                ya los separa, y un margen extra solo correría el botón hacia arriba.
              */
              className="brand-fill text-on-brand mt-5 w-full cursor-pointer rounded-xl px-4 py-2.5 text-sm font-semibold md:mt-0"
            >
              Elegir
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
