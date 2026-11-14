/** "12:05" o "1:02:05": el tiempo de una carga, con segundos. */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0
    ? `${String(hours)}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(minutes)}:${pad(seconds)}`
}

/** "18,4": la energía con un decimal, como la muestra un cargador. */
export function formatKwh(kwh: number): string {
  return kwh.toLocaleString('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

export function formatKw(kw: number): string {
  return kw.toLocaleString('es-AR', { maximumFractionDigits: 0 })
}
