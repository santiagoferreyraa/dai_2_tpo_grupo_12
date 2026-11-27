import { CONNECTOR_TYPE_LABEL, formatPower, maxPower } from '../format'
import type { StationDetail } from '../types'

/**
 * Fila del listado. Tocarla en cualquier parte abre el detalle; la franja del borde
 * derecho es el acceso a la edición.
 *
 * La tarjeta es un `<button>` y no un `<div>` con `onClick` para que el teclado y los
 * lectores de pantalla la vean como lo que es. Eso obliga a que la franja de edición quede
 * FUERA, como hermana: un botón adentro de otro botón es HTML inválido y el navegador lo
 * desarma. De ahí que estén superpuestas en vez de anidadas, y que el borde y la sombra
 * vivan en el `<li>`, que es el único que las contiene a las dos.
 */
interface StationCardProps {
  station: StationDetail
  /**
   * Marcada como la que se está viendo en el panel lateral. Solo pasa en pantalla ancha:
   * en el celular el panel tapa el listado y no hay nada que marcar.
   */
  selected?: boolean
  onOpen: () => void
  onEdit: () => void
}

export default function StationCard({
  station,
  selected = false,
  onOpen,
  onEdit,
}: StationCardProps) {
  const power = maxPower(station)
  const count = station.connectors.length
  const [cover] = station.photoUrls

  /* Con varios conectores no entran todos: van los dos primeros y el resto como "+N". */
  const shownTypes = station.connectors.slice(0, 2)
  const hiddenCount = count - shownTypes.length

  const types =
    count === 0 ? null : (
      <>
        {shownTypes.map((c) => CONNECTOR_TYPE_LABEL[c.connectorType]).join(' · ')}
        {hiddenCount > 0 && <span className="text-st-muted"> +{hiddenCount}</span>}
      </>
    )

  return (
    <li
      className={`relative rounded-2xl transition-transform ${
        /*
          El anillo va acá y no en el botón para que dé la vuelta entera, franja de edición
          incluida. El agrandado es apenas perceptible a propósito: lo suficiente para que
          la fila elegida se despegue de las demás, no tanto como para descolocar la lista.
        */
        selected ? 'ring-st-accent scale-[1.015] ring-2' : ''
      }`}
    >
      <button
        type="button"
        onClick={onOpen}
        /*
          Sin `aria-label` (QA-21, WCAG 2.5.3 "Label in Name"): el nombre sale de lo que está
          escrito adentro —estación, dirección, conectores—, así que coincide con lo que se ve. El
          "Ver" de adelante lo agrega el `sr-only` de abajo. Con "Ver <nombre>" como etiqueta, la
          dirección y los datos que se leen en la tarjeta quedaban fuera del nombre.
        */
        aria-current={selected}
        className={`focus-visible:outline-st-accent block w-full rounded-2xl py-4 pr-12 pl-5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 ${
          /* El vidrio de las tarjetas del resto de la aplicación; elegida, con el borde verde. */
          selected ? 'glass-panel border-primary/60' : 'glass-panel hover:border-primary/60'
        }`}
      >
        <span className="sr-only">Ver </span>
        <div className="flex items-center gap-4">
          {/*
            La miniatura aparece recién en pantalla mediana. En el celular el ancho es todo
            lo que hay y se lo lleva el texto, que es lo que se lee de un vistazo.
          */}
          {cover && (
            <img
              src={cover}
              alt=""
              loading="lazy"
              className="hidden h-24 w-24 shrink-0 rounded-xl object-cover md:block"
            />
          )}

          <div className="min-w-0 flex-1">
            <h2 className="text-st-text truncate text-xl font-bold">{station.name}</h2>
            <p className="text-st-muted mt-1 truncate text-[11px]">{station.address}</p>

            {/*
              De acá para abajo, los mismos datos dos veces. En el celular van en una línea,
              porque no hay ancho para más; en pantalla grande sobra lugar y se despliegan
              como columnas rotuladas, que es lo que hace legible una fila larga.
            */}
            <div className="mt-3 flex items-baseline justify-between gap-3 md:hidden">
              <span className="text-st-text flex items-baseline gap-4 text-sm font-semibold">
                {types ?? <span className="text-st-muted font-normal italic">Sin conectores</span>}
                {power !== null && <span>{formatPower(power)}</span>}
              </span>

              <span className="text-st-muted shrink-0 text-[11px] font-medium tracking-wide">
                {count} {count === 1 ? 'CONECTOR' : 'CONECTORES'}
              </span>
            </div>

            <dl className="mt-3 hidden gap-10 md:flex">
              <div>
                <dt className="text-st-muted text-[10px] font-bold tracking-widest">TIPOS</dt>
                <dd className="text-st-text mt-0.5 text-sm font-semibold">
                  {types ?? <span className="text-st-muted font-normal italic">—</span>}
                </dd>
              </div>
              <div>
                <dt className="text-st-muted text-[10px] font-bold tracking-widest">
                  POTENCIA MÁX.
                </dt>
                <dd className="text-st-text mt-0.5 text-sm font-semibold">
                  {power !== null ? formatPower(power) : <span className="text-st-muted">—</span>}
                </dd>
              </div>
              <div>
                <dt className="text-st-muted text-[10px] font-bold tracking-widest">CONECTORES</dt>
                <dd className="text-st-text mt-0.5 text-sm font-semibold">{count}</dd>
              </div>
            </dl>
          </div>
        </div>
      </button>

      {/*
        Franja del borde derecho. `vertical-rl` gira el texto y `rotate-180` lo deja
        leyéndose de abajo hacia arriba. Va encima de la tarjeta, no adentro: ver el
        comentario del componente.
      */}
      <button
        type="button"
        onClick={onEdit}
        aria-label={`Editar ${station.name}`}
        /*
          La franja está apoyada ENCIMA de la tarjeta, no recortada de ella: por eso lleva
          fondo propio y una sombra que cae hacia la izquierda, que es lo que la despega.
          Va con el verde de la marca, como el botón de agregar y el resto de las acciones de
          la aplicación: se lee como un borde macizo y, además, como algo que se puede tocar.
        */
        className="brand-fill text-on-primary hover:brightness-110 focus-visible:outline-st-accent absolute top-0 right-0 flex h-full w-10 items-center justify-center rounded-r-2xl shadow-[-8px_0_14px_-6px_rgba(0,0,0,0.65)] transition-colors focus-visible:outline-2"
      >
        <span className="rotate-180 text-[11px] font-extrabold tracking-widest [writing-mode:vertical-rl]">
          EDITAR
        </span>
      </button>
    </li>
  )
}
