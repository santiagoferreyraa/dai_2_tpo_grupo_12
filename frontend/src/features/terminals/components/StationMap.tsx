/**
 * Mapa de estaciones de carga (RF07).
 *
 * En esta etapa dibuja las estaciones que recibe por props y avisa cuál se eligió. La búsqueda
 * por viewport —traducir el recuadro visible a centro y radio, con debounce— entra después.
 */

import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'

import { useTheme } from '@/features/theme/theme'

import {
  ARGENTINA_BOUNDS,
  DEFAULT_CENTER,
  DEFAULT_ZOOM,
  MAX_NATIVE_ZOOM,
  MAX_ZOOM,
  MIN_ZOOM,
  TILES,
} from '../mapConfig'
import StationHoverCard from './StationHoverCard'
import { DEPARTURE_PIN, DEVICE_PIN } from '../devicePin'
import { stationPin } from '../stationPin'
import type { Route } from '../routing'
import type { DeparturePoint } from '../useDepartureOrigin'
import type { StationResult } from '../types'

/**
 * Cuánto hay que sostener el mouse sobre un pin antes de que aparezca la tarjeta.
 *
 * La espera es el punto: sin ella, cruzar el mapa con el mouse abre y cierra media docena de
 * tarjetas al pasar. Con la demora solo aparece la de la estación en la que uno se detuvo.
 */
const HOVER_DELAY_MS = 800

/**
 * Le avisa a Leaflet cuando cambia el tamaño del contenedor.
 *
 * Leaflet mide el contenedor UNA vez, al crearse, y con esa medida decide qué mosaicos pedir.
 * Acá el contenedor todavía no tiene su tamaño final en ese momento —lo define el flex, después
 * del primer pintado—, así que el mapa queda convencido de ser mucho más chico de lo que es:
 * se ve un cuadrado de mapa en el medio y gris alrededor, con los pins cayendo afuera.
 *
 * Un ResizeObserver y no un useEffect a secas porque el problema se repite en cada cambio de
 * tamaño: al redimensionar la ventana, y sobre todo al abrir y cerrar el panel lateral.
 */
function InvalidateSizeOnResize() {
  const map = useMap()

  useEffect(() => {
    const observer = new ResizeObserver(() => map.invalidateSize())
    observer.observe(map.getContainer())
    return () => observer.disconnect()
  }, [map])

  return null
}

/**
 * Lleva el centro del mapa a la estación elegida. **No toca el zoom.**
 *
 * flyTo y no setView: el desplazamiento animado deja ver hacia dónde se movió el mapa. Con un
 * salto seco, quien viene de elegir una ficha en el carrusel pierde la referencia de dónde
 * estaba parado.
 *
 * **La escala la decide el usuario y nadie más.** Acá había dos movimientos automáticos de zoom:
 * este, que acercaba hasta FOCUS_ZOOM con cada estación elegida, y un encuadre que después
 * alejaba el mapa hasta que entrara el recorrido entero. Entre los dos, el mapa cambiaba de
 * escala solo —dos veces— cada vez que se tocaba un pin. Molestaba sobre todo reservando desde
 * una dirección escrita, que es cuando el conductor ya acomodó el mapa a mano alrededor de ese
 * punto y la pantalla se lo volvía a mover. Queda el desplazamiento, que es lo mínimo para que
 * el pin elegido entre en pantalla; acercar y alejar es gesto del usuario.
 *
 * Recibe la estación entera y no su id para que el efecto dependa del objeto: `find` sobre el
 * arreglo devuelve siempre la misma referencia mientras no cambie la selección, así que un
 * repintado cualquiera no vuelve a mover el mapa.
 *
 * `bottomInsetPx` es el alto que le tapa el panel al mapa. Sin compensarlo, en celular el pin
 * queda centrado en la ventana y por lo tanto DEBAJO del panel que se acaba de abrir: el mapa
 * se mueve hacia una estación que no se ve. Se corrige subiendo el centro la mitad de lo
 * tapado, que es lo que vuelve a dejar el pin en el medio del hueco visible.
 */
function PanToStation({
  station,
  bottomInsetPx,
}: {
  station: StationResult | null
  bottomInsetPx: number
}) {
  const map = useMap()

  useEffect(() => {
    if (station === null) return

    /*
     * El zoom se lee del mapa y se le devuelve igual: `flyTo` pide un nivel, y pasarle el que ya
     * tiene es la forma de que el viaje sea solo desplazamiento. Se lee adentro del efecto y no
     * entra en las dependencias a propósito: que el usuario cambie el zoom no es motivo para
     * volver a centrar el mapa.
     */
    const zoom = map.getZoom()
    const target = L.latLng(station.latitude, station.longitude)
    if (bottomInsetPx === 0) {
      map.flyTo(target, zoom)
      return
    }

    /*
     * La corrección se hace en píxeles y no en grados: cuántos grados son 150 px depende del zoom
     * y de la latitud. `project` al zoom actual convierte una vez y evita las dos cuentas.
     */
    const point = map.project(target, zoom).add([0, bottomInsetPx / 2])
    map.flyTo(map.unproject(point, zoom), zoom)
  }, [map, station, bottomInsetPx])

  return null
}

interface StationMapProps {
  stations: StationResult[]
  selectedStationId: number | null
  onSelect: (stationId: number) => void
  /** Alto que el panel de detalle le tapa al mapa desde abajo. Ver PanToStation. */
  bottomInsetPx?: number
  /** Apaga los pines que no son el elegido. Se usa con el panel abierto. */
  dimUnselected?: boolean
  /**
   * Desde dónde sale el conductor: la ubicación del dispositivo o la dirección que escribió.
   *
   * Es un `DeparturePoint` y no la ubicación del navegador a secas porque el origen dejó de ser
   * uno solo (ver `useDepartureOrigin`). Acá el `kind` decide UNA cosa: qué marcador se dibuja.
   * El disco que late dice "el navegador te está midiendo ahora", y sobre una dirección tecleada
   * eso sería falso.
   */
  origin?: DeparturePoint | null
  /**
   * Dónde está el dispositivo AHORA, independientemente de desde dónde salga el recorrido.
   *
   * **Se dibuja siempre que se sepa, incluso con una dirección elegida como punto de partida.** El
   * disco que late contesta "dónde estoy", y esa pregunta no deja de tener respuesta porque el
   * recorrido arranque en otro lado: con el punto apagado, quien puso una dirección para reservar
   * se quedaba sin la única referencia de dónde está parado sobre el mapa. Con los dos, el alfiler
   * dice desde dónde sale el viaje y el disco dice dónde está el conductor.
   *
   * Cuando el punto de partida ES el dispositivo los dos marcadores caerían en el mismo lugar, y
   * ahí va el disco solo: `origin` no dibuja nada en ese caso.
   */
  deviceLocation?: { latitude: number; longitude: number } | null
  /**
   * El recorrido por calles desde el punto de partida hasta la estación elegida, o null si todavía
   * no llegó, no se pudo calcular o no hay estación elegida.
   *
   * Cuándo se pide y contra qué servicio NO se decide acá: lo decide la pantalla con `useRoute`.
   * Este componente solo dibuja lo que le pasan, que es lo que deja cambiar de proveedor de rutas
   * sin tocar el mapa.
   */
  route?: Route | null
  /**
   * El recorrido hasta la estación que el conductor YA tiene reservada.
   *
   * **Es otro recorrido y no un modo del anterior**, y por eso es otra prop. `route` es el de la
   * estación que se está mirando y cambia con cada pin; este es el del viaje comprometido y se
   * queda en pantalla mientras la reserva esté vigente, se mire lo que se mire. Los dos pueden
   * estar a la vez, que es el caso normal: la línea llena hacia donde hay que ir y la punteada
   * hacia la estación que se está espiando.
   *
   * Se dibuja LLENO, y el de la selección punteado (ver `.station-route--tentative`): mismo verde
   * y misma forma los dos, lo único que los separa es el trazo. El lleno va para el viaje
   * reservado porque es el único que está decidido; el punteado dice "esto es un tanteo".
   */
  bookedRoute?: Route | null
}

export default function StationMap({
  stations,
  selectedStationId,
  onSelect,
  bottomInsetPx = 0,
  dimUnselected = false,
  origin = null,
  deviceLocation = null,
  route = null,
  bookedRoute = null,
}: StationMapProps) {
  const theme = useTheme()
  const [hoveredStationId, setHoveredStationId] = useState<number | null>(null)
  const selectedStation = stations.find((s) => s.stationId === selectedStationId) ?? null
  const hoverTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  function clearHoverTimer() {
    if (hoverTimer.current === null) return
    clearTimeout(hoverTimer.current)
    hoverTimer.current = null
  }

  function startHover(stationId: number) {
    clearHoverTimer()
    hoverTimer.current = setTimeout(() => setHoveredStationId(stationId), HOVER_DELAY_MS)
  }

  function cancelHover() {
    clearHoverTimer()
    setHoveredStationId(null)
  }

  // Si la pantalla se desmonta con el temporizador corriendo, el setState posterior cae sobre
  // un componente que ya no existe.
  useEffect(() => clearHoverTimer, [])

  return (
    <MapContainer
      center={DEFAULT_CENTER}
      zoom={DEFAULT_ZOOM}
      minZoom={MIN_ZOOM}
      maxZoom={MAX_ZOOM}
      maxBounds={ARGENTINA_BOUNDS}
      // Sin esto el borde es elástico: se puede arrastrar afuera y el mapa vuelve solo.
      maxBoundsViscosity={1}
      // Sin botones de zoom: se acerca con la rueda, con pellizco y tocando una estación,
      // que son los tres gestos con los que se usa un mapa. Los botones ocupaban la esquina
      // de abajo a la izquierda, que es justo por donde sube el panel de detalle.
      zoomControl={false}
      // Tampoco va el cartel de atribución que Leaflet dibuja abajo a la derecha: en celular
      // se monta sobre el panel de detalle y en el resto queda debajo del carrusel.
      //
      // OJO: apagar el cartel NO exime de acreditar a Esri y a OpenStreetMap, que la licencia
      // de los mosaicos sigue exigiendo. El crédito tiene que estar visible en algún otro lugar
      // de la aplicación —un pie de página o una pantalla "acerca de"—, y el texto para ponerlo
      // es TILE_ATTRIBUTION, que sigue exportado en mapConfig justo para eso.
      attributionControl={false}
      // Absoluto contra el contenedor, y no h-full, por un motivo concreto: un height en
      // porcentaje se resuelve contra la altura ESPECIFICADA del padre, y la del padre la
      // calcula el flex (height: auto). El porcentaje queda sin referencia, Leaflet mide 0px
      // y no dibuja nada, sin un solo error en consola. inset-0 no depende de porcentajes.
      className="absolute inset-0"
    >
      <InvalidateSizeOnResize />
      <PanToStation station={selectedStation} bottomInsetPx={bottomInsetPx} />

      {/*
        Los mosaicos cambian con el tema, y son DOS juegos distintos del mismo proveedor: no es el
        mismo mapa con un filtro encima. Un filtro sobre mosaicos claros no da un mapa oscuro, da
        los mismos trazos apagados; el proveedor sí redibuja el mapa con tinta clara sobre fondo
        oscuro, que es otra cosa.

        `key` fuerza a rehacer la capa al cambiar de tema. Sin eso Leaflet cambia la dirección pero
        deja en pantalla los mosaicos viejos hasta que hace falta pedir uno nuevo, así que al
        cambiar de tema el mapa se queda con el color anterior hasta que alguien lo mueve.
      */}
      <TileLayer
        key={theme}
        url={theme === 'light' ? TILES.light : TILES.dark}
        maxNativeZoom={MAX_NATIVE_ZOOM}
        maxZoom={MAX_ZOOM}
      />

      {stations.map((station) => (
        <Marker
          key={station.stationId}
          position={[station.latitude, station.longitude]}
          icon={stationPin(
            station,
            station.stationId === selectedStationId,
            dimUnselected && station.stationId !== selectedStationId,
          )}
          eventHandlers={{
            mouseover: () => startHover(station.stationId),
            mouseout: cancelHover,
            click: () => onSelect(station.stationId),
          }}
        >
          {hoveredStationId === station.stationId && (
            // permanent porque el que decide cuándo se ve es el temporizador, no Leaflet.
            // opacity 1 porque los tooltips vienen al 0.9 y la tarjeta se vería lavada.
            // El offset negativo compensa el margen que Leaflet reserva para la flecha del
            // tooltip: la flecha está oculta, pero el espacio se sigue contando. Medido con la
            // tarjeta abierta, dejaba su círculo 8px a la derecha del pin, y el pin asomaba por
            // debajo en vez de quedar tapado.
            <Tooltip
              permanent
              direction="right"
              offset={[-8, 1]}
              opacity={1}
              className="station-tooltip"
            >
              <StationHoverCard station={station} />
            </Tooltip>
          )}
        </Marker>
      ))}

      {/*
        El punto y nada más: el círculo de precisión que había acá se sacó por decisión de
        producto.

        Vale saber qué se perdió, porque el dato sigue estando en `accuracyM`. El círculo era
        el margen de error que informa el navegador, y su función era impedir que un punto
        solo afirmara una exactitud que la medición no tiene: sin GPS la ubicación sale de la
        red y puede errarle un kilómetro. El costo era que en ese mismo caso —el peor— tapaba
        media pantalla, que es lo que lo volvía intolerable justo cuando más decía.

        Si alguna vez se quiere el aviso sin el manchón, la forma es dibujarlo solo cuando la
        precisión es MALA de verdad (por encima de unos 500 m): ahí el círculo es una
        advertencia y no un adorno permanente.

        `interactive={false}`: el marcador se dibuja encima de los pines de estación y sin
        esto se queda con los clics de cualquiera que le caiga debajo. La ubicación no es
        algo que se elija, así que no tiene por qué capturar el mouse.
      */}
      {deviceLocation !== null && (
        <Marker
          position={[deviceLocation.latitude, deviceLocation.longitude]}
          icon={DEVICE_PIN}
          interactive={false}
          /*
            Por encima de los pines de estación. Leaflet ordena los marcadores por latitud
            —el que está más al sur tapa al que está más al norte—, y con ese criterio la
            ubicación propia desaparece detrás de cualquier estación que le quede al sur.
          */
          zIndexOffset={1000}
        />
      )}

      {/*
        El alfiler del punto de partida, solo cuando es una dirección escrita. Cuando el punto de
        partida es el dispositivo ya lo dibujó el disco de arriba, y los dos marcadores caerían
        exactamente en el mismo lugar.

        Un escalón por debajo del disco —999 contra 1000— porque cuando quedan cerca el que tiene
        que leerse entero es el "estás acá": el alfiler se entiende igual con la cabeza asomando,
        y el disco con un pedazo tapado deja de leerse como un punto.
      */}
      {origin !== null && origin.kind === 'address' && (
        <Marker
          position={[origin.latitude, origin.longitude]}
          icon={DEPARTURE_PIN}
          interactive={false}
          zIndexOffset={999}
        />
      )}

      {/*
        El recorrido por calles hasta la estación elegida, y mientras siga elegida.

        Es una línea quieta a propósito: dice por dónde se va, así que se tiene que leer como el
        camino a hacer. Punteada la de la estación que se está mirando, llena la de la reserva: ver
        `bookedRoute`.

        **Y si no hay recorrido no se dibuja nada.** Acá hubo una recta punteada de respaldo,
        para cuando el servicio de rutas no contestaba; se sacó porque una recta entre dos
        puntos NO es una respuesta más pobre a la misma pregunta, es la respuesta a otra: con
        una avenida de por medio puede quedar a la mitad de lo que hay que manejar. Quien la
        mira no tiene cómo saber cuál de las dos está viendo, así que lo único honesto es no
        dibujarla. Que faltó se dice con palabras, en el panel de detalle.

        El estilo vive en `.station-route`, en index.css.
      */}
      {/*
        El punteado va PRIMERO y el lleno encima. Los dos suelen salir del mismo lugar, así que las
        primeras cuadras se pisan, y ahí el que tiene que ganar es el de la reserva: al revés, los
        guiones del tanteo se dibujarían sobre la línea llena y no se vería ni uno ni el otro.
      */}
      {route !== null && (
        <Polyline
          positions={route.coordinates}
          interactive={false}
          pathOptions={{ className: 'station-route station-route--tentative' }}
        />
      )}

      {bookedRoute !== null && (
        <Polyline
          positions={bookedRoute.coordinates}
          interactive={false}
          pathOptions={{ className: 'station-route' }}
        />
      )}
    </MapContainer>
  )
}
