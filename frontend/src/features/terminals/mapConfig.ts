/**
 * Constantes del mapa: proveedor de mosaicos, límites y vista inicial.
 *
 * Separadas del componente por una razón práctica: un archivo que exporta un componente y
 * además constantes rompe el Fast Refresh de Vite, y cada cambio recarga la página entera en
 * vez de reemplazar el componente. Con el mapa eso significa perder el centro y el zoom en
 * cada guardado.
 */

import L from 'leaflet'

/**
 * Recuadro que encierra la Argentina continental, de Tierra del Fuego al límite con Bolivia.
 *
 * El mapa no es un archivo que se descargue: son mosaicos que un proveedor sirve según hacia
 * dónde esté mirando el usuario. "El mapa de Argentina" se consigue limitando la vista, no
 * bajando un país. Sin esto el usuario termina arrastrando hasta el Atlántico, sin una sola
 * estación en pantalla y sin entender por qué.
 */
export const ARGENTINA_BOUNDS = L.latLngBounds([-55.2, -73.6], [-21.7, -53.6])

/**
 * Proveedor de mosaicos: los basemaps "Canvas" de ArcGIS. Gratis, sin API key y sin registro.
 *
 * Se descartaron dos alternativas, y por qué importa:
 *
 * CARTO (`basemaps.cartocdn.com`) es el que da el look de la referencia, pero ya no es libre:
 * responde 200 y devuelve el mosaico atravesado por un "API KEY REQUIRED". No falla, se ve mal.
 *
 * OpenStreetMap crudo es libre de verdad, pero viene con parques verdes y autopistas naranjas.
 * Oscurecerlo con un filtro CSS no da un mapa oscuro: da los MISMOS colores apagados y sucios,
 * porque un filtro transforma una imagen que ya tiene color, no vuelve a dibujar el mapa.
 *
 * Estos vienen en gris de origen, sin un solo color de relleno. Sobre eso el filtro de
 * `index.css` sí funciona, porque oscurecer un gris no puede ensuciar ningún color.
 */
export const TILES = {
  dark: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
  /* Para cuando entre el tema claro (RNF08). Mismo proveedor, así el mapa no cambia de
     carácter al cambiar de tema: solo de luminosidad. */
  light:
    'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}',
}

/**
 * OJO con el orden: {z}/{y}/{x}, con la fila ANTES de la columna. Es al revés que en OSM y que
 * en casi todo lo demás. Invertidos, el mapa carga igual pero muestra otro lugar del mundo.
 */

/**
 * Obligatoria por licencia de los mosaicos. No se saca.
 *
 * El cartel que Leaflet dibujaba sobre el mapa sí se sacó, porque se montaba con el panel y el
 * carrusel; el crédito en sí no es opcional y tiene que aparecer en otro lado de la aplicación
 * —pie de página o pantalla "acerca de"—. Esta constante es el texto para ponerlo ahí.
 */
export const TILE_ATTRIBUTION =
  'Mosaicos &copy; <a href="https://www.esri.com/">Esri</a> &mdash; Esri, HERE, Garmin, &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

/**
 * Obligatoria por licencia del servicio de rutas, y es una deuda distinta de la de arriba.
 *
 * Los mosaicos y las rutas vienen de dos proveedores que no tienen nada que ver entre sí: el
 * dibujo del mapa es de Esri, el recorrido lo calcula OSRM sobre los datos de OpenStreetMap. Que
 * OSM aparezca en las dos no las vuelve la misma línea —acredita dos usos distintos del mismo
 * dato—, y acreditar solo una deja al otro proveedor sin crédito.
 *
 * Va al mismo lugar que TILE_ATTRIBUTION: el pie de página o la pantalla "acerca de" que el
 * proyecto todavía debe. Ver `routing.ts`, que es donde se explica de dónde sale la ruta.
 */
export const ROUTING_ATTRIBUTION =
  'Rutas por <a href="https://project-osrm.org/">OSRM</a>, con datos de <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> (ODbL)'

/**
 * El servicio tiene mosaicos hasta el zoom 16. Del 17 en adelante devuelve 200 con un mosaico
 * vacío: el mapa se pondría negro sin un solo error que lo explique.
 *
 * MAX_NATIVE_ZOOM es hasta dónde se piden mosaicos. Hoy queda por encima de MAX_ZOOM, o sea
 * que no llega a actuar; se deja igual porque es el límite real del proveedor y es lo que
 * evita el mapa negro si alguna vez se sube el techo de acá abajo.
 */
export const MAX_NATIVE_ZOOM = 16

/**
 * Hasta dónde puede acercarse el usuario.
 *
 * El mapa es para elegir a qué estación ir, no para mirar una fachada. Pasando de 15 la
 * pantalla queda con una sola estación y ninguna referencia alrededor, que es justo lo que no
 * sirve para decidir. A 15 se llega a la cuadra sin perder las calles de alrededor.
 *
 * Queda un nivel por debajo del techo del proveedor (16), así que los mosaicos siguen siendo
 * reales: es una decisión de producto y no una limitación técnica.
 */
export const MAX_ZOOM = 15

/** Obelisco. Punto de partida mientras no haya geolocalización del usuario. */
export const DEFAULT_CENTER: L.LatLngExpression = [-34.6037, -58.3816]

/**
 * Radio de la búsqueda mientras no exista la búsqueda por viewport.
 *
 * 4.000 km desde el Obelisco cubre el país entero con margen —Ushuaia está a unos 2.400— así
 * que ninguna estación queda afuera. El centro NO es arbitrario: `distanceKm` la calcula el
 * backend contra el punto consultado y el carrusel la muestra, así que buscar desde otro lado
 * pondría en cada ficha una distancia que no significa nada para quien mira este mapa.
 */
export const COUNTRY_RADIUS_KM = 4000

export const DEFAULT_ZOOM = 12

/** Alejarse más que esto saca al país entero de escala y no muestra nada útil. */
export const MIN_ZOOM = 4

/**
 * Zoom al que se acerca el mapa cuando se elige una estación.
 *
 * Atado a MAX_ZOOM a propósito: elegir una estación tiene que dejar el mapa tan cerca como el
 * usuario podría ponerlo a mano, ni más ni menos. Con los dos valores sueltos, mover uno solo
 * dejaba el vuelo y el zoom manual desalineados.
 */
export const FOCUS_ZOOM = MAX_ZOOM
