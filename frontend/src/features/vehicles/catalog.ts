import { useEffect, useState } from 'react'

import { listVehicleModels } from './data/vehicleModelsRepository'
import type { VehicleModel } from './types'

/**
 * El catálogo de modelos, pedido UNA vez por carga de la aplicación y compartido.
 *
 * **La caché vive en el módulo y no en un estado de React**, por lo mismo que el avatar y la
 * sesión: el selector del perfil no es el único lugar que lo puede querer, y un estado dentro de
 * un componente vuelve a pedirlo cada vez que esa pantalla se monta. Entrar a editar el perfil,
 * salir y volver a entrar son tres viajes a la red para traer las mismas veinte filas.
 *
 * **Es una caché para siempre, sin invalidación, y está bien que lo sea.** El catálogo cambia
 * cuando alguien corre una migración nueva, o sea entre dos despliegues: dentro de una visita no
 * hay nada que refrescar. Recargar la página lo vuelve a pedir, que es exactamente cuándo puede
 * haber cambiado.
 */
let cache: VehicleModel[] | null = null

/**
 * La petición en vuelo, mientras la hay.
 *
 * **Sin esto, dos componentes que monten en el mismo cuadro piden el catálogo dos veces**: el
 * segundo mira `cache`, la ve vacía porque el primero todavía no volvió, y arranca su propia
 * llamada. Guardando la promesa, el segundo se cuelga de la que ya está viajando.
 */
let inFlight: Promise<VehicleModel[]> | null = null

function loadCatalog(): Promise<VehicleModel[]> {
  if (cache !== null) return Promise.resolve(cache)

  inFlight ??= listVehicleModels()
    .then((models) => {
      cache = models
      return models
    })
    .finally(() => {
      /*
        La promesa se suelta pase lo que pase. Si falló, dejarla guardada haría que todo el que
        pregunte después reciba el mismo error para siempre, sin llegar a reintentar nunca.
      */
      inFlight = null
    })

  return inFlight
}

export interface VehicleCatalog {
  models: VehicleModel[]
  loading: boolean
  error: string | null
}

/** El catálogo para una pantalla. Vuelve a renderizar cuando llega. */
export function useVehicleModels(): VehicleCatalog {
  const [models, setModels] = useState<VehicleModel[]>(cache ?? [])
  /* Con la caché ya llena no hay nada que esperar: el primer render tiene que salir con la lista. */
  const [loading, setLoading] = useState(cache === null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (cache !== null) return

    /*
      `cancelled` y no un `AbortController`: la petición es compartida, así que cancelarla porque
      ESTA pantalla se desmontó le rompería la carga a cualquier otra que esté esperando la misma
      promesa. Lo que hay que evitar es escribir en un componente que ya no está, y para eso
      alcanza con no llamar a los setters.
    */
    let cancelled = false

    loadCatalog()
      .then((loaded) => {
        if (!cancelled) setModels(loaded)
      })
      .catch((cause: unknown) => {
        if (cancelled) return
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar el catálogo de autos')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  return { models, loading, error }
}

/**
 * Los modelos agrupados por marca, en el orden en que vinieron.
 *
 * El backend ya los manda ordenados por marca y después por modelo, así que agrupar es recorrer
 * la lista una vez y no ordenar de nuevo. Lo usa el selector para dibujar los `<optgroup>`: con
 * veinte modelos de nueve marcas, una lista plana obliga a leerlos todos para encontrar el suyo.
 */
export function groupByBrand(models: VehicleModel[]): [string, VehicleModel[]][] {
  const groups = new Map<string, VehicleModel[]>()

  for (const model of models) {
    const brand = groups.get(model.brand)
    if (brand === undefined) groups.set(model.brand, [model])
    else brand.push(model)
  }

  return [...groups]
}
