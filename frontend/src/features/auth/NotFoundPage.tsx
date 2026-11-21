import { type PointerEvent, useEffect, useRef } from 'react'
import { Link } from 'react-router'

import { useMediaQuery } from '@/lib/useMediaQuery'

/**
 * Pantalla de "no encontrada".
 *
 * Sale en dos casos que, a propósito, no se distinguen: una dirección que no existe y una que
 * existe pero pide un rol que el usuario no tiene. Decirle "no tenés permiso" sería confirmarle
 * que la sección existe, que es justo lo que el backend evita desde SEC-05 respondiendo 404 en
 * vez de 403 (decidido por Manuel el 01/11; reemplaza a la pantalla de acceso denegado).
 *
 * Por eso se dibuja en el lugar y no tiene dirección propia: si el guard redirigiera, la barra
 * cambiaría solo en el caso del permiso y la diferencia quedaría a la vista. Y por eso el texto
 * no sugiere "volver a intentar más tarde": eso es de un error del servidor, que es pasajero; acá
 * reintentar no cambia nada, ni en un caso ni en el otro.
 *
 * La salida está en el texto, en la palabra "inicio", y no en un botón aparte. Tiene que verse
 * igual: sin una salida a la vista, la única que queda es el botón de atrás del navegador. Por
 * eso va en el verde de la marca y subrayada, y no solo en negrita.
 */
export default function NotFoundPage() {
  return (
    <section className="flex h-full items-center justify-center p-6">
      <div className="flex w-full max-w-xl flex-col items-center text-center">
        <h1 className="w-full">
          <BubbleDigits />
          <span className="sr-only">Página no encontrada</span>
        </h1>

        <p className="text-text mt-2 text-xl font-semibold">No encontramos esta página</p>
        <p className="text-text-muted mt-2 max-w-md text-sm">
          Puede que el enlace esté mal escrito o que la página ya no exista. Volvé al{' '}
          <Link
            className="text-primary focus-visible:outline-primary rounded-sm font-semibold underline decoration-2 underline-offset-4 hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2"
            to="/"
          >
            inicio
          </Link>{' '}
          y seguí desde ahí.
        </p>
      </div>
    </section>
  )
}

/** Cuánto asoma el borde que cambia de color por fuera del cuerpo de la burbuja. */
const RIM_OUT = 5
/** Cuánto se mete el borde por dentro, para que no quede una ranura entre el borde y el cuerpo. */
const RIM_IN = 2

/**
 * Los dígitos, dibujados como el recorrido del centro de un tubo grueso, cada uno centrado en su
 * propio (0, 0) para poder inclinarlo, empujarlo y hacerlo flotar alrededor de su centro.
 *
 * El 4 es el "abierto" —brazo, barra y palo sueltos, sin el triángulo cerrado—: con un tubo tan
 * grueso el triángulo se tapa y el número deja de leerse. El 0 es un círculo y no un óvalo,
 * porque va como una dona.
 */
const GLYPHS = {
  four: {
    d: 'M -58 -86 C -64 -40 -66 -6 -64 22 L 66 22 M 40 -98 L 40 96',
    tube: 64,
  },
  zero: {
    d: 'M 0 -80 A 80 80 0 1 1 0 80 A 80 80 0 1 1 0 -80 Z',
    tube: 92,
  },
} as const

type Glyph = keyof typeof GLYPHS

/**
 * Dónde va cada dígito y cuánto se inclina: los 4 abiertos hacia afuera y el 0 al medio, más
 * grande y ADELANTE, tapando un poco a los dos.
 *
 * Los ritmos de flotar son distintos a propósito: si los tres se movieran juntos se leería como
 * un bloque que sube y baja, y no como tres burbujas sueltas.
 */
const DIGITS: {
  glyph: Glyph
  x: number
  y: number
  tilt: number
  /** Cuánto tarda en subir y bajar, en segundos. */
  period: number
  /** En qué punto de la subida arranca, de 0 a 1. */
  phase: number
}[] = [
  { glyph: 'four', x: 140, y: 182, tilt: -10, period: 4.2, phase: 0 },
  { glyph: 'four', x: 500, y: 172, tilt: 7, period: 4.8, phase: 0.55 },
  { glyph: 'zero', x: 318, y: 168, tilt: 0, period: 3.6, phase: 0.3 },
]

const ZERO = DIGITS.findIndex((d) => d.glyph === 'zero')

/** Cuánto sube y baja cada burbuja, y cuántos grados se balancea. */
const FLOAT_RISE = 7
const FLOAT_SWAY = 1.5

/** Hasta qué distancia del puntero se empuja una burbuja, y cuánto como mucho. En unidades SVG. */
const REPEL_RADIUS = 260
const REPEL_MAX = 42

/**
 * El resorte que lleva cada burbuja hacia donde la empuja el mouse, o de vuelta a su lugar.
 *
 * Rigidez baja y amortiguación alta: la burbuja acelera de a poco, se pasa apenas y vuelve sin
 * rebotar de más, como algo que flota en el agua. Por eso tarda en volver, y es a propósito.
 */
const SPRING_STIFFNESS = 0.025
const SPRING_DAMPING = 0.86

type Push = { x: number; y: number; vx: number; vy: number }

/** Hacia dónde quiere irse una burbuja: lejos del puntero, más cuanto más cerca está. */
function repelTarget(digit: { x: number; y: number }, pointer: DOMPoint) {
  const dx = digit.x - pointer.x
  const dy = digit.y - pointer.y
  const distance = Math.hypot(dx, dy)
  if (distance >= REPEL_RADIUS) return { x: 0, y: 0 }
  // Al cuadrado, para que el empuje arranque suave en el borde del radio y no de golpe.
  const strength = (1 - distance / REPEL_RADIUS) ** 2 * REPEL_MAX
  const norm = distance || 1
  return { x: (dx / norm) * strength, y: (dy / norm) * strength }
}

function restTransform({ x, y, tilt }: (typeof DIGITS)[number]) {
  return `translate(${x} ${y}) rotate(${tilt})`
}

/**
 * El "404" en burbujas.
 *
 * **No es una tipografía sino un dibujo.** Ninguna letra gratis se parece de verdad a un globo;
 * acá cada dígito es un recorrido trazado con un tubo muy grueso de puntas y uniones redondas.
 * Al no haber `<text>`, de paso, no hay nada que se pueda seleccionar con el mouse.
 *
 * Cada burbuja tiene dos capas, las dos a partir del mismo recorrido:
 *
 * - el CUERPO, muy translúcido, con el filtro que le da los brillos de una pompa;
 * - un BORDE fino recortado con una máscara para que quede solo el anillo de afuera, con un
 *   degradado que gira despacio, así los colores van recorriendo el contorno.
 *
 * **Lo que el 0 tapa de los 4 está recortado.** Son translúcidas, así que sin el recorte se verían
 * los 4 a través del 0 y el número se ensuciaría. El recorte es una máscara con la silueta del
 * 0, que se mueve junto con él.
 *
 * **El movimiento se calcula cuadro a cuadro y se escribe directo en el SVG**, sin pasar por el
 * estado de React: son tres transformaciones sesenta veces por segundo, y re-renderizar el
 * componente para cada una sería trabajo tirado. Lo mismo el mouse: el evento solo anota hacia
 * dónde quiere irse cada burbuja, y el resorte de cada cuadro las lleva.
 *
 * Los colores no están acá: salen de variables de `notFound.css`, que cambian con el tema.
 *
 * Con "reducir movimiento" activado las burbujas quedan quietas en su lugar, no se alejan del
 * mouse y el borde no gira.
 *
 * Es decorativo para el lector de pantalla: lo que anuncia el título es el texto oculto de al
 * lado, no "cuatro, cero, cuatro".
 */
function BubbleDigits() {
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const svgRef = useRef<SVGSVGElement>(null)
  const digitRefs = useRef<(SVGGElement | null)[]>([])
  const zeroCutoutRef = useRef<SVGUseElement>(null)
  const targets = useRef(DIGITS.map(() => ({ x: 0, y: 0 })))
  const pushes = useRef<Push[]>(DIGITS.map(() => ({ x: 0, y: 0, vx: 0, vy: 0 })))

  useEffect(() => {
    if (reducedMotion) {
      DIGITS.forEach((digit, i) =>
        digitRefs.current[i]?.setAttribute('transform', restTransform(digit)),
      )
      zeroCutoutRef.current?.setAttribute('transform', restTransform(DIGITS[ZERO]))
      return
    }

    let frame = 0
    const start = performance.now()

    const tick = (now: number) => {
      const seconds = (now - start) / 1000

      DIGITS.forEach((digit, i) => {
        const push = pushes.current[i]
        const target = targets.current[i]
        push.vx = (push.vx + (target.x - push.x) * SPRING_STIFFNESS) * SPRING_DAMPING
        push.vy = (push.vy + (target.y - push.y) * SPRING_STIFFNESS) * SPRING_DAMPING
        push.x += push.vx
        push.y += push.vy

        const angle = (seconds / digit.period + digit.phase) * Math.PI * 2
        const rise = Math.sin(angle) * FLOAT_RISE
        const sway = Math.cos(angle) * FLOAT_SWAY

        const x = (digit.x + push.x).toFixed(2)
        const y = (digit.y + push.y + rise).toFixed(2)
        const transform = `translate(${x} ${y}) rotate(${(digit.tilt + sway).toFixed(2)})`
        digitRefs.current[i]?.setAttribute('transform', transform)
        if (i === ZERO) zeroCutoutRef.current?.setAttribute('transform', transform)
      })

      frame = requestAnimationFrame(tick)
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [reducedMotion])

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    if (reducedMotion) return
    const matrix = svgRef.current?.getScreenCTM()
    if (!matrix) return
    const pointer = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse())
    targets.current = DIGITS.map((digit) => repelTarget(digit, pointer))
  }

  function handlePointerLeave() {
    targets.current = DIGITS.map(() => ({ x: 0, y: 0 }))
  }

  const bubble = (i: number) => {
    const digit = DIGITS[i]
    const { tube } = GLYPHS[digit.glyph]
    return (
      <g
        key={i}
        ref={(node) => {
          digitRefs.current[i] = node
        }}
        transform={restTransform(digit)}
      >
        <use
          {...tubeProps(digit.glyph, tube)}
          filter="url(#nf-bubble)"
          stroke={`url(#nf-body-${digit.glyph})`}
        />
        <use
          {...tubeProps(digit.glyph, tube + RIM_OUT)}
          mask={`url(#nf-rim-mask-${digit.glyph})`}
          stroke="url(#nf-rim)"
        />
      </g>
    )
  }

  return (
    <svg
      ref={svgRef}
      aria-hidden="true"
      className="not-found-digits mx-auto block w-full select-none"
      focusable="false"
      onPointerLeave={handlePointerLeave}
      onPointerMove={handlePointerMove}
      viewBox="-10 -10 660 370"
    >
      <defs>
        {Object.entries(GLYPHS).map(([name, { d }]) => (
          <path key={name} d={d} id={`nf-${name}`} />
        ))}

        {/* El anillo del borde: lo que cubre el tubo un poco más ancho y no cubre el cuerpo. */}
        {Object.entries(GLYPHS).map(([name, { tube }]) => (
          <mask
            key={name}
            height="400"
            id={`nf-rim-mask-${name}`}
            maskUnits="userSpaceOnUse"
            width="400"
            x="-200"
            y="-200"
          >
            <use {...tubeProps(name as Glyph, tube + RIM_OUT)} stroke="#fff" />
            <use {...tubeProps(name as Glyph, tube - RIM_IN)} stroke="#000" />
          </mask>
        ))}

        {/*
          Lo que el 0 tapa de los 4: todo visible menos la silueta del 0, un poco más ancha que
          su borde para que no quede un hilo de 4 asomando. La silueta se mueve con el 0 (ver el
          efecto de arriba).
        */}
        <mask
          height="560"
          id="nf-behind-zero"
          maskUnits="userSpaceOnUse"
          width="860"
          x="-100"
          y="-100"
        >
          <rect fill="#fff" height="560" width="860" x="-100" y="-100" />
          <use
            ref={zeroCutoutRef}
            {...tubeProps('zero', GLYPHS.zero.tube + RIM_OUT + 6)}
            stroke="#000"
            transform={restTransform(DIGITS[ZERO])}
          />
        </mask>

        {/*
          El cuerpo: más lleno arriba que abajo. Uno por dígito y no uno solo con variables,
          porque un degradado toma las variables del lugar donde está definido, no del dígito que
          lo usa.
        */}
        {(Object.keys(GLYPHS) as Glyph[]).map((name) => (
          <linearGradient key={name} id={`nf-body-${name}`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" style={{ stopColor: `var(--nf-${name}-top)` }} />
            <stop offset="1" style={{ stopColor: `var(--nf-${name}-bottom)` }} />
          </linearGradient>
        ))}

        {/* El borde: cuatro colores que vuelven al primero, así el giro no tiene un corte. */}
        <linearGradient id="nf-rim" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" style={{ stopColor: 'var(--nf-rim-a)' }} />
          <stop offset="0.3" style={{ stopColor: 'var(--nf-rim-b)' }} />
          <stop offset="0.55" style={{ stopColor: 'var(--nf-rim-c)' }} />
          <stop offset="0.8" style={{ stopColor: 'var(--nf-rim-d)' }} />
          <stop offset="1" style={{ stopColor: 'var(--nf-rim-a)' }} />
          {!reducedMotion && (
            <animateTransform
              attributeName="gradientTransform"
              dur="9s"
              from="0 0.5 0.5"
              repeatCount="indefinite"
              to="360 0.5 0.5"
              type="rotate"
            />
          )}
        </linearGradient>

        {/*
          Los brillos de la pompa. El cuerpo es casi transparente, así que primero se rellena la
          silueta a opacidad completa (`solid`): sin eso, el relieve sale de un alfa bajísimo y
          no hay brillo que ver.

          Sobre la silueta desenfocada —alta en el centro del tubo, baja en los bordes— van una
          luz especular desde arriba a la izquierda, que es el reflejo de la pompa, y un canto de
          luz sobre el borde de abajo, que es lo que una burbuja tiene donde la pared se curva.
          Las dos se recortan a la silueta, así que no se salen.

          Las regiones van en unidades del dibujo y no en porcentajes: los porcentajes se miden
          sobre el recorrido sin el grosor del tubo, y recortarían medio dígito.
        */}
        <filter
          filterUnits="userSpaceOnUse"
          height="400"
          id="nf-bubble"
          width="400"
          x="-200"
          y="-200"
        >
          <feComponentTransfer in="SourceAlpha" result="solid">
            <feFuncA slope="20" type="linear" />
          </feComponentTransfer>
          <feGaussianBlur in="solid" result="relief" stdDeviation="11" />
          <feSpecularLighting
            in="relief"
            result="specular"
            specularConstant="1.2"
            specularExponent="45"
            style={{ lightingColor: 'var(--nf-shine)' }}
            surfaceScale="8"
          >
            <fePointLight x="-90" y="-170" z="230" />
          </feSpecularLighting>
          <feComposite in="specular" in2="solid" operator="in" result="shine" />

          <feOffset dy="-12" in="solid" result="lifted" />
          <feGaussianBlur in="lifted" result="liftedBlur" stdDeviation="9" />
          <feComposite in="solid" in2="liftedBlur" operator="out" result="rimEdge" />
          <feFlood result="edgeColor" style={{ floodColor: 'var(--nf-edge)' }} />
          <feComposite in="edgeColor" in2="rimEdge" operator="in" result="edge" />

          <feMerge>
            <feMergeNode in="SourceGraphic" />
            <feMergeNode in="edge" />
            <feMergeNode in="shine" />
          </feMerge>
        </filter>

        {/*
          El resplandor del conjunto: el mismo dibujo muy desenfocado por detrás, que es lo que
          hace que las burbujas se destaquen del fondo en vez de perderse en él. Se recorta a lo que
          queda AFUERA de las burbujas: adentro sumaba color y les quitaba la transparencia.
        */}
        <filter filterUnits="userSpaceOnUse" height="520" id="nf-glow" width="820" x="-90" y="-90">
          <feGaussianBlur in="SourceGraphic" result="blurred" stdDeviation="18" />
          <feComponentTransfer in="blurred" result="glowAll">
            <feFuncA slope="0.8" type="linear" />
          </feComponentTransfer>
          <feComponentTransfer in="SourceAlpha" result="solid">
            <feFuncA slope="20" type="linear" />
          </feComponentTransfer>
          <feComposite in="glowAll" in2="solid" operator="out" result="glow" />
          <feMerge>
            <feMergeNode in="glow" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <g filter="url(#nf-glow)">
        <g mask="url(#nf-behind-zero)">{DIGITS.map((_, i) => (i === ZERO ? null : bubble(i)))}</g>
        {bubble(ZERO)}
      </g>
    </svg>
  )
}

/** Lo común a cada capa de un dígito: el mismo recorrido, trazado como un tubo redondeado. */
function tubeProps(glyph: Glyph, width: number) {
  return {
    href: `#nf-${glyph}`,
    fill: 'none',
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    strokeWidth: width,
  } as const
}
