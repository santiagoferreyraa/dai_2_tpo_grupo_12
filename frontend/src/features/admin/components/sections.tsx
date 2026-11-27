/** Las secciones del backoffice, en el orden del riel y de las pastillas. */
import type { ReactNode } from 'react'

import {
  CardIcon,
  GaugeIcon,
  PlugIcon,
  ProfileIcon,
  StationsIcon,
} from '@/features/navigation/icons'

export type AdminSection = 'dashboard' | 'users' | 'stations' | 'connectors' | 'pricing'

export const ADMIN_SECTIONS: { key: AdminSection; label: string; icon: ReactNode }[] = [
  { key: 'dashboard', label: 'Tablero', icon: <GaugeIcon className="h-5 w-5" /> },
  { key: 'users', label: 'Usuarios', icon: <ProfileIcon className="h-5 w-5" /> },
  { key: 'stations', label: 'Estaciones', icon: <StationsIcon className="h-5 w-5" /> },
  { key: 'connectors', label: 'Conectores', icon: <PlugIcon className="h-5 w-5" /> },
  { key: 'pricing', label: 'Precios', icon: <CardIcon className="h-5 w-5" /> },
]
