import { Link } from 'react-router'

import { roleLabel } from '@/features/auth/roles'
import { useSession } from '@/features/auth/session'
import ProfileAvatar from '@/features/profile/components/ProfileAvatar'
import { displayNameOf } from '@/lib/displayName'

import { PROFILE_SECTION } from './navSections'

/**
 * La ficha del usuario, arriba a la derecha en escritorio y tablet.
 *
 * **Son dos cosas distintas, no una ficha con dos contenidos.** Sin sesión hay una sola acción
 * posible —entrar— y eso es un botón, verde y con el texto solo: un ícono al lado de dos palabras
 * que ya dicen todo no agrega nada, y el verde es con lo que la aplicación pide acción en el
 * resto de la pantalla. Con sesión no hay nada que pedir: hay algo que informar —quién entró y
 * con qué rol— y eso es una ficha.
 *
 * Lo que sí comparten es el alto, y es a propósito: es el mismo del buscador y el del interruptor
 * de tema, que son sus vecinos de fila. Uno que creciera al iniciar sesión correría a los otros
 * de lugar y la franja daría un salto.
 */

/** El alto de la franja. Lo fija el buscador; acá se repite para no quedar desparejo. */
const HEIGHT = 'h-12'

export default function ProfilePill() {
  const session = useSession()

  if (session === null) {
    /* El destino es `/login`, la pantalla de la feature Auth. */
    return (
      <Link
        to="/login"
        className={`brand-fill text-on-primary ${HEIGHT} flex items-center rounded-3xl px-6 text-sm font-semibold`}
      >
        Iniciar sesión
      </Link>
    )
  }

  return (
    <Link
      to={PROFILE_SECTION.to}
      /*
        El radio es el de las tarjetas de la home, para que la ficha se lea como parte de la
        misma familia. El `pl-1.5` no es simetría rota por gusto: con la punta redondeada el
        borde izquierdo se curva hacia adentro, y con el mismo padding de la derecha el círculo
        quedaría pisando esa curva.
      */
      className={`glass-panel hover:border-primary/60 ${HEIGHT} flex items-center gap-2.5 rounded-3xl pr-1.5 pl-1.5 transition-colors xl:pr-5`}
      /*
        Sin `aria-label` (QA-21, WCAG 2.5.3 "Label in Name"): el nombre accesible es lo que se ve
        —el nombre y el rol—. Antes decía "Perfil de <correo>", y el correo no aparece en la ficha:
        quien usa la app por voz dice el nombre que lee y el control no respondía.
      */
    >
      {/*
        El avatar elegido, en el redondel de la ficha. **Es el mismo componente que dibuja el de la
        cabecera del perfil**, y eso es lo que hace que la ficha de arriba y la pantalla a la que
        lleva muestren siempre la misma cara: son el mismo dato leído dos veces, no dos dibujos
        que hay que acordarse de cambiar juntos.

        Quien todavía no eligió ve el volante, que es lo que había antes del catálogo. Ahí sí
        conviene el círculo verde de fondo: un ícono de trazo suelto sobre el vidrio de la ficha
        no se lee como un avatar, se lee como un ícono al lado del nombre. Con una cara elegida ese
        fondo sobra, porque el dibujo ya trae el suyo y taparlo con verde sería pintarle un borde
        a algo que es redondo de fábrica.

        36 píxeles: es lo que deja el alto de la franja —48— con el aire de la punta redondeada.
      */}
      <ProfileAvatar size={36} placeholderClassName="text-background brand-fill" />

      {/*
        Hasta `xl` la ficha es solo el avatar (QA-21): con el nombre a la vista no entraba en la
        franja. El texto NO se saca, se esconde con `sr-only`: sigue siendo el nombre accesible del
        enlace, así que el lector de pantalla lo dice igual que en escritorio.
      */}
      <span className="sr-only leading-tight xl:not-sr-only xl:flex xl:flex-col">
        <span className="text-text text-sm font-medium">{displayNameOf(session)}</span>
        {/*
          El rol debajo del nombre, en chico y en el verde de la marca. Es lo que explica por qué
          dos cuentas ven pantallas distintas, y sin él un operador no tiene forma de saber con
          cuál entró. Va en el verde de TINTA y no en el degradado: sobre once píxeles de alto un
          degradado no se ve, y la punta clara del par sobre el tema claro no se lee.
        */}
        <span className="text-primary text-[11px] font-semibold">{roleLabel(session.role)}</span>
      </span>
    </Link>
  )
}
