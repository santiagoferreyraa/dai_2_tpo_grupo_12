import { useState } from 'react'

import { BoltIcon } from '@/features/navigation/icons'

import { EV_FACTS } from '../data/evFacts'

/**
 * Una curiosidad sobre autos eléctricos. Ocupa el lugar del tiempo de carga cuando no hay sesión.
 *
 * **Por qué el tiempo de carga no puede estar ahí sin sesión.** Ese recuadro dice "TU auto tarda
 * tanto", y el auto sale del perfil: sin cuenta no hay perfil del que sacarlo. Su cara de "elegí
 * tu auto" tampoco sirve acá, porque manda al formulario del perfil, que es justamente a donde
 * quien no entró no puede ir. La curiosidad, en cambio, es cierta para cualquiera.
 *
 * **Y por qué se reemplaza en vez de sacarse.** La fila del medio son tres recuadros y el ancho
 * está repartido entre ellos: quitando uno, los otros dos se estiran y la columna de la derecha
 * deja de cerrar contra el mapa chico. Además, el hueco tendría que explicarse igual, y un
 * cartel que dice "entrá para ver esto" es peor visita que algo que se puede leer.
 *
 * **Se elige una sola vez, al montar.** Con el sorteo en el cuerpo del componente la frase
 * cambiaría en cada render —y esta pantalla vuelve a renderizar cada vez que llegan las
 * estaciones—, así que el texto se sacudiría solo mientras alguien lo está leyendo. Dentro del
 * inicializador de `useState` el sorteo corre una vez y el resultado queda fijo hasta que se
 * vuelve a entrar a la portada, que es cuando tiene sentido que haya algo nuevo.
 */
export default function EvFactCard({ className = '' }: { className?: string }) {
  const [fact] = useState(() => EV_FACTS[Math.floor(Math.random() * EV_FACTS.length)])

  return (
    /* El mismo vidrio, el mismo radio y la misma fila que `ChargeTimeCard`: es su reemplazo en la
       grilla, así que la fila del medio tiene que seguir midiendo lo mismo. */
    <article className={`glass-panel flex items-center gap-5 rounded-3xl p-6 ${className}`}>
      {/*
        El rayo, suelto y grande como el reloj al que reemplaza. Acá sí es el símbolo correcto:
        el recuadro no habla de una estación ni de un auto en particular, habla de lo eléctrico.
      */}
      <BoltIcon className="text-primary h-16 w-16 shrink-0" />

      <div className="min-w-0">
        <p className="text-text text-lg leading-snug font-bold text-balance">
          {fact.lead} <span className="text-primary">{fact.highlight}</span>
          {fact.tail}
        </p>
        <p className="text-text-muted mt-1 text-sm">{fact.detail}</p>
        {/*
          El renglón chico que en el otro recuadro aclaraba el margen del estimado, acá dice de
          qué se trata esto: es una curiosidad, no un dato de la cuenta de nadie. Sin él, un
          titular con un número en verde en el mismo lugar donde antes había una medición se lee
          como si fuera una.
        */}
        <p className="text-text-muted mt-1 text-xs">
          Entrá a tu cuenta para ver acá cuánto tarda en cargar tu auto.
        </p>
      </div>
    </article>
  )
}
