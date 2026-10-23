#!/usr/bin/env node

/*
 * Corre un comando con las variables del .env de la raíz cargadas en el entorno.
 *
 * Existe porque Spring no lee archivos .env: los secretos (la firma del JWT, las contraseñas de
 * la base y del broker) tienen que llegarle como variables de entorno. Así los scripts de
 * package.json los pasan sin que nadie tenga que exportarlos a mano en cada terminal, y sin que
 * los valores terminen escritos en un archivo versionado.
 *
 * Si no hay .env, el comando corre igual. En los perfiles `local` y `dev` no hace falta, porque
 * traen valores de desarrollo; sin perfil (`pnpm demo`, el JAR) el backend aborta el arranque
 * con "Could not resolve placeholder", que es justo lo que tiene que pasar. El .env se genera con
 * `pnpm env:init`.
 *
 * Una variable que ya esté definida en el entorno le gana al .env.
 */

import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'

const command = process.argv.slice(2)

if (command.length === 0) {
  console.error('Uso: node scripts/with-env.mjs <comando> [argumentos...]')
  process.exit(1)
}

if (existsSync('.env')) {
  process.loadEnvFile('.env')
}

/*
 * Una sola cadena con `shell: true`: en Windows `mvn` es un .cmd y solo lo encuentra el shell.
 * Pasar los argumentos como lista junto con el shell es lo que Node desaconseja (DEP0190), y
 * acá no hace falta, porque los comandos salen de package.json y no llevan espacios.
 */
const child = spawn(command.join(' '), { stdio: 'inherit', shell: true })

child.on('exit', (code, signal) => {
  process.exit(code ?? (signal ? 1 : 0))
})
