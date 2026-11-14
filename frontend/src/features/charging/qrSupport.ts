/*
 * `BarcodeDetector` es la API del navegador que lee códigos de barras y QR. No está en los tipos de
 * TypeScript todavía, así que se declara lo poco que se usa.
 */
export interface DetectedBarcode {
  rawValue: string
}

export interface BarcodeDetectorInstance {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>
}

export interface BarcodeDetectorConstructor {
  new (options?: { formats?: string[] }): BarcodeDetectorInstance
}

export function barcodeDetector(): BarcodeDetectorConstructor | null {
  const candidate = (globalThis as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector
  return candidate ?? null
}

/** Si este navegador puede leer QR con la cámara. Donde no, la pantalla ofrece solo el PIN. */
export function canScanQr(): boolean {
  return (
    barcodeDetector() !== null &&
    typeof navigator !== 'undefined' &&
    navigator.mediaDevices?.getUserMedia !== undefined
  )
}
