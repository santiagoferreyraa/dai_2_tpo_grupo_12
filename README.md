# Ecopedia

Plataforma de gestión, reserva y tarificación de estaciones de carga rápida para vehículos eléctricos.

TP Integrador — **Desarrollo de Aplicaciones II** (3.4.218, Comisión Lunes TM, 2.º Cuatrimestre 2026).

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Backend | Spring Boot 3.5 · Java 21 |
| Persistencia | PostgreSQL 16 · Spring Data JPA |
| Mensajería | ActiveMQ Artemis (JMS) |
| SOAP | Spring-WS (contract-first) |
| Frontend | React 19 · TypeScript · Vite · TailwindCSS 4 |
| Infra local | Docker Compose |

---

## Requisitos

- JDK 21 o superior
- Maven 3.9+
- Node 20+
- pnpm 11+ *(`npm install -g pnpm`)*
- Docker Desktop *(opcional — para el ambiente de la demo, ver "Los tres ambientes", o para correr
  todo sin instalar nada más, ver "Todo con Docker")*

---

## Cómo levantar el proyecto

### El camino corto

Una sola vez, después de clonar:

```bash
pnpm install                  # en la raíz: el lanzador
pnpm --dir frontend install   # el frontend
```

Y de ahí en más, **un solo comando levanta todo**:

```bash
pnpm dev
```

Backend y frontend arrancan juntos en la misma terminal, pero **cada uno con su panel**: la
lista de procesos queda a la izquierda y el log del que esté seleccionado ocupa el resto, con
su propio scroll. Se cambia de uno a otro con las flechas, y las teclas están siempre a la
vista en el recuadro de ayuda. Es [mprocs](https://www.npmjs.com/package/mprocs), y se
configura en `mprocs.yaml`.

Lo que más se usa: **reiniciar un solo proceso sin bajar el otro**. Tocaste una clase del
backend y querés relevantarlo sin perder el estado del navegador — lo reiniciás desde el
panel y Vite ni se entera.

El frontend queda en http://localhost:5173. **Se navega siempre por el 5173:** el proxy ya está
configurado y reparte `/api` entre los backends.

**Son cuatro procesos, porque son cuatro artefactos desplegables**: `ecopedia-core` en el 8081
(usuarios, estaciones), `ecopedia-charging` en el 8082 (reservas), `ecopedia-integration` en el
8083 (medios de pago) y `ecopedia-async` en el 8084 (notificaciones). Ver ARQUITECTURA §6.4. El
proxy de Vite manda `/api/bookings` al 8082, `/api/payment-methods` al 8083,
`/api/notifications` al 8084 y todo lo demás al 8081, así que desde el navegador se ve como una
sola API.

**Del sexto al octavo proceso no son Ecopedia:** son los sistemas externos simulados. La pasarela
de pagos, en el 8086 (`gateway` en el panel), a la que Pagos le habla por REST; la distribuidora
eléctrica, en el 8087 (`grid`), a la que RedEléctrica le pregunta por SOAP antes de una carga
rápida; y los cargadores, en el 8088 (`charger`), que Terminales enciende y apaga por REST y que
publican la telemetría en el broker. El navegador nunca les habla directo. Ver "La pasarela de pagos
simulada", "La distribuidora eléctrica simulada" y "Los cargadores simulados", más abajo.

| Comando | Qué hace |
|---------|----------|
| `pnpm dev` | Los ocho procesos, cada uno en su panel |
| `pnpm dev:back` | Solo `ecopedia-core` (8081) |
| `pnpm dev:charging` | Solo Reservas (`ecopedia-charging`, 8082). Necesita el backend arriba |
| `pnpm dev:pay` | Solo Pagos (`ecopedia-integration`, 8083), que sirve los medios de pago |
| `pnpm dev:async` | Solo Notificaciones (`ecopedia-async`, 8084). Para recibir avisos necesita el broker: ver "Notificaciones" |
| `pnpm dev:gateway` | Solo la pasarela de pagos simulada (8086). Pagos la necesita para cargar tarjetas |
| `pnpm dev:grid` | Solo la distribuidora eléctrica simulada (8087, SOAP). Todavía no la llama ninguna pantalla |
| `pnpm dev:charger` | Solo los cargadores simulados (8088). Para encender necesitan el broker. Todavía no los llama ninguna pantalla |
| `pnpm dev:front` | Solo el frontend |
| `pnpm dev:plain` | Todos menos Notificaciones en una sola tira de logs, con prefijos `[back]`/`[charging]`/`[pay]`/`[gateway]`/`[grid]`/`[charger]`/`[front]` |
| `pnpm dev:mem` | Los ocho, pero con las bases **en memoria**: se borra todo al bajar |
| `pnpm demo` | Los ocho contra **PostgreSQL** (la pasarela, con su propia base en archivo; la distribuidora y los cargadores no tienen base). Necesita la infraestructura arriba |
| `pnpm free-ports` | Libera el 8081, el 8082, el 8083, el 8084, el 8086, el 8087, el 8088 y el 5173 a mano |
| `pnpm build` | Empaqueta el frontend adentro del JAR del backend |
| `pnpm start` | Corre ese JAR |

`pnpm dev` levanta los backends y la pasarela con el perfil `local`: **bases H2 en un archivo de tu
disco, sin PostgreSQL instalado y sin Docker.** Lo que cargues sobrevive a bajar y volver a
levantar. Consolas de H2: http://localhost:8081/h2-console, http://localhost:8082/h2-console y
http://localhost:8083/h2-console

Los backends validan el mismo token, así que tienen que compartir el secreto de firma: sale de
la variable `ECOPEDIA_JWT_SECRET`. Con `pnpm dev` y `pnpm dev:mem` no hace falta definirla,
porque los perfiles `local` y `dev` traen un valor de desarrollo. **Sin perfil no hay valor por
defecto**: ver "El `.env`", más abajo.

**`pnpm start` todavía no llega a Reservas:** sirve front y API desde el JAR de core, en el 8081,
y ahí no hay nada escuchando `/api/bookings`. Para probar reservas se usa `pnpm dev`.

**`pnpm dev` no levanta Notificaciones ni necesita Docker.** Reservas publica los avisos en el
broker, y si el broker no está, la reserva se confirma igual y el aviso queda en el log de
`charging`. Para trabajar sobre las notificaciones, ver "Notificaciones" más abajo.

### Todo con Docker

Para correr el sistema entero **sin instalar JDK, Maven, Node ni pnpm**: solo Git y Docker (Docker
Desktop, o Docker Engine con `docker compose`). Es lo que corre el servidor de la demo.

```bash
git clone https://github.com/santiagoferreyraa/dai_2_tpo_grupo_12.git
cd dai_2_tpo_grupo_12
node scripts/env-init.mjs     # = pnpm env:init. Sin Node: ver abajo
docker compose up -d --build
```

Y se entra por **http://localhost**. La primera vez compila todo adentro de Docker: en una PC
de desarrollo, unos 4 minutos entre construir y arrancar (más si tiene que bajar las imágenes
base, o en una máquina chica). Las siguientes, con la caché, alrededor de un minuto. Los primeros minutos el navegador puede
ver **502**: la página ya está, pero los módulos todavía arrancan.

**Sin Node en la máquina**, el `.env` se genera con el mismo script, dentro de un contenedor:

```bash
docker run --rm -v "$PWD":/app -w /app node:24-alpine node scripts/env-init.mjs          # bash
docker run --rm -v "${PWD}:/app" -w /app node:24-alpine node scripts/env-init.mjs        # PowerShell
```

**Qué levanta.** Once contenedores: PostgreSQL, Artemis, los cuatro módulos, los tres simuladores y
`web`, que es [Caddy](https://caddyserver.com/) sirviendo el front y repartiendo `/api` entre los
módulos con las mismas reglas que el proxy de Vite (`docker/Caddyfile`). **Hacia afuera solo se
publica `web`**; los módulos y los simuladores se hablan por la red interna de compose, y la base y el
broker quedan en `127.0.0.1`, como siempre. Corre sin perfil de Spring, o sea contra PostgreSQL:
es el mismo ambiente que `pnpm demo`, **con los mismos volúmenes**, así que comparten los datos.

| Comando | Qué hace |
|---|---|
| `docker compose ps` | El estado de los once. Los Java pasan a `healthy` cuando escuchan |
| `docker compose logs -f core` | El log de uno (`charging`, `integration`, `async`, `payment-gateway`, `power-grid`, `charger`, `web`...) |
| `docker compose down` | Baja todo. Los datos quedan en los volúmenes |
| `git pull && docker compose up -d --build` | Actualiza a lo último |

**El primer `ADMIN`** se crea igual que en "Promover un usuario a `CPO`": registrarse desde la web y
cambiar el rol por SQL, con `role = 'ADMIN'`, por el `docker exec ... psql` del ambiente compartido.

**No mezclarlo con `pnpm demo`.** Para `pnpm demo`, la infraestructura se levanta nombrando los dos
servicios (`docker compose up -d postgres activemq`, ver "Con PostgreSQL"). Con las dos cosas arriba
quedan dos copias de cada módulo contra la misma base y el mismo broker.

**Si el 80 está ocupado** en tu máquina, agregá `ECOPEDIA_HTTP_PORT=8080` al `.env` y entrá por
http://localhost:8080.

**En un servidor, con dominio:** `ECOPEDIA_SITE_ADDRESS=<el dominio>` en el `.env`, el dominio
apuntando a la máquina y los puertos 80 y 443 abiertos. Caddy saca el certificado de Let's Encrypt
solo y atiende en HTTPS. Los certificados se guardan en un volumen, así que un `up` nuevo no pide
otro.

**Memoria:** cada JVM tiene un techo de 384 MB (`JAVA_TOOL_OPTIONS` en `docker-compose.yml`). Con
todo arriba y en reposo, los once ocupan unos 2,7 GB; a Docker Desktop conviene darle al menos 6 GB.

### Los tres ambientes

El proyecto corre contra tres bases distintas, y la diferencia entre ellas es **quién ve los datos
y cuánto duran**. El esquema es siempre el mismo: lo construyen las mismas migraciones de Flyway,
así que no hay forma de que se bifurque.

| Ambiente | Comando | Motor | Los datos | ¿Docker? |
|---|---|---|---|---|
| **Propio** | `pnpm dev` | H2 en archivo | tuyos, **sobreviven** al reinicio | no |
| **Descartable** | `pnpm dev:mem` | H2 en memoria | se borran al bajar | no |
| **Compartido** | `pnpm demo` | PostgreSQL | únicos, los ve todo el que apunte ahí | sí (o PostgreSQL instalado) |

**El de todos los días es `pnpm dev`.** Te registrás una vez, promovés tu usuario a `CPO` una vez,
y eso queda. Las bases viven en `backend/ecopedia-core/data/`, `backend/ecopedia-charging/data/` y
`backend/ecopedia-integration/data/`, que están en el `.gitignore`: **cada integrante tiene sus
propios datos** y nadie ve los del otro. Un módulo por archivo, porque cada uno gobierna su
esquema y su propio historial de Flyway.

**`pnpm dev:mem` es para empezar de cero.** Es el perfil `dev`, el mismo que usan los tests. Sirve
sobre todo cuando estás tocando una migración: ver abajo.

**`pnpm demo` es el ambiente de las entregas**, y el único que puede compartirse entre dos
máquinas. Es sobre el que hay que ensayar la demo, aunque el desarrollo no lo use.

#### Empezar de cero

Cada ambiente se resetea distinto:

```bash
rm -rf backend/*/data              # propio: borra las bases H2 de los dos módulos
                                   # descartable: no hace falta, se borra solo al bajar
docker compose down -v             # compartido: borra el volumen de PostgreSQL
```

**Cuándo lo vas a necesitar:** una migración que ya se aplicó **no se puede editar**. Flyway le
guarda el checksum y, si el archivo cambia, aborta el arranque con `Migration checksum mismatch`.
Es la contracara de que los datos persistan, y es la misma regla que rige en PostgreSQL — por eso
conviene acostumbrarse acá y no descubrirla la semana de la entrega. Mientras estés iterando sobre
una migración, `pnpm dev:mem` te evita el borrado en cada vuelta.

#### Promover un usuario a `CPO`

Registrarse deja el rol `CONDUCTOR`, así que `/stations` rebota a `/forbidden`. El ABM pide `CPO`,
y el rol se cambia a mano. La consulta es la misma en los tres; cambia por dónde entrás:

```sql
update core.users set role = 'CPO' where email = 'el-tuyo@ejemplo.com';
```

- **Propio y descartable:** http://localhost:8081/h2-console, con la URL que el log del arranque
  imprime en la línea `Database available at`, usuario `sa` y la contraseña vacía.
- **Compartido:** `docker exec -it ecopedia-postgres psql -U ecopedia -d ecopedia`

En el ambiente propio se hace **una sola vez**. En el descartable, en cada arranque — y ése es
justo el momento en que algo sale mal delante del docente, así que la demo no se hace ahí.

**Un `CPO` ve y toca solo sus estaciones**: las que dio de alta él. El backend le responde 403
ante cualquier cambio sobre una ajena, y `/stations` le muestra únicamente las propias. Las 15
del seed son del usuario con id 1 —el primero que se registró en esa base—; el `ADMIN` las
administra todas desde `/admin`.

### Si el puerto quedó tomado

No debería pasar, porque `dev:back` y `dev:front` liberan su puerto antes de arrancar. Pero
conviene saber por qué existe esa precaución, que es contraintuitiva.

`mvn spring-boot:run` **no corre la aplicación: la lanza en una segunda JVM**. Cuando se corta
el lanzador con Ctrl+C, la señal llega arriba de la cadena —`pnpm` → `mprocs` → shell → `mvn`
→ `java`— y en Windows los nietos no la reciben: la aplicación queda viva, con el 8081 tomado
y su base H2 adentro. La terminal ya se cerró, así que el próximo arranque falla con
`Port 8081 was already in use` y nada en pantalla explica de dónde sale.

Si hiciera falta destrabarlo a mano:

```bash
pnpm free-ports
```

**Lo que no hay que hacer es matar `java.exe` por nombre:** en Windows se lleva puesto el
servidor de lenguaje de VS Code, y el editor se queda sin autocompletado ni errores hasta que
se lo reinicia. `pnpm free-ports` mata únicamente al proceso que escucha el puerto.

### Con PostgreSQL

H2 alcanza para desarrollar, pero **las demos de las entregas corren sobre PostgreSQL**, que es
lo que se documenta y se defiende. Primero, una sola vez, el `.env` (ver abajo):

```bash
pnpm env:init
```

Después la infraestructura:

```bash
docker compose up -d postgres activemq
```

**Nombrando los dos servicios.** Un `docker compose up` a secas levanta el sistema entero en
contenedores (ver "Todo con Docker"), y con `pnpm demo` arriba quedarían dos copias de cada
módulo contra la misma base y el mismo broker.

Deja arriba PostgreSQL (`localhost:5432`) y ActiveMQ Artemis (`localhost:61616`, consola web en
http://localhost:8161/console). **Los dos escuchan solo en `127.0.0.1`**: desde otra máquina de la
red no se llega, y está bien, porque los usan los backends de esta misma. Y después los seis
procesos:

```bash
pnpm demo
```

Levanta backend, Reservas, Pagos, Notificaciones y frontend **sin perfil**, que es el que ya
apunta a PostgreSQL. Si preferís uno solo, `pnpm demo:back`, `pnpm demo:charging`,
`pnpm demo:pay` y `pnpm demo:async`.

**Si la base no está arriba, el backend no arranca.** Es el error más común de este ambiente, y
casi siempre falta el `docker compose up -d postgres activemq`. Se ve en dos lugares distintos y conviene reconocer
los dos:

- **En el panel `back`**, al final de la traza: `java.net.ConnectException: Connection refused`,
  y el proceso termina con `BUILD FAILURE`.
- **En el navegador**, algo menos obvio: **el front levanta igual y cada llamada a `/api`
  devuelve 502.** El proxy de Vite sigue en pie, pero no tiene a quién reenviarle. Un 502 acá no
  es un problema del frontend: dice que el backend no está.

**Los dos módulos comparten la base pero no el schema.** `core` y `charging` entran a la misma
base `ecopedia` y cada uno crea el suyo, con su propia `flyway_schema_history`. Es lo que hace
que puedan migrar por separado sin pisarse.

#### El `.env`

Los secretos viven en un `.env` en la raíz, **que no se versiona**. `pnpm env:init` lo genera a
partir de `.env.example` con valores al azar, y no pisa uno que ya exista. Tiene siete secretos y
dos ajustes:

| Variable | Qué es |
|---|---|
| `ECOPEDIA_JWT_SECRET` | La firma de los tokens. La comparten los cuatro backends |
| `ECOPEDIA_DB_PASSWORD` | La contraseña de PostgreSQL |
| `ECOPEDIA_BROKER_PASSWORD` | La contraseña de Artemis |
| `ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN` | La clave con la que Pagos se presenta ante la pasarela simulada. La leen los dos |
| `ECOPEDIA_POWER_GRID_PASSWORD` | La contraseña del UsernameToken con que RedEléctrica se presenta ante la distribuidora simulada. La leen los dos |
| `ECOPEDIA_CHARGER_ACCESS_TOKEN` | La clave con la que Terminales (en core) se presenta ante los cargadores simulados. La leen los dos |
| `ECOPEDIA_INTERNAL_TOKEN` | La clave de servicio entre artefactos de Ecopedia, para las rutas `/internal/**` (hoy, `charging` llamando a RedEléctrica en `integration`). La leen los dos |
| `ECOPEDIA_LOGIN_MAX_FAILURES` | Cuántos logins fallidos seguidos bloquean una cuenta. Sin valor, 5 |
| `ECOPEDIA_LOGIN_LOCK_MINUTES` | Cuántos minutos dura ese bloqueo. Sin valor, 15; para la demo conviene 1 |

Un `.env` generado antes de que existieran los dos ajustes no los tiene, y no hace falta
regenerarlo: core usa los valores por defecto. Para cambiarlos, se agregan a mano.

**La clave de la pasarela, la contraseña de la distribuidora, la clave de los cargadores y la de servicio,
en cambio, son secretos:** un `.env` anterior no las tiene, y sin ellas `pnpm demo` no levanta los procesos que
las usan (`Could not resolve placeholder 'ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN'` en la pasarela y en
Pagos, `... 'ECOPEDIA_POWER_GRID_PASSWORD'` en la distribuidora y también en Pagos, que es el mismo
artefacto que RedEléctrica, y `... 'ECOPEDIA_CHARGER_ACCESS_TOKEN'` en los cargadores y **en core**,
que es el que les da las órdenes, y `... 'ECOPEDIA_INTERNAL_TOKEN'` en Pagos y en Reservas). Se agrega a
mano la línea que falte, `ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN=`, `ECOPEDIA_POWER_GRID_PASSWORD=`,
`ECOPEDIA_CHARGER_ACCESS_TOKEN=` o `ECOPEDIA_INTERNAL_TOKEN=`, con cualquier valor largo al azar, o se borra el `.env` y se corre
`pnpm env:init` de nuevo (eso cambia también los demás secretos). `pnpm dev` y `pnpm dev:mem` no
las necesitan.

`docker compose` lo lee solo, y los scripts del backend lo cargan con `scripts/with-env.mjs`,
así que no hay que exportar nada a mano. Una variable que ya esté en el entorno le gana al
archivo.

**Sin `.env`**, `docker compose up` se niega a arrancar y dice qué variable falta, y los
backends sin perfil abortan con `Could not resolve placeholder 'ECOPEDIA_JWT_SECRET'`. Es a
propósito: el ambiente compartido no puede arrancar con un secreto que está publicado en el repo.
`pnpm dev` y `pnpm dev:mem` no lo necesitan.

**Si ya tenías los contenedores de antes**, con la contraseña `ecopedia`: los dos guardan la
contraseña en su volumen al crearse, así que cambiar el `.env` no la cambia adentro. Para
PostgreSQL, sin perder los datos:

```bash
docker exec -it ecopedia-postgres psql -U ecopedia -d ecopedia -c "alter user ecopedia password '<la del .env>'"
```

Para Artemis, que solo guarda mensajes en tránsito, se recrea el volumen:

```bash
docker compose rm -sf activemq
docker volume rm dai_2_tpo_activemq-data   # el prefijo es el nombre de la carpeta del repo
docker compose up -d activemq
```

**Cambiar `ECOPEDIA_JWT_SECRET` desloguea a todo el mundo:** los tokens ya emitidos dejan de
verificar. En pantalla se ve como "me sacó la sesión".

#### Si el seed de estaciones choca

El síntoma es el arranque abortando con esto, y sin la aplicación levantada:

```
SQL State  : 23505
Message    : ERROR: duplicate key value violates unique constraint "stations_pkey"
Location   : db/migration/V202609071900__seed_stations.sql
```

**Por qué pasa.** El seed inserta las 15 estaciones con ids explícitos del 1 al 15. Si la base ya
tiene una fila con alguno de esos ids, la migración choca. Solo puede ocurrirle a un volumen
anterior al seed en el que alguien cargó estaciones a mano: en una base nueva el seed corre
primero y nadie puede adelantársele.

**Qué NO es.** No es que el seed rompa la secuencia de ids: la migración termina con
`ALTER TABLE ... RESTART WITH 16`, así que la primera estación que crees por la aplicación toma
el 16. Comprobado corriendo.

**Cómo se sale.** Borrando la fila que estorba, o con `docker compose down -v` si la base no tenía
nada que valiera la pena. **No hace falta `flyway repair`:** PostgreSQL revierte la migración
entera, así que en el historial no queda ninguna fila fallada.

#### Sin Docker

Docker es el camino cómodo, no un requisito del proyecto. La alternativa es **instalar PostgreSQL**
en la máquina y crear la base con los mismos valores que declara `docker-compose.yml` —base
`ecopedia`, usuario `ecopedia`, puerto 5432— y la contraseña que diga `ECOPEDIA_DB_PASSWORD` en el
`.env`. Con eso `pnpm demo` funciona
igual, porque lo único que le importa es qué hay escuchando en el 5432.

Si preferís otros valores, no hay que tocar código: salen de `ECOPEDIA_DB_URL`, `ECOPEDIA_DB_USER`
y `ECOPEDIA_DB_PASSWORD`. Ver "Direcciones y configuración".

Y para el ambiente compartido de la Entrega Final vale lo mismo: alcanza con que **una** máquina
del equipo tenga PostgreSQL, con o sin Docker. Las demás entran por el frontend de esa máquina,
no directo a la base.

### Notificaciones

Al confirmar o cancelar una reserva, Reservas deja un mensaje en la cola
`notifications.dispatch` de Artemis, y `ecopedia-async` (8084) lo consume, arma el aviso, lo
guarda y manda el mail. **El mail es simulado:** se escribe en el log.

`pnpm dev` ya levanta `async`, así que lo único que hay que agregar es el broker, que es el de
`docker-compose.yml`. Para probarlo sin tocar PostgreSQL alcanza con levantar el broker solo, con
el ambiente propio. El broker también necesita el `.env` (`pnpm env:init`, una vez):

```bash
docker compose up -d activemq
pnpm dev          # core, Reservas, Pagos, Notificaciones y front
```

**Sin el broker el proceso arranca igual** y la campanita y la sección del perfil contestan —el
historial es HTTP y no pasa por la cola—, pero no llega ningún aviso nuevo y el panel `async` se
llena de reintentos de conexión. Si al confirmar una reserva no aparece nada, ese panel y el
`WARN` de `charging` ("No se pudo publicar el aviso en la cola…") son los dos lugares donde
mirar primero.

Con `pnpm demo` no hace falta nada aparte: `docker compose up -d postgres activemq` ya trae el
broker junto con PostgreSQL.

Dónde se ve cada paso, después de confirmar una reserva:

| Qué | Dónde |
|-----|-------|
| Reservas publicó | Panel `charging`: `Aviso booking-N-BOOKING_CONFIRMED publicado en la cola notifications.dispatch` |
| El mensaje pasó por el broker | http://localhost:8161/console (usuario `ecopedia`, contraseña la de `ECOPEDIA_BROKER_PASSWORD` en el `.env`) → *Queues* → `notifications.dispatch`: los contadores de agregados y consumidos |
| Notificaciones lo recibió y mandó el mail | Panel `async`: `Recibido de la cola...` y la línea `[MAIL SIMULADO]` |
| Quedó guardado | `GET http://localhost:8084/api/notifications` con el token del conductor, o la consola de H2 en http://localhost:8084/h2-console (tabla `async.notifications`) |

**Para ver el desacople**, que es lo que muestra la mensajería: bajá `async`, confirmá una
reserva —se confirma igual— y mirá en la consola que el mensaje queda esperando en la cola.
Al volver a levantar `async`, lo consume y manda el mail.

Dos cosas que conviene reconocer:

- **Sin el broker, `async` arranca igual** pero deja cada 5 segundos una línea de ERROR con
  `Could not refresh JMS Connection`. No es un error del módulo: falta `docker compose up -d`.
- **Sin el broker, Reservas reserva igual**, y en su panel aparece en WARN
  `No se pudo publicar el aviso`, con el aviso entero. Ese aviso no se reenvía solo.

### La pasarela de pagos simulada

Pagos no valida ni cobra tarjetas por su cuenta: le habla por REST a la pasarela, un proceso
aparte en `simulation/payment-gateway` (8086), con su propia base y su propio contrato (RNF04).
La API imita la forma de la de Mercado Pago (`/v1/card_tokens`, `/v1/payments`,
`/v1/payments/{id}/refunds`). `pnpm dev` ya la levanta; sin ella, cargar una tarjeta responde
"No pudimos comunicarnos con el procesador de pagos".

**Solo acepta tarjetas de prueba.** Cualquier otro número —aunque sea una tarjeta real y
válida— se rechaza antes de tokenizar. Cada una se comporta distinto, para poder mostrar las
fallas a pedido:

| Número | Marca | Qué hace |
|--------|-------|----------|
| `4242 4242 4242 4242` | VISA | Aprueba todo |
| `4111 1111 1111 1111` | VISA | Aprueba todo |
| `5555 5555 5555 4444` | Mastercard | Aprueba todo |
| `3782 822463 10005` | American Express | Aprueba todo |
| `4000 0000 0000 0002` | VISA | Rechaza el cobro y la validación |
| `4000 0000 0000 9995` | VISA | La validación da que sí, pero el cobro se rechaza por fondos |
| `4000 0000 0000 0077` | VISA | Aprueba, pero tarda 8 s: más que el timeout de Pagos (5 s) |

Vencimiento futuro cualquiera y cualquier nombre.

**La pasarela no guarda el número de la tarjeta**, ni en la base ni en el log: guarda marca,
últimos cuatro, vencimiento y cómo se comporta la tarjeta de prueba. Se ve en la consola de H2,
http://localhost:8086/h2-console, tabla `card_tokens` (la URL de conexión es la que imprime el
log). La base está en `simulation/payment-gateway/data/`, que no se versiona.

Dos cosas más: **escucha solo en `127.0.0.1`** y **pide una clave en cada llamada**
(`Authorization: Bearer`), la de `ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN`. Con `pnpm dev` y
`pnpm dev:mem` los dos lados traen la misma de desarrollo.

### La distribuidora eléctrica simulada

Antes de habilitar una carga rápida, RedEléctrica le pregunta a la distribuidora si el nodo de la
estación aguanta la potencia pedida (RF15). La distribuidora es el sistema legado: un proceso aparte
en `simulation/power-grid` (8087), que publica un servicio **SOAP con su WSDL** (RNF04). `pnpm dev`
ya la levanta. **Todavía no la llama ninguna pantalla:** quien la usa es SesionesDeCarga, por la ruta
interna de abajo, y la habilitación que la dispara llega con ECO-44.

| Qué | Dónde |
|-----|-------|
| El servicio | `POST http://localhost:8087/ws` |
| El WSDL | http://localhost:8087/ws/powerGrid.wsdl |
| El XSD que importa | http://localhost:8087/ws/power-grid.xsd |

Una sola operación, `CheckNodeFeasibility`: recibe `nodeId` y `requestedPowerKw` y contesta
`feasible`, `maxAllowedPowerKw` y, si no autoriza, el motivo. **Un "no" de la red es una respuesta,
no un error.** Los Faults son dos: `UnknownNodeFault` (el nodo no existe) e `InvalidDemandFault`
(potencia nula, negativa o de más de 1000 kW).

**Conoce solo un catálogo de nodos de prueba**, uno por comportamiento. Las 15 estaciones del seed
ya tienen el suyo (el campo "nodo de la distribuidora" de los formularios de estación):

| Nodo | Qué hace | Estaciones del seed |
|------|----------|---------------------|
| `CABA-CENTRO` | Autoriza hasta 600 kW | 1, 2, 4, 5 |
| `CABA-NORTE` | Autoriza hasta 400 kW | 3, 6, 7, 8 |
| `CABA-OESTE` | Tope de demanda vigente: autoriza hasta 50 kW | 9, 10 |
| `CABA-SUR` | En mantenimiento: no autoriza nada | 11 |
| `GBA-NORTE` | Autoriza hasta 300 kW | 12, 13, 14 |
| `LA-PLATA` | Autoriza, pero tarda 8 s: más que el timeout de RedEléctrica (5 s) | 15 |

**Pide WS-Security en cada pedido:** un UsernameToken con el usuario `ecopedia` y la contraseña de
`ECOPEDIA_POWER_GRID_PASSWORD` (con `pnpm dev` y `pnpm dev:mem`, una de desarrollo). Además escucha
solo en `127.0.0.1`, valida cada pedido contra el XSD, rechaza el XML con `DOCTYPE` (no hay
entidades externas) y corta los pedidos de más de 16 KB.

**Para probarla contra el proceso real**, con la distribuidora arriba (`pnpm dev:grid`):

```bash
mvn -pl backend/ecopedia-integration test -Dtest=PowerGridLiveTest -Dpower-grid.live=true
```

Corre cada nodo, los dos Faults, la contraseña equivocada y el timeout del nodo lento con el
cliente de verdad. CI no lo corre, porque necesita el proceso levantado. Si el `.env` tiene
`ECOPEDIA_POWER_GRID_PASSWORD` (con `pnpm dev` también la usan), se agrega `-Dpower-grid.password=` con esa.

#### Quién le pregunta: SesionesDeCarga, por una ruta interna

SesionesDeCarga vive en `ecopedia-charging` y RedEléctrica en `ecopedia-integration`, así que le pregunta
por **`POST /internal/power-grid/feasibility`** (8083). Las rutas `/internal/**` no son para usuarios: piden
la **clave de servicio** (`ECOPEDIA_INTERNAL_TOKEN`, Bearer), el token de un conductor no las abre, y el
proxy de Vite no las manda. Contesta 200 con la respuesta de la red (también un "no"), 422 con el motivo
si la distribuidora rechazó el pedido (`UNKNOWN_NODE`, `INVALID_DEMAND`) y 503 si no contestó.

Con la respuesta, `ChargingPowerPolicy` decide con qué potencia se habilita el conector (decidido el
28/10; todavía no la llama nadie, la usa la habilitación de ECO-44):

| Caso | Potencia |
|------|----------|
| Conector de hasta 22 kW | La suya, sin preguntar (RF15 pide la consulta solo para la carga rápida) |
| La red autoriza | La del conector |
| La red dice que no, con tope | Hasta el tope |
| La red dice que no, con tope 0 (`CABA-SUR`) | No arranca: es la falla de RF13, reintegro de la seña |
| Estación sin nodo, nodo desconocido, red caída o lenta | Potencia normal, 22 kW |

Para probar el recorrido entero, con `pnpm dev` arriba:

```bash
mvn -pl backend/ecopedia-charging test -Dtest=PowerGridFeasibilityLiveTest -Dpower-grid.live=true
```

Si el `.env` tiene `ECOPEDIA_INTERNAL_TOKEN`, se agrega `-Dinternal.token=` con esa.

Para mirarla a mano, un pedido mínimo con `curl` (el cuerpo, en un archivo):

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:pg="http://ecopedia.com/simulation/power-grid/v1">
  <soapenv:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>ecopedia</wsse:Username>
        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">ecopedia-dev-only-power-grid-password</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
  </soapenv:Header>
  <soapenv:Body>
    <pg:CheckNodeFeasibilityRequest>
      <pg:nodeId>CABA-OESTE</pg:nodeId>
      <pg:requestedPowerKw>150</pg:requestedPowerKw>
    </pg:CheckNodeFeasibilityRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

```bash
curl -H "Content-Type: text/xml" --data-binary @pedido.xml http://localhost:8087/ws
```

### Los cargadores simulados

El hardware de las estaciones —o la central del fabricante que lo controla— es un proceso aparte en
`simulation/charger` (8088). Terminales le da dos órdenes por REST, **encender** y **apagar** un
conector, y mientras el conector carga, el simulador publica una lectura cada 2 segundos en el
**tópico** `telemetry.charging` de Artemis: kWh acumulados, potencia y porcentaje de batería. `pnpm dev`
ya lo levanta. **Todavía no lo llama ninguna pantalla:** la habilitación de la carga llega con ECO-44,
y quien lee el tópico, con ECO-45.

| Orden | Qué hace |
|-------|----------|
| `POST /v1/connectors/{id}/session` | Enciende el conector para una sesión. 201 si lo encendió; 200 si ya estaba cargando esa misma sesión |
| `DELETE /v1/connectors/{id}/session` | Lo apaga: 200 con lo entregado, o 204 si no estaba cargando |
| `GET /v1/connectors/{id}` | En qué está: `AVAILABLE`, `CHARGING` o `FAULTED` |
| `POST /v1/connectors/{id}/fault` | Lo rompe, para la demo: si estaba cargando, corta con una lectura de falla |
| `DELETE /v1/connectors/{id}/fault` | Lo repara |

Lo que conviene saber:

- **Simula también el auto.** El pedido trae la potencia permitida (`powerLimitKw`) y, si se quiere, la
  batería del auto y su potencia máxima; sin esos datos usa una batería de 60 kWh que llega con entre
  el 15 y el 40 %. Carga a potencia plena hasta el 80 % y después baja, como una carga rápida de verdad.
  Si la batería se llena, la sesión termina sola.
- **El tiempo va acelerado:** cada 2 segundos reales carga 1 minuto, así una carga de 40 minutos dura
  80 segundos. Se cambia con `ECOPEDIA_CHARGER_READING_INTERVAL` y `ECOPEDIA_CHARGER_MINUTES_PER_READING`.
- **Sin broker no enciende:** contesta 503 `telemetry_unavailable`, porque una carga sin lecturas no se
  puede cortar ni cobrar. Con `pnpm dev` hace falta `docker compose up -d activemq`, igual que para los
  avisos.
- **Cualquier id positivo es un conector:** el simulador no conoce la base de Ecopedia.
- Escucha solo en `127.0.0.1` y pide la clave de `ECOPEDIA_CHARGER_ACCESS_TOKEN` en cada llamada
  (`Authorization: Bearer`); con `pnpm dev`, una de desarrollo.

Las lecturas se ven en la consola de Artemis (http://localhost:8161/console → *Addresses* →
`telemetry.charging`) o a mano:

```bash
# Con `pnpm dev` la clave es la de desarrollo del perfil `local` (ver application.yml del simulador).
TOKEN="${ECOPEDIA_CHARGER_ACCESS_TOKEN:?Exportá ECOPEDIA_CHARGER_ACCESS_TOKEN}"
curl -X POST http://localhost:8088/v1/connectors/1/session \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sessionId":"prueba-1","powerLimitKw":50}'
curl http://localhost:8088/v1/connectors/1 -H "Authorization: Bearer $TOKEN"
```

**Para probarlo contra el proceso real**, con el simulador y el broker arriba:

```bash
mvn -pl backend/ecopedia-core test -Dtest=ChargerLiveTest -Dcharger.live=true -Dcharger.broker-password=<la del .env>
```

Enciende un conector con el cliente de core, escucha el tópico, lo apaga, y prueba un conector roto y
la clave equivocada. CI no lo corre. Si el `.env` tiene `ECOPEDIA_CHARGER_ACCESS_TOKEN`, el simulador
usa esa clave, y se agrega `-Dcharger.token=` con la misma.

### Producción: un solo artefacto

```bash
pnpm build     # = mvn -Pweb clean package
pnpm start     # = java -jar backend/ecopedia-core/target/ecopedia-core-0.1.0-SNAPSHOT.jar
```

El perfil `web` de Maven buildea el frontend y lo mete adentro del JAR, en `classpath:/static/`.
Queda **un solo artefacto** que sirve el frontend y la API en el mismo puerto y el mismo origen:
no hay proxy de Vite ni CORS que configurar. Es lo que va a correr la máquina que haga de
servidor en el ambiente compartido.

Ese JAR arranca contra PostgreSQL, así que necesita la infraestructura arriba. Para probarlo sin
base:

```bash
java -jar backend/ecopedia-core/target/ecopedia-core-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Tres cosas que conviene saber:

- **El build del frontend no corre en `mvn verify`.** Está bajo el perfil `web` a propósito: si
  entrara en el ciclo normal, el job `Backend (Maven)` de CI pasaría a necesitar Node y a tardar
  varios minutos más, y ese nombre es un check obligatorio del ruleset de `main`.
- **El plugin se baja su propio Node y su propio pnpm** en `frontend/.mvn-node`, en vez de usar
  los del PATH. La primera vez tarda; después queda cacheado. Así el build da igual en las cuatro
  máquinas y en cualquier servidor, tenga o no pnpm instalado.
- **Después de un `pnpm build`, el `target/` del backend queda con una copia del frontend.**
  Eso hace que `pnpm dev:back` sirva ese frontend congelado en el 8081. No molesta —en
  desarrollo se navega por el 5173— pero si confunde, `mvn clean` lo borra.

> **Este JAR solo no alcanza para el sistema entero.** Sirve el frontend y la API de
> `ecopedia-core`, pero Reservas, los medios de pago y los avisos viven en otros procesos:
> corriendo así, `/api/bookings` o `/api/payment-methods` le pegan a core y devuelven 404. En
> desarrollo reparte el proxy de Vite; para el despliegue, Caddy (ver "Todo con Docker").

### Módulo por módulo

Cada artefacto tiene su puerto fijo, así los cuatro pueden estar levantados a la vez:

| Módulo | Puerto | Comando |
|--------|--------|---------|
| `ecopedia-core` | 8081 | `mvn -pl backend/ecopedia-core spring-boot:run` |
| `ecopedia-charging` | 8082 | `mvn -pl backend/ecopedia-charging spring-boot:run` |
| `ecopedia-integration` | 8083 | `mvn -pl backend/ecopedia-integration spring-boot:run` |
| `ecopedia-async` | 8084 | `mvn -pl backend/ecopedia-async spring-boot:run` *(consume del broker; por HTTP solo sirve el historial de avisos)* |
| `simulation/payment-gateway` | 8086 | `mvn -pl simulation/payment-gateway spring-boot:run` *(la pasarela simulada; no es Ecopedia)* |
| `simulation/power-grid` | 8087 | `mvn -pl simulation/power-grid spring-boot:run` *(la distribuidora simulada, SOAP; no es Ecopedia)* |
| `simulation/charger` | 8088 | `mvn -pl simulation/charger spring-boot:run` *(los cargadores simulados; no son Ecopedia)* |

Sin perfil apuntan a PostgreSQL. Para el ambiente propio y persistente va
`-Dspring-boot.run.profiles=local`, y para el descartable en memoria,
`-Dspring-boot.run.profiles=dev`.

`mvn verify` además **chequea el formato** con Spotless y falla si algo quedó sin formatear.
Para arreglarlo: `mvn spotless:apply`.

### El frontend por separado

| Comando | Qué hace |
|---------|----------|
| `pnpm dev` | Servidor de desarrollo con hot reload |
| `pnpm build` | Chequeo de tipos (`tsc -b`) + build de producción |
| `pnpm lint` | Linter (oxlint) |
| `pnpm format` | Formatea todo con Prettier |
| `pnpm format:check` | Falla si algo quedó sin formatear (es lo que corre CI) |
| `pnpm preview` | Sirve el build de producción localmente |

Se corren desde `frontend/`, o desde la raíz con `pnpm --dir frontend <comando>`.

**El proxy ya está configurado:** lo que el front pida a `/api/payment-methods` va a
`http://localhost:8083` (`ecopedia-integration`) y todo el resto de `/api/...` a
`http://localhost:8081` (`ecopedia-core`). Se llama a rutas relativas (`fetch('/api/stations')`)
y no hay que tocar CORS en desarrollo.

El orden de las reglas en `vite.config.ts` importa: Vite se queda con la primera que coincide, y
`/api` coincide con todo. La regla específica va arriba.

**Alias de imports:** `@/` apunta a `frontend/src/`, así que se importa `@/components/Map` en
vez de `../../components/Map`.

---

## Cómo trabajamos en equipo

Somos cuatro tocando el mismo repositorio y **todos hacemos de todo**, así que no hay dueños
por módulo. La defensa contra los conflictos es otra: pocos archivos compartidos, formato
idéntico en las cuatro máquinas y ramas que viven horas, no días.

### Configuración de una sola vez

Correr esto una vez por máquina, después de clonar:

```bash
git config pull.rebase true      # 'git pull' rebasea en vez de crear merges de ida y vuelta
git config rerere.enabled true   # recuerda cómo resolviste un conflicto y lo repite solo
```

`rerere` es el que más se agradece: si rebasás la misma rama tres días seguidos, resolvés el
conflicto una vez y las otras dos las resuelve Git.

### El ciclo

```bash
git switch main && git pull            # partir siempre de main actualizado
git switch -c feat/ECO-12-abm-estaciones

# ... trabajar, commitear ...

git pull --rebase origin main          # antes de pushear, SIEMPRE
git push -u origin feat/ECO-12-abm-estaciones
```

Y después el Pull Request, con **squash merge**.

### Las reglas

1. **Nada de push directo a `main`.** Todo entra por PR.
2. **Ramas cortas.** Una rama de cinco horas casi no conflictúa; una de cinco días conflictúa
   siempre. Si algo lleva más de un día, partirlo en dos PR.
3. **Un PR, un tema.** Los PR gigantes tocan archivos que no hacía falta tocar.
4. **Rebase antes de pushear.** Es lo que traslada el conflicto a tu máquina, donde lo
   resolvés vos que sabés qué hiciste, en vez de dejárselo a quien mergea.
5. **Avisar en el grupo en qué archivo estás.** No hace falta repartir roles: alcanza con que
   dos no estén en `TerminalController.java` la misma tarde.
6. **Formatear antes de commitear:** `mvn spotless:apply` y `pnpm format`. Si te olvidás,
   CI te lo marca en el PR.
7. **Agregar una dependencia va en un PR aparte**, y se mergea el mismo día. Así el
   `pnpm-lock.yaml` no queda tocado en tres ramas a la vez.
8. **El código se escribe en inglés; los comentarios, en castellano.** Alcanza a clases,
   métodos, variables, nombres de archivo y carpeta, rutas REST, tablas y columnas. Los
   comentarios, esta documentación y los mensajes al usuario siguen en castellano; los
   mensajes de commit y los PR, en inglés.

### Cuando conflictúa `pnpm-lock.yaml`

Ese archivo lo genera pnpm, no se resuelve a mano. Está marcado en `.gitattributes` para que
Git ni lo intente. Cuando pase:

```bash
git checkout --ours frontend/pnpm-lock.yaml
pnpm --dir frontend install
git add frontend/pnpm-lock.yaml
git rebase --continue
```

### Cuando CI falla por formato

```bash
mvn spotless:apply     # backend
pnpm --dir frontend format
git commit -am "Aplicar formato"
```

---

## Estructura

```
dai_2_tpo/
├── pom.xml                    # POM padre (multi-módulo) + config de Spotless
├── docker-compose.yml         # PostgreSQL + ActiveMQ
├── .gitattributes             # LF en todo; el lock sin merge textual
├── .editorconfig              # Indentación y encoding para los tres IDEs
├── .nvmrc                     # Versión de Node del equipo
├── .github/workflows/ci.yml   # Build + formato en cada PR
├── backend/
│   ├── ecopedia-core/        # Usuarios · Terminales · Tarificación
│   ├── ecopedia-charging/    # Reservas · Sesiones de carga (stateful)
│   ├── ecopedia-integration/ # Pagos (REST) · Red Eléctrica (SOAP)
│   └── ecopedia-async/       # Notificaciones (consumidor JMS + historial por REST)
├── simulation/                # Los sistemas externos simulados: no son Ecopedia
│   ├── payment-gateway/       # La pasarela de pagos (REST/JSON, 8086)
│   ├── power-grid/            # La distribuidora eléctrica (SOAP con WSDL, 8087)
│   └── charger/               # Los cargadores (órdenes por REST, telemetría al tópico, 8088)
├── frontend/                  # React + TypeScript + Vite
│   ├── vite.config.ts         # proxy a /api, alias @/, plugin de Tailwind
│   ├── .prettierrc.json       # Formato compartido
│   └── src/                   # Organizado por feature — ver frontend/src/README.md
└── docs/                      # Diagramas y material de entregas
```

### Módulos previstos

Un módulo Maven por artefacto desplegable. Los componentes de negocio viven dentro de
estos módulos, cada uno con su interfaz explícita.

| Módulo | Componentes | Estado |
|--------|-------------|--------|
| `ecopedia-core` | TerminalService · UserService · PricingService · VehicleService | 🚧 scaffold |
| `ecopedia-charging` | BookingService · ChargingSessionService *(stateful)* | 🚧 scaffold |
| `ecopedia-integration` | PaymentService (REST) · PowerGridService (SOAP) | 🚧 scaffold |
| `ecopedia-async` | NotificationService | 🚧 scaffold |
| `simulation/payment-gateway` | La pasarela de pagos — expone la API REST | ✅ |
| `simulation/power-grid` | La distribuidora, el sistema legado — publica el WSDL | ✅ |
| `simulation/charger` | Los cargadores — reciben órdenes por REST y publican la telemetría al tópico | ✅ |

---

## Convención de capas

Cada componente se organiza en tres capas, en paquetes separados:

```
<componente>/
├── presentacion/   # Controladores REST y DTOs. Sin reglas de negocio.
├── negocio/        # Interfaz del componente, implementación y modelo de dominio.
└── datos/          # Interfaces DAO + su implementación (subpaquete jpa/).
```

La capa de negocio depende de la **interfaz** del DAO, nunca de Spring Data directamente.

---

## Direcciones y configuración

**Ninguna dirección de red se escribe a mano.** Ni una URL, ni un host, ni un puerto, dentro
de un archivo `.java`, `.ts` o `.tsx`.

Hoy todo corre en `localhost` y parece que da lo mismo. No da lo mismo: para la Entrega Final
la aplicación tiene que correr en una máquina que haga de servidor, con dos dispositivos
apuntando al mismo estado. Si para entonces hay direcciones desparramadas por el código,
mudar el ambiente deja de ser cambiar un valor y pasa a ser un refactor por todo el proyecto,
justo en la semana de la entrega.

### Backend

Todo dato de conexión sale de una variable de entorno **con valor por defecto para
desarrollo**, como ya hacen los `application.yml`:

```yaml
url: ${ECOPEDIA_DB_URL:jdbc:postgresql://localhost:5432/ecopedia}
```

Así el que clona el repo levanta el módulo sin configurar nada, y el ambiente compartido se
arma exportando variables, sin tocar el código.

**Los secretos son la excepción: no tienen valor por defecto.** La firma del JWT
(`ECOPEDIA_JWT_SECRET`), las contraseñas de la base (`ECOPEDIA_DB_PASSWORD`) y del broker
(`ECOPEDIA_BROKER_PASSWORD`) y la clave de la pasarela (`ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN`)
salen solo del entorno. Un valor escrito en el YAML sería
público, porque el repo lo es, y con el secreto del JWT a la vista cualquiera se firma un token
de `ADMIN`. Solo los perfiles `local` y `dev` traen un secreto de desarrollo, para que
`pnpm dev` y los tests anden sin configurar nada.

Si un módulo necesita hablar con otro, la dirección se declara igual, bajo la clave
`ecopedia:` del YAML — nunca incrustada donde se hace la llamada. `ecopedia-integration` ya
lo hace así con los sistemas externos simulados.

### Frontend

Las llamadas van a rutas **relativas** que empiezan con `/api`, y pasan por el cliente HTTP
de `src/lib/`. El proxy de Vite las redirige al backend, así que el frontend no sabe ni
necesita saber dónde está la API — y de paso no hay CORS que configurar.

```ts
// Bien: anda igual en desarrollo y en el ambiente compartido.
await api.get('/stations')

// Mal: ata el código a una máquina, y el token de sesión hay que acordarse de mandarlo.
await fetch('http://localhost:8081/api/stations')
```

Que todas las llamadas pasen por un solo lugar es además lo que permite agregar la
autenticación una vez, cuando exista el login, en vez de en cada pantalla.

---

## Migraciones de base de datos

El esquema lo gobierna **Flyway**, no Hibernate. `ddl-auto` está en `validate`: Hibernate
verifica que las entidades coincidan con las tablas, pero no las toca. Con `update` y cuatro
personas contra la misma base, el arranque de uno le rompe las tablas al otro.

Los archivos van en `src/main/resources/db/migration/` del módulo que corresponda
(`ecopedia-core` o `ecopedia-charging`).

**Cada módulo tiene su propio schema** dentro de la misma base: `core` y `charging`. Flyway los
crea solo. Es necesario porque, si compartieran schema, compartirían la tabla
`flyway_schema_history`: cada módulo vería las migraciones del otro como "aplicadas pero
ausentes" y ninguno de los dos arrancaría.

### Convención de nombres

```
V<AAAAMMDD><HHmm>__description_in_snake_case.sql
```

Por ejemplo: `V202608241530__create_stations_table.sql`

**Con timestamp, nunca con `V1`, `V2`, `V3`.** Con numeración secuencial, dos personas
escriben `V3__` la misma tarde y chocan; con timestamp el choque es imposible.

### Reglas

- **Una migración ya mergeada no se edita nunca.** Flyway guarda un checksum: si cambiás el
  archivo, el arranque falla en la máquina de todos los que ya la corrieron. Para corregir
  algo, va una migración nueva.
- **SQL de PostgreSQL.** Los perfiles `local` y `dev` corren H2 en `MODE=PostgreSQL` justamente
  para que las mismas migraciones funcionen en los dos motores y el esquema no se bifurque. Es lo
  que hace que los tres ambientes tengan el mismo esquema sin mantener nada en paralelo.
- **Mientras iterás sobre una migración, trabajá con `pnpm dev:mem`.** Como la base se borra al
  bajar, no hay checksum viejo con el que pelearse. Con `pnpm dev` cada cambio al archivo te pide
  borrar `backend/*/data` antes de volver a levantar.
- Si una base quedó en un estado raro, ver "Empezar de cero": se resetea distinto según el
  ambiente.

---

## Documentación

Los documentos de la cursada (consigna consolidada, arquitectura, requerimientos) se
mantienen fuera del repositorio, en la carpeta del TP.
