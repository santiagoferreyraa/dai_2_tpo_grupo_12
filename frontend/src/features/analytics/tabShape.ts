/**
 * La silueta del panel de arriba: una pestaña con el buscador y, debajo, el panel entero, como
 * **una sola figura**.
 *
 * Es la técnica de la cabecera del perfil (`profile/headerShape.ts`) y por el mismo motivo: con
 * vidrio, dos cajas apiladas no se funden. Donde se pisan el cuerpo se suma y queda una mancha, y
 * los dos bordes dejan una costura. Un solo contorno no tiene ninguna de las dos cosas.
 *
 * El hueco a la derecha de la pestaña no es un recorte decorativo: ahí van los botones del
 * período, que flotan sobre el fondo y no sobre el panel.
 */

export const TAB_SHAPE = {
  /** Alto de la pestaña: lo que ocupa el buscador. */
  tabHeight: 56,
  /** Radio de las esquinas de afuera, el `rounded-3xl` del resto de los paneles. */
  radius: 24,
  /** Radio de las esquinas de la pestaña, un poco más chico porque la pestaña es baja. */
  tabRadius: 20,
  /** Radio del empalme cóncavo donde la pestaña baja hasta el borde del panel. */
  fillet: 16,
} as const

const round = (value: number) => Math.round(value * 100) / 100

/**
 * El contorno, en sentido horario desde la esquina de arriba a la izquierda.
 *
 * `tabWidth` es el ancho de la pestaña; `width` y `height`, los de la figura entera.
 */
export function tabOutlinePath(width: number, height: number, tabWidth: number): string {
  const { tabHeight: th, radius: r, tabRadius: tr, fillet: f } = TAB_SHAPE
  const tw = Math.min(tabWidth, width - r - f)

  return [
    `M 0 ${tr}`,
    `A ${tr} ${tr} 0 0 1 ${tr} 0`,
    `H ${round(tw - tr)}`,
    `A ${tr} ${tr} 0 0 1 ${round(tw)} ${tr}`,
    `V ${th - f}`,
    /* Cóncavo: el centro del empalme cae afuera de la figura, así que el arco va al revés. */
    `A ${f} ${f} 0 0 0 ${round(tw + f)} ${th}`,
    `H ${round(width - r)}`,
    `A ${r} ${r} 0 0 1 ${round(width)} ${th + r}`,
    `V ${round(height - r)}`,
    `A ${r} ${r} 0 0 1 ${round(width - r)} ${round(height)}`,
    `H ${r}`,
    `A ${r} ${r} 0 0 1 0 ${round(height - r)}`,
    'Z',
  ].join(' ')
}
