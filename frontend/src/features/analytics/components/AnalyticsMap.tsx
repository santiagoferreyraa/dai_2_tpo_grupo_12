import { useEffect, useRef } from 'react'
import L from 'leaflet'
import { MapContainer, Marker, TileLayer, Tooltip, useMap } from 'react-leaflet'

import { useTheme } from '@/features/theme/theme'
import {
  ARGENTINA_BOUNDS,
  DEFAULT_CENTER,
  DEFAULT_ZOOM,
  MAX_NATIVE_ZOOM,
  MAX_ZOOM,
  MIN_ZOOM,
  TILES,
} from '@/features/terminals/mapConfig'

import { analyticsPin } from '../analyticsPin'
import { formatAmount } from '../format'
import type { DashboardStation } from '../types'

/** Hasta dónde se acerca solo al enfocar una estación. El resto del zoom es del usuario. */
const FOCUS_ZOOM = 14
const FIT_PADDING: L.PointExpression = [48, 48]

type Located = DashboardStation & { position: NonNullable<DashboardStation['position']> }

/** Leaflet mide el contenedor una vez; con el grid, el tamaño final llega después. */
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
 * Lleva el mapa a lo que está elegido, cada vez que cambia la selección.
 *
 * - Una sola estación: vuela hasta ella.
 * - Varias: encuadra a todas las elegidas.
 * - La primera vez, con todas: encuadra sin animación, que es el punto de partida y no un viaje.
 *
 * Después de moverlo, el mapa sigue siendo del usuario: se arrastra y se acerca como siempre. El
 * efecto depende de la clave de la selección y no de los arreglos, así que un repintado cualquiera
 * no le devuelve el encuadre.
 */
function FollowSelection({ stations, selectedKey }: { stations: Located[]; selectedKey: string }) {
  const map = useMap()
  const first = useRef(true)
  const stationsRef = useRef(stations)

  /* Las estaciones se leen de una ref para que cambiar su energía no vuelva a encuadrar el mapa. */
  useEffect(() => {
    stationsRef.current = stations
  })

  useEffect(() => {
    const selected = stationsRef.current.filter((s) =>
      selectedKey.split(',').includes(String(s.id)),
    )
    const animate = !first.current
    if (selected.length === 0 && !first.current) return
    const targets = selected.length === 0 ? stationsRef.current : selected
    if (targets.length === 0) return
    first.current = false

    if (targets.length === 1) {
      const { latitude, longitude } = targets[0].position
      const zoom = Math.max(map.getZoom(), FOCUS_ZOOM)
      if (animate) map.flyTo([latitude, longitude], zoom, { duration: 0.8 })
      else map.setView([latitude, longitude], zoom)
      return
    }

    const bounds = L.latLngBounds(targets.map((s) => [s.position.latitude, s.position.longitude]))
    if (animate)
      map.flyToBounds(bounds, { padding: FIT_PADDING, maxZoom: FOCUS_ZOOM, duration: 0.8 })
    else map.fitBounds(bounds, { padding: FIT_PADDING, maxZoom: FOCUS_ZOOM })
  }, [map, selectedKey, stations.length])

  return null
}

interface AnalyticsMapProps {
  stations: DashboardStation[]
  selectedIds: Set<number>
  /** Tocar un pin enfoca esa estación sola. */
  onFocus: (stationId: number) => void
}

/**
 * El mapa del tablero: las estaciones que el usuario ve en las listas, y nada más.
 *
 * Reusa la configuración del mapa de Terminales —mosaicos, límites, zooms— y no su componente:
 * aquel trae el recorrido, la ubicación del dispositivo y la tarjeta de reserva, que acá no
 * tienen lugar.
 */
export default function AnalyticsMap({ stations, selectedIds, onFocus }: AnalyticsMapProps) {
  const theme = useTheme()
  const located = stations.filter((s): s is Located => s.position !== null)
  const selectedKey = located
    .filter((s) => selectedIds.has(s.id))
    .map((s) => s.id)
    .join(',')

  return (
    <MapContainer
      center={DEFAULT_CENTER}
      zoom={DEFAULT_ZOOM}
      minZoom={MIN_ZOOM}
      maxZoom={MAX_ZOOM}
      maxBounds={ARGENTINA_BOUNDS}
      maxBoundsViscosity={1}
      zoomControl={false}
      /* El crédito de los mosaicos va en otro lado de la aplicación: ver TILE_ATTRIBUTION. */
      attributionControl={false}
      className="absolute inset-0"
    >
      <InvalidateSizeOnResize />
      <FollowSelection stations={located} selectedKey={selectedKey} />
      <TileLayer
        key={theme}
        url={theme === 'light' ? TILES.light : TILES.dark}
        maxNativeZoom={MAX_NATIVE_ZOOM}
        maxZoom={MAX_ZOOM}
      />
      {located.map((station) => {
        const selected = selectedIds.has(station.id)
        return (
          <Marker
            key={station.id}
            position={[station.position.latitude, station.position.longitude]}
            icon={analyticsPin(station.energyKwh, selected)}
            /* Las elegidas arriba: con pines amontonados, la que importa no queda tapada. */
            zIndexOffset={selected ? 500 : 0}
            eventHandlers={{ click: () => onFocus(station.id) }}
          >
            <Tooltip direction="top" offset={[0, -22]} opacity={1} className="station-tooltip">
              <span className="glass-panel glass-modal text-text block rounded-xl px-3 py-1.5 text-xs font-semibold whitespace-nowrap">
                {station.name}
                <span className="text-text-muted ml-2 font-normal">
                  {formatAmount(station.energyKwh)} kWh
                </span>
              </span>
            </Tooltip>
          </Marker>
        )
      })}
    </MapContainer>
  )
}
