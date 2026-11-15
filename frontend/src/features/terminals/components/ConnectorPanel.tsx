import {
  CONNECTOR_TYPE_LABEL,
  STATUS_DOT_CLASS,
  STATUS_LABEL,
  STATUS_TEXT_CLASS,
  formatPower,
} from '../format'
import type { Connector } from '../types'
import ConnectorAccessCode from './ConnectorAccessCode'

/**
 * Conectores de una estación en el detalle: una grilla de recuadros, y nada más.
 *
 * Cada recuadro ya muestra todo lo que hay para saber de un conector —tipo, potencia y
 * estado—, así que no son botones: no habría nada del otro lado. Editarlos es otra cosa y
 * vive en el formulario (ver ConnectorEditor). Lo único que se pide aparte es el PIN de la
 * terminal, que no viaja con el conector (ver ConnectorAccessCode).
 */
interface ConnectorPanelProps {
  connectors: Connector[]
}

export default function ConnectorPanel({ connectors }: ConnectorPanelProps) {
  return (
    <section className="mt-6">
      <h3 className="text-st-muted text-lg font-bold">CONNECTORS</h3>

      {connectors.length === 0 ? (
        <p className="text-st-muted mt-2 text-sm italic">
          Esta estación todavía no tiene conectores.
        </p>
      ) : (
        <ul className="mt-3 grid grid-cols-2 gap-3">
          {connectors.map((connector) => (
            <li key={connector.id} className="bg-st-surface rounded-2xl px-4 py-3">
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
              <ConnectorAccessCode connectorId={connector.id} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
