import { useState } from 'react'

import { useWheelToHorizontal } from '../useWheelToHorizontal'

/**
 * Fotos de la estación en el formulario: se agregan pegando su dirección.
 *
 * No hay selector de archivos, y es a propósito. El backend guarda direcciones
 * (`station_photos.url`), no bytes, así que una foto elegida del disco no tendría dónde
 * subirse: lo único que se podría guardar de ella es un `blob:` local, que existe solo en
 * esta pestaña y queda roto apenas se recarga la página. Guardar eso sería peor que no
 * guardar nada, porque la estación quedaría con fotos que nadie más puede ver.
 *
 * Que se persista la dirección y no el contenido es la decisión que hace reemplazable el
 * almacenamiento: hoy la imagen vive donde la haya publicado el operador y mañana puede
 * vivir en un bucket propio, y ni el dominio ni esta pantalla se enteran. Cuando exista la
 * subida real, lo que cambia acá es de dónde sale la dirección, no qué se guarda.
 */
interface ImagePickerProps {
  value: string[]
  onChange: (photoUrls: string[]) => void
}

/**
 * Solo `http` y `https`.
 *
 * `new URL` acepta cualquier esquema —`blob:`, `data:`, `javascript:`—, y ninguno de esos
 * sirve para una foto que tiene que seguir estando mañana y verse desde otra máquina.
 */
function isPhotoUrl(text: string): boolean {
  try {
    const { protocol } = new URL(text)
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}

const FIELD_CLASS =
  'bg-st-surface-raised text-st-text placeholder:text-st-muted focus:outline-st-accent w-full rounded-full px-4 py-2.5 text-sm focus:outline-2'

export default function ImagePicker({ value, onChange }: ImagePickerProps) {
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string | null>(null)
  const stripRef = useWheelToHorizontal<HTMLDivElement>()

  const add = () => {
    const url = draft.trim()

    if (url === '') return
    if (!isPhotoUrl(url)) {
      setError('Tiene que ser una dirección que empiece con http:// o https://')
      return
    }
    if (value.includes(url)) {
      setError('Esa foto ya está en la lista')
      return
    }

    onChange([...value, url])
    setDraft('')
    setError(null)
  }

  const removeAt = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  return (
    <section className="mt-6">
      <h3 className="text-st-muted text-lg font-bold">FOTOS</h3>

      <div className="mt-3 flex items-center gap-2">
        <input
          type="url"
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value)
            setError(null)
          }}
          /*
            Enter agrega la foto y NO envía el formulario. Sin esto, pegar una dirección y
            apretar Enter guardaría la estación entera a medio llenar: el campo vive adentro
            del <form> de StationFormSheet, y ahí Enter es "enviar".
          */
          onKeyDown={(event) => {
            if (event.key !== 'Enter') return
            event.preventDefault()
            add()
          }}
          placeholder="https://…/foto.jpg"
          aria-label="Dirección de la foto"
          className={FIELD_CLASS}
        />

        {/* `type="button"`, por lo mismo que el Enter: adentro de un <form> el default envía. */}
        <button
          type="button"
          onClick={add}
          aria-label="Agregar foto"
          className="brand-fill text-on-primary focus-visible:outline-st-accent flex h-11 w-12 shrink-0 items-center justify-center rounded-2xl focus-visible:outline-2"
        >
          {/* Dibujada y no el carácter "+", que nunca queda centrado. */}
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="h-5 w-5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          >
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>

      {error && <p className="text-st-offline mt-2 px-4 text-xs">{error}</p>}

      {value.length === 0 ? (
        <p className="text-st-muted mt-3 text-sm italic">Todavía no hay fotos cargadas.</p>
      ) : (
        <div
          ref={stripRef}
          className="max-md:no-scrollbar md:slim-scrollbar mt-3 w-full min-w-0 overflow-x-auto pb-2"
        >
          {/*
            El que scrollea es este contenedor y la lista va adentro con `w-max`. Con el
            `overflow` puesto en la lista misma, su ancho dependía de que se lo fijara el
            padre: adentro del panel lateral no pasaba, la fila crecía hasta desbordarlo
            y el recorte terminaba haciéndolo el panel, que no scrollea de costado.
          */}
          <ul className="flex w-max gap-3">
            {value.map((url, index) => (
              /*
                Sin relleno alrededor: la cruz va DENTRO de la imagen y no colgando de su
                esquina, que además la recortaría el scroll.
              */
              <li key={`${url}-${index}`} className="relative shrink-0">
                <img
                  src={url}
                  alt={`Foto ${index + 1}`}
                  className="bg-st-surface h-24 w-36 rounded-xl object-cover"
                />
                <button
                  type="button"
                  onClick={() => removeAt(index)}
                  aria-label={`Quitar la foto ${index + 1}`}
                  className="bg-st-offline focus-visible:outline-st-accent absolute top-1.5 right-1.5 flex h-6 w-6 items-center justify-center rounded-full text-white focus-visible:outline-2"
                >
                  {/* Dibujada, por el mismo motivo que el "+": el carácter no queda centrado. */}
                  <svg
                    aria-hidden="true"
                    viewBox="0 0 24 24"
                    className="h-3.5 w-3.5"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                  >
                    <path d="M6 6l12 12M18 6L6 18" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
