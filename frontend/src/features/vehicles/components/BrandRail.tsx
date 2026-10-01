import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

/**
 * El riel de marcas del selector de auto: una rueda vertical que gira sin fin.
 *
 * **La escala cuelga de la SELECCIÓN y no de la posición del scroll**, y esa es la diferencia con
 * el carrusel de estaciones del mapa, del que sale todo lo demás. Allá la ficha grande es la que
 * quedó en el centro de la columna; acá la marca grande es la elegida, y las demás se van
 * achicando según cuántos lugares estén de ella.
 *
 * Probé primero con el efecto del mapa —escala por distancia al centro— y está mal para este caso
 * por dos motivos. Uno: con catorce marcas la lista entra casi entera en la columna, así que casi
 * todas caen cerca del centro y el degradado no se ve. Dos, y más importante: soltando el scroll
 * en cualquier lado quedaba una marca grande que NO era la abierta a la derecha, o sea que el
 * tamaño decía una cosa y la grilla otra.
 *
 * Anclado a la selección no hay forma de que eso pase, y de paso el efecto no depende de medir
 * nada: es una cuenta sobre el índice, así que anda igual con la lista llena, filtrada o de una
 * sola marca.
 *
 * **El scroll sigue siendo infinito**: pasada la última marca siguen la primera y las que vienen,
 * sin un borde donde la columna se quede vacía. La ilusión la sostienen dos piezas —la lista
 * repetida tres veces, y un salto de un bloque entero cuando el scroll se sale de la copia del
 * medio—; como lo que se ve después del salto es idéntico a lo que se veía antes, el corte no se
 * percibe. Es el mecanismo de `StationCarousel`, y allá está explicado en detalle.
 *
 * **Y se apaga solo cuando no hace falta.** Con pocas marcas —o con el buscador filtrando— las
 * tres copias entrarían todas a la vez y la misma marca aparecería tres veces, que se lee como un
 * error y no como una rueda. No se puede decidir contando marcas: cuánto ocupan depende del alto
 * de la ventana, así que se mide después de que el navegador acomodó.
 */

/** Cuánto mide la marca elegida respecto de las demás. Es el número que define el efecto. */
const MAX_SCALE = 1.35

/** Lo más chica que llega a verse una marca lejana. */
const MIN_SCALE = 0.8

/**
 * A cuántos lugares de la elegida se llega al tamaño mínimo.
 *
 * Con un alcance corto el degradado se agota en dos renglones y el resto de la lista queda toda
 * del mismo tamaño; con uno largo, las vecinas casi no se distinguen de la elegida. Cuatro deja
 * ver el escalón en las de al lado y todavía tiene a dónde caer.
 */
const REACH = 4

/**
 * Cuántas veces se repite la lista. Tres y no dos: hace falta una copia completa arriba y otra
 * abajo de la visible, o el salto ocurriría con contenido a la vista y se notaría el corte.
 */
const COPIES = 3

/** Cuánto se espera a que termine un desplazamiento suave antes de volver a acomodar el bloque. */
const SMOOTH_SCROLL_MS = 700

interface BrandRailProps {
  brands: string[]
  /** La marca abierta, o `null` mientras el catálogo no llegó. */
  activeBrand: string | null
  onSelect: (brand: string) => void
}

/** La clave identifica a una entrada concreta: la misma marca existe una vez por copia. */
function itemKey(copy: number, brand: string): string {
  return `${String(copy)}:${brand}`
}

/**
 * Cuántos lugares separan a dos marcas, contando por el camino más corto de la rueda.
 *
 * **Circular y no la resta de los índices.** En una lista que da la vuelta, la última marca es
 * vecina de la primera: midiendo derecho, con la primera elegida la última quedaría a trece
 * lugares —o sea, del tamaño mínimo— cuando en pantalla está pegada arriba de ella.
 */
function ringDistance(a: number, b: number, length: number): number {
  const straight = Math.abs(a - b)
  return Math.min(straight, length - straight)
}

export default function BrandRail({ brands, activeBrand, onSelect }: BrandRailProps) {
  const listRef = useRef<HTMLDivElement>(null)
  const itemsRef = useRef(new Map<string, HTMLElement>())

  /*
    Mientras corre un desplazamiento suave no se puede tocar `scrollTop`: cualquier escritura lo
    cancela en seco, y el salto de bloque es exactamente eso. Queda suspendido hasta que termina.
  */
  const smoothScrollRef = useRef(false)
  const smoothTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [looping, setLooping] = useState(false)

  const firstBrand = brands.length > 0 ? brands[0] : null
  const activeIndex = activeBrand === null ? -1 : brands.indexOf(activeBrand)

  /**
   * Alto de un bloque: la distancia entre la misma marca en dos copias consecutivas.
   *
   * Se mide en vez de calcularse sumando alturas porque entre las entradas hay separación y porque
   * cada una mide distinto —la elegida está agrandada—, así que sumar a mano se desincroniza en
   * cuanto alguien cambia de marca.
   */
  const blockHeight = useCallback((): number => {
    if (firstBrand === null) return 0
    const first = itemsRef.current.get(itemKey(0, firstBrand))
    const second = itemsRef.current.get(itemKey(1, firstBrand))
    if (first === undefined || second === undefined) return 0
    return second.offsetTop - first.offsetTop
  }, [firstBrand])

  /**
   * Devuelve el scroll a la copia del medio cuando se salió de ella.
   *
   * Sin suavizado y a propósito: es un corte instantáneo entre dos vistas idénticas, que es
   * exactamente lo que lo vuelve invisible.
   */
  const recenterBlock = useCallback(() => {
    const list = listRef.current
    if (list === null || smoothScrollRef.current) return

    const block = blockHeight()
    if (block <= 0) return

    /*
      En bucle y no con un solo `if`: un salto grande de una vez —arrastrar la barra, o el tope al
      que el navegador recorta al final del contenido— puede dejar la posición a más de un bloque
      de distancia, y corrigiendo de a uno se quedaría afuera.
    */
    while (list.scrollTop < block * 0.5) list.scrollTop += block
    while (list.scrollTop > block * 1.5) list.scrollTop -= block
  }, [blockHeight])

  /**
   * Decide si conviene repetir la lista, midiendo lo que ya dibujó el navegador.
   *
   * Las dos condiciones no son la misma al revés: para encender pide un margen del 20%, y para
   * apagar alcanza con que el bloque entre justo. Esa banda muerta evita que en el borde exacto se
   * prenda y se apague en cada repintado.
   */
  const evaluateLooping = useCallback(() => {
    const list = listRef.current
    if (list === null) return

    if (looping) {
      const block = blockHeight()
      if (block > 0 && block <= list.clientHeight) setLooping(false)
      return
    }

    setLooping(list.scrollHeight > list.clientHeight * 1.2)
  }, [blockHeight, looping])

  /** Deja la marca pedida en el centro, eligiendo la copia que ya está más cerca. */
  const centerBrand = useCallback(
    (brand: string) => {
      const list = listRef.current
      if (list === null) return

      const center = list.scrollTop + list.clientHeight / 2
      let target: number | null = null

      for (let copy = 0; copy < COPIES; copy += 1) {
        const element = itemsRef.current.get(itemKey(copy, brand))
        if (element === undefined) continue

        const itemCenter = element.offsetTop + element.offsetHeight / 2
        if (target === null || Math.abs(itemCenter - center) < Math.abs(target - center)) {
          target = itemCenter
        }
      }

      if (target === null) return

      smoothScrollRef.current = true
      if (smoothTimerRef.current !== null) clearTimeout(smoothTimerRef.current)
      smoothTimerRef.current = setTimeout(() => {
        smoothScrollRef.current = false
        recenterBlock()
      }, SMOOTH_SCROLL_MS)

      list.scrollTo({ top: target - list.clientHeight / 2, behavior: 'smooth' })
    },
    [recenterBlock],
  )

  /**
   * Vuelve a medir tras cada cambio que altere el alto del contenido —otra marca elegida, otro
   * filtro del buscador— y deja el scroll en la copia del medio, que es la única posición desde la
   * que se puede correr en los dos sentidos sin tocar un borde.
   *
   * `useLayoutEffect` y no `useEffect`: corre antes de que el navegador pinte, así el salto inicial
   * a la copia del medio no se ve como un tirón.
   */
  useLayoutEffect(() => {
    const list = listRef.current
    if (list === null) return

    evaluateLooping()

    if (firstBrand !== null) {
      const middle = itemsRef.current.get(itemKey(1, firstBrand))
      if (middle !== undefined) {
        list.scrollTop = middle.offsetTop + middle.offsetHeight / 2 - list.clientHeight / 2
      }
    }

    /* El alto cambia al redimensionar, y con él si un bloque entra o no. */
    const observer = new ResizeObserver(evaluateLooping)
    observer.observe(list)

    return () => {
      observer.disconnect()
      if (smoothTimerRef.current !== null) clearTimeout(smoothTimerRef.current)
    }
  }, [brands.length, evaluateLooping, firstBrand, looping])

  /** Al cambiar de marca, la rueda la trae al centro para que la grande quede a la vista. */
  useEffect(() => {
    if (activeBrand === null) return
    centerBrand(activeBrand)
  }, [activeBrand, centerBrand])

  const copies = looping ? Array.from({ length: COPIES }, (_, copy) => copy) : [0]

  /**
   * Cuál de las copias es la "de verdad" para el lector de pantalla y para el tabulador.
   *
   * Repitiendo es la del medio; sin repetir, la única que hay. Anunciar las tres sería leer la
   * misma lista de marcas tres veces seguidas.
   */
  const realCopy = looping ? 1 : 0

  return (
    <div
      ref={listRef}
      onScroll={recenterBlock}
      role="tablist"
      aria-label="Marcas"
      aria-orientation="vertical"
      /*
        **`absolute inset-0`, y no es cosmético: sin esto el riel no scrollea.** Un contenedor con
        `overflow-y-auto` y sin alto definido crece hasta donde llegue su contenido, así que nunca
        hay nada desbordado que desplazar. Sacándolo del flujo contra un padre `relative` que sí
        tiene alto, la caja queda fija y el contenido de más pasa a ser scroll. Es lo mismo que
        hace el carrusel del mapa con su columna.

        `justify-center` cuando NO repite depende de lo mismo: centrar verticalmente necesita un
        alto contra el cual centrar.
      */
      className={`no-scrollbar absolute inset-0 flex flex-col overflow-y-auto py-4 ${
        looping ? '' : 'justify-center'
      }`}
    >
      {copies.map((copy) =>
        brands.map((brand, index) => {
          const selected = brand === activeBrand
          /*
            Sin marca elegida todavía, todas van al tamaño de reposo: agrandar una al azar diría
            que está elegida cuando no lo está.
          */
          const steps = activeIndex === -1 ? REACH : ringDistance(index, activeIndex, brands.length)
          const scale = MAX_SCALE - (MAX_SCALE - MIN_SCALE) * (Math.min(steps, REACH) / REACH)

          return (
            <button
              key={itemKey(copy, brand)}
              type="button"
              role="tab"
              aria-selected={selected}
              /*
                Las copias son el mismo contenido repetido: anunciarlo tres veces sería ruido. Solo
                la del medio es real para el lector de pantalla y para el tabulador.
              */
              aria-hidden={copy === realCopy ? undefined : true}
              tabIndex={copy === realCopy ? undefined : -1}
              ref={(element) => {
                const key = itemKey(copy, brand)
                if (element === null) itemsRef.current.delete(key)
                else itemsRef.current.set(key, element)
              }}
              onClick={() => {
                onSelect(brand)
              }}
              /*
                **La escala va en el `style` y no como clase.** Es un número calculado por entrada,
                y Tailwind genera las clases leyendo el código fuente: una clase armada con una
                plantilla no existe en la hoja de estilos, así que no pintaría nada.

                `origin-left` para que crezca hacia adentro del panel: con el origen al medio, las
                marcas lejanas se leen como corridas en vez de como más chicas. Y `scale` cambia
                cómo se VE pero no cuánto ocupa, así que el `py` acompaña para que las grandes no
                se monten sobre sus vecinas.
              */
              style={{ transform: `scale(${String(scale)})` }}
              className={`shrink-0 origin-left cursor-pointer px-5 text-left text-base font-extrabold tracking-tight whitespace-nowrap transition-[transform,color,opacity] duration-200 ${
                selected
                  ? 'text-primary py-2.5 opacity-100'
                  : 'text-text-muted hover:text-text py-1.5 opacity-70 hover:opacity-100'
              }`}
            >
              {brand}
            </button>
          )
        }),
      )}
    </div>
  )
}
