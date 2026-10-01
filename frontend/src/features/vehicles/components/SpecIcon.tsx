import { BatteryIcon, BoltIcon, GaugeIcon, PlugIcon } from '@/features/navigation/icons'

import type { SpecIcon as SpecIconName } from '../vehicle'

/**
 * El dibujo de un dato de la ficha: el rayo del motor, el enchufe del conector, el reloj de la
 * potencia y la pila de la batería.
 *
 * **La correspondencia entre dato y dibujo vive acá y en ningún otro lado.** `specsOf` dice QUÉ
 * dibujo le toca a cada dato —un nombre, no un componente, porque es un archivo sin JSX— y esto
 * es lo que lo convierte en el ícono. Estaba escrita adentro de la portada, así que el selector
 * de auto, que muestra exactamente los mismos cuatro datos, tenía que elegir sus dibujos por su
 * cuenta: dos listas que dicen lo mismo y que se separan en cuanto alguien agrega un dato.
 *
 * El color y el tamaño los pone quien lo usa: en la portada son grandes porque la ficha es lo
 * único que hay en la barra, y en el selector más chicos porque conviven con la foto del auto.
 */
const ICONS: Record<SpecIconName, (props: { className?: string }) => React.ReactElement> = {
  motor: BoltIcon,
  connector: PlugIcon,
  power: GaugeIcon,
  battery: BatteryIcon,
}

export default function SpecIcon({
  name,
  className = '',
}: {
  name: SpecIconName
  className?: string
}) {
  const Icon = ICONS[name]
  return <Icon className={className} />
}
