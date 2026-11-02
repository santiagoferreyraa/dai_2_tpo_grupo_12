import type { Role } from '@/features/auth/types'

export interface AdminDashboardMetrics {
  totalUsers: number
  driverCount: number
  cpoCount: number
  adminCount: number
  activeUsers: number
  inactiveUsers: number
  totalStations: number
  activeStations: number
  inactiveStations: number
  totalConnectors: number
  activePricingSchemes: number
}

export interface UserItem {
  id: number
  email: string
  fullName: string
  role: Role
  active: boolean
  createdAt: string
}

export interface ConnectorItem {
  id: number
  stationId: number
  connectorType: string
  maxPowerKw: number
  operationalStatus: string
}

export interface StationItem {
  id: number
  name: string
  address: string
  latitude: number
  longitude: number
  ownerId: number
  active: boolean
  /** Nodo de la distribuidora (RF15); `null` si no tiene. */
  nodeId: string | null
  connectors?: ConnectorItem[]
}

/** Lo que manda el formulario de estación del backoffice, en el alta y en la edición. */
export interface StationFormData {
  name: string
  address: string
  latitude: number
  longitude: number
  nodeId: string | null
}
