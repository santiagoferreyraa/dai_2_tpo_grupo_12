import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { useWheelToHorizontal } from '@/features/terminals/useWheelToHorizontal'

import { groupByBrand, useVehicleModels } from '../catalog'
import type { VehicleModel } from '../types'
import BrandLogo from './BrandLogo'
import BrandRail from './BrandRail'
import VehicleModelDetail from './VehicleModelDetail'
import VehicleModelGrid from './VehicleModelGrid'

/**
 * El selector de auto: marca a la izquierda, sus modelos a la derecha.
 *
 * **Reemplaza al `<select>` que había, y el motivo es que elegir el auto NO es elegir una opción
 * de una lista.** En un desplegable los diecinueve modelos son diecinueve renglones de texto
 * iguales: para reconocer el suyo, el conductor tiene que leerlos: acá lo reconoce por la foto y
 * por el logo de la marca, que es como se reconoce un auto en la vida real. Un desplegable además
 * no tiene dónde mostrar el conector ni las potencias, que son justamente lo que esta elección
 * decide.
 *
 * **Son DOS pasos y no uno, y el primero es la marca.** Nadie busca "un auto": busca el suyo, y
 * sabe de qué marca es antes que nada. Con las marcas fijas a la izquierda, elegir es leer una
 * lista de ocho y después mirar cuatro fotos — en vez de recorrer una grilla de diecinueve donde
 * hay que encontrar el propio entre todos los demás.
 *
 * **Y hay un tercer paso, el detalle, que existe para que la elección sea informada.** La grilla
 * muestra la foto y el nombre, que alcanza para reconocer; los kW y los kWh recién aparecen al
 * tocar un modelo, porque son lo que hay que mirar para confirmar y no para buscar. Metidos en
 * cada tarjeta de la grilla, ocho fichas técnicas juntas no se leen: se saltean.
 *
 * **El marco es el mismo que `BookingDialog`** —portal al `body`, fondo oscurecido con desenfoque,
 * Escape, foco que entra y vuelve— y por los mismos motivos, que están explicados allá. Lo que no
 * comparte es el ancho: esto muestra fotos, así que necesita bastante más que un formulario.
 */
interface VehicleDialogProps {
  /** El auto que está elegido hoy, para marcarlo en la grilla y en el detalle. */
  current: VehicleModel | null
  /**
   * Se llama con el modelo elegido.
   *
   * **Nunca con `null`: desde acá no se puede quedar sin auto.** Hubo un "Quedarme sin auto" al
   * pie y se sacó por innecesario —quien entra a este diálogo viene a elegir uno, y cambiarlo por
   * otro es lo único que se hace después de la primera vez—. El backend sigue aceptando el perfil
   * sin vehículo, así que la operación existe; lo que no existe es el camino desde esta ventana.
   */
  onSelect: (model: VehicleModel) => void
  onClose: () => void
}

export default function VehicleDialog({ current, onSelect, onClose }: VehicleDialogProps) {
  const { models, loading, error } = useVehicleModels()
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  /*
    Lo tipeado en el buscador de marcas. Filtra la LISTA de marcas, no los modelos: quien escribe
    "ren" está buscando Renault, no un modelo que se llame así. Buscar en los dos a la vez haría
    que el riel y la grilla cambien por motivos distintos con la misma tecla.
  */
  const [query, setQuery] = useState('')

  const allBrands = useMemo(() => groupByBrand(models), [models])

  /*
    **La comparación va sin acentos y en minúscula**, por lo mismo que los nombres de archivo:
    quien busca "citroen" en un teclado sin acentos tiene que encontrar Citroën. `normalize('NFD')`
    separa la tilde de su letra y el reemplazo la descarta.

    Sin `useMemo` alrededor del filtro: es un `includes` sobre un par de decenas de cadenas y
    corre una vez por tecla. Memorizarlo costaría más de lo que ahorra.
  */
  const brands = allBrands.filter(([brand]) => matchesQuery(brand, query))

  /*
    La tira de marcas del celular, con la rueda del mouse scrolleando de costado.

    **Sin esto la tira parecía cortada y sin forma de llegar al resto.** Un contenedor con
    `overflow-x: auto` se mueve con el dedo, con la barra o con Shift + rueda; con un mouse común
    la rueda tira para abajo y la tira no se entera. Y como la barra está oculta, no queda ninguna
    pista de que hay más marcas a la derecha. Es el mismo hook que usan los filtros del mapa, la
    galería de fotos de una estación y la tira de días de una reserva, por el mismo motivo.
  */
  const mobileStrip = useWheelToHorizontal<HTMLElement>()

  /*
    La marca abierta. **Es el nombre y no el índice**: la lista llega de la red, así que en el
    primer render está vacía y cualquier índice apuntaría a la nada; peor, si el catálogo cambia
    entre dos visitas el índice 3 pasa a ser otra marca. Con el nombre, lo que no está simplemente
    no se encuentra y se cae a la primera.
  */
  const [openBrand, setOpenBrand] = useState<string | null>(current?.brand ?? null)
  /* El modelo que se está mirando en detalle, o `null` mientras se ve la grilla. */
  const [detail, setDetail] = useState<VehicleModel | null>(null)

  /*
    Con qué marca se abre: la del auto actual si la hay, y si no la primera del catálogo. Se
    resuelve al dibujar y no con un efecto porque es un valor DERIVADO —de lo elegido y de lo que
    llegó—, y un efecto que lo copie a un estado agrega un render en el que no hay ninguna marca
    abierta y el panel derecho aparece vacío por un cuadro.
  */
  const activeBrand = brands.find(([brand]) => brand === openBrand)?.[0] ?? brands[0]?.[0] ?? null
  const activeModels = brands.find(([brand]) => brand === activeBrand)?.[1] ?? []

  const close = useCallback(() => {
    onClose()
  }, [onClose])

  /**
   * Abrir una marca vuelve siempre a la grilla.
   *
   * Sin esto, tocar el riel con un modelo abierto deja en pantalla el detalle de un auto de la
   * marca anterior, con el logo de la nueva arriba. Va compartido entre la rueda y la tira del
   * celular: son dos formas de la misma acción, y escrito dos veces se arregla una sola.
   *
   * **Función común y no `useCallback`.** Nadie la mete en una lista de dependencias —el riel solo
   * la llama desde un `onClick`—, así que memorizarla no evita ningún trabajo, y con la lista
   * vacía el compilador de React avisa que las dependencias que infiere no coinciden.
   */
  function openBrandAndCloseDetail(brand: string): void {
    setOpenBrand(brand)
    setDetail(null)
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      /*
        Escape cierra, siempre. Antes retrocedía del detalle a la grilla, pero el paso atrás se
        sacó de la pantalla —ya no hay botón de volver—, y dejarlo solo en el teclado escondería
        una salida que nadie puede ver: el que mira la ventana no tiene forma de saber que existe.
        Para cambiar de auto está el riel, que devuelve a la grilla de la marca que se toque.
      */
      close()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [close])

  /* Con la ventana abierta el fondo no scrollea: si no, se mueve lo de atrás mientras se elige. */
  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [])

  /* El foco entra al abrir y vuelve al campo del formulario al cerrar. Ver `BookingDialog`. */
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialogRef.current?.focus()
    return () => previous?.focus()
  }, [])

  return createPortal(
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm md:p-6"
      onClick={close}
    >
      {/* El clic de adentro no se propaga: sin esto, tocar el propio contenido cerraría la ventana. */}
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => {
          event.stopPropagation()
        }}
        /*
          Alto fijo en pantalla grande y no "el que pida el contenido": la grilla cambia de tamaño
          con cada marca —Tesla tiene dos modelos y BYD cuatro— y sin un alto estable la ventana
          crecería y se encogería a cada clic del riel, moviendo el propio riel que se está usando.
        */
        className="glass-panel glass-modal relative flex max-h-full w-full max-w-4xl flex-col overflow-hidden rounded-3xl outline-none md:h-[32rem] md:flex-row"
      >
        {/*
          La columna izquierda: el buscador arriba, fijo, y debajo la rueda de marcas.

          **El buscador NO scrollea con las marcas**, de ahí que la columna sea un flex con la
          rueda en un contenedor aparte: un campo que se va para arriba al girar la rueda obliga a
          volver al principio para corregir lo escrito, que es justo lo que uno hace cuando no
          encuentra su marca.

          La rueda va adentro de un contenedor `relative` con `flex-1` porque se posiciona contra
          él —ver `BrandRail`, que explica por qué necesita una caja con alto propio para poder
          scrollear—.

          **Proyecta sombra sobre el panel de la derecha, y por eso lleva `z-10`.** Sin elevarlo,
          la sombra se dibuja igual pero queda tapada por el panel, que viene después en el
          documento y por lo tanto más arriba. La sombra es lo que separa los dos lados: sin ella,
          el riel y la grilla son la misma superficie con una línea en el medio, y la elección de
          marca se lee como parte de la grilla en vez de como el paso anterior.

          **La sombra va solo hacia la derecha** —desplazada en X, sin desplazamiento en Y— porque
          es lo único que hay que sugerir: que esta columna está POR ENCIMA del panel. Una sombra
          pareja alrededor la haría flotar también sobre el borde de la ventana, donde no hay nada
          sobre lo que caer.

          **En el celular el riel no va.** La rueda necesita alto para que la escala se lea, y en
          un teléfono ese alto es justo el que necesitan las fotos. Ahí las marcas vuelven a ser
          una tira horizontal común, arriba: sin escala, sin repetición y sin sombra —que caería
          sobre el logo, que es lo que tiene que leerse primero—.
        */}
        <div className="border-border/60 relative z-10 hidden shrink-0 flex-col border-r md:flex md:w-56 md:shadow-[8px_0_24px_-8px_rgb(0_0_0/0.18)]">
          {/*
            El buscador, apoyado contra el pie del logo de enfrente.

            La misma banda de alto que el encabezado y alineado abajo: así el campo termina a la
            altura en la que termina el logo, en vez de empezar pegado al techo de la ventana y
            dejar los dos lados arrancando a distinta altura. Los números no son libres —`h-22`
            es el alto de la franja y `pb-4` la mitad del aire que le sobra al logo—, y por eso
            están atados al encabezado con un comentario en cada punta.
          */}
          <div className="flex h-22 shrink-0 items-end px-3 pb-4">
            <BrandSearch value={query} onChange={setQuery} />
          </div>

          <div className="relative min-h-0 flex-1">
            <BrandRail
              brands={brands.map(([brand]) => brand)}
              activeBrand={activeBrand}
              onSelect={openBrandAndCloseDetail}
            />
          </div>

          {/*
            Cuando el filtro no deja nada, el hueco se explica. Sin esto la columna queda vacía y
            no se distingue de un catálogo que no cargó.
          */}
          {brands.length === 0 && (
            <p className="text-text-muted absolute inset-x-0 top-20 px-5 text-sm">
              Ninguna marca coincide.
            </p>
          )}
        </div>

        {/*
          El bloque de marcas del celular: el mismo buscador y la misma lista, sin la rueda.

          La tira no lleva la escala ni la repetición: las dos necesitan alto para leerse, y acá el
          alto es justo lo que necesitan las fotos.
        */}
        <div className="border-border/60 flex shrink-0 flex-col gap-2 border-b p-3 md:hidden">
          <BrandSearch value={query} onChange={setQuery} />

          <nav
            ref={mobileStrip}
            aria-label="Marcas"
            className="no-scrollbar flex gap-1 overflow-x-auto"
          >
            {brands.map(([brand]) => (
              <button
                key={brand}
                type="button"
                aria-current={brand === activeBrand}
                onClick={() => {
                  openBrandAndCloseDetail(brand)
                }}
                className={`shrink-0 cursor-pointer rounded-xl px-3 py-2 text-sm font-extrabold tracking-tight whitespace-nowrap uppercase transition-colors ${
                  brand === activeBrand ? 'bg-surface/80 text-text' : 'text-text-muted'
                }`}
              >
                {brand}
              </button>
            ))}
          </nav>
        </div>

        {/* El panel derecho: encabezado con el logo, y debajo la grilla o el detalle. */}
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {/*
            **Alto fijo, y es lo que sostiene la alineación con el buscador de la izquierda.** Los
            logos no miden todos lo mismo —el rombo de Renault es casi cuadrado y la palabra de
            Volkswagen una tira baja—, así que con el alto dictado por el contenido la franja
            cambiaría de altura a cada marca y el campo de la columna de al lado quedaría alineado
            con una sí y con la otra no. Con la banda fija, los dos lados se acomodan contra la
            misma medida. Ver el envoltorio del buscador, que usa este mismo alto.
          */}
          <header className="flex h-22 shrink-0 items-center gap-4 px-5">
            {/*
              El título accesible dice lo que la pantalla muestra —"Elegí tu auto"— y no lo que dice
              el logo: quien navega con lector de pantalla necesita saber qué es esta ventana antes
              que de qué marca son los autos que tiene adelante.
            */}
            <h2 id={titleId} className="sr-only">
              Elegí tu auto
            </h2>

            {/*
              El logo grande: es el título de la pantalla, no una viñeta al lado del título. Es lo
              que confirma en qué marca está parado el conductor, y con la medida de un ícono se
              leía como decoración del encabezado.

              El tope va en alto Y en ancho porque los logos no tienen una forma sola: el rombo de
              Renault es casi cuadrado y la palabra de Volkswagen es una tira larga. Limitando solo
              el alto, un logotipo alargado se comería el ancho del panel hasta chocar con la cruz.
            */}
            {activeBrand !== null && (
              <BrandLogo brand={activeBrand} className="max-h-14 max-w-56 shrink-0" />
            )}

            {/*
              El nombre del modelo, al lado del logo y solo con uno abierto.

              **Subió acá desde el cuerpo del detalle**, y lo que gana no es el encabezado sino lo
              de abajo: la foto y su ficha se quedan con todo el alto del panel en vez de empezar
              debajo de dos renglones de texto. Marca y modelo juntos son lo que identifica al auto,
              así que el logo hace de primera mitad y esto de segunda: escrito abajo, el logo
              quedaba arriba sin nada que nombrar.

              `min-w-0` con `truncate` porque un nombre largo entre un logo ancho y la cruz no
              tiene a dónde crecer: sin esto empuja la cruz fuera del panel.
            */}
            {detail !== null && (
              <p className="text-text min-w-0 truncate text-2xl leading-none font-extrabold tracking-tight uppercase">
                {detail.name}
              </p>
            )}

            <button
              type="button"
              onClick={close}
              aria-label="Cerrar"
              className="text-text-muted hover:text-text hover:bg-surface/70 focus-visible:outline-primary ml-auto flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors focus-visible:outline-2"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                aria-hidden="true"
                className="h-5 w-5"
              >
                <path d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </header>

          <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-5">
            {error !== null ? (
              <p className="text-danger p-4 text-sm" role="alert">
                {error}
              </p>
            ) : loading ? (
              <p className="text-text-muted p-4 text-sm">Buscando los modelos…</p>
            ) : detail !== null ? (
              <VehicleModelDetail
                model={detail}
                selected={detail.id === current?.id}
                onSelect={() => {
                  onSelect(detail)
                }}
              />
            ) : (
              <VehicleModelGrid
                models={activeModels}
                currentId={current?.id ?? null}
                onOpen={setDetail}
              />
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}

/**
 * Si una marca responde a lo que se escribió en el buscador.
 *
 * `includes` y no "empieza con": las marcas compuestas son el caso —quien escribe "benz" espera
 * encontrar Mercedes-Benz—, y con dos decenas de opciones no hay ruido que filtrar.
 */
function matchesQuery(brand: string, query: string): boolean {
  const plain = (value: string) =>
    value
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')

  return plain(brand).includes(plain(query.trim()))
}

/**
 * El buscador de marcas: un campo chico arriba de la lista.
 *
 * **Filtra mientras se escribe y no tiene botón de buscar.** Con dos decenas de marcas, la
 * respuesta a cada tecla es instantánea porque la lista ya está en memoria: un botón agregaría un
 * paso para algo que ya pasó.
 *
 * **Es `type="search"` y no `type="text"`.** El navegador le agrega la cruz para vaciarlo —que es
 * el camino de vuelta a la lista completa sin borrar letra por letra— y en el celular cambia la
 * tecla de Enter del teclado virtual.
 *
 * **Sin autofoco.** El foco al abrir la ventana va al diálogo, que es lo que permite cerrarlo con
 * Escape enseguida; robándoselo el campo, Escape pasaría a vaciar el buscador —así lo define el
 * navegador para `type="search"`— en vez de cerrar. Y en el celular abriría el teclado tapando
 * media pantalla de fotos apenas se entra.
 */
function BrandSearch({ value, onChange }: { value: string; onChange: (next: string) => void }) {
  return (
    <div className="relative">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        aria-hidden="true"
        className="text-text-muted pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>

      <input
        type="search"
        value={value}
        onChange={(event) => {
          onChange(event.target.value)
        }}
        placeholder="Buscar marca"
        aria-label="Buscar marca"
        className="border-border/70 focus:border-primary text-text placeholder:text-text-muted/70 bg-surface/60 w-full rounded-xl border py-2 pr-3 pl-9 text-sm outline-none"
      />
    </div>
  )
}
