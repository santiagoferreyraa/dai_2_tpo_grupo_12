import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'

import { updateMyPassword, updateMyProfile } from '@/features/auth/data/userRepository'
import { applyProfile, useSession } from '@/features/auth/session'
import { ChevronRightIcon, EyeIcon, EyeOffIcon } from '@/features/navigation/icons'
import {
  validateNewPassword,
  validatePasswordConfirmation,
  validateRequiredPassword,
} from '@/features/auth/validation'
import UnderlineField from '@/components/UnderlineField'
import { useVehicleModels } from '@/features/vehicles/catalog'
import VehicleDialog from '@/features/vehicles/components/VehicleDialog'
import { vehicleLabelOf } from '@/features/vehicles/vehicle'
import { ApiError } from '@/lib/api'

import { AVATAR_IDS, avatarSrc, chooseAvatar, useAvatarId } from './avatars'
import ProfileCard from './components/ProfileCard'

/**
 * La edición del perfil: ocupa la tarjeta grande, en lugar de la sección que se estaba mirando.
 *
 * **Es una ruta y no un estado de la cabecera.** El lápiz está arriba, en la barra de datos, y
 * el formulario sale abajo, en la tarjeta: son dos lugares distintos de la pantalla, así que
 * con un estado local habría que pasárselo entre componentes que no se conocen. Con una ruta,
 * el lápiz es un enlace, la tarjeta la elige el ruteo y de yapa funciona el botón de atrás.
 *
 * **No está en el riel.** Las cuatro secciones son lugares donde uno se queda; esto es un paso
 * que empieza en un botón y termina al guardar o al cancelar.
 *
 * **De los cuatro datos que se ven arriba, solo dos se pueden cambiar.** El correo identifica la
 * cuenta y viaja adentro del token; el rol es una decisión administrativa —si se aceptara acá,
 * cualquiera se promovería a administrador desde su propio perfil—. Los que sí se editan son el
 * nombre y el auto.
 *
 * **El auto se ELIGE de una lista, no se escribe.** Lo que sale de acá no es una etiqueta: el
 * conector y las potencias del modelo son los que después filtran estaciones en la portada y
 * estiman cuánto tarda una carga. Un campo de texto libre —"tesla model 3"— no se puede comparar
 * contra el conector de una estación, y una potencia tipeada a mano convierte una estimación en
 * una adivinanza. Ver `vehicles/data/vehicleModelsRepository`.
 *
 * **El avatar sí se elige, y se guarda en otro lado que el resto.** El nombre y la contraseña van
 * al backend; el avatar se queda en el navegador, porque el perfil no tiene dónde guardarlo. Ver
 * `avatars.ts`, que explica la limitación y qué cambia el día que la tenga. Para quien está
 * editando, los tres son lo mismo: se tocan acá y se guardan con el mismo botón.
 */
export default function EditProfilePage() {
  const session = useSession()
  const navigate = useNavigate()

  /*
    El nombre arranca con el que está guardado, pero **el estado no se inicializa con él**: el
    valor inicial de `useState` se toma una sola vez, en el primer render, y para entonces la
    sesión puede no tener todavía el nombre real —lo trae `hydrateFullName` un instante después
    de entrar—. Inicializándolo así, quien llega rápido a esta pantalla se encontraba el campo
    vacío para siempre.

    Lo que se guarda acá es lo TIPEADO, y es `null` mientras nadie haya tocado el campo. Hasta
    entonces se muestra lo que diga la sesión, así que el nombre aparece solo en cuanto llega, y
    desde la primera tecla manda lo que escribió el usuario.
  */
  const [typedName, setTypedName] = useState<string | null>(null)
  const fullName = typedName ?? session?.fullName ?? ''
  const setFullName = setTypedName

  /*
    El avatar se toca acá y se guarda al aceptar, igual que el nombre. **No se guarda al tocarlo**
    aunque escribir en el almacenamiento sea instantáneo y no pueda fallar: quien entró a este
    formulario está probando cosas, y Cancelar tiene que devolver la pantalla a como estaba. Si el
    redondel de la franja de arriba cambiara en el momento del clic, cancelar dejaría la cara
    nueva puesta y el nombre viejo.

    `pickedAvatar` es `null` mientras nadie eligió nada en esta visita, y ahí manda lo guardado.
    Es la misma forma que el nombre, y por el mismo motivo.
  */
  /*
    El auto, con la misma forma que el nombre: `null` mientras nadie tocó el selector en esta
    visita, y hasta entonces manda lo que diga la sesión. **Lo que no comparte es el momento de
    guardar** —el auto se escribe apenas se elige, ver `persistVehicle`—, y el estado sigue
    existiendo igual: el formulario tiene que mostrar lo elegido antes de que vuelva la respuesta,
    y lo vuelve a mandar al aceptar por si aquella no se pudo hacer. El perfil llega un
    instante después de entrar —ver `hydrateProfile`—, así que inicializar el estado con él dejaría
    el selector en "Todavía no elegí" para siempre a quien llega rápido a esta pantalla.

    **Y es `number | null | undefined`, tres valores, porque `null` ya significa algo**: es
    "ningún auto", que es una elección legítima y la que hace quien se lo quiere sacar.
    `undefined` es "no toqué el selector".
  */
  const [pickedVehicle, setPickedVehicle] = useState<number | null | undefined>(undefined)
  const vehicleModelId =
    pickedVehicle === undefined ? (session?.vehicle?.id ?? null) : pickedVehicle

  const savedAvatar = useAvatarId(session?.userId ?? null)
  const [pickedAvatar, setPickedAvatar] = useState<string | null>(null)
  const avatarId = pickedAvatar ?? savedAvatar
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  /* Si la contraseña nueva se ve mientras se escribe. Ver el ojo. */
  const [revealed, setRevealed] = useState(false)

  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  /*
    La contraseña es opcional dentro de este formulario: quien entró a cambiarse el nombre no
    tiene por qué escribir tres campos más. Se valida y se manda solo si tocó alguno de los
    tres, y ahí sí los tres son obligatorios.
  */
  const changingPassword = currentPassword !== '' || newPassword !== '' || confirmation !== ''

  /**
   * Guarda el auto en cuanto se elige, sin esperar al botón de abajo.
   *
   * **Es la excepción a "acá nada se guarda hasta Aceptar", y la hace la propia pantalla del
   * selector.** El nombre y el avatar se escriben en un formulario: lo que se ve mientras se
   * tipea es un borrador, y Cancelar lo descarta. El auto no: se elige adentro de una ventana
   * que se abre, se recorre y se cierra con un botón que dice "Elegir", y esa ventana cerrándose
   * ES el gesto de confirmación. Dejándolo esperando al pie del formulario, quien elegía su auto
   * y se iba a la portada —que es justo lo que invita a hacer, porque la portada es la que usa el
   * dato— no se llevaba nada: ni la ficha en la portada, ni la elección al recargar.
   *
   * **Manda el nombre GUARDADO y no el tipeado**, que es lo que mantiene en pie la regla de
   * arriba para el resto del formulario: el backend reemplaza el nombre con el que le llegue, así
   * que mandando el del campo, elegir un auto guardaría de rebote un nombre a medio escribir que
   * Cancelar ya no podría deshacer.
   *
   * Si el perfil todavía no llegó no hay nombre que mandar —y mandar vacío lo rechaza el
   * backend—, así que ahí no se guarda nada y queda para el envío del formulario, que es lo que
   * pasaba antes de esto. El selector tampoco se puede abrir hasta que llegue el catálogo, que
   * tarda lo mismo.
   */
  async function persistVehicle(vehicleModelId: number | null): Promise<void> {
    const savedName = session?.fullName ?? null
    if (savedName === null || savedName.trim() === '') return

    setFailure(null)
    try {
      applyProfile(await updateMyProfile(savedName, vehicleModelId))
    } catch (cause: unknown) {
      setFailure(cause instanceof ApiError ? cause.message : 'No se pudo guardar el vehículo')
    }
  }

  function validate(): boolean {
    const found: Record<string, string | undefined> = {
      fullName: fullName.trim() === '' ? 'Escribí tu nombre.' : undefined,
      currentPassword: changingPassword ? validateRequiredPassword(currentPassword) : undefined,
      newPassword: changingPassword ? validateNewPassword(newPassword) : undefined,
      confirmation: changingPassword
        ? validatePasswordConfirmation(newPassword, confirmation)
        : undefined,
    }

    setErrors(found)
    return Object.values(found).every((message) => message === undefined)
  }

  async function handleSubmit(event: React.FormEvent): Promise<void> {
    event.preventDefault()
    setFailure(null)
    if (!validate()) return

    setSaving(true)
    try {
      /*
        **La contraseña primero, y el orden no es indistinto.** La única de las dos llamadas que
        el backend puede rechazar por lo que escribió el usuario es esta —si la actual no
        coincide—, así que yendo primero un error deja todo como estaba. Al revés, el nombre ya
        habría quedado guardado y la pantalla mostraría un error después de haber cambiado algo,
        que es la peor combinación posible: no se sabe qué se guardó y qué no.
      */
      if (changingPassword) await updateMyPassword(currentPassword, newPassword)

      const profile = await updateMyProfile(fullName.trim(), vehicleModelId)
      applyProfile(profile)

      /*
        El avatar, al final y solo si se tocó. Va DESPUÉS de las llamadas porque es lo único que
        no puede fallar: guardándolo primero, un backend que rechaza la contraseña dejaría la cara
        cambiada en una edición que la pantalla dice que no se guardó.
      */
      if (pickedAvatar !== null && session !== null) chooseAvatar(session.userId, pickedAvatar)

      void navigate('..', { relative: 'path' })
    } catch (cause: unknown) {
      /* El mensaje viene del backend —"La contraseña actual no es correcta"— y se muestra tal cual. */
      setFailure(cause instanceof ApiError ? cause.message : 'No se pudieron guardar los cambios')
      setSaving(false)
    }
  }

  if (session === null) {
    return (
      <ProfileCard>
        <p className="text-text-muted text-sm">Entrá a tu cuenta para editar tu perfil.</p>
      </ProfileCard>
    )
  }

  return (
    <ProfileCard>
      <h2 className="text-text text-lg font-extrabold tracking-tight">Editar perfil</h2>
      <p className="text-text-muted mt-1 text-sm">
        El correo y el rol no se editan: el primero identifica tu cuenta y el segundo lo asigna un
        administrador.
      </p>

      {/*
        **`autoComplete="off"` en el formulario entero, y no es una manía.** El navegador ve un
        campo de texto arriba y dos de contraseña abajo, decide que esto es un login y lo llena
        con la credencial guardada: el nombre aparecía pisado con un correo y la contraseña actual
        con una clave cualquiera, ya escrita y editable. En un formulario de perfil eso no ayuda,
        confunde: parece que la aplicación está mostrando la contraseña guardada.

        Que esté acá no alcanza —Chrome lo ignora seguido—, así que además cada campo lo repite y
        los dos de contraseña se declaran `new-password`, que es lo único que el navegador respeta
        para no ofrecer lo que tiene guardado.
      */}
      <form
        onSubmit={(event) => void handleSubmit(event)}
        autoComplete="off"
        className="mt-6 flex flex-col gap-5"
      >
        {/*
          El mismo relleno que el recuadro del alta de una tarjeta: los dos son un formulario
          adentro de la tarjeta de una sección. Ver `.glass-inset`.
        */}
        {/*
          **`min-w-0` sobre el `<fieldset>`, que no es lo mismo que sobre un `<div>`.** El navegador
          le da a esta etiqueta —y solo a ella— un `min-inline-size: min-content` en su hoja de
          estilos propia, así que se niega a angostarse por debajo de lo que mide su contenido por
          más `overflow` que tenga adentro. Con la tira de veintitrés avatares eso empujaba el
          recuadro fuera de la tarjeta del perfil. Ver `AvatarPicker`.
        */}
        <fieldset className="glass-inset flex min-w-0 flex-col gap-5 rounded-3xl p-6">
          <legend className="sr-only">Datos de la cuenta</legend>

          <AvatarPicker value={avatarId} onChange={setPickedAvatar} />

          <UnderlineField label="Nombre" error={errors.fullName}>
            {(props) => (
              <input
                {...props}
                type="text"
                name="fullName"
                autoComplete="off"
                maxLength={120}
                placeholder="Como querés que te llamemos"
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
            )}
          </UnderlineField>

          <VehiclePicker
            value={vehicleModelId}
            onChange={(picked) => {
              setPickedVehicle(picked)
              void persistVehicle(picked)
            }}
          />
        </fieldset>

        <fieldset className="glass-inset flex flex-col gap-5 rounded-3xl p-6">
          {/*
            **El título va como `<h3>` y la `<legend>` queda solo para el lector de pantalla.**
            Una leyenda visible no es un renglón más adentro del recuadro: el navegador la dibuja
            MONTADA sobre el borde de arriba, abriéndole un hueco. Eso funciona con el borde
            cuadrado de siempre, pero sobre un panel redondeado y de vidrio el texto queda
            sobresaliendo y el borde cortado. Separando las dos cosas, el grupo sigue anunciándose
            como "Cambiar la contraseña" y el título se dibuja donde corresponde.
          */}
          <legend className="sr-only">Cambiar la contraseña</legend>
          <div>
            <h3 className="text-text text-sm font-semibold">Cambiar la contraseña</h3>
            <p className="text-text-muted mt-1 text-xs">
              Dejá los campos vacíos si no querés cambiarla.
            </p>
          </div>

          <UnderlineField label="Contraseña actual" error={errors.currentPassword}>
            {(props) => (
              <input
                {...props}
                type="password"
                autoComplete="new-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
              />
            )}
          </UnderlineField>
          {/*
            La nueva y su repetición, una al lado de la otra: son el mismo dato escrito dos veces,
            y separadas en dos renglones se leen como dos pedidos distintos. Debajo de `md` se
            apilan, porque dos campos de contraseña en media pantalla de teléfono no entran.
          */}
          <div className="grid gap-5 md:grid-cols-2">
            <UnderlineField
              label="Contraseña nueva"
              hint="Al menos 6 caracteres."
              error={errors.newPassword}
            >
              {(props) => (
                <div className="flex items-center gap-3">
                  <input
                    {...props}
                    type={revealed ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                  />
                  {/*
                    El ojo, tachado mientras la contraseña está oculta. Muestra solo este campo: el
                    de al lado es la comprobación, y verlo también convertiría el repetir en copiar.
                  */}
                  <button
                    type="button"
                    onClick={() => setRevealed(!revealed)}
                    aria-label={revealed ? 'Ocultar la contraseña' : 'Mostrar la contraseña'}
                    aria-pressed={revealed}
                    className="text-text-muted hover:text-text shrink-0 cursor-pointer transition-colors"
                  >
                    {revealed ? (
                      <EyeIcon className="h-4.5 w-4.5" />
                    ) : (
                      <EyeOffIcon className="h-4.5 w-4.5" />
                    )}
                  </button>
                </div>
              )}
            </UnderlineField>

            <UnderlineField label="Repetir la nueva" error={errors.confirmation}>
              {(props) => (
                <input
                  {...props}
                  type="password"
                  autoComplete="new-password"
                  value={confirmation}
                  onChange={(event) => setConfirmation(event.target.value)}
                />
              )}
            </UnderlineField>
          </div>
        </fieldset>

        {failure !== null && (
          <p className="text-danger text-sm" role="alert">
            {failure}
          </p>
        )}

        <div className="flex items-center gap-3">
          {/*
            El botón cambia con el tema por partida doble: el relleno es el par de verdes de la
            marca —distinto en claro y en oscuro— y el texto es `text-on-brand`, oscuro sobre el
            verde encendido y blanco sobre el apagado. Un color fijo pierde en uno de los dos.
          */}
          <button
            type="submit"
            disabled={saving}
            className="brand-fill text-on-brand cursor-pointer rounded-xl px-5 py-2.5 text-sm font-semibold disabled:opacity-60"
          >
            {saving ? 'Guardando…' : 'Aceptar cambios'}
          </button>
          <button
            type="button"
            onClick={() => void navigate('..', { relative: 'path' })}
            className="text-text-muted hover:text-text cursor-pointer px-2 py-2.5 text-sm font-semibold transition-colors"
          >
            Cancelar
          </button>
        </div>
      </form>
    </ProfileCard>
  )
}

/**
 * El campo del auto: muestra el elegido y abre el selector visual.
 *
 * **El campo en sí no elige nada, y por eso es un botón y no un control.** Elegir pasa adentro
 * del diálogo —marca, modelo, ficha—, que necesita bastante más lugar del que hay en un renglón
 * de un formulario. Lo que queda acá es lo que hace falta ver sin abrir nada: qué auto está
 * puesto.
 *
 * **Reemplazó a un `<select>`, y no fue solo estética.** Un desplegable muestra los modelos como
 * renglones de texto iguales: para encontrar el suyo hay que leerlos uno por uno, y no tiene dónde
 * mostrar el conector ni las potencias, que es lo que esta elección decide. Ver `VehicleDialog`.
 *
 * **Sigue adentro de `UnderlineField` aunque no sea un `<input>`.** Lo que da ese envoltorio es el
 * rótulo que se enciende con el foco, el subrayado y el renglón de ayuda: el formulario tiene que
 * verse como uno solo, y un campo con caja propia entre dos subrayados se lee como de otra
 * pantalla. El botón toma la misma clase que los controles y agrega lo suyo.
 *
 * **El catálogo que no llegó no bloquea el formulario.** El campo queda apagado mientras carga y
 * quien vino a cambiarse la contraseña sigue pudiendo guardar. Si falla, se dice por qué en el
 * mismo lugar donde iría la ayuda: el resto del formulario no tiene la culpa.
 */
function VehiclePicker({
  value,
  onChange,
}: {
  /** El modelo marcado, o `null` para "sin auto". */
  value: number | null
  onChange: (vehicleModelId: number | null) => void
}) {
  const { models, loading, error } = useVehicleModels()
  const [open, setOpen] = useState(false)

  /*
    El modelo elegido se busca en el catálogo por id y no se guarda entero en el formulario. El id
    es lo único que viaja al backend, y tener además una copia de la ficha abre la puerta a que las
    dos se separen: el día que el catálogo corrija una potencia, la copia seguiría mostrando la
    vieja hasta que alguien vuelva a elegir el mismo auto.

    Mientras el catálogo no llegó no se encuentra nada, y ahí el campo dice que está buscando.
  */
  const selected = models.find((model) => model.id === value) ?? null

  const hint =
    error ??
    (loading
      ? 'Buscando los modelos…'
      : 'Define el conector y la potencia con los que la portada busca estaciones.')

  return (
    <>
      <UnderlineField label="Auto" hint={hint}>
        {(props) => (
          <button
            {...props}
            type="button"
            disabled={loading || error !== null}
            onClick={() => {
              setOpen(true)
            }}
            /*
              `aria-haspopup="dialog"` es lo que convierte a esto en "abre una ventana" y no en
              "hace algo": sin eso, quien navega con lector de pantalla escucha el nombre del auto
              y un botón, y no tiene forma de saber que lo que sigue es elegir entre varios.
            */
            aria-haspopup="dialog"
            className={`${props.className} flex cursor-pointer items-center justify-between gap-3 text-left disabled:cursor-not-allowed disabled:opacity-60`}
          >
            {/* Apagado cuando no hay auto: el renglón dice qué falta, no un valor. */}
            <span className={selected === null ? 'text-text-muted/70' : ''}>
              {selected === null ? 'Todavía no elegí' : vehicleLabelOf(selected)}
            </span>
            <ChevronRightIcon className="text-text-muted h-4 w-4 shrink-0" />
          </button>
        )}
      </UnderlineField>

      {open && (
        <VehicleDialog
          current={selected}
          onSelect={(model) => {
            onChange(model.id)
            /*
              Elegir cierra la ventana, **y guarda**: a diferencia del nombre y del avatar, acá
              el gesto ya se confirmó adentro del selector. El porqué está en `persistVehicle`.
            */
            setOpen(false)
          }}
          onClose={() => {
            setOpen(false)
          }}
        />
      )}
    </>
  )
}

/**
 * El elegidor de avatar: una tira de caras que se arrastra de costado.

 * **Es una tira y no una grilla, y la diferencia es el alto.** Veintitrés redondeles de 64
 * píxeles en grilla son cuatro renglones, que adentro de un formulario que ya tiene un nombre,
 * un auto y tres contraseñas empuja el botón de guardar fuera de la pantalla. En una fila que
 * scrollea ocupan uno solo, y no hay nada que comparar entre caras: se elige la que gusta, no la
 * mejor de un conjunto.
 *
 * **El elegido se marca con un anillo, no con un borde.** Un borde ocupa lugar y corre a los
 * vecinos un par de píxeles al cambiar de elección, así que la fila entera tiembla con cada clic.
 * El anillo se dibuja por fuera de la caja y no mueve nada. La separación intermedia —el hueco
 * del color del panel entre la cara y el verde— es lo que evita que el anillo se lea como el
 * borde del propio dibujo.
 *
 * **Son botones de radio, no botones a secas.** Elegir un avatar es elegir UNO entre varios, que
 * es exactamente lo que un grupo de radios anuncia: quien navega con lector de pantalla escucha
 * "3 de 23" y se mueve con las flechas. Con veintitrés botones sueltos tendría que tabular por
 * cada uno para enterarse de que son alternativas de lo mismo.
 *
 * Las flechas aparecen recién en pantalla ancha. En el celular la fila se empuja con el dedo, y
 * dos botones ahí se comen el ancho de tres caras para hacer lo que ya se hace arrastrando.
 */
function AvatarPicker({
  value,
  onChange,
}: {
  /** El avatar marcado, o `null` si todavía no se eligió ninguno. */
  value: string | null
  onChange: (avatarId: string) => void
}) {
  const trackRef = useRef<HTMLDivElement>(null)

  /*
    La fila arranca mostrando el elegido. Sin esto, quien ya tiene el avatar veinte abre el
    formulario, ve seis caras ninguna marcada y concluye que no eligió nada: lo suyo está a tres
    pantallas de scroll a la derecha.

    `block: 'nearest'` es lo que mantiene el efecto adentro de la fila. Sin él el navegador
    tambien scrollea la tarjeta del perfil para centrar el redondel verticalmente, y el formulario
    se abre con el titulo ya pasado de largo.

    Corre una sola vez, al montar: encadenarlo a `value` haría saltar la fila con cada clic,
    incluso al elegir una cara que ya estaba a la vista.
  */
  useEffect(() => {
    const track = trackRef.current
    if (track === null || value === null) return

    const selected = track.querySelector(`[data-avatar="${value}"]`)
    selected?.scrollIntoView({ block: 'nearest', inline: 'center' })
    /* eslint-disable-next-line react-hooks/exhaustive-deps -- solo al montar; ver arriba. */
  }, [])

  /** Corre la fila casi una pantalla, dejando una cara de las que ya estaban como referencia. */
  function scrollBy(direction: 1 | -1): void {
    const track = trackRef.current
    if (track === null) return
    track.scrollBy({ left: direction * (track.clientWidth - 72), behavior: 'smooth' })
  }

  return (
    /*
      `min-w-0` acá arriba y no solo en la tira: por omisión un elemento flexible no se achica por
      debajo de su contenido, y el contenido de éste es una fila de veintitrés caras. Sin esto la
      tira no scrollea —se estira— y arrastra el ancho del formulario, de la tarjeta y de la
      pantalla, que termina con una barra de scroll horizontal.
    */
    <div className="flex min-w-0 flex-col gap-1.5">
      <span className="text-sm font-medium">Avatar</span>

      <div className="mt-1 flex items-center gap-2">
        <ScrollArrow direction={-1} onClick={() => scrollBy(-1)} />

        {/*
          `role="radiogroup"` y no un `<fieldset>`: el grupo ya vive adentro del fieldset de los
          datos de la cuenta, y anidar uno adentro de otro le agrega al lector de pantalla un
          nivel que no significa nada.

          El `py-1.5 -my-1.5` es para el anillo del elegido, igual que el de los filtros del mapa
          es para la sombra: un contenedor que scrollea de costado recorta también arriba y abajo,
          y sin lugar de sobra el anillo queda cortado al ras contra el borde de la fila.
        */}
        <div
          ref={trackRef}
          role="radiogroup"
          aria-label="Elegí tu avatar"
          className="no-scrollbar -my-1.5 flex min-w-0 flex-1 gap-3 overflow-x-auto py-1.5"
        >
          {AVATAR_IDS.map((id, index) => {
            const selected = id === value

            return (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={`Avatar ${String(index + 1)} de ${String(AVATAR_IDS.length)}`}
                data-avatar={id}
                onClick={() => onChange(id)}
                /*
                  Solo el elegido queda tabulable, que es cómo funciona un grupo de radios: se
                  entra al grupo con Tab y se cambia de opción con las flechas, que el navegador ya
                  maneja. Mientras no hay ninguno elegido entra el primero, o el grupo quedaría
                  fuera del alcance del teclado justo cuando todavía no se eligió nada.
                */
                tabIndex={selected || (value === null && index === 0) ? 0 : -1}
                className={`h-16 w-16 shrink-0 cursor-pointer rounded-full transition-[box-shadow] ${
                  selected
                    ? 'ring-primary ring-offset-surface ring-2 ring-offset-2'
                    : 'hover:ring-primary/40 hover:ring-offset-surface hover:ring-2 hover:ring-offset-2'
                }`}
              >
                <img src={avatarSrc(id)} alt="" className="h-16 w-16 rounded-full" />
              </button>
            )
          })}
        </div>

        <ScrollArrow direction={1} onClick={() => scrollBy(1)} />
      </div>

      <p className="text-text-muted mt-1 text-xs">
        Se guarda en este navegador: entrando desde otra máquina vas a ver el que tengas ahí.
      </p>
    </div>
  )
}

/**
 * Una de las dos flechas que corren la tira.
 *
 * La izquierda es la misma de la derecha dada vuelta: el juego de íconos tiene un solo cheurón y
 * agregarle el espejado sería dibujar dos veces la misma forma.
 *
 * `aria-hidden` sobre las dos porque no llevan a ninguna parte nueva: todo lo que muestran ya está
 * en el grupo de radios, al que el teclado llega con las flechas sin tocar estos botones. Anunciar
 * "siguiente" y "anterior" agregaría dos paradas que no eligen nada.
 */
function ScrollArrow({ direction, onClick }: { direction: 1 | -1; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-hidden="true"
      tabIndex={-1}
      className="text-text-muted hover:text-text hover:border-primary/60 border-border hidden h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full border transition-colors md:flex"
    >
      <ChevronRightIcon className={`h-4 w-4 ${direction === -1 ? 'rotate-180' : ''}`} />
    </button>
  )
}
