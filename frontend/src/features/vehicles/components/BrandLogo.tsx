import { useState } from 'react'

import { brandLogoSrc } from '../assets'

/**
 * El logo de una marca, con el nombre escrito como reemplazo.
 *
 * **El reemplazo no es una cortesía: es lo que permite que el catálogo funcione con la carpeta de
 * logos vacía.** Los archivos se van cargando de a poco, y sin esto una marca sin logo deja el
 * ícono roto del navegador en el encabezado del diálogo. Escrito, el nombre dice exactamente lo
 * mismo que diría el logo.
 *
 * **Se detecta con `onError` y no preguntando si el archivo existe.** No hay forma de preguntarlo
 * desde el navegador sin pedirlo, así que el intento ES la pregunta: se dibuja la imagen, y si no
 * carga se cambia por el texto. Es también como resuelve el mapa sus pines.
 *
 * `key` en la imagen: al cambiar de marca, React reutilizaría el mismo `<img>` y el estado de
 * "falló" quedaría pegado de la marca anterior, así que una marca con logo aparecería escrita solo
 * por haber pasado antes por una que no lo tiene.
 *
 * **El tamaño lo pone quien lo usa y acá no hay ninguno por omisión.** Con un `max-h` propio, la
 * clase que llega por `className` no lo pisa de forma confiable: entre dos utilidades del mismo
 * grupo gana la que Tailwind haya emitido más abajo en la hoja, no la que esté más a la derecha en
 * el atributo. Un componente que se puede redimensionar no puede depender de ese orden.
 */
export default function BrandLogo({
  brand,
  className = '',
}: {
  brand: string
  className?: string
}) {
  const [failed, setFailed] = useState(false)

  if (failed) {
    return (
      /*
        El reemplazo escrito NO recibe el `className` del logo: son medidas de una imagen —`max-h`,
        `max-w`— y aplicadas a un texto lo recortarían en vez de achicarlo. Lo que tiene que
        coincidir es el peso visual, y eso lo da el cuerpo de la letra.
      */
      <span className="text-text text-3xl font-extrabold tracking-tight uppercase">{brand}</span>
    )
  }

  return (
    <img
      key={brand}
      src={brandLogoSrc(brand)}
      alt={brand}
      onError={() => setFailed(true)}
      className={`w-auto object-contain ${className}`}
    />
  )
}
