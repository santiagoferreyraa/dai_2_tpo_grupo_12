import { useEffect, useState } from 'react'

import BottomSheet from '@/components/BottomSheet'
import ConfirmDialog from '@/components/ConfirmDialog'
import { ApiError } from '@/lib/api'
import { useMediaQuery } from '@/lib/useMediaQuery'

import CardForm from './components/CardForm'
import CardStack from './components/CardStack'
import PaymentMethodRow from './components/PaymentMethodRow'
import { listCards, registerCard, removeCard } from './data/paymentMethodsRepository'
import { describeCard } from './format'
import type { PaymentMethod, RegisterCardInput } from './types'

/**
 * Los medios de pago del conductor (RF02, ECO-26).
 *
 * **Por qué esta pantalla importa más de lo que parece.** Tener al menos una tarjeta vigente es
 * precondición de toda transacción del sistema: sin eso no se reserva (RF08) ni se inicia una
 * carga (RF10). Es la única pantalla que puede desbloquear al conductor, así que cuando la
 * lista está vacía no se limita a estar vacía — lo dice y ofrece la salida.
 *
 * **Tiene dos formas, y la diferencia no es de estilos.** En pantalla ancha las tarjetas van en
 * una lista y el alta se abre en su lugar; en celular se apilan como en una billetera y el alta
 * sube desde abajo, igual que el detalle de una estación en el ABM. No alcanza con esconder una
 * de las dos por CSS: el panel seguiría montado, bloqueando el scroll del fondo y atrapando el
 * foco. Por eso decide `useMediaQuery` y se renderiza una sola.
 *
 * **El estado se recarga del servidor después de cada cambio.** El alta devuelve la tarjeta
 * creada y se la podría agregar a la lista en memoria; la baja, quitarla. Se prefiere volver a
 * pedir: las reglas de qué se lista y qué no —vigentes sí, dadas de baja no, el orden— viven en
 * el backend, y replicarlas acá crea dos definiciones que se desincronizan sin que nadie lo
 * note. Son dos tarjetas: la llamada de más no se siente.
 */
export default function PaymentMethodsPage() {
  const [cards, setCards] = useState<PaymentMethod[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [adding, setAdding] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [removingId, setRemovingId] = useState<number | null>(null)

  /* La tarjeta que el cartel de confirmación está preguntando si eliminar. */
  const [pendingRemoval, setPendingRemoval] = useState<PaymentMethod | null>(null)

  /*
   * El mismo corte que usa el ABM de estaciones. Está escrito acá y no importado de allá porque
   * es una decisión de esta pantalla que hoy coincide, no una regla compartida.
   */
  const wide = useMediaQuery('(min-width: 1024px)')

  /* Se recarga cambiando esta marca. Es lo que dispara el efecto después de un alta o una baja. */
  const [reloadToken, setReloadToken] = useState(0)

  /**
   * Pide de nuevo el listado.
   *
   * `loading` se enciende acá y no adentro del efecto a propósito: encenderlo dentro dispara un
   * render extra en cada pasada —React vuelve a renderizar por un estado que el efecto acaba de
   * cambiar— y además deja la pantalla dependiendo de un orden que no se ve al leerla. El estado
   * lo cambia el evento que lo causó, que es el alta, la baja o el primer montaje.
   */
  function reload() {
    setLoading(true)
    setReloadToken((token) => token + 1)
  }

  useEffect(() => {
    const controller = new AbortController()

    listCards(controller.signal)
      .then((loaded) => {
        setCards(loaded)
        setLoadError(null)
      })
      .catch((cause: unknown) => {
        // Una petición cancelada por desmontaje no es un error que mostrar: la pantalla ya no está.
        if (controller.signal.aborted) return
        setLoadError(
          cause instanceof ApiError ? cause.message : 'No se pudieron cargar tus tarjetas',
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [reloadToken])

  async function handleRegister(input: RegisterCardInput) {
    setSending(true)
    setFormError(null)
    try {
      await registerCard(input)
      setAdding(false)
      reload()
    } catch (cause) {
      // El mensaje viene del backend —"La tarjeta está vencida", "Ese número no es válido"— y se
      // muestra tal cual: es más preciso que cualquier texto genérico que pusiéramos acá.
      setFormError(cause instanceof ApiError ? cause.message : 'No se pudo guardar la tarjeta')
    } finally {
      setSending(false)
    }
  }

  /**
   * Ejecuta la baja que el cartel ya confirmó.
   *
   * Se confirma porque no se deshace desde acá y porque, en una pila de tarjetas parecidas, el
   * botón está a un toque de distancia. El texto del cartel nombra la tarjeta: un "¿Estás
   * seguro?" pelado no ayuda a saber cuál se está por borrar.
   */
  async function confirmRemoval() {
    const card = pendingRemoval
    if (card === null) return

    setRemovingId(card.id)
    setLoadError(null)
    try {
      await removeCard(card.id)
      setPendingRemoval(null)
      reload()
    } catch (cause) {
      /*
       * El cartel se cierra igual: el error se muestra en la pantalla, detrás. Dejarlo abierto
       * con el mensaje adentro invita a reintentar la misma llamada que acaba de fallar.
       */
      setPendingRemoval(null)
      setLoadError(cause instanceof ApiError ? cause.message : 'No se pudo eliminar la tarjeta')
    } finally {
      setRemovingId(null)
    }
  }

  const hasUsableCard = cards.some((card) => !card.expired)

  function closeForm() {
    setAdding(false)
    setFormError(null)
  }

  /*
   * El formulario se arma una sola vez y se ubica en los dos lugares. Escribirlo dos veces
   * —una en la tarjeta de escritorio y otra adentro del panel— es tenerlo que cambiar dos
   * veces cada vez que se le agrega un campo.
   */
  const form = (
    <CardForm onSubmit={handleRegister} onCancel={closeForm} error={formError} sending={sending} />
  )

  return (
    /*
      Sin marco propio —ni ancho máximo, ni relleno, ni scroll—: esta pantalla ya no se monta
      sola en una ruta, se dibuja adentro de la tarjeta de la sección "Medios de pago" del
      perfil, y esa tarjeta pone las tres cosas. Con las suyas encima quedaba una columna
      angosta y centrada adentro de un recuadro ancho, con doble relleno.
    */
    <section className="flex w-full flex-col gap-6">
      <header>
        <h2 className="text-text text-lg font-extrabold tracking-tight">Medios de pago</h2>
        <p className="text-text-muted mt-1 text-sm">
          Necesitás al menos una tarjeta vigente para reservar un conector o iniciar una carga.
        </p>
      </header>

      {/*
        El aviso de que falta una tarjeta vigente se muestra también cuando la lista NO está
        vacía: un conductor con dos tarjetas vencidas ve filas en pantalla y no tiene por qué
        deducir solo que ninguna le sirve.
      */}
      {!loading && !hasUsableCard && (
        <p
          className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-4 text-sm"
          role="status"
        >
          No tenés ninguna tarjeta vigente, así que todavía no podés reservar ni cargar.
        </p>
      )}

      {loadError !== null && (
        <p className="text-sm text-red-600" role="alert">
          {loadError}
        </p>
      )}

      {loading ? (
        <p className="text-text-muted text-sm">Cargando tus tarjetas…</p>
      ) : wide ? (
        <ul className="flex flex-col gap-3">
          {cards.map((card) => (
            <PaymentMethodRow
              key={card.id}
              card={card}
              onRemove={setPendingRemoval}
              removing={removingId === card.id}
            />
          ))}
        </ul>
      ) : (
        <CardStack cards={cards} onRemove={setPendingRemoval} />
      )}

      {/*
        En pantalla ancha el formulario se abre en su lugar, abajo del listado: hay sitio de
        sobra y taparle al usuario lo que ya tiene sería esconder información sin motivo. En
        celular no hay sitio, así que sube desde abajo como el detalle de una estación.
      */}
      {wide && adding ? (
        /*
          El radio de las tarjetas de la portada, con el relleno de un panel de adentro: este
          recuadro vive sobre la tarjeta de la sección, que ya es de vidrio, y un vidrio sobre
          otro suma blanco. Ver `.glass-inset` en `profile/profile.css`.
        */
        <div className="glass-inset rounded-3xl p-6">
          <h2 className="mb-4 text-lg font-semibold">Nueva tarjeta</h2>
          {form}
        </div>
      ) : (
        /*
          El punteado va en el verde de la marca y no en el gris del borde. Es el único llamado a
          la acción de la pantalla cuando no hay ninguna tarjeta —sin esto no se puede reservar ni
          cargar—, y en gris sobre el vidrio apenas se distinguía del fondo. El relleno se enciende
          recién al pasar por encima: lleno desde el principio competiría con las tarjetas que ya
          están en la lista.
        */
        <button
          /*
            `self-center` para que mida lo que dice y no el ancho de la tarjeta de la sección, y
            quede centrado en ella en las dos resoluciones.
            En un monitor ancho el punteado cruzaba la pantalla entera para contener dos palabras
            centradas, y un recuadro vacío de ese tamaño se lee como un lugar donde falta algo,
            no como un botón. Con el ancho del contenido, el punteado vuelve a ser el borde de una
            acción.
          */
          className="border-primary/60 text-primary hover:bg-primary/10 hover:border-primary self-center rounded-2xl border-2 border-dashed px-6 py-3.5 text-sm font-semibold transition-colors"
          type="button"
          onClick={() => setAdding(true)}
        >
          Agregar tarjeta
        </button>
      )}

      <BottomSheet
        open={!wide && adding}
        onClose={closeForm}
        label="Nueva tarjeta"
        // Los tokens compartidos, no los `st-*` oscuros del ABM: esta pantalla es clara.
        backgroundClass="bg-surface"
      >
        <h2 className="mb-4 text-lg font-semibold">Nueva tarjeta</h2>
        {form}
      </BottomSheet>

      <ConfirmDialog
        open={pendingRemoval !== null}
        title="¿Estás seguro?"
        message={
          pendingRemoval === null
            ? ''
            : `Vas a eliminar ${describeCard(pendingRemoval.brand, pendingRemoval.lastFour)}. Para volver a usarla vas a tener que cargarla de nuevo.`
        }
        confirmLabel="Eliminar"
        onConfirm={confirmRemoval}
        onCancel={() => setPendingRemoval(null)}
        busy={removingId !== null}
      />
    </section>
  )
}
