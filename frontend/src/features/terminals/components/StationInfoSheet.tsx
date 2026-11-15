import { formatCoordinate } from '../format'
import { useWheelToHorizontal } from '../useWheelToHorizontal'
import type { StationDetail } from '../types'
import ConnectorPanel from './ConnectorPanel'

/**
 * Detalle de una estación: foto, datos, conectores y galería.
 *
 * El orden es el del diseño: nombre y dirección primero, después la grilla de conectores
 * —que es lo que el operador viene a mirar— y las fotos al final.
 */
interface StationInfoSheetProps {
  station: StationDetail
  onEdit: () => void
}

export default function StationInfoSheet({ station, onEdit }: StationInfoSheetProps) {
  const galleryRef = useWheelToHorizontal<HTMLDivElement>()

  /* La primera foto hace de portada; la galería de abajo las repite todas, esa incluida. */
  const [cover] = station.photoUrls
  const gallery = station.photoUrls

  return (
    <article>
      {cover ? (
        <img
          src={cover}
          alt={station.name}
          className="h-52 w-full rounded-2xl object-cover"
          loading="lazy"
        />
      ) : (
        <div className="glass-inset text-st-muted flex h-52 w-full items-center justify-center rounded-2xl text-sm">
          Sin fotos
        </div>
      )}

      <header className="mt-4 flex items-center justify-between gap-4">
        <h2 className="text-st-text text-2xl font-bold">{station.name}</h2>
        <button
          type="button"
          onClick={onEdit}
          className="text-st-muted hover:text-st-text focus-visible:outline-st-accent shrink-0 text-xs font-medium tracking-wide underline underline-offset-4 focus-visible:outline-2"
        >
          EDITAR ESTACIÓN
        </button>
      </header>

      <p className="text-st-muted mt-1 text-[11px]">{station.address}</p>

      <div className="mt-4">
        <h3 className="text-st-muted text-base font-bold">LATITUD Y LONGITUD</h3>
        <p className="text-st-text mt-1 text-sm">
          {formatCoordinate(station.latitude)} &nbsp; {formatCoordinate(station.longitude)}
        </p>
      </div>

      <ConnectorPanel connectors={station.connectors} />

      <section className="mt-6">
        <h3 className="text-st-muted text-lg font-bold">FOTOS</h3>
        {gallery.length === 0 ? (
          <p className="text-st-muted mt-2 text-sm italic">
            Esta estación no tiene fotos cargadas.
          </p>
        ) : (
          /*
            Tira horizontal: en un celular las fotos no entran en fila y apilarlas empujaría
            todo lo demás fuera de la pantalla.
          */
          <div
            ref={galleryRef}
            className="max-md:no-scrollbar md:slim-scrollbar mt-3 w-full min-w-0 overflow-x-auto pb-2"
          >
            {/*
              El que scrollea es este contenedor y la lista va adentro con `w-max`. Con el
              `overflow` puesto en la lista misma, su ancho dependía de que se lo fijara el
              padre: adentro del panel lateral no pasaba, la fila crecía hasta desbordarlo
              y el recorte terminaba haciéndolo el panel, que no scrollea de costado.
            */}
            <ul className="flex w-max gap-3">
              {gallery.map((url, index) => (
                <li key={`${url}-${index}`} className="shrink-0">
                  <img
                    src={url}
                    alt={`${station.name} ${index + 1}`}
                    className="h-24 w-36 rounded-xl object-cover"
                    loading="lazy"
                  />
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
    </article>
  )
}
