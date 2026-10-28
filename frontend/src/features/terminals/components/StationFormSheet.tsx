import { useState, type FormEvent } from 'react'

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
  onSubmit: (input: StationInput) => Promise<void>
  onDelete?: () => void
}

interface FormErrors {
  name?: string
  address?: string
  latitude?: string
  longitude?: string
  nodeId?: string
  connectors?: string
}

const FIELD_CLASS =
  'bg-st-surface-raised text-st-text placeholder:text-st-muted focus:outline-st-accent mt-2 w-full rounded-full px-4 py-2.5 text-sm focus:outline-2'

export default function StationFormSheet({ station, onSubmit, onDelete }: StationFormSheetProps) {
  const [name, setName] = useState(station?.name ?? '')
  const [address, setAddress] = useState(station?.address ?? '')
  const [latitude, setLatitude] = useState(station ? formatCoordinate(station.latitude) : '')
  const [longitude, setLongitude] = useState(station ? formatCoordinate(station.longitude) : '')
  const [nodeId, setNodeId] = useState(station?.nodeId ?? '')
  const [photoUrls, setPhotoUrls] = useState<string[]>(station?.photoUrls ?? [])
  const [connectors, setConnectors] = useState<ConnectorDraft[]>(
    station?.connectors.map((connector) => ({ ...connector })) ?? [],
  )

  const [errors, setErrors] = useState<FormErrors>({})
  const [saving, setSaving] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

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

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const input = validate()
    if (!input) return

    setSaving(true)
    setFailure(null)
    try {
      await onSubmit(input)
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
