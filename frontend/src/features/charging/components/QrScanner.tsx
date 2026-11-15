import { useEffect, useRef, useState } from 'react'

import { barcodeDetector } from '../qrSupport'

interface QrScannerProps {
  /** Lo que dice el QR, la primera vez que se lee uno. */
  onDetected: (value: string) => void
  onCancel: () => void
}

type ScannerState = 'starting' | 'scanning' | 'denied' | 'failed'

/**
 * La cámara apuntando al QR de la terminal (RF11, ECO-44).
 *
 * **Sin librería, con la API del navegador** (`BarcodeDetector`). Sumar una dependencia va en un PR
 * aparte (regla 7 del README), y la que hay lee QR en Chrome y Edge de Android y de escritorio, que es
 * donde se usa la app frente a un cargador. Donde no existe —Safari, Firefox— la pantalla no muestra
 * el botón y el conductor tipea el PIN, que está impreso al lado del QR y dice lo mismo.
 *
 * Usa la cámara trasera si hay una (`facingMode: environment`) y la apaga al desmontarse: una cámara
 * que queda prendida después de leer el código es lo primero que nota cualquiera.
 */
export default function QrScanner({ onDetected, onCancel }: QrScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [state, setState] = useState<ScannerState>('starting')

  /*
   * El callback va por ref para que el efecto no se reinicie —y la cámara no se apague y se vuelva a
   * prender— cada vez que el padre se dibuja con una función nueva.
   */
  const onDetectedRef = useRef(onDetected)
  useEffect(() => {
    onDetectedRef.current = onDetected
  }, [onDetected])

  useEffect(() => {
    // La pantalla solo monta el escáner si `canScanQr()`: acá el detector existe.
    const Detector = barcodeDetector()
    if (Detector === null) return

    let stream: MediaStream | null = null
    let frame = 0
    let stopped = false
    const detector = new Detector({ formats: ['qr_code'] })

    async function scan() {
      const video = videoRef.current
      if (stopped || video === null) return
      if (video.readyState >= video.HAVE_ENOUGH_DATA) {
        try {
          const codes = await detector.detect(video)
          const first = codes.find((code) => code.rawValue.trim() !== '')
          if (first && !stopped) {
            stopped = true
            onDetectedRef.current(first.rawValue.trim())
            return
          }
        } catch {
          // Un cuadro que no se pudo leer no es un error: se prueba con el siguiente.
        }
      }
      frame = requestAnimationFrame(() => void scan())
    }

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then(async (media) => {
        if (stopped) {
          media.getTracks().forEach((track) => track.stop())
          return
        }
        stream = media
        const video = videoRef.current
        if (video === null) return
        video.srcObject = media
        await video.play()
        setState('scanning')
        void scan()
      })
      .catch((error: unknown) => {
        const denied = error instanceof DOMException && error.name === 'NotAllowedError'
        setState(denied ? 'denied' : 'failed')
      })

    return () => {
      stopped = true
      cancelAnimationFrame(frame)
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [])

  return (
    <div className="flex flex-col gap-3">
      <div className="relative aspect-square w-full overflow-hidden rounded-2xl bg-black/80">
        <video
          ref={videoRef}
          muted
          playsInline
          className="h-full w-full object-cover"
          aria-label="Vista de la cámara para leer el QR de la terminal"
        />
        {/* El recuadro donde apuntar: decorativo, el detector lee el cuadro entero. */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-[18%] rounded-2xl border-2 border-white/80"
        />
        {state !== 'scanning' && (
          <p className="absolute inset-0 flex items-center justify-center p-6 text-center text-sm font-semibold text-white">
            {state === 'starting' && 'Abriendo la cámara…'}
            {state === 'denied' &&
              'No tenemos permiso para usar la cámara. Habilitalo en el navegador o tipeá el PIN.'}
            {state === 'failed' && 'No pudimos abrir la cámara. Tipeá el PIN de la terminal.'}
          </p>
        )}
      </div>
      <p className="text-text-muted text-xs" role="status">
        {state === 'scanning' ? 'Apuntá al QR que está en la terminal.' : ''}
      </p>
      <button
        type="button"
        onClick={onCancel}
        className="text-text-muted hover:text-text self-start rounded-xl px-3 py-2 text-sm font-semibold transition-colors"
      >
        Tipear el PIN en su lugar
      </button>
    </div>
  )
}
