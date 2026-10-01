/**
 * Dónde viven las imágenes del catálogo: el logo de cada marca y la foto de cada modelo.
 *
 * **Están en `public/` y no importadas**, por lo mismo que los avatares: son decenas de archivos
 * de los que una pantalla muestra unos pocos. Importándolos, el empaquetador los mete todos al
 * bundle y el navegador descarga el catálogo entero para dibujar una grilla de ocho autos. Desde
 * `public` se pide solo lo que se ve.
 *
 * **Las dos direcciones se arman ACÁ y en ningún otro lado.** Es la misma regla que las rutas de
 * la API: si cada componente concatena su propia ruta, mover la carpeta es buscar y reemplazar
 * por todo el proyecto. Acá es cambiar dos plantillas.
 *
 * **Ningún archivo es obligatorio.** Los logos y las fotos se van cargando de a poco, así que el
 * catálogo funciona con la carpeta vacía: los componentes que las muestran dibujan un reemplazo
 * cuando la imagen no carga. Un modelo sin foto se puede elegir igual, porque lo que define al
 * auto son el conector y las potencias, no el dibujo.
 */

import type { VehicleModel } from './types'

/**
 * El nombre de archivo de una marca: minúsculas, sin acentos y con guiones.
 *
 * **Se DERIVA del nombre de la marca en vez de guardarse en la base.** El logo es un dato de la
 * marca, no del modelo, y el catálogo no tiene tabla de marcas: guardarlo en cada fila dejaría
 * diecinueve lugares donde el mismo archivo puede quedar escrito distinto, y bastaría con que uno
 * dijera "mercedes" y otro "mercedes-benz" para que media grilla perdiera el logo.
 *
 * La cuenta es deterministic y aburrida a propósito: lo que entra son nombres comerciales cortos
 * —"BYD", "Mercedes-Benz", "Volkswagen"— y lo que sale tiene que ser adivinable por quien guarda
 * el archivo sin leer este código.
 */
export function brandSlug(brand: string): string {
  return (
    brand
      .toLowerCase()
      /*
        Los acentos se separan de su letra y se descartan, así "Citroën" da "citroen". Un nombre
        de archivo con acento funciona en Linux y en macOS, pero viaja mal por la URL y peor por
        un ZIP hecho en Windows: es el tipo de cosa que anda en la máquina de quien lo subió.
      */
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      /* Todo lo que no sea letra o número pasa a ser separador: espacios, puntos, guiones bajos. */
      .replace(/[^a-z0-9]+/g, '-')
      /* Sin guiones colgando en las puntas, que es lo que dejan un punto o un espacio al final. */
      .replace(/^-+|-+$/g, '')
  )
}

/** El logo de una marca. Ver `BrandLogo`, que dibuja el nombre cuando el archivo no está. */
export function brandLogoSrc(brand: string): string {
  return `/vehicles/brands/${brandSlug(brand)}.png`
}

/** La foto de un modelo. Ver `VehicleImage`, que la reemplaza cuando el archivo no está. */
export function vehicleImageSrc(model: VehicleModel): string {
  return `/vehicles/models/${model.imageSlug}.png`
}

/**
 * La foto que va en lugar de la de un modelo que todavía no tiene archivo.
 *
 * **Vive en la misma carpeta que las fotos reales** y no suelta en `public/`: es una foto de auto
 * más, del mismo tamaño y recortada igual, y separarla haría que quien agrega modelos tenga que
 * saber de dos lugares en vez de uno.
 */
export const VEHICLE_IMAGE_FALLBACK = '/vehicles/models/car-not-found.png'
