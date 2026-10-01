import { Link } from 'react-router'

import { useSession } from '@/features/auth/session'
import { displayNameOf } from '@/lib/displayName'

import { vehicleImageSrc } from '@/features/vehicles/assets'
import SpecIcon from '@/features/vehicles/components/SpecIcon'
import { VEHICLE_IMAGE, specsOf, useDriverVehicle } from '@/features/vehicles/vehicle'

import { greetingFor } from '../greeting'

/**
 * El recuadro grande de la portada: el saludo, el vehículo y su ficha.
 *
 * **Tiene TRES caras y muestran cosas distintas, no la misma con otro texto.** Con el auto ya
 * elegido el protagonista es él, con su ficha técnica abajo. Sin sesión no hay auto que mostrar,
 * así que el recuadro pasa a explicar para qué sirve entrar. Y con sesión pero sin auto —una
 * cuenta recién creada, o alguien que se lo sacó— la invitación es otra: no hay que entrar, hay
 * que elegirlo, y el enlace va al formulario del perfil en vez de al login.
 *
 * **Esa tercera cara existe porque el auto es opcional.** Antes el vehículo era una constante y
 * todo el que entraba tenía uno; ahora sale del perfil y arranca vacío. Sin este caso, una cuenta
 * nueva vería la barra de la ficha con cuatro guiones —o peor, la ficha de otro— y nada que
 * indicara que falta elegir algo.
 *
 * **El auto va como fondo y no como `<img>`.** Eso es lo que lo vuelve opcional: un fondo que no
 * existe simplemente no se dibuja y el recuadro se ve entero, mientras que una imagen rota deja el
 * ícono gris del navegador en el medio de lo primero que alguien mira.
 */

export default function VehicleHero({ className = '' }: { className?: string }) {
  const session = useSession()
  const vehicle = useDriverVehicle()
  const greeting = greetingFor(new Date().getHours())

  /*
    Las dos caras sin ficha comparten la forma —un renglón y un botón— y se diferencian en el
    texto y en a dónde llevan. Resueltas acá arriba, el JSX de abajo tiene una sola rama en vez de
    tres anidadas, y las medidas del auto pueden preguntar por "¿hay ficha?" en vez de repetir la
    condición entera.
  */
  const hasSpecs = session !== null && vehicle !== null

  /*
    La foto del auto elegido, y el dibujo genérico cuando no hay ninguno.

    **Una sola, no las dos.** Probé ponerlas en dos capas para que el genérico hiciera de
    reemplazo cuando la foto de un modelo no está —un fondo no tiene `onError`—, y está mal: las
    fotos del catálogo son PNG con fondo transparente, así que el genérico se veía POR DETRÁS del
    auto de verdad y la portada mostraba dos autos encimados. Lo que un fondo no puede hacer es
    enterarse de que no cargó; si la foto falta, el recuadro queda sin auto, que es lo que hacía
    antes de que hubiera fotos por modelo.

    Sin sesión o sin auto elegido va el genérico. Hubo un `/car-guest.png` aparte para esa cara,
    pero era el mismo dibujo byte por byte. Ver `vehicle.ts`.

    **El porcentaje de `bg-[length:…]` manda sobre la altura del ARCHIVO, no sobre la del auto**, y
    por eso las fotos del catálogo están recortadas al ras: si una trae aire transparente arriba y
    abajo, su auto se dibuja más chico que el de al lado con la misma medida —había fotos con el
    auto ocupando el 48% del archivo y otras el 97%, o sea el doble—. Recortadas, el mismo
    porcentaje da el mismo tamaño para los diecinueve, y nada en el código tiene que saber qué
    margen traía cada una.
  */
  const image = vehicle === null ? VEHICLE_IMAGE : vehicleImageSrc(vehicle)

  /*
   * A qué altura se apoya el auto, y por qué son dos valores y no uno.
   *
   * La medida se toma desde el piso de la tarjeta —un fondo no sabe dónde está un elemento—, así
   * que depende de cuánto ocupe la barra de abajo, y las dos caras no miden lo mismo: la ficha
   * técnica lleva dos renglones y la invitación uno solo, unos diecisiete píxeles de diferencia.
   * Con un valor único, la cara que no se eligió para medir queda con el auto flotando sobre la
   * barra o hundido hasta el paragolpes.
   *
   * El número sale de dónde tiene que caer el borde de arriba de la barra: por la mitad de las
   * ruedas de adelante. Como la barra es de vidrio, esa mitad se sigue viendo a través.
   */
  const carPosition = hasSpecs
    ? 'bg-[position:center_bottom_6.5rem]'
    : 'bg-[position:center_bottom_5.25rem]'

  return (
    <section
      /*
        Las medidas del fondo van como clases y no en el `style` porque cambian con el ancho, y un
        estilo en línea no tiene forma de decir "en el celular así y en escritorio asá". Solo queda
        en línea la dirección de la imagen, que es lo único que no se puede escribir como clase.

        En el celular el auto va más chico que en escritorio: en una columna angosta no hay lugar
        libre al costado del título, así que tiene que caber entre el texto y la barra de la
        ficha, que además acá ocupa dos renglones en vez de uno.

        **Y va APOYADO SOBRE la barra, hundido hasta la mitad de las ruedas de adelante.** A qué
        altura exactamente lo decide `carPosition`, que es distinto en cada cara; el porqué está
        explicado allá. Como la barra es de vidrio, esa mitad de rueda se sigue viendo a través, y
        ahí es donde el auto se corta: por abajo de la barra no asoma nada.

        En escritorio la barra vuelve a ser un solo renglón en las dos caras, así que ahí alcanza
        con una sola altura.

        **El recorte está apagado en el celular** —`overflow-visible`— porque la barra de abajo se
        sale de la tarjeta a propósito y tiene que llegar hasta el borde del teléfono; ver su
        comentario. El auto no necesita el recorte para quedarse adentro: un fondo se recorta
        contra la caja del elemento y respeta las esquinas redondeadas, pase lo que pase con
        `overflow`.
      */
      className={`glass-panel relative flex min-h-[24rem] flex-col justify-between overflow-visible rounded-3xl bg-[length:auto_42%] p-6 ${carPosition} md:min-h-[22rem] md:overflow-hidden md:bg-[length:auto_60%] md:bg-[position:center_bottom_4rem] ${className}`}
      style={{
        backgroundImage: `url("${image}")`,
        backgroundRepeat: 'no-repeat',
      }}
    >
      <header>
        {/*
          El saludo solo en pantalla grande. En el celular el mismo saludo encabeza la pantalla,
          arriba del buscador —ver `MobileHome`—, y dicho dos veces con dos centímetros de
          distancia se lee como un error, no como un énfasis.
        */}
        <p className="text-text-muted hidden text-sm font-medium md:block">
          {session === null ? greeting : `${greeting}, ${displayNameOf(session)}`} 👋
        </p>

        {vehicle === null ? (
          <>
            {/*
              Un punto más chico en el celular. A 4xl, "tu red de carga." no entra en un renglón
              en un teléfono angosto y el título pasa a tres: el tercero se le monta al auto, que
              ya está apoyado sobre la barra y no tiene para dónde bajar.

              **El título es el mismo con sesión y sin ella.** Las dos caras dicen lo mismo —este
              lugar es para tu auto— y lo que cambia es el paso que falta, que lo dice la barra de
              abajo. Dos títulos distintos para la misma idea harían que entrar cambie el encabezado
              sin que haya cambiado nada de lo que se está mirando.
            */}
            <h1 className="text-text mt-2 max-w-sm text-3xl leading-[1.05] font-extrabold tracking-tight text-balance md:text-4xl">
              Tu auto,
              <br />
              <span className="text-primary">tu red de carga.</span>
            </h1>
            {/*
              El párrafo se va en el celular: ahí el auto necesita el alto que ocupan estos tres
              renglones, y sin ellos se le monta encima al título. Lo que dice no se pierde —el
              carrusel de abajo explica el flujo entero y el botón de la barra dice a dónde ir—,
              mientras que un auto pisando el texto rompe lo primero que alguien mira.
            */}
            <p className="text-text-muted mt-3 hidden max-w-xs text-sm leading-relaxed md:block">
              {session === null
                ? 'Entrá y guardá tu vehículo: el mapa te muestra solo las estaciones con el conector que usás.'
                : 'Elegí tu modelo y la portada cuenta cuántas estaciones cargan tu conector y cuánto tarda.'}
            </p>
          </>
        ) : (
          <>
            <h1 className="text-text mt-2 text-4xl leading-none font-extrabold tracking-tight uppercase">
              {vehicle.name}
            </h1>
            <p className="text-text-muted mt-1 text-sm font-bold tracking-wide uppercase">
              {vehicle.brand}
            </p>
          </>
        )}
      </header>

      {/*
        La barra de vidrio de abajo. Con sesión lleva la ficha del auto; sin sesión, la puerta de
        entrada. Es el mismo lugar en las dos caras para que el recuadro no cambie de forma según
        quién esté mirando.

        **En el celular SIGUE DE LARGO hasta el borde del teléfono.** Se sale de la tarjeta, cruza
        el margen de la página y termina justo contra el vidrio del aparato, sin esquina
        redondeada de ese lado: lo que se ve es una faja que entra por debajo del auto y se va de
        la pantalla. Cerrándola antes —sobre el borde de la tarjeta— se le ve el remate y vuelve a
        leerse como una tarjeta chica adentro de otra.

        **`calc(50% - 50vw)` es lo que la lleva hasta ahí, y no un número escrito a mano.** Un
        margen en porcentaje se mide contra el ancho de la caja que la contiene, así que esa
        cuenta da media pantalla menos media tarjeta: exactamente lo que hay entre el borde
        derecho de la barra y el del teléfono, sumando el relleno de la tarjeta y el de la página
        de una sola vez. El día que alguno de los dos cambie, esto sigue llegando al borde.

        Llega al borde y no más allá, así que no empuja la página ni le agrega barra horizontal.

        En escritorio no: ahí la barra es la ficha técnica centrada bajo el auto, y sacarle un
        solo lado la dejaría torcida dentro de un recuadro que es simétrico.
      */}
      <div className="glass-panel relative mt-6 mr-[calc(50%-50vw)] rounded-2xl rounded-r-none px-5 py-4 md:mr-0 md:rounded-r-2xl">
        {vehicle === null ? (
          /*
            La misma forma para las dos caras sin ficha, y lo que cambia es el paso que falta: sin
            sesión hay que entrar, con sesión hay que elegir el auto. El destino del botón es la
            diferencia de verdad —`/login` contra el formulario del perfil—, así que el texto lo
            acompaña en vez de repetir "iniciá sesión" a alguien que ya la tiene.
          */
          <div className="flex flex-wrap items-center justify-between gap-4">
            <p className="text-text text-sm font-semibold">Todavía no elegiste tu vehículo.</p>
            <Link
              to={session === null ? '/login' : '/profile/edit'}
              className="brand-fill text-on-primary rounded-xl px-4 py-2 text-sm font-semibold transition-colors"
            >
              {session === null ? 'Iniciar sesión' : 'Elegir mi auto'}
            </Link>
          </div>
        ) : (
          /*
            Cada dato es un dibujo a la izquierda y, a su derecha, el rótulo arriba del valor.

            Los cuatro se reparten la barra en columnas iguales y cada uno queda centrado en la
            suya. Es lo que los mantiene separados en un monitor grande sin que la separación sea
            un número escrito a mano: la columna mide un cuarto de lo que haya, así que el aire
            entre ellos lo calcula el navegador y cambia solo con el ancho de la pantalla.

            En pantalla angosta son dos columnas de dos. Cuatro no entran, y apretarlos hasta que
            entren pierde justo lo que se está buscando acá.

            **Y en el celular los cuatro van más chicos.** Con las medidas de escritorio, media
            columna se la lleva el dibujo y "150 kW" se parte en dos renglones; peor todavía, la
            barra crece hasta comerse el lugar donde va el auto. Achicar el dibujo y el valor
            devuelve las dos cosas: el número entero en un renglón, y una barra de la altura que
            el recuadro puede pagar.
          */
          <dl className="grid grid-cols-2 justify-items-center gap-x-3 gap-y-4 sm:grid-cols-4 md:gap-x-4 md:gap-y-5">
            {specsOf(vehicle).map((spec) => {
              return (
                <div key={spec.label} className="flex items-center gap-2 md:gap-3">
                  {/*
                    El dibujo va suelto, en el verde de la marca, del tamaño que antes tenía su
                    recuadro. El recuadro tenue lo separaba del valor, pero adentro de una barra
                    que ya es de vidrio eran cuatro cajas dentro de otra caja; el dibujo grande
                    sostiene solo el mismo peso, y el aire alrededor hace el trabajo que hacía el
                    borde.
                  */}
                  <SpecIcon
                    name={spec.icon}
                    className="text-primary h-8 w-8 shrink-0 md:h-10 md:w-10"
                  />

                  <div>
                    <dt className="text-text-muted text-[11px] font-medium">{spec.label}</dt>
                    <dd className="text-text text-base leading-tight font-extrabold tracking-tight md:text-lg">
                      {spec.value}
                    </dd>
                  </div>
                </div>
              )
            })}
          </dl>
        )}
      </div>
    </section>
  )
}
