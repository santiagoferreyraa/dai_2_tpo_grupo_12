import { useState } from 'react'

import { vehicleImageSrc } from '../assets'
import type { VehicleModel } from '../types'

/**
 * La foto de un modelo, con una silueta de auto como reemplazo.
 *
 * **La silueta va dibujada acá y no es un archivo.** Si el reemplazo de "falta una imagen" fuera a
 * su vez una imagen, bastaría con que ESA no estuviera para volver al ícono roto del navegador.
 * Un SVG en línea no puede faltar.
 *
 * **Se ve apagada a propósito.** Tiene que leerse como "acá va una foto que todavía no está", no
 * como el dibujo del auto: en una grilla de ocho, una silueta con el mismo peso que las fotos
 * reales haría dudar de cuál es cuál.
 *
 * El `alt` queda vacío cuando hay foto porque el nombre del modelo está escrito al lado, y
 * repetirlo le haría leer dos veces lo mismo a quien usa un lector de pantalla.
 */
export default function VehicleImage({
  model,
  className = '',
}: {
  model: VehicleModel
  className?: string
}) {
  const [failed, setFailed] = useState(false)

  if (failed) {
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
      key={model.id}
      src={vehicleImageSrc(model)}
      alt=""
      onError={() => setFailed(true)}
      className={`object-contain ${className}`}
    />
  )
}
