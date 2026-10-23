import { Link } from 'react-router'

import { roleLabel } from '@/features/auth/roles'
import { useSession } from '@/features/auth/session'
import { vehicleLabelOf } from '@/features/vehicles/vehicle'
import { displayNameOf } from '@/lib/displayName'

/**
 * Lo que dice la cabecera del perfil: los cuatro datos del usuario, o el campo para editarlos.
 *
 * **Vive aparte de la figura que lo contiene** porque son dos cosas distintas: la silueta con el
 * redondel es de escritorio y la dibuja `ProfileHeader`; esto es lo que va escrito adentro, y es
 * lo mismo en el celular, donde por ahora va en una tarjeta común.
 *
 * **Solo muestra: editar vive en otra ruta.** El lápiz de la cabecera lleva a `/profile/edit`,
 * que ocupa la tarjeta grande. Antes el formulario reemplazaba a estos cuatro datos adentro de
 * la barra, que son cincuenta y seis píxeles de alto: entraba un campo y nada más.
 *
 * **No pide nada al backend para mostrarse.** Los cuatro datos ya están en la sesión: el correo
 * y el rol viajan en el token, y el nombre lo trae `hydrateProfile` apenas se entra. Pedirlo de
 * nuevo acá sería una segunda llamada para traer lo mismo, y encima dejaría a esta pantalla
 * mostrando un cartel de error cuando el resto de la aplicación sigue andando.
 *
 * **Los cuatro se reparten el ancho en columnas iguales**, igual que la ficha del auto de la
 * portada. Es lo que los mantiene separados en un monitor grande sin escribir a mano la
 * separación: la columna mide un cuarto de lo que haya.
 */

export default function ProfileIdentity() {
  const session = useSession()

  if (session === null) {
    return (
      <div className="flex w-full flex-wrap items-center justify-between gap-3">
        <p className="text-text text-sm font-semibold">
          Todavía no iniciaste sesión. Con una cuenta podés reservar un conector.
        </p>
        <Link
          to="/login"
          className="brand-fill text-on-primary rounded-xl px-4 py-2 text-sm font-semibold"
        >
          Iniciar sesión
        </Link>
      </div>
    )
  }

  const fields = [
    { label: 'Nombre', value: displayNameOf(session) },
    { label: 'Correo', value: session.email },
    /*
      El guion es para quien todavía no eligió auto, que es un estado normal y no un dato que
      falta. No se dice "elegí tu auto" acá: esta barra es una ficha de datos, no un lugar donde
      se actúa, y el lápiz que está al lado ya lleva al formulario donde se elige.
    */
    { label: 'Auto', value: session.vehicle === null ? '—' : vehicleLabelOf(session.vehicle) },
    { label: 'Rol', value: roleLabel(session.role) },
  ]

  return (
    <dl className="grid w-full grid-cols-2 gap-x-4 gap-y-2 md:grid-cols-4">
      {fields.map((field) => (
        <div key={field.label} className="min-w-0">
          <dt className="text-text-muted text-[10px] font-medium tracking-wide uppercase">
            {field.label}
          </dt>
          {/*
            El valor se corta con puntos suspensivos y no se parte en dos renglones: un correo
            largo haría crecer la barra, y la barra tiene el alto del redondel.
          */}
          <dd className="text-text truncate text-sm leading-tight font-extrabold tracking-tight">
            {field.value}
          </dd>
        </div>
      ))}
    </dl>
  )
}
