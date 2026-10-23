/**
 * Carrusel de estaciones, superpuesto sobre el borde derecho del mapa.
 *
 * No es una lista con scroll y ya: la ficha que queda en el centro vertical se ve entera y las
 * demás se achican y se apagan a medida que se alejan. Eso pone el foco en una sola estación
 * por vez sin esconder las otras, que es lo que hace que el mapa siga leyéndose atrás.
 *
 * Va superpuesto y no al costado para no robarle ancho al mapa: el mapa es la pantalla, esto
 * es una capa encima.
 *
 * El scroll es infinito: al pasar la última estación siguen la primera y las que vienen, sin
 * un borde donde la columna se quede vacía. Cómo se consigue está explicado en COPIES.
 */

import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'

import { useMediaQuery } from '@/lib/useMediaQuery'

import StationResultCard from './StationResultCard'
import type { StationResult } from '../types'

/**
 * Cuánto se encoge y se apaga la ficha más lejana del centro.
 *
 * Están acá y no repartidos por el código porque son el efecto: se tocan estos dos números y
 * cambia el carácter del carrusel entero.
 */
const MIN_SCALE = 0.72
const MIN_OPACITY = 0.25

/**
 * Cuánto crece la ficha que tiene el mouse encima.
 *
 * Multiplica a la escala del efecto en vez de reemplazarla: una ficha lejana crece un poco
 * respecto de su tamaño, no salta al de la del centro. Si la reemplazara, pasar el mouse por
 * la columna haría saltar cada ficha al frente y el efecto de foco dejaría de leerse.
 */
const HOVER_SCALE = 1.06

/**
 * Cuántas veces se repite la lista, una atrás de la otra.
 *
 * El scroll infinito es una ilusión sostenida por dos piezas: la lista está repetida tres
 * veces, y cuando el scroll se sale de la copia del medio se lo corre un bloque entero de
 * golpe. Como lo que se ve después del salto es idéntico a lo que se veía antes, el salto no
 * se percibe: el usuario nunca llega a un borde.
 *
 * Tres y no dos porque hacen falta una copia completa arriba y otra abajo de la visible. Con
 * dos, el salto ocurriría con contenido a la vista y se notaría el corte.
 */
const COPIES = 3

/** Cuánto se espera a que termine un desplazamiento suave antes de volver a acomodar el bloque. */
const SMOOTH_SCROLL_MS = 700

interface StationCarouselProps {
  stations: StationResult[]
  selectedStationId: number | null
  onSelect: (stationId: number) => void
}

/** La clave identifica a una ficha concreta: la misma estación existe una vez por copia. */
function itemKey(copy: number, stationId: number): string {
  return `${copy}:${stationId}`
}

export default function StationCarousel({
  stations,
  selectedStationId,
  onSelect,
}: StationCarouselProps) {
  const listRef = useRef<HTMLDivElement>(null)
  const itemsRef = useRef(new Map<string, HTMLElement>())

  /*
   * Si la columna está guardada fuera de la pantalla.
   *
   * Vive acá y no en la pantalla que la usa porque es presentación de este componente: quien lo
   * monta decide SI hay carrusel, no cuánto de él se ve. Subirlo a `StationsMapPage` obligaría a
   * pasar un par de props que ningún otro componente necesita.
   *
   * **No se reabre solo al elegir una estación desde el mapa.** Guardar la columna es una decisión
   * del usuario sobre cuánto mapa quiere ver, y traerla de vuelta por su cuenta le desharía esa
   * decisión justo cuando está mirando otra cosa.
   */
  const [collapsed, setCollapsed] = useState(false)

  /*
   * La lengüeta y la columna están en ramas distintas del DOM, así que `aria-controls` es lo único
   * que le dice a un lector de pantalla qué abre ese botón. `useId` y no una constante porque nada
   * impide que algún día haya dos carruseles en la misma página.
   */
  const listId = useId()

  /*
   * Con movimiento reducido el carrusel aparece y desaparece de una. El deslizado es lo que explica
   * A DÓNDE se fue la columna, pero quien pidió menos movimiento ya aceptó perder esa clase de
   * pista, y el estado final es idéntico.
   */
  const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')

  /*
   * La ficha que tiene el mouse encima, como ref y no como estado: igual que la escala del
   * scroll, se pinta escribiendo el estilo, así que pasarla por React solo agregaría un
   * render por cada entrada y salida del mouse sin cambiar nada de lo que se ve.
   */
  const hoveredRef = useRef<string | null>(null)
  const frameRef = useRef<number | null>(null)

  // Mientras corre un desplazamiento suave no se puede tocar scrollTop: cualquier escritura lo
  // cancela en seco. El salto de bloque queda suspendido hasta que termina.
  const smoothScrollRef = useRef(false)
  const smoothTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  /**
   * Si la lista se repite o si es una lista común.
   *
   * Repetir solo tiene sentido cuando un bloque no entra en la columna. Con pocos resultados
   * —el buscador puede dejar uno solo— las tres copias se verían todas a la vez y la misma
   * estación aparecería tres veces, que se lee como un error y no como un carrusel.
   *
   * No se puede decidir por la cantidad de estaciones: cuánto ocupan depende del alto de la
   * ventana y de si hay una ficha desplegada. Se mide después de que el navegador acomodó.
   */
  const [looping, setLooping] = useState(false)

  const firstStationId = stations.length > 0 ? stations[0].stationId : null

  /**
   * Alto de un bloque: la distancia entre la misma ficha en dos copias consecutivas.
   *
   * Se mide en vez de calcularse sumando alturas porque las fichas cambian de alto al
   * desplegarse, y porque entre ellas hay separación: sumar a mano se desincroniza al primer
   * cambio de diseño.
   */
  const blockHeight = useCallback((): number => {
    if (firstStationId === null) return 0
    const first = itemsRef.current.get(itemKey(0, firstStationId))
    const second = itemsRef.current.get(itemKey(1, firstStationId))
    if (first === undefined || second === undefined) return 0
    return second.offsetTop - first.offsetTop
  }, [firstStationId])

  /**
   * Reparte escala y opacidad según la distancia de cada ficha al centro de la columna.
   *
   * Escribe directo sobre el estilo del elemento en vez de pasar por el estado de React: esto
   * corre en cada cuadro del scroll, y volver a renderizar cuarenta y cinco fichas por cuadro
   * se nota.
   *
   * Mide con offsetTop y no con getBoundingClientRect: offsetTop es la posición de diseño,
   * ajena a la escala que este mismo código acaba de aplicar. Midiendo el rectángulo pintado,
   * el efecto se estaría alimentando de su propio resultado.
   */
  const paint = useCallback(() => {
    const list = listRef.current
    if (list === null) return

    const center = list.scrollTop + list.clientHeight / 2
    const reach = list.clientHeight / 2

    for (const [key, element] of itemsRef.current.entries()) {
      const offset = Math.abs(element.offsetTop + element.offsetHeight / 2 - center)
      const distance = reach === 0 ? 0 : Math.min(1, offset / reach)

      const hover = key === hoveredRef.current ? HOVER_SCALE : 1

      element.style.transform = `scale(${(1 - (1 - MIN_SCALE) * distance) * hover})`
      element.style.opacity = String(1 - (1 - MIN_OPACITY) * distance)
    }
  }, [])

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

    // En bucle y no con un solo if: un salto grande de una vez —arrastrar la barra de scroll,
    // o el tope al que el navegador recorta al final del contenido— puede dejar la posición a
    // más de un bloque de distancia, y corrigiendo de a uno se quedaría afuera.
    while (list.scrollTop < block * 0.5) list.scrollTop += block
    while (list.scrollTop > block * 1.5) list.scrollTop -= block
  }, [blockHeight])

  /**
   * Decide si conviene repetir la lista, midiendo lo que ya dibujó el navegador.
   *
   * Las dos condiciones no son la misma al revés: para encender pide un margen del 20 %, y
   * para apagar alcanza con que el bloque entre justo. Esa banda muerta evita que en el borde
   * exacto se prenda y se apague en cada repintado.
   */
  const evaluateLooping = useCallback(() => {
    const list = listRef.current
    if (list === null) return

    if (looping) {
      const block = blockHeight()
      if (block > 0 && block <= list.clientHeight) setLooping(false)
      return
    }

    // Sin repetir, el contenido desplazable ES una copia: si no llena la columna, no hay nada
    // que hacer girar.
    setLooping(list.scrollHeight > list.clientHeight * 1.2)
  }, [blockHeight, looping])

  /**
   * El scroll dispara muchas veces por cuadro; el rAF colapsa la ráfaga en un solo repintado.
   *
   * Cancela el cuadro anterior en vez de descartar el pedido nuevo: si el navegador deja de
   * entregar cuadros —una pestaña en segundo plano no recibe ninguno—, descartando pedidos
   * quedaría una marca puesta para siempre y el efecto no volvería a pintar nunca más.
   */
  const schedulePaint = useCallback(() => {
    if (frameRef.current !== null) cancelAnimationFrame(frameRef.current)
    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = null
      paint()
    })
  }, [paint])

  const handleScroll = useCallback(() => {
    recenterBlock()
    schedulePaint()
  }, [recenterBlock, schedulePaint])

  /** Deja la ficha pedida en el centro, eligiendo la copia que ya está más cerca. */
  const centerStation = useCallback(
    (stationId: number) => {
      const list = listRef.current
      if (list === null) return

      const center = list.scrollTop + list.clientHeight / 2
      let target: number | null = null

      for (let copy = 0; copy < COPIES; copy += 1) {
        const element = itemsRef.current.get(itemKey(copy, stationId))
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
   * Vuelve a medir tras cada cambio que altere el alto del contenido —otra búsqueda, otra
   * estación desplegada— y deja el scroll en la copia del medio, que es la única posición desde
   * la que se puede correr en los dos sentidos sin tocar un borde.
   *
   * useLayoutEffect y no useEffect: corre antes de que el navegador pinte, así el salto inicial
   * a la copia del medio no se ve como un tirón.
   */
  useLayoutEffect(() => {
    const list = listRef.current
    if (list === null) return

    evaluateLooping()

    if (firstStationId !== null) {
      const middle = itemsRef.current.get(itemKey(1, firstStationId))
      if (middle !== undefined) {
        list.scrollTop = middle.offsetTop + middle.offsetHeight / 2 - list.clientHeight / 2
      }
    }

    paint()

    // El alto de la columna cambia al redimensionar la ventana, y con él tanto el centro como
    // la respuesta a si un bloque entra o no.
    const observer = new ResizeObserver(() => {
      evaluateLooping()
      schedulePaint()
    })
    observer.observe(list)

    return () => {
      observer.disconnect()
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current)
      if (smoothTimerRef.current !== null) clearTimeout(smoothTimerRef.current)
    }
  }, [evaluateLooping, firstStationId, looping, paint, schedulePaint, stations.length])

  /**
   * Al elegir una estación —desde acá o desde un pin del mapa— el carrusel la trae al centro.
   *
   * Corre después de pintar, así que la ficha ya está desplegada con sus conectores y el
   * centrado usa el alto nuevo. Si corriera antes, quedaría centrada la ficha plegada y al
   * crecer se saldría de lugar.
   */
  useEffect(() => {
    if (selectedStationId === null) return
    centerStation(selectedStationId)
  }, [centerStation, selectedStationId])

  const copies = looping ? Array.from({ length: COPIES }, (_, copy) => copy) : [0]

  /**
   * Cuál de las copias es la "de verdad" para quien usa lector de pantalla y para el tabulador.
   *
   * Repitiendo es la del medio; sin repetir, la única que hay. Anunciar las tres sería leer la
   * misma lista de estaciones tres veces seguidas.
   */
  const realCopy = looping ? 1 : 0

  return (
    /*
      Acá había un degradado oscuro detrás de las fichas, para despegarlas del mapa. Se sacó:
      las fichas ya tienen su propio vidrio y su sombra, así que se sostienen solas, y el
      degradado apagaba una franja de mapa de casi treinta rem sin dar nada a cambio. Desde que
      el mapa acompaña al tema, además, oscurecerlo era exactamente lo contrario de lo que hace
      el tema claro.

      Los z-index altos no son un número al azar. Leaflet le pone z-index a sus propios panes
      —200 los mosaicos, 600 los marcadores, hasta 1000 los controles— y el contenedor del mapa no
      crea contexto de apilamiento, así que esos panes compiten directamente con esta capa. Con
      z-index automático, el carrusel queda DEBAJO de los mosaicos aunque venga después en el DOM:
      se ve mientras los mosaicos no cargaron y desaparece cuando cargan.

      En celular el carrusel no va. Una columna de 320px sobre una pantalla de 390 no deja mapa:
      taparía justo lo que se vino a mirar. Ahí el patrón es el panel que sube desde abajo.

      **Este envoltorio existe para el plegado.** Es la pieza que se desplaza, y lleva adentro la
      columna que scrollea y la lengüeta que la trae de vuelta. Tiene que ser un elemento aparte
      de la columna por dos motivos: la lengüeta no puede scrollear con las fichas, y el desplazado
      no puede aplicarse sobre un contenedor con `overflow-y-auto` sin que el navegador le sume
      una barra horizontal al sacar el contenido de su caja.
    */
    <div
      className={`absolute inset-y-0 right-0 z-[1110] hidden w-80 md:block ${
        collapsed ? 'translate-x-full' : 'translate-x-0'
      } ${prefersReducedMotion ? '' : 'transition-transform duration-300 ease-out'}`}
    >
      {/*
        La lengüeta para esconder y traer el carrusel.

        **Va adentro del envoltorio y corrida hacia afuera con `-translate-x-full`**, y no suelta
        en la pantalla, porque así la mueve el mismo desplazado que mueve la columna: plegado, el
        envoltorio se corre sus 320px hacia la derecha y la lengüeta cae justo sobre el borde de la
        pantalla. Con un `right` animado aparte habría dos animaciones que mantener en sincronía, y
        la lengüeta se despegaría de la columna en el medio del recorrido.

        Chica a propósito: es un control de la interfaz, no una acción de la pantalla. Lo que sí es
        grande es el alto, porque es un blanco que se busca con el mouse de costado.

        La flecha apunta a donde va a ir el carrusel —a la derecha para esconderlo, a la izquierda
        para traerlo—, que es lo que deja adivinar qué hace antes de tocarlo.
      */}
      <button
        type="button"
        onClick={() => setCollapsed((open) => !open)}
        aria-expanded={!collapsed}
        aria-controls={listId}
        aria-label={collapsed ? 'Mostrar lista de estaciones' : 'Ocultar lista de estaciones'}
        /*
          El vidrio, el borde y la sombra son los MISMOS que los de `StationResultCard`, a
          propósito: la lengüeta es parte de la columna, no un control del mapa. Con un fondo
          propio se leía como una pieza suelta pegada al costado.

          Sin esquinas redondeadas por lo mismo. Las fichas son rectas, y una lengüeta redondeada
          contra una columna de rectángulos se despega justo en el punto donde tiene que
          pertenecer.
        */
        className="border-border bg-background/95 text-text-muted hover:text-text focus-visible:outline-primary absolute top-1/2 left-0 flex h-16 w-6 -translate-x-full -translate-y-1/2 items-center justify-center border shadow-lg transition-colors focus-visible:-outline-offset-2 focus-visible:outline-2"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-4 w-4"
          aria-hidden="true"
        >
          <path d={collapsed ? 'm15 18-6-6 6-6' : 'm9 18 6-6-6-6'} />
        </svg>
      </button>

      <div
        id={listId}
        ref={listRef}
        onScroll={handleScroll}
        /*
          `inset-0` y no `inset-y-0 right-0`: adentro del envoltorio la columna ocupa todo, y el
          ancho lo fija el envoltorio. Repetirlo acá abriría la puerta a que los dos se separen.

          `aria-hidden` plegado, porque un lector de pantalla no tiene forma de saber que la
          columna está fuera de la pantalla: la leería entera como si estuviera a la vista.
        */
        aria-hidden={collapsed || undefined}
        className={`station-carousel absolute inset-0 flex flex-col gap-3 overflow-y-auto px-4 ${
          looping ? '' : 'justify-center'
        }`}
      >
        {stations.length === 0 && (
          <p className="border-border bg-background/95 text-text-muted my-auto border p-4 text-sm">
            Ninguna estación coincide con la búsqueda.
          </p>
        )}

        {copies.map((copy) =>
          stations.map((station) => (
            <button
              key={itemKey(copy, station.stationId)}
              type="button"
              // Las copias son el mismo contenido repetido: para quien navega con lector de
              // pantalla, anunciarlo tres veces sería ruido. Solo la del medio es real.
              aria-hidden={copy === realCopy ? undefined : true}
              tabIndex={copy === realCopy ? undefined : -1}
              ref={(element) => {
                const key = itemKey(copy, station.stationId)
                if (element === null) itemsRef.current.delete(key)
                else itemsRef.current.set(key, element)
              }}
              onClick={() => onSelect(station.stationId)}
              /*
                Mouse y no pointer: en una pantalla táctil los eventos de puntero disparan al
                tocar, y la ficha quedaría agrandada después del toque hasta que se toque otra.
                Repintar a mano —en vez de esperar al próximo scroll— es lo que hace que el
                cambio se vea en el momento.
              */
              onMouseEnter={() => {
                hoveredRef.current = itemKey(copy, station.stationId)
                paint()
              }}
              onMouseLeave={() => {
                hoveredRef.current = null
                paint()
              }}
              // La escala crece hacia la izquierda: el borde derecho queda fijo contra la
              // columna y las fichas lejanas se leen como más angostas, no como corridas.
              className="origin-right shrink-0 text-left transition-transform duration-150"
            >
              <StationResultCard
                station={station}
                expanded={station.stationId === selectedStationId}
              />
            </button>
          )),
        )}
      </div>
    </div>
  )
}
