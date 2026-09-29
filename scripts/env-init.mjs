#!/usr/bin/env node

/*
 * Genera el .env de la raíz a partir de .env.example, con secretos aleatorios.
 *
 * Cada línea de .env.example cuyo valor sea `generar` recibe un valor al azar, sacado del
 * generador criptográfico de Node. El resto se copia tal cual. Así la plantilla es la única
 * lista de variables, y agregar una nueva es agregarla ahí.
 *
 * Los valores van en hexadecimal a propósito: no tienen caracteres que el shell, docker compose o
 * la línea de comandos de Artemis interpreten. Un valor que arranque con `-`, por ejemplo, lo
 * leería como una opción.
 *
 * Nunca pisa un .env existente. Cambiar el secreto del JWT desloguea a todo el mundo, y cambiar
 * las contraseñas deja afuera a una base que ya se creó con las anteriores (ver el README).
 */

import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'

const TEMPLATE = '.env.example'
const TARGET = '.env'

if (existsSync(TARGET)) {
  console.error(`Ya hay un ${TARGET}: no se pisa. Si querés regenerarlo, borralo primero.`)
  process.exit(1)
}

/*
 * La firma del JWT lleva 48 bytes al azar: en hexadecimal son 96 caracteres, y la clave se arma
 * con esos caracteres, así que pasa de sobra los 64 bytes que pide HS512.
 */
const SIZES = { ECOPEDIA_JWT_SECRET: 48 }
const DEFAULT_SIZE = 24

const lines = readFileSync(TEMPLATE, 'utf8')
  .split(/\r?\n/)
  .map((line) => {
    const match = /^([A-Z0-9_]+)=generar$/.exec(line)
    if (!match) return line
    const name = match[1]
    return `${name}=${randomBytes(SIZES[name] ?? DEFAULT_SIZE).toString('hex')}`
  })

writeFileSync(TARGET, lines.join('\n'))
console.log(`Listo: ${TARGET} generado con valores nuevos. No se versiona (está en el .gitignore).`)
