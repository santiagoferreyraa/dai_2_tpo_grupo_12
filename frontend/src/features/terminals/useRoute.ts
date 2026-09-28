/**
 * La ruta por calles del dispositivo a la estación elegida, mientras siga elegida.
 *
 * Es el pegamento entre `useDeviceLocation`, que avisa cada vez que el usuario se mueve, y
 * `fetchRoute`, que consulta un servidor público con un límite de una consulta por segundo. Sin
 * algo en el medio, caminar media cuadra con la pantalla abierta dispara una ráfaga de consultas
 * y el servidor corta.
 *
 * **Nunca lanza.** Que no haya ruta es un estado, no un error: el servidor de OSRM es de
 * demostración y puede caerse, y cuando eso pasa la pantalla tiene que decirlo sin romperse.
 *
 * Los motivos por los que puede no haber ruta se separan porque no se dicen igual: 'idle' es que no
 * hay nada que pedir, 'unavailable' que se intentó y el servicio no estuvo, 'inaccurate' que el
 * navegador no sabe dónde está el usuario ni con veinticinco kilómetros de margen, y 'off-road' que
 * alguna de las dos puntas no tiene una calle cerca. Los dos últimos no hablan del servicio sino de
 * dónde está parado el usuario.
 *
 * **Y hay un caso intermedio que NO es un motivo para no haber ruta**: la precisión mediocre, la de
 * una computadora de escritorio. Ahí la ruta se calcula, se dibuja y vale; lo único que se agrega es
 * `approximate`, para que el panel pueda aclarar que el arranque no es exacto. Ver GOOD_ACCURACY_M.
 */

import { useEffect, useRef, useState } from 'react'

import { fetchRoute, RouteUnavailableError, type Point, type Route } from './routing'

/**
 * Cuánto se tiene que haber movido el usuario para que valga la pena volver a pedir la ruta.
 *
 * El problema que resuelve: con GPS de celular, `watchPosition` avisa cada pocos segundos aunque
 * el usuario esté quieto, porque la medición oscila. Cada aviso es una posición nueva, y sin este
 * umbral cada una pediría una ruta nueva —contra un servidor limitado a 1 req/s— que además
 * volvería a reencuadrar el mapa solo, con el usuario mirando.
 *
 * 200 m es la escala en la que una ruta urbana cambia de verdad: por debajo, el recorrido es el
 * mismo salvo los primeros metros, y redibujarlo no le dice nada nuevo a nadie.
 */
const REFETCH_THRESHOLD_M = 200

/**
 * Lo mínimo que tiene que pasar entre dos consultas, en milisegundos.
 *
 * **Es un plazo entre consultas, no una espera antes de cada una**, y la diferencia es todo lo
 * que se siente. Antes acá había un debounce: cada selección esperaba un segundo entero y recién
 * ahí preguntaba, así que la ruta tardaba en aparecer SIEMPRE, incluso eligiendo una sola estación
 * con el servicio ocioso. Ahora la primera consulta sale en el momento y el plazo solo frena a la
 * siguiente, que es lo único que hacía falta frenar.
 *
 * Qué sigue protegiendo: recorrer el carrusel con las flechas cambia la selección en cada tecla, y
 * sin freno eso sería una consulta por estación que se pasa de largo, todas descartadas menos la
 * última. Con el plazo, la primera sale ya y el resto se junta en una sola al final. El segundo es
 * además el piso que pide la política de uso del servidor de demostración de OSRM.
 */
const MIN_INTERVAL_MS = 1000

/**
 * Hasta dónde la precisión informada por el navegador se considera buena y no se dice nada.
 *
 * Por encima de esto la ruta se sigue calculando y se sigue dibujando: lo único que cambia es que
 * el panel aclara que el punto de partida es aproximado. Ver `approximate` en RouteState.
 *
 * **Acá hubo un bloqueo y estaba mal.** La primera versión de este archivo no pedía la ruta cuando
 * la precisión pasaba de mil metros, con el argumento de que un origen dudoso produce una ruta
 * dudosa. El argumento es cierto y el remedio era desproporcionado: en una computadora de
 * escritorio la ubicación sale de la red y pasa los mil metros casi siempre, así que el efecto
 * real fue que la ruta dejó de aparecer para todo un tipo de dispositivo. Una ruta con el arranque
 * a ochocientos metros sigue contestando bien la pregunta que se le hace —cuánto hay hasta esta
 * estación y por dónde—; lo que no se puede es presentarla como exacta.
 *
 * Mil metros porque es donde termina lo que puede lograr un GPS o el posicionamiento por WiFi. Más
 * que eso ya es la ubicación por red, que es de otra escala.
 */
const GOOD_ACCURACY_M = 1000

/**
 * Lo peor que puede ser la precisión y que todavía valga la pena preguntar, en metros.
 *
 * Esto sí es un bloqueo, pero para el caso en que el navegador directamente no sabe dónde está el
 * usuario: veinticinco kilómetros es media provincia, y una ruta que arranca en un punto cualquiera
 * de media provincia no es aproximada, es inventada. Ahí ni se gasta la consulta.
 *
 * Es el compañero de MAX_SNAP_M en `routing.ts` y no lo reemplaza: aquel mira DÓNDE cayó la
 * posición —si hay una calle cerca—, este mira cuánto vale. Una ubicación por IP a 8 km puede caer
 * justo sobre una avenida, pasar el techo del pegado con cinco metros, y seguir siendo inventada.
 */
const MAX_ACCURACY_M = 25_000

export type RouteStatus =
  /** No hay ubicación, o no hay estación elegida: no hay nada que pedir. */
  | 'idle'
  | 'loading'
  | 'ready'
  /** El servicio falló, o no hay camino por calles entre los dos puntos. */
  | 'unavailable'
  /** La ubicación del dispositivo es demasiado imprecisa para partir de ella. */
  | 'inaccurate'
  /** Alguna de las dos puntas quedó demasiado lejos de cualquier calle. */
  | 'off-road'

export interface RouteState {
  status: RouteStatus
  /** Solo con `status === 'ready'`. En cualquier otro caso es null. */
  route: Route | null
  /**
   * La ruta está calculada desde un punto de partida que puede estar corrido.
   *
   * Es una advertencia sobre la ruta, no un reemplazo de la ruta: con esto en `true` el recorrido
   * se dibuja igual y los números valen igual, solo que el arranque es aproximado. Separarlo del
   * `status` es deliberado —una ruta aproximada SIGUE estando 'ready'—, porque si fuera un estado
   * más, todo el código que pregunta "¿hay ruta?" tendría que acordarse de contemplarlo, y el que
   * se olvide deja de dibujarla.
   */
  approximate: boolean
}

/**
 * El origen del recorrido: un punto que además sabe cuánto puede estar errado.
 *
 * La precisión viaja con la posición y no aparte porque son inseparables: una coordenada sin su
 * margen de error es una afirmación que el dato no siempre banca, que es lo mismo que ya explica
 * `useDeviceLocation` sobre `accuracyM`. `DeviceLocation` encaja en este tipo tal cual.
 */
export interface RouteOrigin extends Point {
  /** Radio en metros dentro del cual está el usuario, con 95% de confianza. */
  accuracyM: number
}

const IDLE: RouteState = { status: 'idle', route: null, approximate: false }

/*
 * Se devuelve solo cuando HAY una estación elegida: sin destino no hay nada que calcular, así que
 * quejarse de la precisión sería contestar una pregunta que nadie hizo.
 */
const INACCURATE: RouteState = { status: 'inaccurate', route: null, approximate: false }

/**
 * Distancia en metros entre dos puntos, por la fórmula del haversine.
 *
 * Es para comparar contra un umbral de cientos de metros, no para mostrarle un número a nadie:
 * la distancia que se muestra sale de la ruta, que es la real. Para este uso la esfera alcanza
 * de sobra y evita traer una dependencia.
 */
function metersBetween(a: Point, b: Point): number {
  const EARTH_RADIUS_M = 6_371_000
  const toRadians = (degrees: number) => (degrees * Math.PI) / 180

  const dLat = toRadians(b.latitude - a.latitude)
  const dLon = toRadians(b.longitude - a.longitude)
  const lat1 = toRadians(a.latitude)
  const lat2 = toRadians(b.latitude)

  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2

  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h))
}

/**
 * La ruta de `from` a `to`, recalculada solo cuando cambia el destino o el origen se movió de
 * verdad.
 *
 * `from` y `to` se pasan como objetos y no como coordenadas sueltas porque es lo que tienen a
 * mano quienes llaman —la ubicación del hook y la estación elegida—, pero adentro el efecto NO
 * depende de esos objetos: depende de sus números. `watchPosition` entrega un objeto nuevo en
 * cada arreglo del GPS, y una dependencia por identidad volvería a disparar todo en cada uno,
 * que es justo lo que el umbral existe para evitar.
 */
export function useRoute(from: RouteOrigin | null, to: Point | null): RouteState {
  const [state, setState] = useState<RouteState>(IDLE)

  /*
   * La ubicación es demasiado imprecisa para partir de ella. Se decide durante el render porque es
   * un hecho que ya está en la prop: no hay nada que consultar ni que esperar, y pasarlo por un
   * efecto solo agregaría un cuadro mostrando "buscando" algo que no se va a buscar.
   */
  const inaccurate = from !== null && from.accuracyM > MAX_ACCURACY_M

  /*
   * La precisión alcanza para calcular pero no para prometer exactitud. No se guarda en el estado
   * porque no cuesta nada derivarlo, y sobre todo porque tiene que seguir al valor de AHORA: si
   * viviera en el estado junto a la ruta, un arreglo del GPS que mejora la precisión dejaría el
   * cartel de "aproximado" puesto sobre una ruta que ya no lo es.
   */
  const approximate = from !== null && from.accuracyM > GOOD_ACCURACY_M

  /*
   * El origen con el que se calculó la ruta que está en pantalla. Vive en un ref y no en el
   * estado porque no se dibuja: solo sirve para decidir si la posición nueva justifica otra
   * consulta. En el estado, escribirlo provocaría un repintado por cada aviso del GPS.
   */
  const routedFrom = useRef<Point | null>(null)

  /*
   * El origen que el efecto va a usar, ya filtrado por el umbral. Es un estado y no un ref
   * justamente porque el efecto TIENE que volver a correr cuando cambia, y un ref no despierta
   * a nadie.
   */
  const [origin, setOrigin] = useState<Point | null>(null)

  /*
   * Cuándo salió la última consulta, para no pasar de una por segundo. Arranca en cero —y no en
   * `Date.now()`— justamente para que la PRIMERA no espere nada: con el reloj en cero, el plazo ya
   * está vencido desde antes de montar.
   */
  const lastRequestAt = useRef(0)

  /*
   * El `setState` adentro del efecto es a propósito y no se puede derivar durante el render: lo
   * que dispara este cambio no es una prop ni un evento de la interfaz sino el GPS, que avisa
   * cuando se le da la gana. Es exactamente el caso para el que existen los efectos —sincronizar
   * con algo de afuera—, y el umbral es lo que impide que cada aviso se convierta en un repintado.
   */
  useEffect(() => {
    /*
     * La ubicación imprecisa se corta ACÁ, y no más adelante, para que la consulta no llegue a
     * salir: es una petición que ya sabemos que va a devolver una ruta sin sentido, y el servidor
     * de OSRM es de demostración y está limitado. Lo barato es no hacerla.
     */
    if (from === null || inaccurate) {
      routedFrom.current = null
      /* eslint-disable-next-line react/set-state-in-effect -- sincroniza con el GPS; ver arriba. */
      setOrigin(null)
      return
    }

    const previous = routedFrom.current
    if (previous !== null && metersBetween(previous, from) < REFETCH_THRESHOLD_M) return

    routedFrom.current = from
    /* eslint-disable-next-line react/set-state-in-effect -- sincroniza con el GPS; ver arriba. */
    setOrigin(from)
  }, [from, inaccurate])

  const destinationLat = to?.latitude ?? null
  const destinationLon = to?.longitude ?? null

  /*
   * Si falta una de las dos puntas no hay nada que pedir. Se resuelve durante el render y no
   * apagando el estado desde un efecto: sin origen o sin destino, "no hay ruta" es un hecho que
   * ya se sabe en este render, y escribirlo en el estado lo mostraría un cuadro tarde —el panel
   * alcanzaría a dibujar la ruta de la estación anterior—.
   */
  const active = origin !== null && destinationLat !== null && destinationLon !== null

  useEffect(() => {
    if (origin === null || destinationLat === null || destinationLon === null) return

    /*
     * El estado de carga se pone acá y no adentro del temporizador: la consulta puede llegar a
     * esperar hasta un segundo si viene pegada a otra, y durante esa espera la pantalla tiene que
     * decir que está buscando. Si no, el panel muestra "no se pudo" por un instante antes de
     * mostrar la ruta, que se lee como un error que se corrige solo.
     */
    /* eslint-disable-next-line react/set-state-in-effect -- arranca la consulta; ver arriba. */
    setState({ status: 'loading', route: null, approximate: false })

    /*
     * Cuánto falta para que se cumpla el plazo desde la consulta anterior. Con el servicio ocioso
     * —que es el caso normal: se elige una estación y se la mira— da cero y `setTimeout` dispara
     * en el próximo tick, así que la ruta sale a pedir en el momento.
     */
    const sinceLast = Date.now() - lastRequestAt.current
    const wait = Math.max(0, MIN_INTERVAL_MS - sinceLast)

    const controller = new AbortController()
    const timer = setTimeout(() => {
      /*
       * El reloj se marca cuando la consulta SALE y no cuando se programó: si se marcara al
       * programar, dos selecciones seguidas se repartirían mal el plazo y la segunda saldría antes
       * de que se cumpliera. Las que se cancelan antes de salir no lo tocan, que es lo correcto:
       * nunca llegaron al servidor.
       */
      lastRequestAt.current = Date.now()

      fetchRoute(origin, { latitude: destinationLat, longitude: destinationLon }, controller.signal)
        .then((route) => setState({ status: 'ready', route, approximate: false }))
        .catch((error: unknown) => {
          /*
           * Cancelar hace que `fetch` rechace, y ese rechazo no es una falla: quien cancela ya
           * pidió otra cosa, y marcar un error acá haría parpadear el cartel entre una consulta y
           * la siguiente.
           */
          if (controller.signal.aborted) return

          /*
           * El pegado fallido se distingue del servicio caído porque son dos cosas distintas para
           * quien mira: una habla de dónde está parado, la otra de si el servicio anda. Cualquier
           * otro rechazo —incluido un error de programación acá adentro— cae en 'unavailable',
           * que es lo conservador: decir que no se pudo antes que inventar un motivo.
           */
          const offRoad = error instanceof RouteUnavailableError && error.reason === 'off-road'
          setState({
            status: offRoad ? 'off-road' : 'unavailable',
            route: null,
            approximate: false,
          })
        })
    }, wait)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [origin, destinationLat, destinationLon])

  if (inaccurate && to !== null) return INACCURATE
  if (!active) return IDLE

  /*
   * `approximate` se pega acá y no adentro del estado por lo que explica su declaración: el estado
   * guarda el resultado de la consulta, que no cambia; la advertencia sigue a la precisión de
   * ahora, que sí.
   */
  return state.status === 'ready' ? { ...state, approximate } : state
}
