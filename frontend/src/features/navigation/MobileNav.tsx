import { useEffect, useId, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router'

import { useSession } from '@/features/auth/session'

import { isSectionActive, MOBILE_SECTIONS, visibleSections, type NavSection } from './navSections'
import { NAV_GEOMETRY, notchCutPath, sectionCenter } from './notchGeometry'

/**
 * La barra de navegación del celular: flota abajo, con el círculo verde sobre la sección
 * activa y una muesca en la barra por donde el círculo asoma.
 *
 * **El círculo y la muesca siguen siendo una sola cosa que se mueve.** Es lo que hace que el
 * efecto se lea: si la muesca saltara y el círculo se deslizara, durante el viaje el círculo
 * caminaría sobre la barra llena y llegaría a un agujero que lo esperaba.
 *
 * Lo que los mantiene pegados es que los dos salen del MISMO número animado. `--active-index`
 * lo anima el CSS —con la curva de siempre, la de `BottomSheet` y el panel del mapa—, el
 * círculo lo lee directo en su `left`, y el `path` de la muesca se redibuja leyendo ese mismo
 * valor interpolado en cada cuadro. No hay dos animaciones que sincronizar: hay una, y la otra
 * es un espejo.
 *
 * **Por qué la barra ahora es un SVG y no un div con máscara.** La versión anterior abría el
 * hueco con un `radial-gradient`, que se anima solo y no necesita medir nada. Pero el
 * encuentro entre el círculo y el borde recto quedaba en punta, y los labios redondeados que
 * lo arreglan son arcos tangentes: no hay degradado que los describa. Ver `notchGeometry.ts`.
 *
 * El costo de ese cambio es tener que medir el ancho de la barra, porque un `path` se escribe
 * en píxeles. Lo hace un `ResizeObserver`, que es una sola suscripción y cubre todos los casos
 * —girar el teléfono, abrir el teclado, cambiar el zoom— sin escuchar `resize` a mano.
 */
export default function MobileNav() {
  const { pathname, key: locationKey } = useLocation()
  const session = useSession()
  /*
    Solo las que le corresponden a quien mira. Acá el filtro pesa doble: de la CANTIDAD de
    secciones sale el ancho de cada una y, con él, dónde se para el círculo. Por eso todo lo que
    sigue cuenta sobre `sections` y no sobre la lista completa.
  */
  const sections = visibleSections(MOBILE_SECTIONS, session?.role ?? null)

  const routeIndex = sections.findIndex((section) => isSectionActive(section, pathname))

  /*
   * La solapa abierta, si hay una. Se guarda junto con la entrada del historial en la que se
   * abrió (`location.key`), y eso es lo que la cierra al navegar: en cualquier otra navegación
   * —incluso a la misma ruta— deja de corresponder y se lee como cerrada, sin un efecto que la
   * apague a mano. La ruta sola no alcanzaba: tocar Mapa estando en el mapa no la cambia.
   *
   * **Por eso tocar una opción o un ícono no la cierra a mano.** Cerrarla en el toque la apaga un
   * cuadro antes de que llegue la ruta nueva, y en ese cuadro el círculo apunta a la pantalla de
   * antes: arranca hacia allá y después vuelve. Esperando a la navegación, el círculo baja directo
   * a donde va.
   */
  const [openGroup, setOpenGroup] = useState<{ to: string; locationKey: string } | null>(null)
  const groupIndex =
    openGroup !== null && openGroup.locationKey === locationKey
      ? sections.findIndex((s) => s.to === openGroup.to && s.group !== undefined)
      : -1
  const flapOpen = groupIndex >= 0

  /*
   * Dónde está el círculo. Con la solapa abierta, sobre la sección que la abrió, aunque la ruta
   * sea otra: el círculo viaja hasta ahí, sube y se parte en los dos de la solapa.
   */
  const activeIndex = flapOpen ? groupIndex : routeIndex

  /*
   * Una ruta que no es ninguna de las cinco —el detalle de una estación, mañana— no tiene
   * dónde parar el círculo. En vez de dejarlo en la sección equivocada, la barra se dibuja
   * lisa y sin círculo: no decir nada es mejor que mentir.
   */
  const hasActive = activeIndex >= 0
  const activeSection = hasActive ? sections[activeIndex] : null
  /* En una solapa, el círculo lleva el ícono de la opción en la que se está, no el del grupo. */
  const ActiveIcon =
    activeSection === null
      ? null
      : (activeSection.group?.find((o) => isSectionActive(o, pathname))?.Icon ?? activeSection.Icon)

  /* Con la solapa abierta, tocar afuera o Escape la cierran. */
  useEffect(() => {
    if (!flapOpen) return
    const close = (event: PointerEvent) => {
      if (navRef.current !== null && !navRef.current.contains(event.target as Node)) {
        setOpenGroup(null)
      }
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenGroup(null)
    }
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [flapOpen])

  const navRef = useRef<HTMLElement>(null)
  const barRef = useRef<HTMLDivElement>(null)
  const cutRef = useRef<SVGPathElement>(null)
  const [barWidth, setBarWidth] = useState(0)

  /* El `id` de la máscara tiene que ser único en el documento: `useId` lo garantiza. */
  const maskId = useId()

  useEffect(() => {
    const bar = barRef.current
    if (bar === null) return

    const observer = new ResizeObserver(([entry]) => {
      setBarWidth(entry.contentRect.width)
    })
    observer.observe(bar)
    return () => observer.disconnect()
  }, [])

  /*
   * El espejo: sigue el valor que el CSS está interpolando y redibuja el recorte con él.
   *
   * Corre por `requestAnimationFrame` y **solo mientras dura el viaje**: la condición de corte
   * es que el valor leído haya llegado al destino. Si el navegador no soporta `@property`, la
   * variable no se interpola, el primer cuadro ya lee el destino y el bucle termina ahí mismo
   * —la muesca aparece puesta en su lugar, igual que el círculo, sin animación y sin quedar
   * girando de fondo—.
   */
  useEffect(() => {
    const nav = navRef.current
    const cut = cutRef.current
    if (nav === null || cut === null || barWidth === 0) return

    let frame = 0
    const draw = () => {
      const sampled = Number.parseFloat(getComputedStyle(nav).getPropertyValue('--active-index'))
      const index = Number.isNaN(sampled) ? activeIndex : sampled

      cut.setAttribute('d', notchCutPath(sectionCenter(index, barWidth, sections.length)))

      /* Medio milésimo de sección: por debajo de eso no hay píxel que cambie. */
      if (Math.abs(index - activeIndex) > 0.0005) frame = requestAnimationFrame(draw)
    }

    draw()
    return () => cancelAnimationFrame(frame)
  }, [activeIndex, barWidth, sections.length])

  const { barHeight, barRadius, trackInset } = NAV_GEOMETRY

  return (
    /*
      `md:hidden` y no un `useMediaQuery`: acá las dos barras pueden convivir en el DOM porque
      ninguna atrapa el foco ni bloquea el scroll —que es lo que sí obliga a decidir en
      JavaScript cuál se monta en el caso de los paneles—. Con CSS solo, además, no hay un
      primer cuadro con la barra equivocada.
    */
    <nav
      ref={navRef}
      aria-label="Navegación principal"
      className="mobile-nav pointer-events-none fixed inset-x-0 bottom-0 z-[1100] px-4 md:hidden"
      style={
        {
          /*
            Las medidas salen de `notchGeometry.ts` y se le pasan al CSS como variables, en vez
            de estar escritas en los dos lados. El `path` y el círculo caen en el mismo lugar
            porque leen lo mismo.
          */
          '--active-index': activeIndex,
          '--puck-size': `${NAV_GEOMETRY.puckSize}px`,
          '--notch-lift': `${NAV_GEOMETRY.notchLift}px`,
          '--track-inset': `${trackInset}px`,
          '--section-count': sections.length,
        } as React.CSSProperties
      }
    >
      {/*
        El aire de abajo lo pone `.mobile-nav` con `env(safe-area-inset-bottom)` y no con un
        número fijo: en los teléfonos con barra de gestos, un valor a ojo deja la navegación
        debajo de la franja del sistema, donde el dedo la toca pero el sistema se lo queda.
      */}
      <div ref={barRef} className="pointer-events-auto relative" style={{ height: barHeight }}>
        {/*
          Las solapas van PRIMERO, antes que la barra: así la barra se pinta encima y la solapa
          asoma desde atrás de ella, en vez de apoyarse sobre ella. Hay una por cada sección con
          grupo, siempre montadas, para que abrir y cerrar sea una transición y no un aparecer.
        */}
        {barWidth > 0 &&
          sections.map((section, index) =>
            section.group === undefined ? null : (
              <GroupFlap
                key={section.to}
                section={section}
                open={index === groupIndex}
                centerX={sectionCenter(index, barWidth, sections.length)}
                barWidth={barWidth}
                pathname={pathname}
              />
            ),
          )}

        {/*
          La barra, dibujada como una figura y no como una caja con fondo.

          El hueco se saca con una máscara de SVG: un rectángulo blanco —lo que se ve— menos el
          recorte negro de la muesca. Tiene que ser un agujero de verdad porque la barra flota
          sobre el contenido: en la pantalla del mapa, lo que se ve por el hueco son los
          mosaicos, y un círculo pintado del color del fondo se notaría al instante.

          Mientras no se midió el ancho no se dibuja nada. Es un solo cuadro, y es preferible a
          mostrar la muesca en el lugar equivocado.
        */}
        {barWidth > 0 && (
          <svg
            className="mobile-nav__bar absolute inset-0 z-10"
            width={barWidth}
            height={barHeight}
            aria-hidden="true"
          >
            <defs>
              <mask
                id={maskId}
                maskUnits="userSpaceOnUse"
                x={0}
                y={0}
                width={barWidth}
                height={barHeight}
              >
                <rect x={0} y={0} width={barWidth} height={barHeight} fill="#fff" />
                {hasActive && (
                  <path
                    ref={cutRef}
                    fill="#000"
                    d={notchCutPath(sectionCenter(activeIndex, barWidth, sections.length))}
                  />
                )}
              </mask>
            </defs>

            <rect
              x={0}
              y={0}
              width={barWidth}
              height={barHeight}
              rx={barRadius}
              fill="var(--color-surface)"
              mask={`url(#${maskId})`}
            />
          </svg>
        )}

        {/*
          Los enlaces van por encima del SVG. El padding horizontal es el mismo `trackInset`
          que usa la geometría: de él sale el centro de cada sección, así que si se separaran,
          el círculo dejaría de caer sobre su ícono.
        */}
        <div
          className="relative z-20 flex h-full items-stretch"
          style={{ paddingLeft: trackInset, paddingRight: trackInset }}
        >
          {sections.map((section, index) => {
            const active = index === activeIndex
            /*
              El ícono de la sección activa no se esconde con `display`: se apaga. El que se ve
              es el mismo dibujo dentro del círculo de arriba, y dejar que este ocupe su lugar
              mantiene la etiqueta clavada donde está mientras el círculo viaja. Sacándolo del
              flujo, los textos darían un saltito en cada cambio.
            */
            const content = (
              <>
                <section.Icon
                  className={`h-5 w-5 transition-opacity duration-200 ${
                    active ? 'opacity-0' : 'text-text-muted opacity-100'
                  }`}
                />
                <span
                  className={`text-[10px] leading-none font-medium transition-colors duration-200 ${
                    active ? 'text-text font-semibold' : 'text-text-muted'
                  }`}
                >
                  {section.label}
                </span>
              </>
            )

            /* Una sección con grupo no navega: abre y cierra su solapa. */
            if (section.group !== undefined) {
              const open = index === groupIndex
              return (
                <button
                  key={section.to}
                  type="button"
                  aria-expanded={open}
                  aria-controls={flapId(section)}
                  onClick={() => setOpenGroup(open ? null : { to: section.to, locationKey })}
                  className="flex flex-1 flex-col items-center justify-center gap-1"
                >
                  {content}
                </button>
              )
            }

            return (
              <NavLink
                key={section.to}
                to={section.to}
                end={section.end}
                className="flex flex-1 flex-col items-center justify-center gap-1"
              >
                {content}
              </NavLink>
            )
          })}
        </div>

        {/*
          El círculo. Va afuera del SVG y no adentro: adentro lo recortaría la misma máscara
          que abre el hueco para él.

          `pointer-events-none` porque no es un botón: el que recibe el toque es el enlace de
          la barra que tiene debajo. Si el círculo capturara el toque, tocar la sección activa
          no haría nada.
        */}
        {/*
          Con la solapa abierta, el círculo sube y se desvanece mientras de su lugar salen los dos
          de la solapa (`mobile-nav__puck--lifted`).
        */}
        {ActiveIcon !== null && (
          <div
            className={`mobile-nav__puck brand-fill text-on-primary pointer-events-none absolute z-30 flex items-center justify-center rounded-full ${
              flapOpen ? 'mobile-nav__puck--lifted' : ''
            }`}
          >
            <ActiveIcon className="h-6 w-6" />
          </div>
        )}
      </div>
    </nav>
  )
}

function flapId(section: NavSection): string {
  return `mobile-nav-flap-${section.to.replace(/\W/g, '')}`
}

/** El ancho de la solapa: dos opciones con su círculo y su rótulo, con aire entre las dos. */
const FLAP_WIDTH = 216
/** Cuánto asoma la solapa por encima de la barra. El resto queda escondido detrás. */
const FLAP_VISIBLE = 96
/** Cuánto se mete detrás de la barra: así el borde de abajo nunca se ve. */
const FLAP_TUCK = 32
/** El diámetro de los dos círculos de la solapa: un poco menos que el de la barra. */
const OPTION_SIZE = 44
/** Dónde arranca cada círculo, desde el borde de arriba de la solapa. */
const OPTION_TOP = 14

/**
 * La solapa de una sección con grupo: sale de atrás de la barra con sus dos opciones.
 *
 * **El círculo de la barra se parte en los dos de acá.** Cerrada, cada círculo está corrido hasta
 * el lugar del círculo de la barra —abajo y al centro— y transparente; al abrirse viajan cada uno
 * a su opción, con la misma curva que mueve el círculo entre secciones. Visto de afuera, el
 * círculo sube a la solapa y se divide en dos.
 *
 * El de la opción en la que se está va en el verde de la marca, como el de la barra; el otro, en
 * vidrio. Si no se está en ninguna de las dos, los dos van en vidrio.
 *
 * Va un tono más oscura que la barra para que se lea como algo que sale DE ella y no como otra
 * barra flotando arriba.
 */
function GroupFlap({
  section,
  open,
  centerX,
  barWidth,
  pathname,
}: {
  section: NavSection
  open: boolean
  centerX: number
  barWidth: number
  pathname: string
}) {
  const options = section.group ?? []
  /* Centrada sobre su sección, sin salirse de los bordes de la barra. */
  const left = Math.min(Math.max(centerX - FLAP_WIDTH / 2, 0), barWidth - FLAP_WIDTH)
  /* Cuánto hay que correr cada círculo para que, cerrado, caiga sobre el de la barra. */
  const puckOffsetX = (index: number) =>
    centerX - (left + (FLAP_WIDTH / options.length) * (index + 0.5))
  /*
    Lo vertical: el centro del círculo de la barra está `notchLift + puckSize / 2` por encima de su
    borde, y el de cada opción, `FLAP_VISIBLE - (OPTION_TOP + OPTION_SIZE / 2)`. La diferencia es
    cuánto baja el círculo cerrado.
  */
  const puckOffsetY =
    FLAP_VISIBLE -
    (OPTION_TOP + OPTION_SIZE / 2) -
    (NAV_GEOMETRY.notchLift + NAV_GEOMETRY.puckSize / 2)

  return (
    <div
      id={flapId(section)}
      aria-hidden={!open}
      inert={!open}
      className={`mobile-nav__flap absolute z-0 flex rounded-t-[28px] ${open ? 'mobile-nav__flap--open' : ''}`}
      style={{
        left,
        width: FLAP_WIDTH,
        bottom: NAV_GEOMETRY.barHeight - FLAP_TUCK,
        height: FLAP_VISIBLE + FLAP_TUCK,
        paddingBottom: FLAP_TUCK,
      }}
    >
      {options.map((option, index) => {
        const here = isSectionActive(option, pathname)
        return (
          <NavLink
            key={option.to}
            to={option.to}
            end={option.end}
            className="flex flex-1 flex-col items-center gap-1.5"
            style={{ paddingTop: OPTION_TOP }}
          >
            <span
              className={`mobile-nav__option grid place-items-center rounded-full ${
                here ? 'brand-fill text-on-primary' : 'mobile-nav__option--idle text-text'
              }`}
              style={
                {
                  width: OPTION_SIZE,
                  height: OPTION_SIZE,
                  '--from-x': `${puckOffsetX(index)}px`,
                  '--from-y': `${puckOffsetY}px`,
                } as React.CSSProperties
              }
            >
              <option.Icon className="h-5 w-5" />
            </span>
            <span
              className={`mobile-nav__option-label text-[11px] leading-none ${
                here ? 'text-text font-semibold' : 'text-text-muted font-medium'
              }`}
            >
              {option.label}
            </span>
          </NavLink>
        )
      })}
    </div>
  )
}
