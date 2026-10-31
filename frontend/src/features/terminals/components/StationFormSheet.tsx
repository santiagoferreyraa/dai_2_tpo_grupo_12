import { useState, type FormEvent } from 'react'

import { describePeak, useNetworkRates, useStationPricing } from '@/features/pricing/usePricing'
import type { StationPricingInput } from '@/features/pricing/types'

import { formatCoordinate, parseDecimal } from '../format'
import { normalizeGridNode, toGridNodeId, validateGridNode } from '../gridNode'
import type { ConnectorDraft, StationDetail, StationInput } from '../types'
import ConnectorEditor from './ConnectorEditor'
import ImagePicker from './ImagePicker'

/**
 * Alta y edición de una estación. Es el mismo formulario: cambian el título, el texto del
 * botón y si hay datos iniciales.
 *
 * Las coordenadas se guardan como texto mientras se escriben y recién se convierten a
 * número al validar. Guardarlas como número obliga a decidir qué es "-34," a mitad de
 * tipeo, y cualquier respuesta borra lo que el usuario venía escribiendo.
 */
interface StationFormSheetProps {
  /** `null` es un alta. */
  station: StationDetail | null
  /**
   * Recibe la estación y su cobro: la seña y la hora pico, que se guardan aparte, en Tarificación.
   * El cobro llega `null` si no se pudo leer el de la estación y el operador no lo tocó: mandar los
   * valores por omisión pisaría los que la estación tenía.
   */
  onSubmit: (input: StationInput, pricing: StationPricingInput | null) => Promise<void>
  onDelete?: () => void
  /** Un problema que trae el formulario desde antes de montarse; ver `StationsPage`. */
  initialFailure?: string | null
}

interface FormErrors {
  name?: string
  address?: string
  latitude?: string
  longitude?: string
  nodeId?: string
  deposit?: string
  connectors?: string
}

const FIELD_CLASS =
  'bg-st-surface-raised text-st-text placeholder:text-st-muted focus:outline-st-accent mt-2 w-full rounded-full px-4 py-2.5 text-sm focus:outline-2'

export default function StationFormSheet({
  station,
  onSubmit,
  onDelete,
  initialFailure = null,
}: StationFormSheetProps) {
  const [name, setName] = useState(station?.name ?? '')
  const [address, setAddress] = useState(station?.address ?? '')
  const [latitude, setLatitude] = useState(station ? formatCoordinate(station.latitude) : '')
  const [longitude, setLongitude] = useState(station ? formatCoordinate(station.longitude) : '')
  const [nodeId, setNodeId] = useState(station?.nodeId ?? '')
  const [photoUrls, setPhotoUrls] = useState<string[]>(station?.photoUrls ?? [])
  const [connectors, setConnectors] = useState<ConnectorDraft[]>(
    station?.connectors.map((connector) => ({ ...connector })) ?? [],
  )

  /*
   * La seña y la hora pico (Manuel, 31/10: las fija el CPO, por estación). Mientras el operador no las
   * toca valen `null` y se muestra lo que tiene la estación; una estación nueva arranca sin seña y con
   * hora pico, que es lo mismo que el backend le asigna a una estación sin configurar.
   */
  const stationPricing = useStationPricing(station?.id ?? null)
  const rates = useNetworkRates()
  const [depositText, setDepositText] = useState<string | null>(null)
  const [peakChoice, setPeakChoice] = useState<boolean | null>(null)
  const loadedPricing =
    stationPricing === null
      ? { depositAmount: 0, peakPricingEnabled: true }
      : stationPricing.status === 'ready'
        ? stationPricing.value
        : null
  const deposit = depositText ?? (loadedPricing ? String(loadedPricing.depositAmount) : '')
  const peak = peakChoice ?? loadedPricing?.peakPricingEnabled ?? true

  const [errors, setErrors] = useState<FormErrors>({})
  const [saving, setSaving] = useState(false)
  const [failure, setFailure] = useState<string | null>(initialFailure)

  const validate = (): StationInput | null => {
    const found: FormErrors = {}

    if (name.trim() === '') found.name = 'El nombre es obligatorio'
    if (address.trim() === '') found.address = 'La dirección es obligatoria'

    /*
     * Los rangos son los de las coordenadas geográficas. Sin ellos, un dedo de más
     * ("-345,6") entra como una estación que la búsqueda por recuadro nunca va a encontrar.
     */
    const lat = parseDecimal(latitude)
    if (lat === null) found.latitude = 'Latitud inválida'
    else if (lat < -90 || lat > 90) found.latitude = 'Entre -90 y 90'

    const lon = parseDecimal(longitude)
    if (lon === null) found.longitude = 'Longitud inválida'
    else if (lon < -180 || lon > 180) found.longitude = 'Entre -180 y 180'

    const nodeError = validateGridNode(nodeId)
    if (nodeError) found.nodeId = nodeError

    const depositValue = deposit.trim() === '' ? 0 : parseDecimal(deposit)
    if (depositValue === null || depositValue < 0) {
      found.deposit = 'La seña tiene que ser un monto en pesos, de 0 en adelante'
    }

    /*
     * Un conector sin potencia no es un conector a medio cargar: es uno que la búsqueda por
     * potencia mínima nunca va a devolver y que en la tarjeta se lee como "0 kW". Se llega
     * fácil, porque el campo arranca vacío y porque un texto que todavía no es número
     * —"8," a mitad de tipeo— también deja el valor en cero.
     */
    const invalid = connectors.findIndex((connector) => !(connector.maxPowerKw > 0))
    if (invalid !== -1) {
      found.connectors = `El conector ${invalid + 1} necesita una potencia mayor a cero`
    }

    setErrors(found)
    if (Object.keys(found).length > 0) return null

    return {
      name: name.trim(),
      address: address.trim(),
      latitude: lat as number,
      longitude: lon as number,
      photoUrls,
      nodeId: toGridNodeId(nodeId),
      connectors,
    }
  }

  /** El cobro a guardar, o `null` si no se pudo leer el de la estación y el operador no lo tocó. */
  const pricingToSave = (): StationPricingInput | null => {
    if (loadedPricing === null && depositText === null && peakChoice === null) return null
    return {
      depositAmount: deposit.trim() === '' ? 0 : (parseDecimal(deposit) ?? 0),
      peakPricingEnabled: peak,
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const input = validate()
    if (!input) return

    setSaving(true)
    setFailure(null)
    try {
      await onSubmit(input, pricingToSave())
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'No se pudo guardar la estación')
    } finally {
      setSaving(false)
    }
  }

  const errorText = (message?: string) =>
    message ? <span className="text-st-offline mt-1 block px-4 text-xs">{message}</span> : null

  return (
    /* `noValidate`: los mensajes los pone el formulario, no las burbujas del navegador. */
    <form onSubmit={handleSubmit} noValidate>
      <label className="block">
        <span className="text-st-muted text-lg font-bold">NAME</span>
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="YPF Station"
          className={FIELD_CLASS}
        />
      </label>
      {errorText(errors.name)}

      <label className="mt-5 block">
        <span className="text-st-muted text-lg font-bold">ADDRESS</span>
        <input
          type="text"
          value={address}
          onChange={(event) => setAddress(event.target.value)}
          placeholder="Av. San Juan 2901, C1235 Cdad. Autónoma…"
          className={FIELD_CLASS}
        />
      </label>
      {errorText(errors.address)}

      <div className="mt-5 grid grid-cols-2 gap-x-4">
        <label className="block">
          <span className="text-st-muted text-lg font-bold">LAT.</span>
          <input
            type="text"
            inputMode="decimal"
            value={latitude}
            onChange={(event) => setLatitude(event.target.value)}
            placeholder="-34.603754"
            className={FIELD_CLASS}
          />
          {errorText(errors.latitude)}
        </label>

        <label className="block">
          <span className="text-st-muted text-lg font-bold">LON.</span>
          <input
            type="text"
            inputMode="decimal"
            value={longitude}
            onChange={(event) => setLongitude(event.target.value)}
            placeholder="-58.381659"
            className={FIELD_CLASS}
          />
          {errorText(errors.longitude)}
        </label>
      </div>

      {/* Opcional: sin nodo la estación existe igual, pero no puede ofrecer carga rápida (RF15). */}
      <label className="mt-5 block">
        <span className="text-st-muted text-lg font-bold">GRID NODE</span>
        <input
          type="text"
          value={nodeId}
          onChange={(event) => setNodeId(normalizeGridNode(event.target.value))}
          placeholder="CABA-CENTRO"
          maxLength={40}
          autoCapitalize="characters"
          spellCheck={false}
          className={FIELD_CLASS}
        />
      </label>
      {errors.nodeId ? (
        errorText(errors.nodeId)
      ) : (
        <span className="text-st-muted mt-1 block px-4 text-xs">
          Opcional. Lo indica la distribuidora en el contrato de conexión. Sin nodo, la estación no
          puede ofrecer carga rápida.
        </span>
      )}

      {/* El cobro de la estación: la seña para reservar y la hora pico. Los precios del kWh son del ADMIN. */}
      <label className="mt-5 block">
        <span className="text-st-muted text-lg font-bold">DEPOSIT</span>
        <input
          type="text"
          inputMode="decimal"
          value={deposit}
          onChange={(event) => setDepositText(event.target.value)}
          placeholder={loadedPricing === null ? 'Cargando…' : '0'}
          className={FIELD_CLASS}
        />
      </label>
      {errors.deposit ? (
        errorText(errors.deposit)
      ) : (
        <span className="text-st-muted mt-1 block px-4 text-xs">
          Seña para reservar, en pesos. Con 0, la estación no pide seña.
        </span>
      )}

      <label className="mt-5 flex cursor-pointer items-start gap-3 px-1">
        <input
          type="checkbox"
          checked={peak}
          onChange={(event) => setPeakChoice(event.target.checked)}
          className="accent-st-accent mt-1 h-4 w-4 shrink-0 cursor-pointer"
        />
        <span>
          <span className="text-st-muted block text-lg font-bold">PEAK PRICING</span>
          <span className="text-st-muted block text-xs">
            Cobrar tarifa de hora pico
            {rates.status === 'ready' ? ` (${describePeak(rates.value)})` : ''} a las cargas que
            empiezan en esa franja.
          </span>
        </span>
      </label>

      <ConnectorEditor value={connectors} onChange={setConnectors} />
      {errorText(errors.connectors)}

      <ImagePicker value={photoUrls} onChange={setPhotoUrls} />

      {failure && <p className="text-st-offline mt-4 text-sm">{failure}</p>}

      <button
        type="submit"
        disabled={saving}
        className="brand-fill text-on-primary focus-visible:outline-st-accent mt-7 w-full rounded-full py-4 text-base font-extrabold tracking-wide disabled:opacity-60"
      >
        {saving ? 'SAVING…' : station ? 'SAVE CHANGES' : 'ADD'}
      </button>

      {/* La baja es lógica: la estación deja de listarse, no se borra. */}
      {station && onDelete && (
        <button
          type="button"
          onClick={onDelete}
          className="text-st-offline hover:text-st-offline/80 focus-visible:outline-st-accent mt-4 w-full text-xs font-bold tracking-wide underline underline-offset-4 focus-visible:outline-2"
        >
          DEACTIVATE STATION
        </button>
      )}
    </form>
  )
}
