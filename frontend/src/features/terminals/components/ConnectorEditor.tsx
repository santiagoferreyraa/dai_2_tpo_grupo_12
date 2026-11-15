import { useState } from 'react'

import {
  CONNECTOR_TYPES,
  CONNECTOR_TYPE_LABEL,
  OPERATIONAL_STATUSES,
  STATUS_DOT_CLASS,
  STATUS_LABEL,
  STATUS_TEXT_CLASS,
  formatPower,
  parseDecimal,
} from '../format'
import type { ConnectorDraft, ConnectorType, OperationalStatus } from '../types'

/**
 * Conectores dentro del formulario de alta y edición.
 *
 * Repite el gesto del detalle —grilla de recuadros, tocar uno lo abre, la flecha vuelve—
 * para que agregar y editar se sientan igual que mirar. El recuadro punteado que agrega es
 * el mismo que el de las fotos, más bajo y con la palabra en vez del signo.
 *
 * El estado de los conectores lo tiene el formulario: acá solo se selecciona cuál se está
 * editando. Así el guardado ve la lista entera en un solo lugar.
 */
interface ConnectorEditorProps {
  value: ConnectorDraft[]
  onChange: (connectors: ConnectorDraft[]) => void
}

/** Con qué nace un conector nuevo. Coincide con el default del backend: AVAILABLE. */
const NEW_CONNECTOR: ConnectorDraft = {
  connectorType: 'CCS2',
  maxPowerKw: 0,
  operationalStatus: 'AVAILABLE',
}

const FIELD_CLASS =
  'bg-st-surface-raised text-st-text placeholder:text-st-muted focus:outline-st-accent w-full rounded-full px-4 py-2.5 text-sm focus:outline-2'

export default function ConnectorEditor({ value, onChange }: ConnectorEditorProps) {
  /*
   * Se selecciona por posición y no por id porque los conectores nuevos todavía no tienen
   * uno. Al eliminar se vuelve a la grilla, así ninguna posición queda apuntando a otro.
   */
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)

  /* Texto crudo del campo de potencia: "8," no es un número pero sí un estado válido al tipear. */
  const [powerText, setPowerText] = useState('')

  const openAt = (index: number) => {
    setPowerText(String(value[index].maxPowerKw).replace('.', ','))
    setSelectedIndex(index)
  }

  const add = () => {
    const next = [...value, { ...NEW_CONNECTOR }]
    onChange(next)
    setPowerText('')
    setSelectedIndex(next.length - 1)
  }

  const patch = (changes: Partial<ConnectorDraft>) => {
    if (selectedIndex === null) return
    onChange(
      value.map((draft, index) => (index === selectedIndex ? { ...draft, ...changes } : draft)),
    )
  }

  const remove = () => {
    if (selectedIndex === null) return
    onChange(value.filter((_, index) => index !== selectedIndex))
    setSelectedIndex(null)
  }

  const selected = selectedIndex === null ? null : (value[selectedIndex] ?? null)

  if (selected) {
    return (
      <section className="mt-6">
        <header className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setSelectedIndex(null)}
            aria-label="Volver a la lista de conectores"
            className="border-st-border text-st-text hover:bg-st-surface-raised focus-visible:outline-st-accent flex h-8 w-8 items-center justify-center rounded-full border text-lg leading-none focus-visible:outline-2"
          >
            &#8592;
          </button>
          <h3 className="text-st-muted text-lg font-bold">CONECTOR</h3>
        </header>

        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-5">
          <label className="block">
            <span className="text-st-muted block text-base font-bold">POTENCIA MÁXIMA</span>
            <span className="mt-2 flex items-center gap-2">
              <input
                type="text"
                inputMode="decimal"
                value={powerText}
                placeholder="8,2"
                onChange={(event) => {
                  setPowerText(event.target.value)
                  patch({ maxPowerKw: parseDecimal(event.target.value) ?? 0 })
                }}
                className={FIELD_CLASS}
              />
              <span className="text-st-text text-sm font-bold">kW</span>
            </span>
          </label>

          <label className="block">
            <span className="text-st-muted block text-base font-bold">ESTADO</span>
            <select
              value={selected.operationalStatus}
              onChange={(event) =>
                patch({ operationalStatus: event.target.value as OperationalStatus })
              }
              className={`${FIELD_CLASS} mt-2 appearance-none`}
            >
              {OPERATIONAL_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABEL[status]}
                </option>
              ))}
            </select>
          </label>
        </div>

        <label className="mt-5 block">
          <span className="text-st-muted block text-base font-bold">TIPO</span>
          <select
            value={selected.connectorType}
            onChange={(event) => patch({ connectorType: event.target.value as ConnectorType })}
            className={`${FIELD_CLASS} mt-2 w-40 appearance-none`}
          >
            {CONNECTOR_TYPES.map((type) => (
              <option key={type} value={type}>
                {CONNECTOR_TYPE_LABEL[type]}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          onClick={remove}
          className="text-st-offline hover:text-st-offline/80 focus-visible:outline-st-accent mt-5 text-xs font-bold tracking-wide underline underline-offset-4 focus-visible:outline-2"
        >
          QUITAR CONECTOR
        </button>
      </section>
    )
  }

  return (
    <section className="mt-6">
      <h3 className="text-st-muted text-lg font-bold">CONECTORES</h3>

      <ul className="mt-3 grid grid-cols-2 gap-3">
        {value.map((connector, index) => (
          <li key={connector.id ?? `new-${index}`}>
            <button
              type="button"
              onClick={() => openAt(index)}
              className="glass-inset hover:border-primary/60 focus-visible:outline-st-accent w-full rounded-2xl px-4 py-3 text-left transition-colors focus-visible:outline-2"
            >
              <span className="text-st-text block text-sm font-bold">
                {CONNECTOR_TYPE_LABEL[connector.connectorType]}
              </span>
              <span className="text-st-muted mt-1 block text-xs">
                {formatPower(connector.maxPowerKw)}
              </span>
              <span className="mt-2 flex items-center gap-2">
                <span
                  className={`h-2 w-2 shrink-0 rounded-full ${STATUS_DOT_CLASS[connector.operationalStatus]}`}
                />
                <span
                  className={`truncate text-[10px] font-bold tracking-wide ${STATUS_TEXT_CLASS[connector.operationalStatus]}`}
                >
                  {STATUS_LABEL[connector.operationalStatus]}
                </span>
              </span>
            </button>
          </li>
        ))}

        <li>
          <button
            type="button"
            onClick={add}
            className="border-st-muted/60 text-st-muted hover:border-st-accent hover:text-st-accent focus-visible:outline-st-accent flex h-full min-h-[76px] w-full items-center justify-center rounded-2xl border-2 border-dashed text-xs font-bold tracking-widest transition-colors focus-visible:outline-2"
          >
            + CONECTOR
          </button>
        </li>
      </ul>
    </section>
  )
}
