import { type ReactNode, useEffect, useState } from 'react'

import type { Role } from '@/features/auth/types'
import {
  fetchNetworkRates,
  fetchStationPricing,
  saveNetworkRates,
  saveStationPricing,
} from '@/features/pricing/data/pricingRepository'
import type { NetworkRates } from '@/features/pricing/types'
import { CONNECTOR_TYPE_LABEL, CONNECTOR_TYPES } from '@/features/terminals/format'
import { normalizeGridNode, toGridNodeId, validateGridNode } from '@/features/terminals/gridNode'
import { BoltIcon, ClockIcon, EditIcon, PlugIcon, TrashIcon } from '@/features/navigation/icons'
import AdminTabs from './components/AdminNav'
import { ADMIN_SECTIONS, type AdminSection } from './components/sections'
import AdminOverview from './components/AdminOverview'
import PricingInsights from './components/PricingInsights'
import PasswordConfirmDialog from './components/PasswordConfirmDialog'
import SortableHeader from './components/SortableHeader'
import {
  type ConnectorSort,
  type ConnectorSortKey,
  DEFAULT_CONNECTOR_SORT,
  nextConnectorSort,
  sortConnectors,
} from './connectorSort'
import {
  activateUser,
  addConnector,
  changeConnectorStatus,
  configureConnector,
  createStation,
  deactivateStation,
  deactivateUser,
  deleteConnector,
  fetchAdminDashboard,
  fetchConnectors,
  fetchStations,
  fetchUsers,
  updateStation,
  updateUserRole,
} from './data/adminRepository'
import { STALE_VERSION_MESSAGE, isStaleVersion } from '@/lib/entityTag'
import { adminErrorMessage, confirmationErrorMessage } from './errorMessage'
import type { AdminDashboardMetrics, ConnectorItem, StationItem, UserItem } from './types'
import {
  ACTION_DANGER,
  ACTION_NEUTRAL,
  ACTION_OK,
  BUTTON_GHOST,
  BUTTON_PRIMARY,
  CONNECTOR_TYPE_TEXT,
  FIELD,
  TONE_SELECT_TEXT,
  type Tone,
  riseDelay,
} from './components/styles'
import {
  EmptyState,
  Notice,
  Panel,
  SearchInput,
  SectionHeader,
  StatusText,
  BareSelect,
} from './components/ui'

/** La baja o el cambio de rol que espera la contraseña del ADMIN en el diálogo. */
type PendingUserAction =
  { kind: 'deactivate'; user: UserItem } | { kind: 'role'; user: UserItem; role: Role }

export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<AdminSection>('dashboard')

  // Dashboard State
  const [metrics, setMetrics] = useState<AdminDashboardMetrics | null>(null)
  const [loadingMetrics, setLoadingMetrics] = useState(true)

  // Users State
  const [users, setUsers] = useState<UserItem[]>([])
  const [selectedRole, setSelectedRole] = useState<Role | 'ALL'>('ALL')
  const [searchUser, setSearchUser] = useState('')
  const [loadingUsers, setLoadingUsers] = useState(false)
  const [deactivatingUserId, setDeactivatingUserId] = useState<number | null>(null)
  const [pendingUserAction, setPendingUserAction] = useState<PendingUserAction | null>(null)
  const [confirmingUserAction, setConfirmingUserAction] = useState(false)
  const [confirmError, setConfirmError] = useState<string | null>(null)

  // Stations State
  const [stations, setStations] = useState<StationItem[]>([])
  const [searchStation, setSearchStation] = useState('')
  const [loadingStations, setLoadingStations] = useState(false)
  const [deactivatingStationId, setDeactivatingStationId] = useState<number | null>(null)

  // Station Form Modal State
  const [stationModalOpen, setStationModalOpen] = useState<'create' | 'edit' | null>(null)
  const [editingStation, setEditingStation] = useState<StationItem | null>(null)
  const [stationForm, setStationForm] = useState({
    name: '',
    address: '',
    latitude: -34.6037,
    longitude: -58.3816,
    // Texto mientras se edita; vacío es "sin nodo" y viaja como null (toGridNodeId).
    nodeId: '',
  })
  const [stationNodeError, setStationNodeError] = useState<string | null>(null)
  const [submittingStation, setSubmittingStation] = useState(false)

  // Connector Form Modal State
  const [connectorModalOpen, setConnectorModalOpen] = useState<'create' | 'edit' | null>(null)
  const [targetStationId, setTargetStationId] = useState<number | null>(null)
  const [editingConnector, setEditingConnector] = useState<ConnectorItem | null>(null)
  const [connectorForm, setConnectorForm] = useState({
    connectorType: 'CCS2',
    maxPowerKw: 50,
  })
  const [submittingConnector, setSubmittingConnector] = useState(false)

  // Connectors List State
  const [connectors, setConnectors] = useState<ConnectorItem[]>([])
  const [loadingConnectors, setLoadingConnectors] = useState(false)
  const [searchConnector, setSearchConnector] = useState('')
  const [connectorStationFilter, setConnectorStationFilter] = useState<number | 'ALL'>('ALL')
  const [connectorSort, setConnectorSort] = useState<ConnectorSort>(DEFAULT_CONNECTOR_SORT)
  const [updatingConnectorStatusId, setUpdatingConnectorStatusId] = useState<number | null>(null)

  // Precios de la red (Manuel, 31/10: los fija solo el ADMIN, para todas las estaciones)
  const [networkRates, setNetworkRates] = useState<NetworkRates | null>(null)
  const [ratesForm, setRatesForm] = useState({ normal: '', fast: '', penalty: '' })
  const [pricingSuccessMsg, setPricingSuccessMsg] = useState<string | null>(null)
  const [pricingErrorMsg, setPricingErrorMsg] = useState<string | null>(null)
  const [loadingPricing, setLoadingPricing] = useState(false)

  // Seña y hora pico de la estación del formulario: se guardan aparte, en Tarificación
  const [stationPricingForm, setStationPricingForm] = useState({ deposit: '0', peak: true })
  const [stationPricingLoaded, setStationPricingLoaded] = useState(true)
  const [stationDepositError, setStationDepositError] = useState<string | null>(null)
  /*
   * El 412 de una estación que otro guardó mientras el formulario estaba abierto (QA-30). Va adentro
   * del formulario y no en el aviso de la página, que queda tapado por el fondo del modal.
   */
  const [stationStaleError, setStationStaleError] = useState<string | null>(null)

  // Global Action Feedback
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const loadMetrics = async () => {
    setLoadingMetrics(true)
    try {
      const data = await fetchAdminDashboard()
      setMetrics(data)
    } catch (err) {
      console.error(err)
    } finally {
      setLoadingMetrics(false)
    }
  }

  /*
    Las tres cargas aceptan `silent`: la de cuando se entra a la sección muestra "Cargando…"; la de
    después de una acción —cambiar un rol, el estado de un conector, dar de baja— no. Antes la
    lista se vaciaba y se volvía a armar después de cada cambio, y se veía como si la página
    entera se recargara, con el scroll de vuelta arriba (pedido del 27/11).
  */
  const loadUsers = async (silent = false) => {
    if (!silent) setLoadingUsers(true)
    try {
      const roleQuery = selectedRole === 'ALL' ? undefined : selectedRole
      const data = await fetchUsers(roleQuery)
      setUsers(data)
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No pudimos traer los usuarios.'))
    } finally {
      if (!silent) setLoadingUsers(false)
    }
  }

  const loadStations = async (silent = false) => {
    if (!silent) setLoadingStations(true)
    try {
      const data = await fetchStations()
      setStations(data)
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No pudimos traer las estaciones.'))
    } finally {
      if (!silent) setLoadingStations(false)
    }
  }

  const loadConnectors = async (silent = false) => {
    if (!silent) setLoadingConnectors(true)
    try {
      const stationId = connectorStationFilter === 'ALL' ? undefined : connectorStationFilter
      const data = await fetchConnectors(stationId)
      setConnectors(data)
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No pudimos traer los conectores.'))
    } finally {
      if (!silent) setLoadingConnectors(false)
    }
  }

  const loadRates = async () => {
    setLoadingPricing(true)
    setPricingErrorMsg(null)
    try {
      const rates = await fetchNetworkRates()
      setNetworkRates(rates)
      setRatesForm({
        normal: String(rates.normalKwhRate),
        fast: String(rates.fastKwhRate),
        penalty: String(rates.excessPenaltyPerMin),
      })
    } catch {
      setPricingErrorMsg('No pudimos traer los precios. Probá de nuevo en un momento.')
    } finally {
      setLoadingPricing(false)
    }
  }

  useEffect(() => {
    loadMetrics()
  }, [])

  useEffect(() => {
    if (activeTab === 'users') loadUsers()
    if (activeTab === 'stations') loadStations()
    if (activeTab === 'connectors') {
      loadStations()
      loadConnectors()
    }
    if (activeTab === 'pricing') loadRates()
  }, [activeTab, selectedRole, connectorStationFilter])

  /*
   * Cambiar el rol y dar de baja abren el diálogo de la contraseña; la llamada sale al confirmar
   * (Manuel, 02/11). A una cuenta ADMIN no se llega: sus controles están deshabilitados, y si alguien
   * llama a la API igual, el backend la rechaza con ADMIN_PROTECTED.
   */
  const handleUpdateRole = (user: UserItem, role: Role) => {
    if (role === user.role) return
    setConfirmError(null)
    setPendingUserAction({ kind: 'role', user, role })
  }

  const handleDeactivateUser = (user: UserItem) => {
    setConfirmError(null)
    setPendingUserAction({ kind: 'deactivate', user })
  }

  const cancelUserAction = () => {
    setPendingUserAction(null)
    setConfirmError(null)
  }

  const confirmUserAction = async (password: string) => {
    if (pendingUserAction === null) return
    const action = pendingUserAction
    setConfirmingUserAction(true)
    setConfirmError(null)
    setActionError(null)
    setActionSuccess(null)
    try {
      if (action.kind === 'role') {
        await updateUserRole(action.user.id, action.role, password)
        setActionSuccess(`${action.user.fullName} ahora tiene el rol ${action.role}.`)
      } else {
        await deactivateUser(action.user.id, password)
        setActionSuccess(`${action.user.fullName} fue dado de baja.`)
      }
      setPendingUserAction(null)
      await loadUsers(true)
      await loadMetrics()
    } catch (err) {
      // La contraseña equivocada o el bloqueo se corrigen ahí mismo: el diálogo sigue abierto.
      const inline = confirmationErrorMessage(err)
      if (inline !== null) {
        setConfirmError(inline)
        return
      }
      setPendingUserAction(null)
      setActionError(
        adminErrorMessage(
          err,
          action.kind === 'role'
            ? 'No se pudo actualizar el rol.'
            : 'No se pudo dar de baja al usuario.',
        ),
      )
    } finally {
      setConfirmingUserAction(false)
    }
  }

  const handleActivateUser = async (userId: number) => {
    if (!confirm(`¿Confirma reactivar al usuario con ID ${userId}?`)) return
    setDeactivatingUserId(userId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await activateUser(userId)
      setActionSuccess(`El usuario ${userId} fue reactivado exitosamente.`)
      await loadUsers(true)
      await loadMetrics()
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No se pudo reactivar al usuario.'))
    } finally {
      setDeactivatingUserId(null)
    }
  }

  // Deactivate Station
  const handleDeactivateStation = async (stationId: number) => {
    if (!confirm(`¿Confirma la baja de la estación de carga ${stationId}?`)) return
    setDeactivatingStationId(stationId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await deactivateStation(stationId)
      setActionSuccess(`La estación ${stationId} fue dada de baja exitosamente.`)
      await loadStations(true)
      await loadMetrics()
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No se pudo dar de baja la estación.'))
    } finally {
      setDeactivatingStationId(null)
    }
  }

  // Open Station Modal for Create / Edit
  const openCreateStationModal = () => {
    setEditingStation(null)
    setStationForm({ name: '', address: '', latitude: -34.6037, longitude: -58.3816, nodeId: '' })
    setStationNodeError(null)
    // Una estación nueva arranca sin seña y con hora pico, como una sin configurar en el backend.
    setStationPricingForm({ deposit: '0', peak: true })
    setStationPricingLoaded(true)
    setStationDepositError(null)
    setStationStaleError(null)
    setStationModalOpen('create')
  }

  const openEditStationModal = (station: StationItem) => {
    setEditingStation(station)
    setStationForm({
      name: station.name,
      address: station.address,
      latitude: station.latitude,
      longitude: station.longitude,
      nodeId: station.nodeId ?? '',
    })
    setStationNodeError(null)
    setStationDepositError(null)
    setStationStaleError(null)
    setStationPricingLoaded(false)
    setStationModalOpen('edit')
    fetchStationPricing(station.id).then(
      (pricing) => {
        setStationPricingForm({
          deposit: String(pricing.depositAmount),
          peak: pricing.peakPricingEnabled,
        })
        setStationPricingLoaded(true)
      },
      () => setStationDepositError('No pudimos traer la seña de la estación.'),
    )
  }

  const handleStationSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const nodeError = validateGridNode(stationForm.nodeId)
    setStationNodeError(nodeError)
    const deposit = Number(stationPricingForm.deposit.replace(',', '.'))
    const depositError =
      stationPricingForm.deposit.trim() === '' || !Number.isFinite(deposit) || deposit < 0
        ? 'La seña tiene que ser un monto en pesos, de 0 en adelante.'
        : null
    setStationDepositError(depositError)
    if (nodeError || depositError) return
    const pricing = { depositAmount: deposit, peakPricingEnabled: stationPricingForm.peak }

    const payload = { ...stationForm, nodeId: toGridNodeId(stationForm.nodeId) }
    setSubmittingStation(true)
    setActionError(null)
    setActionSuccess(null)
    setStationStaleError(null)
    try {
      if (stationModalOpen === 'create') {
        const created = await createStation(payload)
        try {
          await saveStationPricing(created.id, pricing)
        } catch {
          /*
           * DEF-28: la estación ya existe y la seña no se guardó. El formulario pasa a editar la
           * estación recién creada, con lo que el operador había cargado: guardar de nuevo la edita y
           * no la crea otra vez. Lo mismo que hace /stations.
           */
          setEditingStation(created)
          setStationModalOpen('edit')
          setStationDepositError(
            'La estación se guardó, pero no pudimos guardar la seña ni la hora pico. Revisalas y guardá de nuevo.',
          )
          await loadStations(true)
          return
        }
        setActionSuccess(`¡"${created.name}" creada exitosamente con ID ${created.id}!`)
      } else if (stationModalOpen === 'edit' && editingStation) {
        const updated = await updateStation(editingStation.id, payload, editingStation.version)
        /*
         * Con la versión nueva: si la seña falla después, reintentar desde el mismo formulario tiene
         * que mandar la versión que acaba de quedar guardada, o daría 412 contra sí mismo (QA-30).
         */
        setEditingStation(updated)
        // Si no se pudo leer la seña, no se pisa con la que muestra el formulario por omisión.
        if (stationPricingLoaded) await saveStationPricing(updated.id, pricing)
        setActionSuccess(`¡Estación ${updated.id} "${updated.name}" actualizada correctamente!`)
      }
      setStationModalOpen(null)
      await loadStations(true)
      await loadMetrics()
    } catch (err) {
      if (isStaleVersion(err)) {
        setStationStaleError(STALE_VERSION_MESSAGE)
      } else {
        setActionError(adminErrorMessage(err, 'No se pudo guardar la estación.'))
      }
    } finally {
      setSubmittingStation(false)
    }
  }

  // Open Connector Modal for Create / Edit
  const openAddConnectorModal = (stationId: number) => {
    setTargetStationId(stationId)
    setEditingConnector(null)
    setConnectorForm({ connectorType: 'CCS2', maxPowerKw: 50 })
    setConnectorModalOpen('create')
  }

  const openEditConnectorModal = (connector: ConnectorItem) => {
    setEditingConnector(connector)
    setConnectorForm({ connectorType: connector.connectorType, maxPowerKw: connector.maxPowerKw })
    setConnectorModalOpen('edit')
  }

  const handleConnectorSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmittingConnector(true)
    setActionError(null)
    setActionSuccess(null)
    try {
      if (connectorModalOpen === 'create' && targetStationId) {
        const created = await addConnector(targetStationId, connectorForm)
        setActionSuccess(
          `¡Conector ${created.id} (${created.connectorType}) agregado a la estación ${targetStationId}!`,
        )
      } else if (connectorModalOpen === 'edit' && editingConnector) {
        const updated = await configureConnector(editingConnector.id, connectorForm)
        setActionSuccess(`¡Conector ${updated.id} configurado exitosamente!`)
      }
      setConnectorModalOpen(null)
      await loadStations(true)
      await loadConnectors(true)
      await loadMetrics()
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No se pudo guardar el conector.'))
    } finally {
      setSubmittingConnector(false)
    }
  }

  // Change Operational Status of Connector
  const handleChangeConnectorStatus = async (connectorId: number, status: string) => {
    /* El estado nuevo se ve al instante; si el backend lo rechaza, la recarga lo vuelve atrás. */
    setConnectors((current) =>
      current.map((c) => (c.id === connectorId ? { ...c, operationalStatus: status } : c)),
    )
    setUpdatingConnectorStatusId(connectorId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await changeConnectorStatus(connectorId, status)
      setActionSuccess(`Estado del conector ${connectorId} cambiado a ${status}.`)
      await loadConnectors(true)
      await loadStations(true)
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No se pudo cambiar el estado del conector.'))
    } finally {
      setUpdatingConnectorStatusId(null)
    }
  }

  // Delete Connector
  const handleDeleteConnector = async (connectorId: number) => {
    if (!confirm(`¿Confirma eliminar el conector ${connectorId}?`)) return
    setActionError(null)
    setActionSuccess(null)
    try {
      await deleteConnector(connectorId)
      setActionSuccess(`Conector ${connectorId} eliminado exitosamente.`)
      await loadConnectors(true)
      await loadStations(true)
      await loadMetrics()
    } catch (err) {
      setActionError(adminErrorMessage(err, 'No se pudo eliminar el conector.'))
    }
  }

  // Precios de la red
  const handleSaveRates = async (e: React.FormEvent) => {
    e.preventDefault()
    const normal = Number(ratesForm.normal.replace(',', '.'))
    const fast = Number(ratesForm.fast.replace(',', '.'))
    const penalty = Number(ratesForm.penalty.replace(',', '.'))
    if (!(normal > 0) || !(fast > 0) || !(penalty >= 0)) {
      setPricingErrorMsg(
        'Los precios del kWh tienen que ser mayores a cero, y la multa, de 0 en adelante.',
      )
      return
    }
    setLoadingPricing(true)
    setPricingSuccessMsg(null)
    setPricingErrorMsg(null)
    try {
      const rates = await saveNetworkRates({
        normalKwhRate: normal,
        fastKwhRate: fast,
        excessPenaltyPerMin: penalty,
      })
      setNetworkRates(rates)
      setPricingSuccessMsg('Precios guardados. Valen desde ahora para todas las estaciones.')
    } catch {
      setPricingErrorMsg('No pudimos guardar los precios. Probá de nuevo en un momento.')
    } finally {
      setLoadingPricing(false)
    }
  }

  const filteredUsers = users.filter((u) => {
    if (!searchUser.trim()) return true
    const q = searchUser.toLowerCase()
    return u.email.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q)
  })

  const filteredStations = stations.filter((s) => {
    if (!searchStation.trim()) return true
    const q = searchStation.toLowerCase()
    return s.name.toLowerCase().includes(q) || s.address.toLowerCase().includes(q)
  })

  // El orden se aplica sobre lo que deja el buscador.
  const filteredConnectors = sortConnectors(
    connectors.filter((c) => {
      if (!searchConnector.trim()) return true
      const q = searchConnector.toLowerCase()
      return (
        c.id.toString().includes(q) ||
        c.connectorType.toLowerCase().includes(q) ||
        c.operationalStatus.toLowerCase().includes(q) ||
        (c.stationId && c.stationId.toString().includes(q))
      )
    }),
    connectorSort,
  )

  const connectorHeader = (key: ConnectorSortKey, label: string) => (
    <SortableHeader
      direction={connectorSort.key === key ? connectorSort.direction : null}
      onSort={() => setConnectorSort((current) => nextConnectorSort(current, key))}
    >
      {label}
    </SortableHeader>
  )

  /* Lo escrito en los precios difiere de lo guardado: habilita guardar y descartar. */
  const ratesDirty =
    networkRates !== null &&
    (parseRate(ratesForm.normal) !== networkRates.normalKwhRate ||
      parseRate(ratesForm.fast) !== networkRates.fastKwhRate ||
      parseRate(ratesForm.penalty) !== networkRates.excessPenaltyPerMin)

  const sectionTitle = ADMIN_SECTIONS.find((s) => s.key === activeTab)?.label ?? 'Tablero'

  return (
    /*
      Scrollea adentro de su propia caja, como la portada y como contacto, y no empujando a la
      ventana entera. Antes era la excepción: el contenido desbordaba el `<main>` y lo que
      scrolleaba era el documento, así que las filas de la tabla se metían detrás de la franja de
      navegación —que es transparente— y se leían mezcladas con los rótulos de las secciones. Con
      la caja propia, el recorte lo hace el `<main>` y arriba hay un borde parejo que el velo
      puede desvanecer. Ver `TopFade`.
    */
    <div className="flex min-h-0 flex-1 flex-col">
      {pendingUserAction !== null && (
        <PasswordConfirmDialog
          open
          title={pendingUserAction?.kind === 'role' ? 'Cambiar el rol' : 'Dar de baja'}
          subject={
            pendingUserAction === null
              ? ''
              : `${pendingUserAction.user.fullName} · ${pendingUserAction.user.email} · ${pendingUserAction.user.role}`
          }
          consequence={
            pendingUserAction === null
              ? ''
              : pendingUserAction.kind === 'deactivate'
                ? 'No va a poder entrar hasta que un administrador lo reactive.'
                : pendingUserAction.role === 'ADMIN'
                  ? 'Pasa a ser administrador. Después no se le va a poder sacar el rol ni dar de baja desde acá.'
                  : `Pasa de ${pendingUserAction.user.role} a ${pendingUserAction.role}.`
          }
          confirmLabel={pendingUserAction?.kind === 'role' ? 'Cambiar el rol' : 'Dar de baja'}
          busyLabel={pendingUserAction?.kind === 'role' ? 'Cambiando…' : 'Dando de baja…'}
          busy={confirmingUserAction}
          error={confirmError}
          onConfirm={confirmUserAction}
          onCancel={cancelUserAction}
        />
      )}

      {/*
        A lo ancho de toda la pantalla, sin el tope de 1280 px de antes: el tablero se adapta a la
        ventana en vez de quedar centrado con márgenes vacíos a los costados.

        Arriba, las pestañas de las secciones; abajo, el contenido, que es lo único que scrollea. El
        tablero, en cambio, mide exactamente lo que queda (`md:h-full`) y no scrollea: por eso el
        espacio de abajo lo pone la columna y no la caja que scrollea, o el tablero se pasaría por
        ese margen. En el celular, `pb-36` es el lugar de la barra flotante de navegación.
      */}
      <div className="flex min-h-0 w-full flex-1 flex-col gap-3 px-4 pt-6 md:px-6 md:pt-2">
        <h1 className="sr-only">Backoffice · {sectionTitle}</h1>
        <AdminTabs active={activeTab} onChange={setActiveTab} />

        {actionSuccess && <Notice tone="success">{actionSuccess}</Notice>}
        {actionError && <Notice tone="error">{actionError}</Notice>}

        {/*
          La caja que scrollea recorta todo lo que se le sale, también la sombra de los paneles: con
          el borde justo contra el panel, la sombra quedaba cortada en una línea recta arriba y abajo.
          Por eso tiene aire adentro (`pt-4`, `md:pb-6`) que se le devuelve afuera con el margen
          negativo de arriba: el contenido queda en el mismo lugar y la sombra tiene dónde caer. Al
          tablero no le cambia la cuenta: su `h-full` se mide contra el contenido, sin ese aire.

          Y ese mismo aire de arriba es un desvanecido (`mask-image`): lo que sube scrolleando se
          funde antes de llegar a las pestañas en vez de pegarse a ellas. El contenido quieto no se
          toca, porque arranca justo donde la máscara ya es opaca.
        */}
        <div className="-mx-4 -mt-4 min-h-0 flex-1 overflow-y-auto px-4 pt-4 pb-36 [mask-image:linear-gradient(to_bottom,transparent,black_1rem)] md:-mx-6 md:px-6 md:pb-6">
          {/* TABLERO */}
          {activeTab === 'dashboard' &&
            (loadingMetrics && metrics === null ? (
              <EmptyState busy>Cargando el tablero…</EmptyState>
            ) : metrics ? (
              <AdminOverview
                metrics={metrics}
                onNavigate={setActiveTab}
                onOpenStation={(name) => {
                  setSearchStation(name)
                  setActiveTab('stations')
                }}
              />
            ) : (
              <EmptyState>No pudimos traer las métricas del sistema.</EmptyState>
            ))}

          {/*
            USUARIOS, ESTACIONES y CONECTORES: el recuadro mide lo que deja la ventana
            (`md:h-full`) y no scrollea; lo que scrollea es la lista de adentro (pedido del 27/11),
            así el título, el buscador y los filtros quedan siempre a la vista. Cada lista tiene
            aire adentro devuelto con margen negativo: sin él, el borde del scroll cortaba la sombra
            de las filas y, en Estaciones, la tarjeta de un borde al agrandarse con el mouse.
            En el celular, el recuadro tiene un alto máximo —lo que queda entre las pestañas y la
            barra de abajo— pero no se estira si la lista es corta; adentro scrollea igual la lista.

            Las listas llevan `relative` y no es decoración: los rótulos `sr-only` de cada fila son
            absolutos, y sin un ancestro posicionado adentro del scroll se ubicaban contra el
            recuadro y se escapaban de la lista, estirando la página con su posición de la fila 40.
          */}
          {/* USUARIOS */}
          {activeTab === 'users' && (
            <Panel
              labelledBy="admin-users-title"
              className="flex flex-col gap-5 max-md:max-h-[calc(100svh-14.5rem)] md:h-full md:min-h-[24rem]"
            >
              <SectionHeader
                id="admin-users-title"
                title="Usuarios"
                description="Cambiá el rol de una cuenta o dala de baja. Las dos piden tu contraseña."
                actions={
                  <>
                    <SearchInput
                      label="Buscar usuario por nombre o correo"
                      value={searchUser}
                      onChange={setSearchUser}
                      placeholder="Buscar por nombre o correo"
                    />
                    <label className="w-full sm:w-auto">
                      <span className="sr-only">Filtrar por rol</span>
                      <select
                        value={selectedRole}
                        onChange={(e) => setSelectedRole(e.target.value as Role | 'ALL')}
                        className={`${FIELD} sm:w-48`}
                      >
                        <option value="ALL">Todos los roles</option>
                        <option value="CONDUCTOR">Conductores</option>
                        <option value="CPO">Operadores (CPO)</option>
                        <option value="ADMIN">Administradores</option>
                      </select>
                    </label>
                  </>
                }
              />

              {loadingUsers ? (
                <EmptyState busy>Cargando usuarios…</EmptyState>
              ) : filteredUsers.length === 0 ? (
                <EmptyState>Ningún usuario coincide con la búsqueda.</EmptyState>
              ) : (
                /*
                  La tabla scrollea de costado adentro del panel en el celular: las cinco columnas
                  no entran en 360 y apilarlas escondería la comparación entre filas, que es para lo
                  que sirve una tabla.
                */
                <div className="slim-scrollbar relative -mx-2 -mb-2 min-h-0 flex-1 overflow-y-auto px-2 pb-2 md:overflow-auto">
                  <table className="admin-table w-full border-separate border-spacing-y-2 text-left text-sm md:min-w-[46rem]">
                    <thead className="text-text-muted text-xs uppercase">
                      <tr>
                        <th className="px-4 py-1 font-semibold">Usuario</th>
                        <th className="px-4 py-1 font-semibold">Rol</th>
                        <th className="px-4 py-1 font-semibold">Estado</th>
                        <th className="px-4 py-1 font-semibold">Alta</th>
                        <th className="px-4 py-1 text-right font-semibold">Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredUsers.map((user) => (
                        <tr key={user.id} className="admin-row">
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-3">
                              <span
                                aria-hidden="true"
                                className={`grid h-9 w-9 shrink-0 place-items-center rounded-full text-xs font-bold ${
                                  user.active
                                    ? 'brand-fill text-on-primary'
                                    : 'bg-text-muted/20 text-text'
                                }`}
                              >
                                {initialsOf(user.fullName)}
                              </span>
                              <div className="min-w-0">
                                <p className="text-text truncate font-semibold">
                                  {user.fullName}
                                  <span className="text-text-muted ml-2 text-xs font-normal">
                                    #{user.id}
                                  </span>
                                </p>
                                <p className="text-text-muted truncate text-xs">{user.email}</p>
                              </div>
                            </div>
                          </td>
                          <td data-label="Rol" className="px-4 py-3">
                            <label>
                              <span className="sr-only">Rol de {user.fullName}</span>
                              <BareSelect
                                tone="text-text"
                                value={user.role}
                                disabled={user.role === 'ADMIN' || !user.active}
                                title={
                                  user.role === 'ADMIN'
                                    ? 'Un administrador cambia de rol desde la base de datos.'
                                    : undefined
                                }
                                onChange={(e) => handleUpdateRole(user, e.target.value as Role)}
                              >
                                <option value="CONDUCTOR">CONDUCTOR</option>
                                <option value="CPO">CPO</option>
                                <option value="ADMIN">ADMIN</option>
                              </BareSelect>
                            </label>
                          </td>
                          <td data-label="Estado" className="px-4 py-3">
                            {user.active ? (
                              <StatusText tone="ok">Activo</StatusText>
                            ) : (
                              <StatusText tone="off">Dado de baja</StatusText>
                            )}
                          </td>
                          <td
                            data-label="Alta"
                            className="text-text-muted px-4 py-3 text-xs whitespace-nowrap"
                          >
                            <span>{formatJoined(user.createdAt)}</span>
                          </td>
                          <td data-label="Acciones" className="px-4 py-3 text-right">
                            {/* Un ADMIN activo no tiene acciones: se da de baja por la base (02/11). */}
                            {user.active && user.role === 'ADMIN' ? (
                              <span className="text-text-muted text-xs font-bold tracking-wide uppercase">
                                Protegido
                              </span>
                            ) : user.active ? (
                              <button
                                type="button"
                                onClick={() => handleDeactivateUser(user)}
                                className={ACTION_DANGER}
                              >
                                Dar de baja
                              </button>
                            ) : (
                              <button
                                type="button"
                                disabled={deactivatingUserId === user.id}
                                onClick={() => handleActivateUser(user.id)}
                                className={ACTION_OK}
                              >
                                {deactivatingUserId === user.id ? 'Reactivando…' : 'Reactivar'}
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Panel>
          )}

          {/* ESTACIONES */}
          {activeTab === 'stations' && (
            <Panel
              labelledBy="admin-stations-title"
              className="flex flex-col gap-5 max-md:max-h-[calc(100svh-14.5rem)] md:h-full md:min-h-[24rem]"
            >
              <SectionHeader
                id="admin-stations-title"
                title="Estaciones"
                description="Creá estaciones en cualquier punto de la red, editá sus datos o dalas de baja."
                actions={
                  <>
                    <SearchInput
                      label="Buscar estación por nombre o dirección"
                      value={searchStation}
                      onChange={setSearchStation}
                      placeholder="Buscar por nombre o dirección"
                    />
                    <button
                      type="button"
                      onClick={openCreateStationModal}
                      className={`${BUTTON_PRIMARY} w-full sm:w-auto`}
                    >
                      <span aria-hidden="true" className="text-lg leading-none">
                        +
                      </span>
                      Crear estación
                    </button>
                  </>
                }
              />

              {loadingStations ? (
                <EmptyState busy>Cargando estaciones…</EmptyState>
              ) : filteredStations.length === 0 ? (
                <EmptyState>
                  {stations.length === 0
                    ? 'Todavía no hay estaciones registradas.'
                    : 'Ninguna estación coincide con la búsqueda.'}
                </EmptyState>
              ) : (
                <ul className="slim-scrollbar relative -m-4 grid min-h-0 flex-1 grid-cols-1 content-start gap-4 overflow-y-auto p-4 md:grid-cols-2 2xl:grid-cols-3">
                  {filteredStations.map((station, index) => (
                    <li
                      key={station.id}
                      className="glass-inset analytics-rise flex flex-col gap-3 rounded-2xl p-4 admin-station-card scale-100 hover:scale-[1.02]"
                      style={riseDelay(Math.min(index, 8) * 40)}
                    >
                      {/*
                        Tarjeta limpia (pedido del 27/11): el nombre, la dirección y un solo renglón
                        de datos sueltos, sin recuadros adentro; abajo, las acciones como texto.
                      */}
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="text-text truncate text-base font-bold">
                            {station.name}
                            <span className="text-text-muted ml-2 text-xs font-semibold">
                              #{station.id}
                            </span>
                          </h3>
                          <p className="text-text-muted truncate text-xs">{station.address}</p>
                        </div>
                        {station.active ? (
                          <StatusText tone="ok">Operativa</StatusText>
                        ) : (
                          <StatusText tone="off">Dada de baja</StatusText>
                        )}
                      </div>

                      <p className="text-text-muted flex flex-wrap justify-between gap-x-4 gap-y-1 text-xs">
                        <span>
                          <span className="text-text font-bold">
                            {station.connectors?.length ?? '–'}
                          </span>{' '}
                          {station.connectors?.length === 1 ? 'conector' : 'conectores'}
                        </span>
                        <span>
                          Nodo{' '}
                          <span className="text-text font-bold">
                            {station.nodeId ?? 'sin nodo'}
                          </span>
                        </span>
                        <span className="tabular-nums">
                          {station.latitude.toFixed(3)}, {station.longitude.toFixed(3)}
                        </span>
                      </p>

                      <div className="border-border/50 mt-auto flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-t pt-2">
                        <button
                          type="button"
                          onClick={() => openAddConnectorModal(station.id)}
                          className={ACTION_OK}
                        >
                          <PlugIcon className="h-4 w-4" />
                          Agregar conector
                        </button>
                        <button
                          type="button"
                          onClick={() => openEditStationModal(station)}
                          className={ACTION_NEUTRAL}
                        >
                          <EditIcon className="h-4 w-4" />
                          Editar
                        </button>
                        {station.active && (
                          <button
                            type="button"
                            disabled={deactivatingStationId === station.id}
                            onClick={() => handleDeactivateStation(station.id)}
                            className={ACTION_DANGER}
                          >
                            {deactivatingStationId === station.id
                              ? 'Dando de baja…'
                              : 'Dar de baja'}
                          </button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          )}

          {/* CONECTORES */}
          {activeTab === 'connectors' && (
            <Panel
              labelledBy="admin-connectors-title"
              className="flex flex-col gap-5 max-md:max-h-[calc(100svh-14.5rem)] md:h-full md:min-h-[24rem]"
            >
              <SectionHeader
                id="admin-connectors-title"
                title="Conectores"
                description="Todos los conectores de la red: su potencia, su estado y a qué estación pertenecen."
                actions={
                  <>
                    <SearchInput
                      label="Buscar conector por número, tipo o estado"
                      value={searchConnector}
                      onChange={setSearchConnector}
                      placeholder="Buscar por número, tipo o estado"
                    />
                    <label className="w-full sm:w-auto">
                      <span className="sr-only">Filtrar por estación</span>
                      <select
                        value={connectorStationFilter}
                        onChange={(e) =>
                          setConnectorStationFilter(
                            e.target.value === 'ALL' ? 'ALL' : Number(e.target.value),
                          )
                        }
                        className={`${FIELD} sm:w-56`}
                      >
                        <option value="ALL">Todas las estaciones</option>
                        {stations.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.id} · {s.name}
                          </option>
                        ))}
                      </select>
                    </label>
                  </>
                }
              />

              {loadingConnectors ? (
                <EmptyState busy>Cargando conectores…</EmptyState>
              ) : filteredConnectors.length === 0 ? (
                <EmptyState>Ningún conector coincide con la búsqueda.</EmptyState>
              ) : (
                <div className="slim-scrollbar relative -mx-2 -mb-2 min-h-0 flex-1 overflow-y-auto px-2 pb-2 md:overflow-auto">
                  <table className="admin-table w-full border-separate border-spacing-y-2 text-left text-sm md:min-w-[46rem]">
                    <thead className="text-text-muted text-xs uppercase">
                      <tr>
                        {connectorHeader('id', 'Conector')}
                        {connectorHeader('station', 'Estación')}
                        {connectorHeader('type', 'Tipo')}
                        {connectorHeader('power', 'Potencia máx.')}
                        {connectorHeader('status', 'Estado')}
                        <th className="px-4 py-1 text-right font-semibold">Acciones</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredConnectors.map((connector) => {
                        const parentStation = stations.find((s) => s.id === connector.stationId)
                        const tone = CONNECTOR_TONE[connector.operationalStatus] ?? 'off'
                        return (
                          <tr key={connector.id} className="admin-row">
                            <td className="text-text px-4 py-3 font-bold tabular-nums">
                              #{connector.id}
                            </td>
                            <td data-label="Estación" className="text-text px-4 py-3 font-semibold">
                              <span className="block max-w-56 truncate">
                                {parentStation?.name ?? `Estación ${connector.stationId}`}
                              </span>
                            </td>
                            <td data-label="Tipo" className="px-4 py-3">
                              <span
                                className={`text-xs font-bold tracking-wide ${
                                  CONNECTOR_TYPE_TEXT[connector.connectorType] ?? 'text-text'
                                }`}
                              >
                                {CONNECTOR_TYPE_LABEL[
                                  connector.connectorType as keyof typeof CONNECTOR_TYPE_LABEL
                                ] ?? connector.connectorType}
                              </span>
                            </td>
                            <td
                              data-label="Potencia máx."
                              className="text-text px-4 py-3 font-bold tabular-nums"
                            >
                              <span>{connector.maxPowerKw} kW</span>
                            </td>
                            <td data-label="Estado" className="px-4 py-3">
                              <label>
                                <span className="sr-only">Estado del conector {connector.id}</span>
                                <BareSelect
                                  tone={TONE_SELECT_TEXT[tone]}
                                  value={connector.operationalStatus}
                                  disabled={updatingConnectorStatusId === connector.id}
                                  onChange={(e) =>
                                    handleChangeConnectorStatus(connector.id, e.target.value)
                                  }
                                >
                                  <option value="AVAILABLE">Disponible</option>
                                  <option value="OCCUPIED">Ocupado</option>
                                  <option value="OUT_OF_SERVICE">Fuera de servicio</option>
                                </BareSelect>
                              </label>
                            </td>
                            {/*
                              Sin rótulo en la tarjeta del celular: editar a la izquierda y borrar a
                              la derecha se explican solos. En la tabla de escritorio van juntos a
                              la derecha, bajo la columna Acciones.
                            */}
                            <td className="px-4 py-3">
                              <div className="flex w-full items-center justify-between gap-1 md:justify-end">
                                <button
                                  type="button"
                                  onClick={() => openEditConnectorModal(connector)}
                                  aria-label={`Editar el conector ${connector.id}`}
                                  className={ACTION_OK}
                                >
                                  <EditIcon className="h-5 w-5" />
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleDeleteConnector(connector.id)}
                                  aria-label={`Eliminar el conector ${connector.id}`}
                                  className={ACTION_DANGER}
                                >
                                  <TrashIcon className="h-5 w-5" />
                                </button>
                              </div>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </Panel>
          )}

          {/* PRECIOS */}
          {activeTab === 'pricing' && (
            <Panel
              labelledBy="admin-pricing-title"
              className="flex flex-col gap-5 max-md:max-h-[calc(100svh-14.5rem)] md:h-full md:min-h-[24rem]"
            >
              <SectionHeader
                id="admin-pricing-title"
                title="Precios de la carga"
                description="Valen para todas las estaciones. La seña y la hora pico las define cada operador en su estación."
              />

              {pricingSuccessMsg && <Notice tone="success">{pricingSuccessMsg}</Notice>}
              {pricingErrorMsg && <Notice tone="error">{pricingErrorMsg}</Notice>}

              <form
                onSubmit={handleSaveRates}
                className="slim-scrollbar relative -m-2 flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto p-2"
              >
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <RateField
                    label="Carga normal"
                    hint={
                      networkRates
                        ? `Hasta ${String(networkRates.fastChargeThresholdKw)} kW`
                        : 'Hasta el umbral de carga rápida'
                    }
                    unit="por kWh"
                    value={ratesForm.normal}
                    saved={networkRates?.normalKwhRate}
                    onChange={(value) => setRatesForm({ ...ratesForm, normal: value })}
                    icon={<PlugIcon className="h-5 w-5" />}
                    delayMs={0}
                  />
                  <RateField
                    label="Carga rápida"
                    hint={
                      networkRates
                        ? `Más de ${String(networkRates.fastChargeThresholdKw)} kW`
                        : 'Sobre el umbral de carga rápida'
                    }
                    unit="por kWh"
                    value={ratesForm.fast}
                    saved={networkRates?.fastKwhRate}
                    onChange={(value) => setRatesForm({ ...ratesForm, fast: value })}
                    icon={<BoltIcon className="h-5 w-5" />}
                    delayMs={60}
                  />
                  <RateField
                    label="Multa por exceso"
                    hint="Por cada minuto que el auto sigue enchufado"
                    unit="por minuto"
                    value={ratesForm.penalty}
                    saved={networkRates?.excessPenaltyPerMin}
                    onChange={(value) => setRatesForm({ ...ratesForm, penalty: value })}
                    icon={<ClockIcon className="h-5 w-5" />}
                    delayMs={120}
                  />
                </div>

                {/*
                  Las reglas del backend, dibujadas, y un simulador que usa lo que está escrito
                  arriba: el ADMIN ve cuánto le sale una carga a alguien antes de guardar.
                */}
                {networkRates && (
                  <PricingInsights
                    draft={{
                      normal: parseRate(ratesForm.normal),
                      fast: parseRate(ratesForm.fast),
                      penalty: parseRate(ratesForm.penalty),
                    }}
                    rates={networkRates}
                  />
                )}

                <div className="flex flex-col-reverse items-stretch justify-end gap-3 sm:flex-row sm:items-center">
                  {ratesDirty && (
                    <p className="text-text-muted text-xs sm:mr-auto">Hay cambios sin guardar.</p>
                  )}
                  {ratesDirty && networkRates && (
                    <button
                      type="button"
                      onClick={() =>
                        setRatesForm({
                          normal: String(networkRates.normalKwhRate),
                          fast: String(networkRates.fastKwhRate),
                          penalty: String(networkRates.excessPenaltyPerMin),
                        })
                      }
                      className={BUTTON_GHOST}
                    >
                      Descartar cambios
                    </button>
                  )}
                  <button
                    type="submit"
                    disabled={loadingPricing || !ratesDirty}
                    className={`${BUTTON_PRIMARY} px-6`}
                  >
                    {loadingPricing ? 'Guardando…' : 'Guardar precios'}
                  </button>
                </div>
              </form>
            </Panel>
          )}
        </div>
      </div>

      {/* MODAL CREAR/EDITAR ESTACIÓN */}
      {stationModalOpen && (
        <Modal
          titleId="admin-station-modal"
          title={stationModalOpen === 'create' ? 'Crear estación' : 'Editar estación'}
          description="Los datos físicos de la estación y lo que cobra por reservar."
          onClose={() => setStationModalOpen(null)}
        >
          <form onSubmit={handleStationSubmit} className="flex flex-col gap-4">
            <LabeledField label="Nombre">
              <input
                type="text"
                required
                placeholder="Ej: Estación Puerto Madero"
                value={stationForm.name}
                onChange={(e) => setStationForm({ ...stationForm, name: e.target.value })}
                className={FIELD}
              />
            </LabeledField>

            <LabeledField label="Dirección">
              <input
                type="text"
                required
                placeholder="Ej: Av. Alicia Moreau de Justo 1000"
                value={stationForm.address}
                onChange={(e) => setStationForm({ ...stationForm, address: e.target.value })}
                className={FIELD}
              />
            </LabeledField>

            <div className="grid grid-cols-2 gap-3">
              <LabeledField label="Latitud">
                <input
                  type="number"
                  step="any"
                  required
                  value={stationForm.latitude}
                  onChange={(e) =>
                    setStationForm({ ...stationForm, latitude: Number(e.target.value) })
                  }
                  className={FIELD}
                />
              </LabeledField>
              <LabeledField label="Longitud">
                <input
                  type="number"
                  step="any"
                  required
                  value={stationForm.longitude}
                  onChange={(e) =>
                    setStationForm({ ...stationForm, longitude: Number(e.target.value) })
                  }
                  className={FIELD}
                />
              </LabeledField>
            </div>

            <LabeledField
              label="Nodo de la distribuidora (opcional)"
              hint={
                stationNodeError ??
                'Lo indica la distribuidora en el contrato de conexión. Sin nodo, la estación no puede ofrecer carga rápida.'
              }
              hintIsError={stationNodeError !== null}
            >
              <input
                type="text"
                placeholder="Ej: CABA-CENTRO"
                maxLength={40}
                autoCapitalize="characters"
                spellCheck={false}
                value={stationForm.nodeId}
                onChange={(e) =>
                  setStationForm({ ...stationForm, nodeId: normalizeGridNode(e.target.value) })
                }
                className={FIELD}
              />
            </LabeledField>

            <LabeledField
              label="Seña para reservar ($)"
              hint={stationDepositError ?? 'Con 0, la estación no pide seña.'}
              hintIsError={stationDepositError !== null}
            >
              <input
                type="text"
                inputMode="decimal"
                disabled={!stationPricingLoaded}
                placeholder={stationPricingLoaded ? '0' : 'Cargando…'}
                value={stationPricingLoaded ? stationPricingForm.deposit : ''}
                onChange={(e) =>
                  setStationPricingForm({ ...stationPricingForm, deposit: e.target.value })
                }
                className={FIELD}
              />
            </LabeledField>

            <label className="text-text flex cursor-pointer items-center gap-2.5 text-sm font-semibold">
              <input
                type="checkbox"
                disabled={!stationPricingLoaded}
                checked={stationPricingForm.peak}
                onChange={(e) =>
                  setStationPricingForm({ ...stationPricingForm, peak: e.target.checked })
                }
                className="accent-primary h-4 w-4"
              />
              Cobrar tarifa de hora pico
            </label>

            {stationStaleError && (
              <p role="alert" className="text-danger text-sm font-medium">
                {stationStaleError}
              </p>
            )}

            <div className="mt-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setStationModalOpen(null)}
                className={BUTTON_GHOST}
              >
                Cancelar
              </button>
              <button type="submit" disabled={submittingStation} className={BUTTON_PRIMARY}>
                {submittingStation ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* MODAL CREAR/EDITAR CONECTOR */}
      {connectorModalOpen && (
        <Modal
          titleId="admin-connector-modal"
          title={
            connectorModalOpen === 'create'
              ? `Agregar conector a la estación ${String(targetStationId)}`
              : `Editar el conector ${String(editingConnector?.id)}`
          }
          description="El tipo de ficha y la potencia máxima que entrega."
          onClose={() => setConnectorModalOpen(null)}
        >
          <form onSubmit={handleConnectorSubmit} className="flex flex-col gap-4">
            <LabeledField label="Tipo de conector">
              <select
                value={connectorForm.connectorType}
                onChange={(e) =>
                  setConnectorForm({ ...connectorForm, connectorType: e.target.value })
                }
                className={FIELD}
              >
                {CONNECTOR_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {CONNECTOR_TYPE_LABEL[t]}
                  </option>
                ))}
              </select>
            </LabeledField>

            <LabeledField label="Potencia máxima (kW)">
              <input
                type="number"
                step="0.1"
                min="1"
                required
                value={connectorForm.maxPowerKw}
                onChange={(e) =>
                  setConnectorForm({ ...connectorForm, maxPowerKw: Number(e.target.value) })
                }
                className={FIELD}
              />
            </LabeledField>

            <div className="mt-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConnectorModalOpen(null)}
                className={BUTTON_GHOST}
              >
                Cancelar
              </button>
              <button type="submit" disabled={submittingConnector} className={BUTTON_PRIMARY}>
                {submittingConnector ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}

/** Las iniciales del nombre, para el redondel de cada fila de usuarios. */
function initialsOf(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean)
  const first = parts[0]?.[0] ?? '?'
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase()
}

/** La fecha de alta, corta: "12 nov 2026". */
function formatJoined(createdAt: string): string {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '–'
  return date.toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' })
}

const CONNECTOR_TONE: Record<string, Tone> = {
  AVAILABLE: 'ok',
  OCCUPIED: 'warn',
  OUT_OF_SERVICE: 'off',
}

/** Un campo con su rótulo arriba y, si hace falta, la ayuda o el error abajo. */
function LabeledField({
  label,
  hint,
  hintIsError = false,
  children,
}: {
  label: string
  hint?: string
  hintIsError?: boolean
  children: ReactNode
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-text-muted text-xs font-semibold">{label}</span>
      {children}
      {hint !== undefined && (
        <span className={`text-xs ${hintIsError ? 'text-danger' : 'text-text-muted'}`}>{hint}</span>
      )}
    </label>
  )
}

/** Un precio de la red como tarjeta: ícono, rótulo, el monto grande y la unidad. */
function RateField({
  label,
  hint,
  unit,
  value,
  saved,
  onChange,
  icon,
  delayMs,
}: {
  label: string
  hint: string
  unit: string
  value: string
  /** Lo guardado, para avisar cuánto valía antes si se cambió. */
  saved?: number
  onChange: (value: string) => void
  icon: ReactNode
  delayMs: number
}) {
  return (
    <label
      className="glass-inset analytics-rise focus-within:border-primary flex flex-col gap-3 rounded-2xl p-4"
      style={riseDelay(delayMs)}
    >
      <span className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="brand-fill text-on-primary grid h-10 w-10 shrink-0 place-items-center rounded-full"
        >
          {icon}
        </span>
        <span className="min-w-0">
          <span className="text-text block text-sm font-bold">{label}</span>
          <span className="text-text-muted block text-xs">{hint}</span>
        </span>
      </span>
      {/*
        Sin recuadro (pedido del 27/11): el monto grande, suelto, con el signo y la unidad a los
        costados. Lo único que lo marca como editable es la raya fina de abajo, que se pone verde
        con el foco, como los campos subrayados del resto de la aplicación.
      */}
      <span className="border-border/60 focus-within:border-primary flex items-baseline gap-2 border-b pb-1 transition-colors">
        <span aria-hidden="true" className="text-text-muted charge-digits text-4xl">
          $
        </span>
        <input
          type="text"
          inputMode="decimal"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="charge-digits text-text min-w-0 flex-1 bg-transparent text-5xl tabular-nums outline-none md:text-6xl"
        />
        <span className="text-text-muted shrink-0 text-sm font-semibold">{unit}</span>
      </span>
      {saved !== undefined && parseRate(value) !== saved && (
        <span className="text-text-muted text-xs">
          Antes: <span className="text-text font-semibold tabular-nums">${String(saved)}</span>
        </span>
      )}
    </label>
  )
}

/**
 * Una ventana sobre el backoffice: el vidrio con cuerpo de las ventanas de la aplicación
 * (`.glass-modal`), centrada sobre un velo. Se cierra con Escape o tocando afuera.
 *
 * Va por encima de la franja de navegación (`z-[1210]`, el mismo piso que el diálogo de la
 * contraseña): con el `z-50` de antes, la franja —que está en 1150— quedaba encima del velo.
 */
function Modal({
  titleId,
  title,
  description,
  onClose,
  children,
}: {
  titleId: string
  title: string
  description: string
  onClose: () => void
  children: ReactNode
}) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-[1210] grid place-items-center p-4">
      <button
        type="button"
        aria-label="Cerrar"
        tabIndex={-1}
        onClick={onClose}
        className="absolute inset-0 h-full w-full cursor-default bg-black/50 backdrop-blur-sm"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="glass-panel glass-modal analytics-rise relative max-h-[90vh] w-full max-w-md overflow-y-auto rounded-3xl p-6"
      >
        <h2 id={titleId} className="text-text text-lg font-bold">
          {title}
        </h2>
        <p className="text-text-muted mt-1 mb-5 text-sm">{description}</p>
        {children}
      </div>
    </div>
  )
}

/** Un precio escrito a mano, con coma o punto, como número; `null` si todavía no es un número. */
function parseRate(text: string): number | null {
  if (text.trim() === '') return null
  const value = Number(text.replace(',', '.'))
  return Number.isFinite(value) ? value : null
}
