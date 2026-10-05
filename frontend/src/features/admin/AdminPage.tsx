import { useEffect, useState } from 'react'

import type { Role } from '@/features/auth/types'
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
  fetchPricingScheme,
  fetchStations,
  fetchUsers,
  savePricingScheme,
  updateStation,
  updateUserRole,
} from './data/adminRepository'
import type {
  AdminDashboardMetrics,
  ConnectorItem,
  PricingSchemeResponse,
  PricingStrategyType,
  StationItem,
  UserItem,
} from './types'

const CONNECTOR_TYPES = ['TYPE_1', 'TYPE_2', 'CCS2', 'CHADEMO', 'TESLA_SUPERCHARGER']

export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<
    'dashboard' | 'users' | 'stations' | 'connectors' | 'pricing'
  >('dashboard')

  // Dashboard State
  const [metrics, setMetrics] = useState<AdminDashboardMetrics | null>(null)
  const [loadingMetrics, setLoadingMetrics] = useState(true)

  // Users State
  const [users, setUsers] = useState<UserItem[]>([])
  const [selectedRole, setSelectedRole] = useState<Role | 'ALL'>('ALL')
  const [searchUser, setSearchUser] = useState('')
  const [loadingUsers, setLoadingUsers] = useState(false)
  const [deactivatingUserId, setDeactivatingUserId] = useState<number | null>(null)
  const [updatingUserRoleId, setUpdatingUserRoleId] = useState<number | null>(null)

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
  })
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
  const [updatingConnectorStatusId, setUpdatingConnectorStatusId] = useState<number | null>(null)

  // Pricing Scheme State
  const [pricingConnectorId, setPricingConnectorId] = useState<number>(1)
  const [pricingScheme, setPricingScheme] = useState<PricingSchemeResponse | null>(null)
  const [strategyType, setStrategyType] = useState<PricingStrategyType>('FLAT_RATE')
  const [kwhRate, setKwhRate] = useState<number>(150)
  const [depositAmount, setDepositAmount] = useState<number>(500)
  const [excessPenaltyPerMin, setExcessPenaltyPerMin] = useState<number>(10)
  const [peakKwhRate, setPeakKwhRate] = useState<number>(250)
  const [pricingSuccessMsg, setPricingSuccessMsg] = useState<string | null>(null)
  const [pricingErrorMsg, setPricingErrorMsg] = useState<string | null>(null)
  const [loadingPricing, setLoadingPricing] = useState(false)

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

  const loadUsers = async () => {
    setLoadingUsers(true)
    try {
      const roleQuery = selectedRole === 'ALL' ? undefined : selectedRole
      const data = await fetchUsers(roleQuery)
      setUsers(data)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Error al cargar usuarios')
    } finally {
      setLoadingUsers(false)
    }
  }

  const loadStations = async () => {
    setLoadingStations(true)
    try {
      const data = await fetchStations()
      setStations(data)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Error al cargar estaciones')
    } finally {
      setLoadingStations(false)
    }
  }

  const loadConnectors = async () => {
    setLoadingConnectors(true)
    try {
      const stationId = connectorStationFilter === 'ALL' ? undefined : connectorStationFilter
      const data = await fetchConnectors(stationId)
      setConnectors(data)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Error al cargar conectores')
    } finally {
      setLoadingConnectors(false)
    }
  }

  useEffect(() => {
    loadMetrics()
  }, [])

  useEffect(() => {
    if (activeTab === 'users') loadUsers()
    if (activeTab === 'stations') loadStations()
    if (activeTab === 'connectors' || activeTab === 'pricing') {
      loadStations()
      loadConnectors()
    }
  }, [activeTab, selectedRole, connectorStationFilter])

  // Change User Role
  const handleUpdateRole = async (userId: number, newRole: Role) => {
    setUpdatingUserRoleId(userId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await updateUserRole(userId, newRole)
      setActionSuccess(`El rol del usuario ${userId} fue actualizado a ${newRole}.`)
      await loadUsers()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo actualizar el rol.')
    } finally {
      setUpdatingUserRoleId(null)
    }
  }

  // Deactivate User
  const handleDeactivateUser = async (userId: number) => {
    if (!confirm(`¿Confirma dar de baja al usuario con ID ${userId}?`)) return
    setDeactivatingUserId(userId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await deactivateUser(userId)
      setActionSuccess(`El usuario ${userId} fue dado de baja exitosamente.`)
      await loadUsers()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo dar de baja al usuario.')
    } finally {
      setDeactivatingUserId(null)
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
      await loadUsers()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo reactivar al usuario.')
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
      await loadStations()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo dar de baja la estación.')
    } finally {
      setDeactivatingStationId(null)
    }
  }

  // Open Station Modal for Create / Edit
  const openCreateStationModal = () => {
    setEditingStation(null)
    setStationForm({ name: '', address: '', latitude: -34.6037, longitude: -58.3816 })
    setStationModalOpen('create')
  }

  const openEditStationModal = (station: StationItem) => {
    setEditingStation(station)
    setStationForm({
      name: station.name,
      address: station.address,
      latitude: station.latitude,
      longitude: station.longitude,
    })
    setStationModalOpen('edit')
  }

  const handleStationSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmittingStation(true)
    setActionError(null)
    setActionSuccess(null)
    try {
      if (stationModalOpen === 'create') {
        const created = await createStation(stationForm)
        setActionSuccess(`¡"${created.name}" creada exitosamente con ID ${created.id}!`)
      } else if (stationModalOpen === 'edit' && editingStation) {
        const updated = await updateStation(editingStation.id, stationForm)
        setActionSuccess(`¡Estación ${updated.id} "${updated.name}" actualizada correctamente!`)
      }
      setStationModalOpen(null)
      await loadStations()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Error al procesar la estación.')
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
      await loadStations()
      await loadConnectors()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Error al guardar el conector.')
    } finally {
      setSubmittingConnector(false)
    }
  }

  // Change Operational Status of Connector
  const handleChangeConnectorStatus = async (connectorId: number, status: string) => {
    setUpdatingConnectorStatusId(connectorId)
    setActionError(null)
    setActionSuccess(null)
    try {
      await changeConnectorStatus(connectorId, status)
      setActionSuccess(`Estado del conector ${connectorId} cambiado a ${status}.`)
      await loadConnectors()
      await loadStations()
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : 'No se pudo cambiar el estado del conector.',
      )
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
      await loadConnectors()
      await loadStations()
      await loadMetrics()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo eliminar el conector.')
    }
  }

  // Pricing Scheme Handlers
  const handleSelectConnectorForPricing = (connectorId: number) => {
    setPricingConnectorId(connectorId)
    setActiveTab('pricing')
    handleQueryPricingSchemeForId(connectorId)
  }

  const handleQueryPricingSchemeForId = async (id: number) => {
    if (!id || id <= 0) return
    setLoadingPricing(true)
    setPricingSuccessMsg(null)
    setPricingErrorMsg(null)
    try {
      const scheme = await fetchPricingScheme(id)
      setPricingScheme(scheme)
      setStrategyType(scheme.strategyType || 'FLAT_RATE')
      setKwhRate(scheme.kwhRate || 150)
      setDepositAmount(scheme.depositAmount || 500)
      setExcessPenaltyPerMin(scheme.excessPenaltyPerMin || 0)
      setPeakKwhRate(scheme.peakKwhRate || 250)
    } catch (err) {
      setPricingScheme(null)
      setPricingErrorMsg(
        err instanceof Error
          ? err.message
          : `No se encontró esquema configurado para el conector ${id}`,
      )
    } finally {
      setLoadingPricing(false)
    }
  }

  const handleSavePricingScheme = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoadingPricing(true)
    setPricingSuccessMsg(null)
    setPricingErrorMsg(null)
    try {
      const resp = await savePricingScheme({
        connectorId: pricingConnectorId,
        strategyType,
        kwhRate,
        depositAmount,
        excessPenaltyPerMin,
        peakKwhRate: strategyType === 'PEAK_OFF_PEAK' ? peakKwhRate : null,
      })
      setPricingScheme(resp)
      setPricingSuccessMsg(
        `¡Esquema tarifario para el conector ${pricingConnectorId} guardado exitosamente!`,
      )
    } catch (err) {
      setPricingErrorMsg(
        err instanceof Error ? err.message : 'Error al guardar el esquema tarifario.',
      )
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

  const filteredConnectors = connectors.filter((c) => {
    if (!searchConnector.trim()) return true
    const q = searchConnector.toLowerCase()
    return (
      c.id.toString().includes(q) ||
      c.connectorType.toLowerCase().includes(q) ||
      c.operationalStatus.toLowerCase().includes(q) ||
      (c.stationId && c.stationId.toString().includes(q))
    )
  })

  return (
    /*
      Scrollea adentro de su propia caja, como la portada y como contacto, y no empujando a la
      ventana entera. Antes era la excepción: el contenido desbordaba el `<main>` y lo que
      scrolleaba era el documento, así que las filas de la tabla se metían detrás de la franja de
      navegación —que es transparente— y se leían mezcladas con los rótulos de las secciones. Con
      la caja propia, el recorte lo hace el `<main>` y arriba hay un borde parejo que el velo
      puede desvanecer. Ver `TopFade`.
    */
    <div className="flex-1 overflow-y-auto">
      {/* `pb-36` en el celular es el lugar de la barra flotante de navegación. */}
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 pb-36 md:px-8 md:pb-6">
        {/* Banner / Header */}
        <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
          <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <div>
              <span className="text-xs font-bold tracking-widest text-primary uppercase">
                Centro de Control • Administrador
              </span>
              <h1 className="text-2xl font-extrabold text-text md:text-3xl">
                Backoffice de Administración
              </h1>
              <p className="mt-1 text-sm text-text-muted">
                Gestión global de la plataforma Ecopedia: usuarios, roles, estaciones, conectores y
                tarifas.
              </p>
            </div>
            <div className="flex items-center gap-2 self-start rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary md:self-auto">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              Sesión Administrador Activa
            </div>
          </div>

          {/* Feedback de acciones globales */}
          {actionSuccess && (
            <div className="mt-4 rounded-xl border border-emerald-500/40 bg-emerald-500/10 p-4 text-sm font-medium text-emerald-400">
              {actionSuccess}
            </div>
          )}
          {actionError && (
            <div className="mt-4 rounded-xl border border-rose-500/40 bg-rose-500/10 p-4 text-sm font-medium text-rose-400">
              {actionError}
            </div>
          )}

          {/* Pestañas de Navegación */}
          <div className="mt-6 flex flex-wrap gap-2 border-t border-border/50 pt-4">
            <button
              type="button"
              onClick={() => setActiveTab('dashboard')}
              className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'dashboard'
                  ? 'brand-fill text-on-primary shadow-lg shadow-primary/20'
                  : 'border border-border/60 bg-surface/40 text-text-muted hover:border-primary/40 hover:text-text'
              }`}
            >
              Tablero General
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('users')}
              className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'users'
                  ? 'brand-fill text-on-primary shadow-lg shadow-primary/20'
                  : 'border border-border/60 bg-surface/40 text-text-muted hover:border-primary/40 hover:text-text'
              }`}
            >
              Gestión de Usuarios
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('stations')}
              className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'stations'
                  ? 'brand-fill text-on-primary shadow-lg shadow-primary/20'
                  : 'border border-border/60 bg-surface/40 text-text-muted hover:border-primary/40 hover:text-text'
              }`}
            >
              Gestión de Estaciones
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('connectors')}
              className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'connectors'
                  ? 'brand-fill text-on-primary shadow-lg shadow-primary/20'
                  : 'border border-border/60 bg-surface/40 text-text-muted hover:border-primary/40 hover:text-text'
              }`}
            >
              Catálogo de Conectores
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('pricing')}
              className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'pricing'
                  ? 'brand-fill text-on-primary shadow-lg shadow-primary/20'
                  : 'border border-border/60 bg-surface/40 text-text-muted hover:border-primary/40 hover:text-text'
              }`}
            >
              Esquemas Tarifarios
            </button>
          </div>
        </div>

        {/* TAB 1: DASHBOARD */}
        {activeTab === 'dashboard' && (
          <div className="flex flex-col gap-6">
            {loadingMetrics ? (
              <div className="rounded-2xl border border-border/80 bg-surface/80 p-8 text-center text-text-muted">
                Cargando métricas del sistema...
              </div>
            ) : metrics ? (
              <>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <div className="rounded-2xl border border-border/80 bg-surface/80 p-5 shadow-lg backdrop-blur-xl">
                    <span className="text-xs font-semibold text-text-muted uppercase">
                      Usuarios Registrados
                    </span>
                    <div className="mt-2 text-3xl font-extrabold text-text">
                      {metrics.totalUsers}
                    </div>
                    <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
                      <span className="text-primary font-semibold">
                        {metrics.activeUsers} activos
                      </span>
                      •<span>{metrics.inactiveUsers} dados de baja</span>
                    </div>
                  </div>

                  <div className="rounded-2xl border border-border/80 bg-surface/80 p-5 shadow-lg backdrop-blur-xl">
                    <span className="text-xs font-semibold text-text-muted uppercase">
                      Conductores
                    </span>
                    <div className="mt-2 text-3xl font-extrabold text-cyan-400">
                      {metrics.driverCount}
                    </div>
                    <p className="mt-2 text-xs text-text-muted">
                      Usuarios con rol CONDUCTOR en la app
                    </p>
                  </div>

                  <div className="rounded-2xl border border-border/80 bg-surface/80 p-5 shadow-lg backdrop-blur-xl">
                    <span className="text-xs font-semibold text-text-muted uppercase">
                      Operadores CPO
                    </span>
                    <div className="mt-2 text-3xl font-extrabold text-purple-400">
                      {metrics.cpoCount}
                    </div>
                    <p className="mt-2 text-xs text-text-muted">
                      Operadores de infraestructura de carga
                    </p>
                  </div>

                  <div className="rounded-2xl border border-border/80 bg-surface/80 p-5 shadow-lg backdrop-blur-xl">
                    <span className="text-xs font-semibold text-text-muted uppercase">
                      Estaciones de Carga
                    </span>
                    <div className="mt-2 text-3xl font-extrabold text-emerald-400">
                      {metrics.totalStations}
                    </div>
                    <div className="mt-2 text-xs text-text-muted">
                      {metrics.activeStations} activas • {metrics.totalConnectors} conectores
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
                  <h3 className="text-lg font-bold text-text">Estado General del Sistema</h3>
                  <p className="mt-1 text-sm text-text-muted">
                    Resumen de distribución de roles y disponibilidad de infraestructura.
                  </p>

                  <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <div className="rounded-xl border border-border/50 bg-surface/40 p-4">
                      <h4 className="text-sm font-semibold text-text">Distribución de Permisos</h4>
                      <ul className="mt-3 flex flex-col gap-2 text-sm">
                        <li className="flex justify-between">
                          <span className="text-text-muted">Conductores de EV:</span>
                          <span className="font-bold text-text">{metrics.driverCount}</span>
                        </li>
                        <li className="flex justify-between">
                          <span className="text-text-muted">Operadores CPO:</span>
                          <span className="font-bold text-text">{metrics.cpoCount}</span>
                        </li>
                        <li className="flex justify-between">
                          <span className="text-text-muted">Administradores de Sistema:</span>
                          <span className="font-bold text-text">{metrics.adminCount}</span>
                        </li>
                      </ul>
                    </div>

                    <div className="rounded-xl border border-border/50 bg-surface/40 p-4">
                      <h4 className="text-sm font-semibold text-text">Red de Estaciones</h4>
                      <ul className="mt-3 flex flex-col gap-2 text-sm">
                        <li className="flex justify-between">
                          <span className="text-text-muted">Estaciones Activas:</span>
                          <span className="font-bold text-emerald-400">
                            {metrics.activeStations}
                          </span>
                        </li>
                        <li className="flex justify-between">
                          <span className="text-text-muted">Estaciones Dadas de Baja:</span>
                          <span className="font-bold text-rose-400">
                            {metrics.inactiveStations}
                          </span>
                        </li>
                        <li className="flex justify-between">
                          <span className="text-text-muted">Total Conectores Registrados:</span>
                          <span className="font-bold text-text">{metrics.totalConnectors}</span>
                        </li>
                      </ul>
                    </div>
                  </div>
                </div>
              </>
            ) : null}
          </div>
        )}

        {/* TAB 2: GESTIÓN DE USUARIOS */}
        {activeTab === 'users' && (
          <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-xl font-bold text-text">Padrón de Usuarios</h2>
                <p className="text-sm text-text-muted">
                  Administra los permisos de usuario y realiza la baja lógica.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <input
                  type="text"
                  placeholder="Buscar por nombre o email..."
                  value={searchUser}
                  onChange={(e) => setSearchUser(e.target.value)}
                  className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text placeholder-text-muted focus:border-primary focus:outline-none"
                />

                <select
                  value={selectedRole}
                  onChange={(e) => setSelectedRole(e.target.value as Role | 'ALL')}
                  className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                >
                  <option value="ALL">Todos los Roles</option>
                  <option value="CONDUCTOR">Conductores</option>
                  <option value="CPO">Operadores (CPO)</option>
                  <option value="ADMIN">Administradores</option>
                </select>
              </div>
            </div>

            {loadingUsers ? (
              <div className="mt-6 py-8 text-center text-sm text-text-muted">
                Cargando lista de usuarios...
              </div>
            ) : filteredUsers.length === 0 ? (
              <div className="mt-6 rounded-xl border border-border/50 bg-surface/30 p-8 text-center text-sm text-text-muted">
                No se encontraron usuarios que coincidan con los filtros aplicados.
              </div>
            ) : (
              <div className="mt-6 overflow-x-auto">
                <table className="w-full text-left text-sm text-text">
                  <thead className="border-b border-border/60 text-xs text-text-muted uppercase">
                    <tr>
                      <th className="py-3 px-4">ID</th>
                      <th className="py-3 px-4">Nombre Completo</th>
                      <th className="py-3 px-4">Email</th>
                      <th className="py-3 px-4">Cambiar Rol</th>
                      <th className="py-3 px-4">Estado</th>
                      <th className="py-3 px-4 text-right">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40">
                    {filteredUsers.map((user) => (
                      <tr key={user.id} className="hover:bg-surface/40 transition-colors">
                        <td className="py-3.5 px-4 font-mono text-xs font-semibold text-text-muted">
                          {user.id}
                        </td>
                        <td className="py-3.5 px-4 font-semibold text-text">{user.fullName}</td>
                        <td className="py-3.5 px-4 text-text-muted">{user.email}</td>
                        <td className="py-3.5 px-4">
                          <select
                            value={user.role}
                            disabled={updatingUserRoleId === user.id || !user.active}
                            onChange={(e) => handleUpdateRole(user.id, e.target.value as Role)}
                            className="rounded-lg border border-border/80 bg-surface/90 px-2.5 py-1 text-xs font-semibold text-text focus:border-primary focus:outline-none disabled:opacity-50"
                          >
                            <option value="CONDUCTOR">CONDUCTOR</option>
                            <option value="CPO">CPO</option>
                            <option value="ADMIN">ADMIN</option>
                          </select>
                        </td>
                        <td className="py-3.5 px-4">
                          {user.active ? (
                            <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400">
                              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                              Activo
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-rose-400">
                              <span className="h-1.5 w-1.5 rounded-full bg-rose-400" />
                              Dado de baja
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          {user.active ? (
                            <button
                              type="button"
                              disabled={deactivatingUserId === user.id}
                              onClick={() => handleDeactivateUser(user.id)}
                              className="rounded-lg border border-rose-500/40 bg-rose-500/10 px-3 py-1.5 text-xs font-semibold text-rose-400 transition-all hover:bg-rose-500 hover:text-white disabled:opacity-50"
                            >
                              {deactivatingUserId === user.id ? 'Dando de baja...' : 'Dar de Baja'}
                            </button>
                          ) : (
                            <button
                              type="button"
                              disabled={deactivatingUserId === user.id}
                              onClick={() => handleActivateUser(user.id)}
                              className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-1.5 text-xs font-semibold text-emerald-400 transition-all hover:bg-emerald-500 hover:text-white disabled:opacity-50"
                            >
                              {deactivatingUserId === user.id ? 'Reactivando...' : 'Reactivar'}
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: GESTIÓN DE ESTACIONES */}
        {activeTab === 'stations' && (
          <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-xl font-bold text-text">Red de Estaciones de Carga</h2>
                <p className="text-sm text-text-muted">
                  Crea nuevas estaciones, edita sus datos o deshabilítalas.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <input
                  type="text"
                  placeholder="Buscar por nombre o dirección..."
                  value={searchStation}
                  onChange={(e) => setSearchStation(e.target.value)}
                  className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text placeholder-text-muted focus:border-primary focus:outline-none"
                />

                <button
                  type="button"
                  onClick={openCreateStationModal}
                  className="rounded-xl brand-fill px-4 py-2 text-xs font-bold text-on-primary shadow-lg shadow-primary/20 transition-all hover:opacity-90"
                >
                  Crear Estación
                </button>
              </div>
            </div>

            {loadingStations ? (
              <div className="mt-6 py-8 text-center text-sm text-text-muted">
                Cargando catálogo de estaciones...
              </div>
            ) : filteredStations.length === 0 ? (
              <div className="mt-6 rounded-xl border border-border/50 bg-surface/30 p-8 text-center text-sm text-text-muted">
                No se encontraron estaciones registradas.
              </div>
            ) : (
              <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
                {filteredStations.map((station) => (
                  <div
                    key={station.id}
                    className="flex flex-col justify-between rounded-xl border border-border/60 bg-surface/40 p-4 transition-all hover:border-primary/40"
                  >
                    <div>
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-xs font-semibold text-text-muted">
                          Estación {station.id}
                        </span>
                        {station.active ? (
                          <span className="rounded-full bg-emerald-500/20 border border-emerald-500/30 px-2.5 py-0.5 text-xs font-bold text-emerald-400">
                            Operativa
                          </span>
                        ) : (
                          <span className="rounded-full bg-rose-500/20 border border-rose-500/30 px-2.5 py-0.5 text-xs font-bold text-rose-400">
                            Dada de baja
                          </span>
                        )}
                      </div>

                      <h3 className="mt-2 text-base font-bold text-text">{station.name}</h3>
                      <p className="mt-0.5 text-xs text-text-muted">{station.address}</p>

                      <div className="mt-3 flex items-center gap-3 text-xs text-text-muted">
                        <span>Lat: {station.latitude}</span>
                        <span>Lng: {station.longitude}</span>
                      </div>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-border/40 pt-3">
                      <button
                        type="button"
                        onClick={() => openAddConnectorModal(station.id)}
                        className="rounded-lg border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-bold text-primary transition-all hover:bg-primary hover:text-on-primary"
                      >
                        Agregar Conector
                      </button>

                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => openEditStationModal(station)}
                          className="rounded-lg border border-border/80 bg-surface/80 px-2.5 py-1 text-xs font-semibold text-text hover:border-primary/40"
                        >
                          Editar
                        </button>

                        {station.active && (
                          <button
                            type="button"
                            disabled={deactivatingStationId === station.id}
                            onClick={() => handleDeactivateStation(station.id)}
                            className="rounded-lg border border-rose-500/40 bg-rose-500/10 px-2.5 py-1 text-xs font-semibold text-rose-400 transition-all hover:bg-rose-500 hover:text-white disabled:opacity-50"
                          >
                            {deactivatingStationId === station.id ? 'Baja...' : 'Dar de Baja'}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 4: CATÁLOGO DE CONECTORES */}
        {activeTab === 'connectors' && (
          <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-xl font-bold text-text">Catálogo Global de Conectores</h2>
                <p className="text-sm text-text-muted">
                  Visualiza todos los conectores registrados. Configura su potencia, cambia su
                  estado o asígnales esquema tarifario directamente.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <input
                  type="text"
                  placeholder="Buscar por ID, tipo o estado..."
                  value={searchConnector}
                  onChange={(e) => setSearchConnector(e.target.value)}
                  className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text placeholder-text-muted focus:border-primary focus:outline-none"
                />

                <select
                  value={connectorStationFilter}
                  onChange={(e) =>
                    setConnectorStationFilter(
                      e.target.value === 'ALL' ? 'ALL' : Number(e.target.value),
                    )
                  }
                  className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                >
                  <option value="ALL">Todas las Estaciones</option>
                  {stations.map((s) => (
                    <option key={s.id} value={s.id}>
                      Estación {s.id} - {s.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {loadingConnectors ? (
              <div className="mt-6 py-8 text-center text-sm text-text-muted">
                Cargando conectores...
              </div>
            ) : filteredConnectors.length === 0 ? (
              <div className="mt-6 rounded-xl border border-border/50 bg-surface/30 p-8 text-center text-sm text-text-muted">
                No se encontraron conectores cargados.
              </div>
            ) : (
              <div className="mt-6 overflow-x-auto">
                <table className="w-full text-left text-sm text-text">
                  <thead className="border-b border-border/60 text-xs text-text-muted uppercase">
                    <tr>
                      <th className="py-3 px-4">ID Conector</th>
                      <th className="py-3 px-4">Estación</th>
                      <th className="py-3 px-4">Tipo Conector</th>
                      <th className="py-3 px-4">Potencia Máx.</th>
                      <th className="py-3 px-4">Estado Operativo</th>
                      <th className="py-3 px-4 text-right">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40">
                    {filteredConnectors.map((connector) => {
                      const parentStation = stations.find((s) => s.id === connector.stationId)
                      return (
                        <tr key={connector.id} className="hover:bg-surface/40 transition-colors">
                          <td className="py-3.5 px-4 font-mono text-xs font-bold text-primary">
                            {connector.id}
                          </td>
                          <td className="py-3.5 px-4 font-semibold text-text">
                            {parentStation
                              ? `${parentStation.id} - ${parentStation.name}`
                              : `Estación ${connector.stationId}`}
                          </td>
                          <td className="py-3.5 px-4 font-mono text-xs font-semibold text-text-muted">
                            {connector.connectorType}
                          </td>
                          <td className="py-3.5 px-4 font-bold text-text">
                            {connector.maxPowerKw} kW
                          </td>
                          <td className="py-3.5 px-4">
                            <select
                              value={connector.operationalStatus}
                              disabled={updatingConnectorStatusId === connector.id}
                              onChange={(e) =>
                                handleChangeConnectorStatus(connector.id, e.target.value)
                              }
                              className={`rounded-lg border px-2.5 py-1 text-xs font-bold focus:outline-none ${
                                connector.operationalStatus === 'AVAILABLE'
                                  ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400'
                                  : connector.operationalStatus === 'OCCUPIED'
                                    ? 'bg-amber-500/10 border-amber-500/40 text-amber-400'
                                    : 'bg-rose-500/10 border-rose-500/40 text-rose-400'
                              }`}
                            >
                              <option value="AVAILABLE">AVAILABLE (Disponible)</option>
                              <option value="OCCUPIED">OCCUPIED (Ocupado)</option>
                              <option value="OUT_OF_SERVICE">
                                OUT_OF_SERVICE (Fuera de Servicio)
                              </option>
                            </select>
                          </td>
                          <td className="py-3.5 px-4 text-right">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => handleSelectConnectorForPricing(connector.id)}
                                className="rounded-lg border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-bold text-primary hover:bg-primary hover:text-on-primary"
                              >
                                Configurar Tarifa
                              </button>
                              <button
                                type="button"
                                onClick={() => openEditConnectorModal(connector)}
                                className="rounded-lg border border-border/80 bg-surface/80 px-2.5 py-1 text-xs font-semibold text-text hover:border-primary/40"
                              >
                                Editar
                              </button>
                              <button
                                type="button"
                                onClick={() => handleDeleteConnector(connector.id)}
                                className="rounded-lg border border-rose-500/40 bg-rose-500/10 px-2 py-1 text-xs font-semibold text-rose-400 hover:bg-rose-500 hover:text-white"
                              >
                                Eliminar
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
          </div>
        )}

        {/* TAB 5: ESQUEMAS TARIFARIOS */}
        {activeTab === 'pricing' && (
          <div className="rounded-2xl border border-border/80 bg-surface/80 p-6 shadow-xl backdrop-blur-xl">
            <div>
              <h2 className="text-xl font-bold text-text">Gestión de Esquemas Tarifarios</h2>
              <p className="text-sm text-text-muted">
                Define y consulta la tarifa vigente de los conectores de la red.
              </p>
            </div>

            {pricingSuccessMsg && (
              <div className="mt-4 rounded-xl border border-emerald-500/40 bg-emerald-500/10 p-4 text-sm font-medium text-emerald-400">
                {pricingSuccessMsg}
              </div>
            )}
            {pricingErrorMsg && (
              <div className="mt-4 rounded-xl border border-rose-500/40 bg-rose-500/10 p-4 text-sm font-medium text-rose-400">
                {pricingErrorMsg}
              </div>
            )}

            <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
              {/* Selector interactivo de conector */}
              <div className="rounded-xl border border-border/60 bg-surface/40 p-5">
                <h3 className="text-sm font-bold text-text">1. Seleccionar Conector</h3>
                <p className="mt-1 text-xs text-text-muted">
                  Elige un conector del catálogo para cargar su tarifa configurada.
                </p>

                <div className="mt-4 flex flex-col gap-3">
                  <label className="text-xs font-semibold text-text-muted">
                    Conector Seleccionado
                  </label>
                  <select
                    value={pricingConnectorId}
                    onChange={(e) => {
                      const id = Number(e.target.value)
                      setPricingConnectorId(id)
                      handleQueryPricingSchemeForId(id)
                    }}
                    className="rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                  >
                    {connectors.length === 0 ? (
                      <option value={1}>Conector 1</option>
                    ) : (
                      connectors.map((c) => {
                        const st = stations.find((s) => s.id === c.stationId)
                        return (
                          <option key={c.id} value={c.id}>
                            Conector {c.id} ({c.connectorType} - {c.maxPowerKw}kW){' '}
                            {st ? `• ${st.name}` : ''}
                          </option>
                        )
                      })
                    )}
                  </select>

                  <button
                    type="button"
                    onClick={() => handleQueryPricingSchemeForId(pricingConnectorId)}
                    disabled={loadingPricing}
                    className="mt-2 rounded-xl border border-primary/40 bg-primary/10 py-2.5 text-xs font-bold text-primary transition-all hover:bg-primary hover:text-on-primary disabled:opacity-50"
                  >
                    {loadingPricing ? 'Consultando...' : 'Consultar Tarifa Vigente'}
                  </button>
                </div>

                {pricingScheme && (
                  <div className="mt-4 rounded-lg border border-border/50 bg-surface/60 p-3 text-xs">
                    <span className="font-bold text-primary">Esquema Actual Registrado:</span>
                    <div className="mt-1 text-text">
                      Estrategia: <strong>{pricingScheme.strategyType}</strong>
                    </div>
                    <div className="text-text">
                      Tarifa Base: <strong>${pricingScheme.kwhRate}/kWh</strong>
                    </div>
                    <div className="text-text">
                      Seña: <strong>${pricingScheme.depositAmount}</strong>
                    </div>
                    {pricingScheme.excessPenaltyPerMin ? (
                      <div className="text-text">
                        Penalización: <strong>${pricingScheme.excessPenaltyPerMin}/min</strong>
                      </div>
                    ) : null}
                    {pricingScheme.peakKwhRate ? (
                      <div className="text-text">
                        Tarifa Pico: <strong>${pricingScheme.peakKwhRate}/kWh</strong>
                      </div>
                    ) : null}
                  </div>
                )}
              </div>

              {/* Formulario de Configuración */}
              <form
                onSubmit={handleSavePricingScheme}
                className="rounded-xl border border-border/60 bg-surface/40 p-5 lg:col-span-2"
              >
                <h3 className="text-sm font-bold text-text">2. Definir / Actualizar Tarifa</h3>
                <p className="mt-1 text-xs text-text-muted">
                  Aplica la regla de cálculo para el conector {pricingConnectorId}.
                </p>

                <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label className="text-xs font-semibold text-text-muted">
                      Estrategia de Tarificación
                    </label>
                    <select
                      value={strategyType}
                      onChange={(e) => setStrategyType(e.target.value as PricingStrategyType)}
                      className="mt-1.5 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    >
                      <option value="FLAT_RATE">FLAT_RATE (Tarifa Plana)</option>
                      <option value="PEAK_OFF_PEAK">PEAK_OFF_PEAK (Horario Pico / Valle)</option>
                      <option value="OCCUPANCY_PENALTY">
                        OCCUPANCY_PENALTY (Recargo por Ocupación)
                      </option>
                    </select>
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-text-muted">
                      Tarifa Base ($ / kWh)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={kwhRate}
                      onChange={(e) => setKwhRate(Number(e.target.value))}
                      required
                      className="mt-1.5 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-text-muted">Monto Seña ($)</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={depositAmount}
                      onChange={(e) => setDepositAmount(Number(e.target.value))}
                      required
                      className="mt-1.5 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-text-muted">
                      Penalización Exceso ($/min)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={excessPenaltyPerMin}
                      onChange={(e) => setExcessPenaltyPerMin(Number(e.target.value))}
                      className="mt-1.5 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    />
                  </div>

                  {strategyType === 'PEAK_OFF_PEAK' && (
                    <div className="sm:col-span-2">
                      <label className="text-xs font-semibold text-text-muted">
                        Tarifa en Horario Pico ($ / kWh)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        value={peakKwhRate}
                        onChange={(e) => setPeakKwhRate(Number(e.target.value))}
                        required
                        className="mt-1.5 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                      />
                    </div>
                  )}
                </div>

                <div className="mt-6 flex justify-end">
                  <button
                    type="submit"
                    disabled={loadingPricing}
                    className="rounded-xl brand-fill px-6 py-2.5 text-xs font-bold text-on-primary shadow-lg shadow-primary/20 transition-all hover:opacity-90 disabled:opacity-50"
                  >
                    {loadingPricing ? 'Guardando...' : 'Guardar Esquema Tarifario'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL CREAR/EDITAR ESTACIÓN */}
        {stationModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-2xl">
              <h3 className="text-lg font-bold text-text">
                {stationModalOpen === 'create' ? 'Crear Nueva Estación' : 'Editar Estación'}
              </h3>
              <p className="mt-1 text-xs text-text-muted">
                Completa la información física de la estación de carga.
              </p>

              <form onSubmit={handleStationSubmit} className="mt-4 flex flex-col gap-3">
                <div>
                  <label className="text-xs font-semibold text-text-muted">
                    Nombre de Estación
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ej: Estación Puerto Madero"
                    value={stationForm.name}
                    onChange={(e) => setStationForm({ ...stationForm, name: e.target.value })}
                    className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-text-muted">Dirección</label>
                  <input
                    type="text"
                    required
                    placeholder="Ej: Av. Alicia Moreau de Justo 1000"
                    value={stationForm.address}
                    onChange={(e) => setStationForm({ ...stationForm, address: e.target.value })}
                    className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-semibold text-text-muted">Latitud</label>
                    <input
                      type="number"
                      step="any"
                      required
                      value={stationForm.latitude}
                      onChange={(e) =>
                        setStationForm({ ...stationForm, latitude: Number(e.target.value) })
                      }
                      className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-text-muted">Longitud</label>
                    <input
                      type="number"
                      step="any"
                      required
                      value={stationForm.longitude}
                      onChange={(e) =>
                        setStationForm({ ...stationForm, longitude: Number(e.target.value) })
                      }
                      className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                    />
                  </div>
                </div>

                <div className="mt-4 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setStationModalOpen(null)}
                    className="rounded-xl border border-border/80 px-4 py-2 text-xs font-semibold text-text hover:bg-surface/60"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    disabled={submittingStation}
                    className="rounded-xl brand-fill px-5 py-2 text-xs font-bold text-on-primary shadow-lg shadow-primary/20 disabled:opacity-50"
                  >
                    {submittingStation ? 'Guardando...' : 'Guardar'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL CREAR/EDITAR CONECTOR */}
        {connectorModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-2xl">
              <h3 className="text-lg font-bold text-text">
                {connectorModalOpen === 'create'
                  ? `Agregar Conector a Estación ${targetStationId}`
                  : `Editar Conector ${editingConnector?.id}`}
              </h3>
              <p className="mt-1 text-xs text-text-muted">
                Configura el tipo y la potencia máxima entregable.
              </p>

              <form onSubmit={handleConnectorSubmit} className="mt-4 flex flex-col gap-3">
                <div>
                  <label className="text-xs font-semibold text-text-muted">Tipo de Conector</label>
                  <select
                    value={connectorForm.connectorType}
                    onChange={(e) =>
                      setConnectorForm({ ...connectorForm, connectorType: e.target.value })
                    }
                    className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                  >
                    {CONNECTOR_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-text-muted">
                    Potencia Máxima (kW)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="1"
                    required
                    value={connectorForm.maxPowerKw}
                    onChange={(e) =>
                      setConnectorForm({ ...connectorForm, maxPowerKw: Number(e.target.value) })
                    }
                    className="mt-1 w-full rounded-xl border border-border/80 bg-surface/90 px-3.5 py-2 text-sm text-text focus:border-primary focus:outline-none"
                  />
                </div>

                <div className="mt-4 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setConnectorModalOpen(null)}
                    className="rounded-xl border border-border/80 px-4 py-2 text-xs font-semibold text-text hover:bg-surface/60"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    disabled={submittingConnector}
                    className="rounded-xl brand-fill px-5 py-2 text-xs font-bold text-on-primary shadow-lg shadow-primary/20 disabled:opacity-50"
                  >
                    {submittingConnector ? 'Guardando...' : 'Guardar'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
