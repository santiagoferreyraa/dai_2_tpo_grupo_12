import { useEffect, useId, useRef, useState } from 'react'

import { departFrom, departFromDevice, DEVICE_DEPARTURE_LABEL, useDeparture } from '../departure'
import type { AddressSuggestion } from '../geocoding'
import { useAddressSearch } from '../useAddressSearch'
import { useDepartureOrigin } from '../useDepartureOrigin'
import { useSuggestionNav } from '../useSuggestionNav'
import type { DeviceLocationStatus } from '../useDeviceLocation'

/**
 * Desde dónde sale el conductor, para elegirlo antes de reservar.
 *
 * Va arriba del día y no abajo de la duración porque es lo primero de la reserva que el conductor
 * puede querer cambiar y lo único que el formulario no puede adivinar: el día de hoy y la hora
 * más cercana son valores por omisión que aciertan casi siempre, "tu ubicación" acierta casi
 * siempre TAMBIÉN, pero cuando no acierta hay que poder verlo sin ir a buscarlo.
 *
 * **Una dirección solo queda elegida si se tocó una sugerencia.** Es la regla de la que cuelga
 * todo el diseño de este campo, y no es una validación: es la forma. Lo que se escribe en el campo
 * NO es el punto de partida, es la consulta; el punto de partida es lo que se elige de la lista, y
 * con eso vienen las coordenadas. Un texto libre como "corrientes 348" no tiene coordenadas, no se
 * puede rutear y no se puede dibujar, así que dejarlo pasar significaría o inventarle un punto o
 * guardar algo que no sirve para nada. Salir sin elegir deja el campo exactamente como estaba.
 *
 * Por eso hay dos estados visuales y no uno solo:
 *
 * - **Elegido**: se lee el punto de partida y hay un botón para cambiarlo. No es un campo de
 *   texto, porque no hay nada que tipear ahí.
 * - **Buscando**: el campo de texto con las coincidencias colgando. Se sale eligiendo una, con
 *   Escape o saliendo del campo, y en los dos últimos casos no cambia nada.
 *
 * El texto escrito no se guarda al salir: la próxima vez que se abra el campo arranca en blanco.
 * Guardarlo dejaría a la vista una búsqueda a medias al lado de un punto de partida que no es ese,
 * que es la confusión que este campo existe para evitar.
 */

/**
 * Lo que se dice de la ubicación del dispositivo cuando no la hay, y qué hacer al respecto.
 *
 * 'ready' y 'locating' no están: el primero no tiene nada que explicar y el segundo lo dice el
 * propio campo mientras busca. Los otros cuatro sí, porque son los casos en los que "Tu ubicación"
 * no va a llegar nunca y el conductor tiene que enterarse de que la salida es escribir una
 * dirección —que es justo el botón que tiene al lado—.
 */
const DEVICE_PROBLEM: Partial<Record<DeviceLocationStatus, string>> = {
  denied: 'No nos diste permiso para ver tu ubicación. Podés escribir una dirección.',
  unavailable: 'No pudimos ver tu ubicación. Podés escribir una dirección.',
  'out-of-bounds': 'Estás fuera del mapa de la Argentina. Escribí desde dónde salís.',
}

/** Un alfiler: el mismo signo que el mapa usa para un lugar. */
function PinIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4 shrink-0"
    >
      <path d="M12 21s7-5.4 7-11a7 7 0 1 0-14 0c0 5.6 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

interface DepartureFieldProps {
  /**
   * La estación que se está reservando.
   *
   * Viaja con la dirección elegida (ver `Departure`): el punto de partida es de ESTE viaje, y el
   * mapa lo usa para no rutear desde acá cuando el conductor está mirando otra estación.
   */
  stationId: number
  /** Apaga el campo mientras viaja un pedido, igual que el resto del formulario. */
  disabled?: boolean
}

export default function DepartureField({ stationId, disabled = false }: DepartureFieldProps) {
  const departure = useDeparture()
  const { device } = useDepartureOrigin()

  const [editing, setEditing] = useState(false)
  const [query, setQuery] = useState('')

  const inputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()
  const labelId = useId()

  /*
   * La búsqueda se inclina hacia donde está el conductor, si se sabe. No hacia el punto de partida
   * elegido: quien ya puso una dirección en Córdoba y la está cambiando casi siempre la está
   * corrigiendo cerca de donde está, no cerca del error anterior.
   */
  const search = useAddressSearch(editing ? query : '', device.location)

  /*
   * Las coincidencias anteriores se quedan a la vista mientras se busca de nuevo, y por eso no se
   * filtra por `status === 'ready'`. Vaciarlas en cada tecla hace que la lista aparezca y
   * desaparezca mientras se escribe, que además de molesto mueve de lugar todo lo que tiene
   * debajo. Lo único que sí las borra es que el buscador falle, que es cuando dejarlas sería
   * hacerlas pasar por las coincidencias de este texto.
   */
  const results = search.status === 'unavailable' ? [] : search.results

  const nav = useSuggestionNav(results, (picked) => {
    if (picked !== null) pick(picked)
  })

  /* Al abrir el campo el foco entra solo: se tocó "Cambiar" para escribir, no para mirarlo. */
  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  function pick(suggestion: AddressSuggestion) {
    departFrom(suggestion, stationId)
    setEditing(false)
    setQuery('')
  }

  /** Sale de la búsqueda sin tocar el punto de partida. Escape, salir del campo, o "Cancelar". */
  function cancel() {
    setEditing(false)
    setQuery('')
  }

  function open() {
    setQuery('')
    nav.reopen()
    setEditing(true)
  }

  const deviceProblem = DEVICE_PROBLEM[device.status] ?? null

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <p id={labelId} className="text-text text-sm font-semibold">
        Salís desde
      </p>

      {!editing && (
        <div className="border-border bg-surface/40 flex items-center gap-3 rounded-xl border px-3 py-2.5">
          <span className="text-primary" aria-hidden="true">
            <PinIcon />
          </span>

          {/*
            El punto de partida escrito, en dos renglones cuando es una dirección: arriba lo que se
            buscó y abajo lo que la distingue de las otras cinco calles con el mismo nombre. Es el
            mismo par que se eligió en la lista, a propósito —si el renglón elegido y el renglón
            confirmado dijeran cosas distintas, no habría forma de saber si se eligió bien—.
          */}
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="text-text truncate text-sm font-semibold">
              {departure.kind === 'device' ? DEVICE_DEPARTURE_LABEL : departure.address.label}
            </span>
            {departure.kind === 'address' && departure.address.detail !== '' && (
              <span className="text-text-muted truncate text-xs">{departure.address.detail}</span>
            )}
            {departure.kind === 'device' && device.status === 'locating' && (
              <span className="text-text-muted truncate text-xs">Buscando dónde estás…</span>
            )}
          </span>

          {/*
            Volver a "Tu ubicación" es un botón aparte y no una opción más de la lista: es el valor
            por omisión, no una dirección, y buscarlo escribiendo no tendría sentido. Solo aparece
            cuando hay algo que deshacer.
          */}
          {departure.kind === 'address' && (
            <button
              type="button"
              onClick={departFromDevice}
              disabled={disabled}
              className="text-text-muted hover:text-text focus-visible:outline-primary shrink-0 cursor-pointer rounded-lg px-2 py-1 text-xs font-semibold transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Usar mi ubicación
            </button>
          )}

          <button
            type="button"
            onClick={open}
            disabled={disabled}
            className="text-primary hover:bg-surface/70 focus-visible:outline-primary shrink-0 cursor-pointer rounded-lg px-2 py-1 text-xs font-semibold transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Cambiar
          </button>
        </div>
      )}

      {editing && (
        /* `relative` porque la lista de coincidencias se cuelga de acá, igual que en el buscador
           de estaciones del mapa. */
        <div className="relative" onKeyDown={nav.onKeyDown}>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              nav.reopen()
            }}
            /*
              Salir del campo cancela. No hace falta cuidarse del clic en una sugerencia: la lista
              elige con `onMouseDown`, que llega ANTES de que el campo pierda el foco.
            */
            onBlur={cancel}
            onKeyDown={(event) => {
              if (event.key === 'Escape') cancel()
            }}
            placeholder="Calle y altura, ciudad"
            autoComplete="off"
            aria-labelledby={labelId}
            role="combobox"
            aria-autocomplete="list"
            aria-controls={listboxId}
            aria-expanded={nav.open}
            aria-activedescendant={
              nav.highlighted >= 0 ? `${listboxId}-${String(nav.highlighted)}` : undefined
            }
            className="border-border bg-surface/60 text-text focus:border-primary placeholder:text-text-muted/70 w-full rounded-xl border px-3 py-2.5 text-sm outline-none"
          />

          {nav.open && (
            <ul
              id={listboxId}
              role="listbox"
              aria-label="Direcciones que coinciden"
              /* Por encima del contenido del diálogo y de los controles de Leaflet en celular. */
              className="glass-panel absolute inset-x-0 top-full z-[1300] mt-2 overflow-hidden rounded-2xl py-1"
            >
              {results.map((suggestion, index) => (
                <li
                  key={suggestion.id}
                  id={`${listboxId}-${String(index)}`}
                  role="option"
                  aria-selected={index === nav.highlighted}
                >
                  <button
                    type="button"
                    /*
                      `onMouseDown` y no `onClick`: el clic llega después del `blur` del campo, y
                      para entonces la búsqueda ya se canceló y este botón no existe. Es el mismo
                      motivo por el que la lista de estaciones del mapa lo hace así.
                    */
                    onMouseDown={(event) => {
                      event.preventDefault()
                      pick(suggestion)
                    }}
                    onMouseEnter={() => nav.setHighlighted(index)}
                    className={`flex w-full flex-col px-4 py-2.5 text-left transition-colors ${
                      index === nav.highlighted ? 'bg-primary/15' : ''
                    }`}
                  >
                    <span className="text-text truncate text-sm font-semibold">
                      {suggestion.label}
                    </span>
                    {suggestion.detail !== '' && (
                      <span className="text-text-muted truncate text-xs">{suggestion.detail}</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/*
        El renglón de abajo, que dice una cosa sola por vez y en este orden: primero lo que está
        pasando con la búsqueda, y si no hay búsqueda, lo que está pasando con la ubicación.

        Mientras se busca se avisa que hay que ELEGIR. Es la regla del campo dicha en el momento en
        que importa: sin eso, quien termina de escribir la dirección entera espera que se guarde
        sola y el campo, al salir, parece haberle perdido lo que puso.
      */}
      {editing ? (
        <p className="text-text-muted text-xs">
          {search.status === 'searching' && 'Buscando direcciones…'}
          {search.status === 'unavailable' &&
            'No pudimos buscar direcciones ahora. Probá de nuevo en un momento.'}
          {search.status === 'ready' &&
            results.length === 0 &&
            'No encontramos esa dirección en la Argentina.'}
          {search.status === 'ready' && results.length > 0 && 'Tocá una para confirmarla.'}
          {search.status === 'idle' && 'Escribí al menos tres letras y elegí una de la lista.'}
        </p>
      ) : (
        departure.kind === 'device' &&
        deviceProblem !== null && <p className="text-text-muted text-xs">{deviceProblem}</p>
      )}
    </div>
  )
}
