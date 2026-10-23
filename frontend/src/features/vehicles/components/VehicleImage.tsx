import { useState } from 'react'

import { VEHICLE_IMAGE_FALLBACK, vehicleImageSrc } from '../assets'
import type { VehicleModel } from '../types'

/**
 * La foto de un modelo, con un reemplazo para cuando el archivo no está.
 *
 * **El reemplazo tiene dos escalones.** El primero es `car-not-found.png`, un auto genérico que
 * ocupa el mismo lugar que una foto real; el segundo es una silueta dibujada acá, para el caso de
 * que ESA imagen tampoco esté. Sin el segundo escalón, bastaría con que faltara el archivo del
 * reemplazo para volver al ícono roto del navegador, y un SVG en línea no puede faltar.
 *
 * La silueta se ve apagada a propósito: tiene que leerse como "acá va una foto que todavía no
 * está", no como el dibujo del auto.
 *
 * El `alt` queda vacío porque el nombre del modelo está escrito al lado, y repetirlo le haría leer
 * dos veces lo mismo a quien usa un lector de pantalla.
 */
export default function VehicleImage({
  model,
  className = '',
}: {
  model: VehicleModel
  className?: string
}) {
  /*
    Cuántos escalones bajó la imagen de ESTE modelo. Va atado al `id` en vez de limpiarse con un
    efecto: cambiar de modelo tiene que volver a intentar con la foto propia, y comparar contra el
    id lo resuelve al dibujar, sin un render intermedio mostrando el reemplazo del anterior.
  */
  const [failed, setFailed] = useState<{ id: number; step: number } | null>(null)
  const step = failed !== null && failed.id === model.id ? failed.step : 0

  if (step >= 2) {
    return (
      <svg
        viewBox="0 0 64 28"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className={`text-text-muted/40 ${className}`}
      >
        <path d="M4 20h56M8 20a4 4 0 1 0 8 0 4 4 0 1 0-8 0M48 20a4 4 0 1 0 8 0 4 4 0 1 0-8 0" />
        <path d="M6 20v-5l6-8h28l10 8 8 1v4" />
        <path d="M22 7v8M40 7l8 8H12" />
      </svg>
    )
  }

  return (
    <img
      /*
        La `key` lleva el escalón y no solo el modelo: cambiando el `src` sobre el mismo elemento,
        algunos navegadores no vuelven a disparar `onError` si la segunda dirección también falla,
        y el reemplazo roto se quedaría puesto.
      */
      key={`${String(model.id)}:${String(step)}`}
      src={step === 0 ? vehicleImageSrc(model) : VEHICLE_IMAGE_FALLBACK}
      alt=""
      onError={() => {
        setFailed({ id: model.id, step: step + 1 })
      }}
      className={`object-contain ${className}`}
    />
  )
}
