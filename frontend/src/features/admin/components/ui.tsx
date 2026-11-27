import type { ReactNode, SelectHTMLAttributes } from 'react'

import { BARE_SELECT, INSET, riseDelay, TONE_TEXT, type Tone } from './styles'

/**
 * Las piezas que repiten todas las secciones del backoffice.
 *
 * Son las mismas superficies del resto de la aplicación —`.glass-panel` afuera, `.glass-inset`
 * adentro, el degradado de la marca para lo que se toca— y la misma entrada escalonada de
 * Estadísticas. Viven acá juntas para que las cinco secciones no repitan cada una su versión de
 * un botón o de un campo, que es como el backoffice había quedado distinto del resto.
 */

/** Un panel de vidrio que entra desde abajo. */
export function Panel({
  children,
  className = '',
  delayMs = 0,
  as: Tag = 'section',
  labelledBy,
}: {
  children: ReactNode
  className?: string
  delayMs?: number
  as?: 'section' | 'div' | 'article'
  labelledBy?: string
}) {
  return (
    <Tag
      aria-labelledby={labelledBy}
      className={`glass-panel analytics-rise relative rounded-3xl p-5 ${className}`}
      style={riseDelay(delayMs)}
    >
      {children}
    </Tag>
  )
}

/** El título de una sección, su bajada y, a la derecha, lo que se puede hacer. */
export function SectionHeader({
  id,
  title,
  description,
  actions,
}: {
  id?: string
  title: string
  description: string
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div className="min-w-0">
        <h2 id={id} className="text-text text-xl font-bold">
          {title}
        </h2>
        <p className="text-text-muted mt-1 text-sm">{description}</p>
      </div>
      {actions !== undefined && (
        <div className="flex flex-wrap items-center gap-2.5 md:justify-end">{actions}</div>
      )}
    </div>
  )
}

/** El buscador redondo de Estadísticas, con la lupa adentro y el rótulo para el lector. */
export function SearchInput({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  return (
    <label
      className={`${INSET} focus-within:border-primary focus-within:ring-primary/40 flex w-full items-center gap-2 rounded-full px-3.5 py-2.5 focus-within:ring-2 sm:w-64`}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        className="text-text-muted h-4 w-4 shrink-0"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <span className="sr-only">{label}</span>
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="text-text placeholder:text-text-muted min-w-0 flex-1 bg-transparent text-sm outline-none"
      />
    </label>
  )
}

/**
 * Un estado escrito: la palabra en mayúsculas, en la tinta de su estado, sin recuadro alrededor.
 * La tinta ya está resuelta por tema para que el texto chico llegue al contraste de WCAG.
 */
export function StatusText({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      className={`text-xs font-bold tracking-wide whitespace-nowrap uppercase ${TONE_TEXT[tone]}`}
    >
      {children}
    </span>
  )
}

/** Lo que se ve mientras carga o cuando no hay nada: un recuadro tenue, centrado. */
export function EmptyState({ children, busy = false }: { children: ReactNode; busy?: boolean }) {
  return (
    <div
      className={`glass-inset text-text-muted rounded-2xl p-8 text-center text-sm ${busy ? 'animate-pulse' : ''}`}
      role={busy ? 'status' : undefined}
    >
      {children}
    </div>
  )
}

/** El aviso de una acción: verde si salió, rojo si no. Se lee solo con el lector de pantalla. */
export function Notice({ tone, children }: { tone: 'success' | 'error'; children: ReactNode }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className="glass-panel analytics-rise text-text relative flex items-start gap-3 overflow-hidden rounded-2xl py-3 pr-4 pl-5 text-sm font-medium"
    >
      {/* La franja de color va como elemento: un borde sumado al vidrio no se aplicaría. */}
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 w-1 ${tone === 'success' ? 'brand-fill' : 'bg-danger'}`}
      />
      {children}
    </div>
  )
}

/**
 * La lista desplegable sin caja de las filas: el valor en mayúsculas y una flechita. El color se
 * pasa en `tone` y lo toman el texto y la flecha a la vez; al pasar el mouse los dos se aclaran.
 */
export function BareSelect({
  tone,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { tone: string }) {
  return (
    <span
      className={`relative -ml-1 inline-flex items-center transition-opacity hover:opacity-75 has-[:disabled]:opacity-60 has-[:disabled]:hover:opacity-60 ${tone}`}
    >
      <select {...props} className={BARE_SELECT}>
        {children}
      </select>
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className="pointer-events-none absolute right-1 h-3.5 w-3.5"
      >
        <path d="m6 9 6 6 6-6" />
      </svg>
    </span>
  )
}
