import type { Role } from '@/features/auth/types'
import { api } from '@/lib/api'

import type {
  AdminDashboardMetrics,
  ConnectorItem,
  StationFormData,
  StationItem,
  UserItem,
} from '../types'

/**
 * Repositorio de llamadas a la API del Backoffice Administrador (ECO-25, ECO-27).
 *
 * Las tarifas ya no están acá: desde el 31/10 son de la red y de cada estación, y las llamadas viven en
 * `features/pricing`, que comparten /admin y /stations.
 */

export async function fetchAdminDashboard(): Promise<AdminDashboardMetrics> {
  try {
    const raw = await api.get<{
      users?: {
        total: number
        active: number
        inactive: number
        conductors: number
        cpos: number
        admins: number
      }
      stations?: { total: number; active: number; inactive: number }
      connectors?: {
        total: number
        available: number
        occupied: number
        outOfService: number
      }
    }>('/admin/dashboard')

    if (raw && raw.users && raw.stations && raw.connectors) {
      return {
        totalUsers: raw.users.total ?? 0,
        driverCount: raw.users.conductors ?? 0,
        cpoCount: raw.users.cpos ?? 0,
        adminCount: raw.users.admins ?? 0,
        activeUsers: raw.users.active ?? 0,
        inactiveUsers: raw.users.inactive ?? 0,
        totalStations: raw.stations.total ?? 0,
        activeStations: raw.stations.active ?? 0,
        inactiveStations: raw.stations.inactive ?? 0,
        totalConnectors: raw.connectors.total ?? 0,
        activePricingSchemes: 0,
      }
    }
  } catch (err) {
    console.error('Error fetching admin dashboard metrics:', err)
  }

  // Fallback: calcular métricas manualmente si el endpoint remoto falla
  const [users, stations] = await Promise.all([
    api.get<UserItem[]>('/users'),
    api.get<StationItem[]>('/stations'),
  ])

  const totalUsers = users.length
  const driverCount = users.filter((u) => u.role === 'CONDUCTOR').length
  const cpoCount = users.filter((u) => u.role === 'CPO').length
  const adminCount = users.filter((u) => u.role === 'ADMIN').length
  const activeUsers = users.filter((u) => u.active).length
  const inactiveUsers = totalUsers - activeUsers

  const totalStations = stations.length
  const activeStations = stations.filter((s) => s.active).length
  const inactiveStations = totalStations - activeStations
  const totalConnectors = stations.reduce(
    (acc, s) => acc + (s.connectors ? s.connectors.length : 0),
    0,
  )

  return {
    totalUsers,
    driverCount,
    cpoCount,
    adminCount,
    activeUsers,
    inactiveUsers,
    totalStations,
    activeStations,
    inactiveStations,
    totalConnectors,
    activePricingSchemes: 0,
  }
}

export function fetchUsers(role?: Role): Promise<UserItem[]> {
  return api.get<UserItem[]>('/users', { params: { role } })
}

export function updateUserRole(userId: number, role: Role): Promise<UserItem> {
  return api.patch<UserItem>(`/users/${userId}/role`, { role })
}

export function deactivateUser(userId: number): Promise<void> {
  return api.delete<void>(`/users/${userId}`)
}

export function activateUser(userId: number): Promise<void> {
  return api.post<void>(`/users/${userId}/activate`)
}

export function fetchStations(): Promise<StationItem[]> {
  return api.get<StationItem[]>('/stations')
}

export function createStation(data: StationFormData): Promise<StationItem> {
  return api.post<StationItem>('/stations', data)
}

export function updateStation(stationId: number, data: StationFormData): Promise<StationItem> {
  return api.put<StationItem>(`/stations/${stationId}`, data)
}

export function deactivateStation(stationId: number): Promise<void> {
  return api.delete<void>(`/stations/${stationId}`)
}

export async function fetchConnectors(stationId?: number): Promise<ConnectorItem[]> {
  try {
    return await api.get<ConnectorItem[]>('/connectors', { params: { stationId } })
  } catch {
    // Si el backend en ejecucion no tiene el nuevo endpoint GET /connectors compilado aun,
    // extrae silenciosamente los conectores incluidos en cada estacion.
    const stations = await api.get<StationItem[]>('/stations')
    const allConnectors: ConnectorItem[] = []
    stations.forEach((s) => {
      if (s.connectors) {
        s.connectors.forEach((c) => {
          allConnectors.push({ ...c, stationId: s.id })
        })
      }
    })
    if (stationId) {
      return allConnectors.filter((c) => c.stationId === stationId)
    }
    return allConnectors
  }
}

export function addConnector(
  stationId: number,
  data: { connectorType: string; maxPowerKw: number },
): Promise<ConnectorItem> {
  return api.post<ConnectorItem>(`/stations/${stationId}/connectors`, data)
}

export function configureConnector(
  connectorId: number,
  data: { connectorType: string; maxPowerKw: number },
): Promise<ConnectorItem> {
  return api.post<ConnectorItem>(`/connectors/${connectorId}/configure`, data)
}

export function changeConnectorStatus(
  connectorId: number,
  operationalStatus: string,
): Promise<void> {
  return api.patch<void>(`/connectors/${connectorId}/status`, { operationalStatus })
}

export function deleteConnector(connectorId: number): Promise<void> {
  return api.delete<void>(`/connectors/${connectorId}`)
}
