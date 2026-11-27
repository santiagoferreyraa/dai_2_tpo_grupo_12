import { ADMIN_SECTIONS, type AdminSection } from './sections'

interface AdminTabsProps {
  active: AdminSection
  onChange: (section: AdminSection) => void
}

/**
 * Las secciones del backoffice como pestañas, arriba a la izquierda, en el lugar donde antes iba el
 * título. Cada una es un botón suelto, como los períodos de Estadísticas.
 *
 * **La abierta es apenas más grande y cambia de color**, como la pestaña activa de un navegador: un
 * escalón de letra más y el degradado de la marca, lo justo para saber dónde se está sin que la
 * fila salte. Las demás quedan en vidrio.
 *
 * Dos detalles que no se ven pero importan:
 *
 * - **Las dos caras tienen borde de 1 px**, transparente en la abierta. Sin borde en la abierta, al
 *   cambiar de pestaña la transición animaba el borde del vidrio hacia el color del texto —casi
 *   negro— y aparecía un contorno oscuro que se desvanecía. Por lo mismo la transición nombra sus
 *   propiedades en vez de `transition-all`.
 * - **La fila tiene aire arriba y abajo adentro del scroll.** Un contenedor con `overflow-x` recorta
 *   también en vertical, y sin ese aire la sombra de las pestañas quedaba cortada en una línea recta.
 *
 * Son botones y no enlaces porque las secciones no tienen dirección propia: cambian lo que muestra
 * la misma pantalla. `aria-current` le dice al lector cuál está abierta. En el celular la fila se
 * arrastra de costado si no entra.
 */
export default function AdminTabs({ active, onChange }: AdminTabsProps) {
  return (
    <nav aria-label="Secciones del backoffice" className="-mx-4 -my-3 shrink-0 md:-mx-6">
      <ul className="no-scrollbar flex items-center gap-2 overflow-x-auto px-4 py-3 md:px-6">
        {ADMIN_SECTIONS.map((section) => {
          const current = section.key === active
          return (
            <li key={section.key} className="shrink-0">
              <button
                type="button"
                aria-current={current ? 'page' : undefined}
                onClick={() => onChange(section.key)}
                className={`focus-visible:outline-primary flex cursor-pointer items-center gap-2 rounded-2xl border whitespace-nowrap transition-[padding,font-size,color,background-color,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] focus-visible:outline-2 focus-visible:outline-offset-2 ${
                  current
                    ? 'brand-fill text-on-primary border-transparent px-4.5 py-2.5 text-base font-extrabold shadow-lg md:text-lg'
                    : 'glass-panel text-text-muted hover:text-text px-4 py-2 text-sm font-semibold'
                }`}
              >
                <span aria-hidden="true">{section.icon}</span>
                {section.label}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
