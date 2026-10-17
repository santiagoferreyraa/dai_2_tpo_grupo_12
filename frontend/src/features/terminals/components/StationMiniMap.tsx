/**
 * El mapa chico y quieto de la portada: una estación, enmarcada, sin poder moverse.
 *
 * **No es `StationMap` con menos props, y esa es una decisión y no una duplicación.** Aquel es
 * un mapa que se opera: arrastra, acerca, muestra tarjetas al pasar el mouse y avisa qué
 * estación se eligió. Este no hace nada de eso. Es una imagen que resulta estar viva, y su
 * único gesto es llevar al mapa de verdad. Meter los dos comportamientos en un componente
 * habría dejado media docena de props apagando cosas, que es la forma más segura de que alguien
 * encienda una sin querer.
 *
 * Lo que SÍ comparte es todo lo que define cómo se ve un mapa de Ecopedia: el proveedor de
 * mosaicos, el filtro del tema y el dibujo de los pines salen de los mismos archivos que usa
 * `StationMap`. Si el mapa grande cambia de aspecto, este cambia con él.
 *
 * **Todo lo que se puede apagar, se apaga.** Y no alcanza con los gestos: encima va
 * `pointer-events: none` sobre el contenedor. Leaflet escucha el clic aunque no haya nada que
 * hacer con él, así que sin eso el enlace de alrededor no recibiría nunca el toque y la tarjeta
 * entera dejaría de ser un botón.
 */

import { MapContainer, Marker, TileLayer } from 'react-leaflet'

import { useTheme } from '@/features/theme/theme'

import { DEVICE_PIN } from '../devicePin'
import { toStationResult } from '../format'
import { MAX_NATIVE_ZOOM, TILES } from '../mapConfig'
import { stationPin } from '../stationPin'
import type { StationDetail } from '../types'
import type { DeviceLocation } from '../useDeviceLocation'

/**
 * Zoom del recuadro chico.
 *
 * Más bajo que el techo del mapa grande a propósito. Allá, acercarse hasta la cuadra es lo
 * correcto porque la pantalla es el mapa; acá el recuadro mide unos doscientos píxeles de alto, y
 * a ese zoom la estación elegida quedaría sola en un rectángulo gris sin una sola referencia. A 14
 * se ven las estaciones de alrededor, que es lo que convierte el recuadro en "acá estás parado" en
 * vez de un cuadrado de mapa cualquiera.
 *
 * Es un valor fijo y no se mueve: este mapa no se puede manejar —es una foto— así que no hay un
 * zoom del usuario que respetar, al contrario del grande.
 */
const MINI_ZOOM = 14

interface StationMiniMapProps {
  /** Todas las estaciones. Se dibujan las que caigan en el recuadro; las demás no molestan. */
  stations: StationDetail[]
  /** La estación sobre la que queda encuadrado el mapa. */
  focus: StationDetail
  /** El punto del usuario, si el navegador lo dio. */
  deviceLocation?: DeviceLocation | null
  className?: string
}

export default function StationMiniMap({
  stations,
  focus,
  deviceLocation = null,
  className = '',
}: StationMiniMapProps) {
  const theme = useTheme()

  return (
    <MapContainer
      /*
        `key` con el id de la estación: el centro de un MapContainer solo se lee al crearlo, así
        que sin esto el recuadro se quedaría clavado en la primera estación más cercana y no
        seguiría al usuario cuando se mueve y pasa a tener otra al lado. Rehacer el mapa es
        barato y es lo correcto acá, donde no hay estado que preservar —ni zoom del usuario ni
        centro arrastrado— justamente porque no se puede tocar.
      */
      key={`${String(focus.id)}-${theme}`}
      center={[focus.latitude, focus.longitude]}
      zoom={MINI_ZOOM}
      /* Todos los gestos apagados: el recuadro es una imagen, no un mapa que se opera. */
      dragging={false}
      scrollWheelZoom={false}
      doubleClickZoom={false}
      touchZoom={false}
      boxZoom={false}
      keyboard={false}
      zoomControl={false}
      attributionControl={false}
      /*
        El crédito de los mosaicos sigue siendo obligatorio aunque el cartel esté apagado, igual
        que en el mapa grande: el texto es TILE_ATTRIBUTION y tiene que aparecer en algún lugar
        visible de la aplicación.

        `pointer-events-none` es lo que deja pasar el clic al enlace de afuera. Ver el
        comentario de arriba.
      */
      className={`pointer-events-none h-full w-full ${className}`}
    >
      <TileLayer
        url={theme === 'light' ? TILES.light : TILES.dark}
        maxNativeZoom={MAX_NATIVE_ZOOM}
      />

      {stations.map((station) => (
        <Marker
          key={station.id}
          position={[station.latitude, station.longitude]}
          /*
            La estación encuadrada va realzada y el resto normal, con el MISMO dibujo que usa el
            mapa grande: quien vio el recuadro en la portada reconoce el pin cuando llega al mapa.
          */
          icon={stationPin(toStationResult(station), station.id === focus.id)}
          interactive={false}
        />
      ))}

      {deviceLocation !== null && (
        <Marker
          position={[deviceLocation.latitude, deviceLocation.longitude]}
          icon={DEVICE_PIN}
          interactive={false}
          zIndexOffset={1000}
        />
      )}
    </MapContainer>
  )
}
