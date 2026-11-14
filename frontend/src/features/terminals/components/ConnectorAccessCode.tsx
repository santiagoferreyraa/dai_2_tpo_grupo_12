import { useState } from 'react'

import { api, ApiError } from '@/lib/api'

interface AccessCodeResponse {
  connectorId: number
  accessCode: string
  qrPayload: string
}

type CodeState =
  | { kind: 'hidden' }
  | { kind: 'loading' }
  | { kind: 'shown'; code: AccessCodeResponse }
  | { kind: 'confirmingRegenerate'; code: AccessCodeResponse }
  | { kind: 'error'; message: string }

interface ConnectorAccessCodeProps {
  connectorId: number
}

/**
 * El PIN de la terminal, para que el operador lo imprima (RF11, ECO-44).
 *
 * Es lo que el conductor escanea o tipea frente al conector para iniciar la carga, y lo que acredita
 * que está ahí. Por eso no viaja con el resto del conector: se pide aparte, solo cuando el operador
 * lo quiere ver, y solo lo puede ver el dueño de la estación.
 *
 * El QR impreso en la terminal tiene que decir exactamente lo que muestra "Contenido del QR". Esta
 * pantalla todavía no lo dibuja: generar el QR es una dependencia nueva y va en un PR aparte (regla 7
 * del README). Cualquier generador de QR sirve con ese texto.
 *
 * Regenerar invalida el código anterior en el acto: es para cuando el PIN se filtró, y obliga a
 * reimprimir el de la terminal. Por eso pide confirmación.
 */
export default function ConnectorAccessCode({ connectorId }: ConnectorAccessCodeProps) {
  const [state, setState] = useState<CodeState>({ kind: 'hidden' })

  async function load(regenerate: boolean) {
    setState({ kind: 'loading' })
    try {
      const path = `/connectors/${String(connectorId)}/access-code`
      const code = regenerate
        ? await api.post<AccessCodeResponse>(path)
        : await api.get<AccessCodeResponse>(path)
      setState({ kind: 'shown', code })
    } catch (error) {
      setState({
        kind: 'error',
        message:
          error instanceof ApiError && error.status === 0
            ? 'Sin conexión con el servidor.'
            : 'No se pudo obtener el código.',
      })
    }
  }

  if (state.kind === 'hidden' || state.kind === 'error') {
    return (
      <div className="mt-2 flex flex-col gap-1">
        <button
          type="button"
          onClick={() => void load(false)}
          className="text-st-muted hover:text-st-text focus-visible:outline-st-accent self-start text-[10px] font-bold tracking-wide underline underline-offset-4 focus-visible:outline-2"
        >
          VER PIN DE LA TERMINAL
        </button>
        {state.kind === 'error' && <span className="text-[10px] text-danger">{state.message}</span>}
      </div>
    )
  }

  if (state.kind === 'loading') {
    return <p className="text-st-muted mt-2 text-[10px]">Cargando…</p>
  }

  const { code } = state
  return (
    <div className="mt-2 flex flex-col gap-1">
      <span className="text-st-muted text-[10px] font-bold tracking-wide">PIN</span>
      <span className="text-st-text font-mono text-lg font-bold tracking-[0.25em] tabular-nums select-all">
        {code.accessCode}
      </span>
      <span className="text-st-muted text-[10px] font-bold tracking-wide">CONTENIDO DEL QR</span>
      <code className="text-st-text text-[10px] break-all select-all">{code.qrPayload}</code>

      {state.kind === 'confirmingRegenerate' ? (
        <span className="mt-1 flex flex-wrap items-center gap-2 text-[10px]">
          <span className="text-st-muted">El PIN actual deja de servir. ¿Seguro?</span>
          <button
            type="button"
            onClick={() => void load(true)}
            className="font-bold text-danger underline underline-offset-4"
          >
            SÍ, REGENERAR
          </button>
          <button
            type="button"
            onClick={() => setState({ kind: 'shown', code })}
            className="text-st-muted underline underline-offset-4"
          >
            CANCELAR
          </button>
        </span>
      ) : (
        <span className="mt-1 flex gap-3 text-[10px] font-bold tracking-wide">
          <button
            type="button"
            onClick={() => setState({ kind: 'confirmingRegenerate', code })}
            className="text-st-muted hover:text-st-text underline underline-offset-4"
          >
            REGENERAR
          </button>
          <button
            type="button"
            onClick={() => setState({ kind: 'hidden' })}
            className="text-st-muted hover:text-st-text underline underline-offset-4"
          >
            OCULTAR
          </button>
        </span>
      )}
    </div>
  )
}
