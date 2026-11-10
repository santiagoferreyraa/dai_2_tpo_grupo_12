import { useState } from 'react'
import type { FormEvent } from 'react'

import { startCharging } from '../data/sessionsRepository'
import { activationErrorMessage, errorCode } from '../messages'
import { canScanQr } from '../qrSupport'
import type { ChargingSession } from '../types'
import QrScanner from './QrScanner'

interface StartChargingFormProps {
  /** La reserva con la que viene el conductor, si llegó desde ella. */
  bookingId?: number
  onStarted: (session: ChargingSession) => void
  /** El backend dice que ya hay una carga en curso: la pantalla vuelve a buscarla. */
  onAlreadyCharging: () => void
}

/** Lo que pone la terminal en su QR: el PIN con este prefijo. Ver `ConnectorAccessCode` en core. */
const QR_PREFIX = 'ecopedia:connector:'

const TARGET_OPTIONS = [60, 80, 90, 100] as const

/**
 * Escanear y elegir hasta dónde cargar (RF11, RF12, ECO-44).
 *
 * El PIN se puede tipear o venir de la cámara; lo que viaja al backend es lo mismo en los dos casos.
 * Un QR leído se manda tal cual, con su prefijo, para que sea el backend el que decida si es de una
 * terminal: la pantalla no adivina.
 *
 * La validación de acá es solo de forma —seis dígitos—, para no gastar un intento por un dedo de
 * más: el backend cuenta los errados y, pasado el tope, deja al conductor esperando.
 */
export default function StartChargingForm({
  bookingId,
  onStarted,
  onAlreadyCharging,
}: StartChargingFormProps) {
  const [pin, setPin] = useState('')
  const [scannedQr, setScannedQr] = useState<string | null>(null)
  const [scanning, setScanning] = useState(false)
  const [target, setTarget] = useState<number>(80)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const scanAvailable = canScanQr()
  const code = scannedQr ?? pin
  const codeReady = scannedQr !== null || /^\d{6}$/.test(pin)

  function handleDetected(value: string) {
    setScanning(false)
    setError(null)
    if (value.toLowerCase().startsWith(QR_PREFIX)) {
      setScannedQr(value)
      setPin(value.slice(QR_PREFIX.length))
    } else {
      setError('Ese QR no es el de una terminal de Ecopedia. Buscá el que está en el cargador.')
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!codeReady || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const session = await startCharging({
        pinOrQr: code,
        targetBatteryPercent: target,
        bookingId,
      })
      onStarted(session)
    } catch (failure) {
      if (errorCode(failure) === 'DRIVER_ALREADY_CHARGING') {
        onAlreadyCharging()
        return
      }
      setError(activationErrorMessage(failure))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)} noValidate className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="text-text text-3xl leading-tight font-extrabold tracking-tight">
          Iniciar carga
        </h1>
        <p className="text-text-muted text-sm leading-relaxed text-pretty">
          Escaneá el QR de la terminal o tipeá el PIN que está impreso al lado. Con eso sabemos que
          estás frente al conector. No se cobra nada ahora: el consumo se cobra cuando termina la
          carga.
        </p>
        {bookingId !== undefined && (
          <p className="text-primary text-xs font-semibold">
            Vas a cargar con tu reserva #{bookingId}.
          </p>
        )}
      </header>

      {scanning ? (
        <QrScanner onDetected={handleDetected} onCancel={() => setScanning(false)} />
      ) : (
        <div className="flex flex-col gap-2">
          <label htmlFor="terminal-pin" className="text-text text-sm font-semibold">
            PIN de la terminal
          </label>
          <div className="flex gap-2">
            <input
              id="terminal-pin"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={pin}
              onChange={(event) => {
                setScannedQr(null)
                setPin(event.target.value.replace(/\D/g, '').slice(0, 6))
              }}
              placeholder="000000"
              aria-describedby="terminal-pin-help"
              className="border-border bg-surface/70 text-text focus-visible:outline-primary w-full rounded-xl border px-4 py-3 text-center font-mono text-2xl tracking-[0.4em] tabular-nums focus-visible:outline-2"
            />
            {scanAvailable && (
              <button
                type="button"
                onClick={() => {
                  setError(null)
                  setScanning(true)
                }}
                className="border-border text-text hover:border-primary shrink-0 rounded-xl border px-4 text-sm font-semibold transition-colors"
              >
                Escanear QR
              </button>
            )}
          </div>
          <p id="terminal-pin-help" className="text-text-muted text-xs">
            {scannedQr !== null
              ? 'QR leído. Revisá el porcentaje y empezá.'
              : scanAvailable
                ? 'Seis dígitos. También podés escanear el QR con la cámara.'
                : 'Seis dígitos. Este navegador no lee QR: tipeá el PIN.'}
          </p>
        </div>
      )}

      <fieldset className="flex flex-col gap-2">
        <legend className="text-text mb-2 text-sm font-semibold">
          ¿Hasta cuánto querés cargar?
        </legend>
        <div className="grid grid-cols-4 gap-2">
          {TARGET_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={target === option}
              onClick={() => setTarget(option)}
              className={`rounded-xl border px-3 py-2 text-sm font-bold tabular-nums transition-colors ${
                target === option
                  ? 'border-primary text-primary bg-primary/10'
                  : 'border-border text-text-muted hover:text-text'
              }`}
            >
              {option}%
            </button>
          ))}
        </div>
        <p className="text-text-muted text-xs">
          La carga se corta sola al llegar a ese porcentaje o cuando se termina tu tiempo
          disponible. Podés detenerla antes cuando quieras.
        </p>
      </fieldset>

      {error !== null && (
        <p className="text-danger text-sm" role="alert">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={!codeReady || submitting || scanning}
        className="brand-fill text-on-primary rounded-xl px-6 py-3 text-sm font-bold transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitting ? 'Verificando terminal y tarjeta…' : 'Empezar a cargar'}
      </button>
    </form>
  )
}
