# ECOpedia — Manual de Usuario de la Plataforma
## Guía Oficial del Conductor, Operador de Estaciones (CPO) y Administrador

**Materia:** Desarrollo de Aplicaciones II (3.4.218) — UADE
**Cuatrimestre:** 2.º Cuatrimestre 2026 — Grupo 12
**Hito de Entrega:** Entrega Final / Validación contra el Sistema (`ECO-160` / `QA-10`)
**Integrantes:** Manuel Núñez, Santiago A. Ferreyra, Santiago Ribecca, Francisco Reyna
**Docente:** Prof. Matías Gabriel Ruiz

---

## 1. Bienvenido a ECOpedia

**ECOpedia** es la plataforma de recarga para vehículos eléctricos (VE) que conecta a conductores con operadores de estaciones de carga (CPO). Desde una única aplicación web podés ubicar cargadores en tiempo real, consultar un asistente conversacional, reservar turnos, habilitar la carga por código o QR, seguir la entrega de energía en vivo y consultar estadísticas de uso.

> [!NOTE]
> Este manual está redactado para el **usuario final** y fue contrastado paso a paso contra la aplicación vigente (`MAN-01` a `MAN-20`). Cada ruta, botón y regla de negocio coincide exactamente con lo que vas a ver en pantalla.

---

## 2. Acceso, Navegación Responsive y Tema Visual

ECOpedia se adapta automáticamente al tamaño de tu pantalla:
* **En computadora de escritorio:** Barra superior fija con accesos según tu rol (por ejemplo *Mapa*, *Cargar*, *Mis estaciones*, *Estadísticas*, *Backoffice*, tema visual y menú de sesión) y mapa a pantalla completa.
* **En celular o tablet:** Barra inferior táctil y paneles deslizables (*bottom sheets*) para operar sin perder visibilidad del mapa.

### Cambio de Tema Visual (`/profile/settings` o ícono superior)
1. Tocá el ícono de tema (sol / luna) en la barra superior o ingresá a **Perfil > Configuración** (`/profile/settings`).
2. Elegí entre modo **Claro**, **Oscuro** o **Sistema**.
3. Tu preferencia queda guardada automáticamente en el navegador para futuras sesiones.

---

## 3. Creación de Cuenta e Inicio de Sesión

### 3.1. Registro de Nueva Cuenta (`/register`)
Toda cuenta creada desde el registro público nace con rol **Conductor**.

1. En el menú superior o en la pantalla de ingreso, hacé clic en **"Registrate"** (ruta `/register`). Verás el formulario **"Crear cuenta"**.
2. Completá los cuatro campos obligatorios:
   * **Nombre completo**
   * **Email** (será tu identificador único de acceso)
   * **Contraseña** (**mínimo 8 caracteres**)
   * **Repetir contraseña** (debe coincidir exactamente con la anterior)
3. Presioná el botón de envío para crear tu cuenta.
4. El sistema creará tu usuario con rol **Conductor** y te llevará a `/login` con el aviso *"Tu cuenta se creó. Entrá con tu email y contraseña."*

> [!IMPORTANT]
> **¿Cómo se accede como Operador (CPO) o Administrador (ADMIN)?**
> El registro público (`/register`) solo crea cuentas de **Conductor**. Las cuentas de **Operador (CPO)** y **Administrador (ADMIN)** son asignadas y provistas previamente por la plataforma desde la gestión administrativa.

### 3.2. Inicio de Sesión (`/login`) y Bloqueo Preventivo
1. Ingresá a `/login` (pantalla **"Iniciar sesión"**).
2. Completá tu **Email** y **Contraseña**.
3. Hacé clic en el botón **"Entrar"**.
4. **Protección por intentos fallidos:** Si ingresás credenciales erróneas verás el aviso *"Credenciales inválidas"*. Si acumulás **5 intentos fallidos dentro de 15 minutos**, la cuenta queda bloqueada temporalmente durante **15 minutos** para proteger tu acceso.

---

## 4. Guía del Conductor (`CONDUCTOR`)

### 4.1. Perfil, Vehículo y Cambio de Contraseña (`/profile/edit`)
1. Ingresá a **Perfil** y tocá el ícono de edición para abrir `/profile/edit`.
2. Podés modificar:
   * **Avatar local** y **Nombre completo**.
   * **Vehículo eléctrico:** elegí tu modelo del catálogo desplegable (no se escribe texto libre; el conector del vehículo elegido sirve para filtrar estaciones compatibles en el mapa). Podés volver a elegir *"Ninguno"* si querés quitarlo.
   * **Contraseña (opcional):** si deseás cambiar tu clave, completá tu contraseña actual, la nueva contraseña (**mínimo 8 caracteres**) y su confirmación.
3. Presioná guardar para confirmar los cambios.

---

### 4.2. Medios de Pago y Deudas Pendientes (`/profile/payment-methods`)

Para poder reservar un turno o iniciar una carga espontánea necesitás tener **al menos una tarjeta vigente registrada** y **no tener deudas pendientes**.

> [!IMPORTANT]
> **Seguridad de tu tarjeta:** ECOpedia nunca almacena los 16 dígitos completos ni el código de seguridad (CVC). Los datos se tokenizan en la pasarela bancaria y en tu perfil solo ves la marca y los últimos 4 dígitos.

#### Agregar o eliminar una tarjeta
1. Entrá a **Perfil > Medios de pago** (`/profile/payment-methods`).
2. Hacé clic en **"Agregar tarjeta"** (se abre el formulario *"Nueva tarjeta"*).
3. Ingresá el número de tarjeta, fecha de vencimiento (mes/año), titular y etiqueta opcional, y confirmá el alta.
4. Para borrar una tarjeta existente, tocá su botón de eliminación y confirmá **"Eliminar"** en el diálogo *"¿Estás seguro?"*.

#### Consultar y pagar deudas pendientes
Si al terminar una carga previa tu tarjeta rechazó el cobro del consumo restante:
1. En la parte superior de `/profile/payment-methods` verás el aviso de **deuda pendiente** con el importe adeudado.
2. Mientras exista una deuda impaga, **no podrás realizar nuevas reservas ni iniciar cargas**.
3. Hacé clic en **"Pagar ahora"**, seleccioná una tarjeta vigente y confirmá el pago para regularizar tu cuenta al instante.

---

### 4.3. Búsqueda en el Mapa (`/stations/map`) y Asistente Conversacional

#### Mapa interactivo (`/stations/map`)
1. Ingresá a `/stations/map` (disponible incluso sin iniciar sesión para explorar).
2. **Pines de color:** cada estación muestra su disponibilidad operativa en tiempo real (disponible, ocupada o fuera de servicio).
3. **Buscador y punto de partida:** escribí una dirección o localidad en el buscador para centrar el mapa y calcular distancias.
4. **Filtros:** filtrá por tipo de conector (**CCS2**, **Tipo 2**, **CHAdeMO**), potencia mínima y estado disponible.
5. **Ficha de estación:** al elegir una estación verás su dirección, foto, cómo llegar (ruta en el mapa), la lista de conectores con potencia máxima, el monto de seña de la estación y un acceso al estimador de costo.

#### Asistente conversacional (chat de búsqueda)
1. Si iniciaste sesión, vas a ver el botón flotante del asistente en la esquina de la pantalla.
2. Podés hacer preguntas en lenguaje natural (por ejemplo: *"¿Dónde puedo cargar cerca?"*, *"¿Cuánto tarda una carga rápida?"* o *"Quiero reservar un turno"*).
3. **Límite de uso:** podés enviar hasta **10 preguntas por minuto**; si superás ese límite el chat te indicará cuántos segundos esperar antes de volver a consultar. Si el servicio de IA no está disponible en ese momento, podés seguir buscando normalmente desde el mapa.

---

### 4.4. Reservar un Turno paso a paso (`/stations/map`)

1. En la ficha de la estación en `/stations/map`, elegí el conector disponible y tocá **"Reservar"**.
2. **Paso 1 — Elegir día, horario y duración (hasta 30 días de anticipación y 4 horas máximo):**
   * Seleccioná el día, la hora de inicio libre y la duración deseada, y presioná continuar.
   * El sistema crea una **retención temporal de 10 minutos** sobre ese horario para que ningún otro conductor pueda tomarlo mientras revisás el resumen.
3. **Paso 2 — Revisión de seña y aceptación de tolerancia:**
   * Verificá el costo estimado, la seña de la estación (que actúa como pago a cuenta y piso de liquidación) y la tarjeta con la que vas a pagar.
   * Marcá la casilla obligatoria aceptando la **tolerancia de 15 minutos**: si no iniciás la carga dentro de los 15 minutos posteriores a la hora de inicio, la reserva se cancela automáticamente por incomparecencia (*no-show*) y la seña no se reintegra.
4. **Paso 3 — Confirmar reserva:**
   * Se cobra la seña una única vez y queda emitida tu reserva confirmada, visible tanto en el banner/burbuja de reserva activa como en `/profile/reservations`.

---

### 4.5. Mis Reservas y Regla de Cancelación (`/profile/reservations`)
1. Ingresá a **Perfil > Mis reservas** (`/profile/reservations`).
2. Podés revisar tu reserva activa, las futuras y el historial de cargas finalizadas.
3. **Cancelar una reserva:**
   * Seleccioná la reserva confirmada y tocá cancelar.
   * **Regla de 5 horas:** si cancelás con **5 horas o más de anticipación** respecto a la hora de inicio, el sistema solicita el **reintegro automático del 100% de la seña**. Si cancelás con menos de 5 horas de anticipación, la seña no se devuelve.

---

### 4.6. Notificaciones y Avisos (`/profile/notifications`)
1. Ingresá a **Perfil > Notificaciones** (`/profile/notifications`).
2. Vas a recibir avisos en horario local (Argentina) sobre reservas confirmadas, cancelaciones, vencimientos por incomparecencia, fallas de activación con reintegro y estado de pagos/deudas.
3. Podés marcar avisos como leídos individualmente, marcar todos como leídos o eliminar avisos (**"Borrar todas"**).

---

### 4.7. Iniciar, Seguir y Detener una Carga (`/charge`)

Podés llegar a `/charge` desde el acceso directo **"Cargar"** del menú o desde el botón **"Iniciar carga"** del aviso de tu reserva activa.

#### Modalidades de carga
* **Con reserva confirmada:** dentro de tu ventana horaria (o desde 15 minutos antes si el conector está libre).
* **Carga espontánea (*walk-in*, sin reserva):** si llegás a un conector disponible sin turno previo, podés habilitarlo directamente siempre que el conector no tenga otra reserva en los **próximos 15 minutos**. La sesión finalizará automáticamente 15 minutos antes de la siguiente reserva si hubiera una más tarde.

#### Pasos para cargar en `/charge`
1. Enchufá el cable al vehículo.
2. En el formulario **"Iniciar carga"**, elegí cómo identificar el conector físico:
   * **Opción A — Código de terminal:** ingresá el **código de acceso de 6 dígitos** visible en la columna del cargador (por ejemplo `000000`).
   * **Opción B — Escanear QR:** tocá el botón de cámara para escanear el código QR del conector.
3. Elegí tu **porcentaje objetivo de batería** (por defecto `80%`, configurable antes de iniciar).
4. Tocá el botón para iniciar la carga. El sistema valida tu tarjeta, consulta factibilidad eléctrica con la distribuidora (en cargas rápidas `> 22 kW`) y enciende el cargador.
5. **Durante la carga:** la tarjeta activa muestra potencia actual (kW), energía acumulada (kWh), porcentaje de batería y tiempo transcurrido.
6. **Finalización:**
   * Podés detenerla manualmente en cualquier momento con el botón **"Detener carga"**.
   * También se corta de manera automática al llegar al **porcentaje objetivo** configurado o al **fin de la ventana** de tu reserva (o 15 min antes del próximo turno en *walk-in*).
7. **Resumen final:** al terminar se muestra un diálogo con kWh entregados, costo real calculado, seña descontada (piso mínimo en cargas normales; con reembolso parcial si falló antes de completar) y estado del cobro adicional.

---

## 5. Guía del Operador de Estaciones (`CPO`)

Al ingresar con una cuenta con rol `CPO`, disponés de las pantallas **Mis estaciones** (`/stations`) y **Estadísticas** (`/analytics`).

### 5.1. Alta y Edición de Estaciones (`/stations`)
1. Ingresá a `/stations`.
2. Para crear una estación, tocá el botón de **agregar estación** (abre **"Nueva estación"**); para editar una existente, seleccionala en la lista y elegí **"Editar estación"**.
3. Completá o actualizá los campos del formulario:
   * **Nombre** y **Dirección**.
   * **Latitud** y **Longitud** geográficas.
   * **Nodo eléctrico (`nodeId`):** código de conexión asignado por la distribuidora eléctrica. *Sin nodo eléctrico, la estación no puede ofrecer carga rápida (`> 22 kW`).*
   * **Fotos (URLs):** hasta las imágenes permitidas de la estación.
   * **Seña para reservar ($):** monto fijo de seña exigido por esta estación (si es `0`, no exige seña).
   * **Cobrar tarifa de hora pico:** interruptor para activar o desactivar el recargo de hora pico configurado en la red para esta estación.
4. Guardá los cambios. La estación queda vinculada automáticamente a tu cuenta `CPO`.

### 5.2. Conectores y Código de Acceso de 6 Dígitos / QR (`/stations`)
1. Dentro del detalle de tu estación en `/stations`, abrí la gestión de conectores.
2. Podés:
   * **Agregar o editar un conector:** elegir estándar (`CCS2`, `TYPE_2` o `CHAdeMO`), definir la **potencia máxima (kW)** (> 0) y cambiar su estado operativo (**Disponible**, **Ocupado** o **Fuera de servicio**).
   * **Consultar el código de acceso de la terminal:** cada conector muestra su **código PIN de 6 dígitos** y su **QR** listo para imprimir o pegar en la columna física para que los conductores lo usen en `/charge`.

### 5.3. Estadísticas de Uso del Operador (`/analytics`)
1. Ingresá a **Estadísticas** (`/analytics`).
2. Visualizás únicamente las métricas de **tus propias estaciones**:
   * Energía total entregada (`kWh`), cantidad de sesiones y potencia simultánea pico (`kW`).
   * Gráfico de demanda por franjas de 15 minutos, mapa de tus estaciones y detalle por conector.
   * Selector de período configurable (por defecto últimas 24 horas; rango máximo permitido de **31 días**).

---

## 6. Guía del Administrador (`ADMIN`)

Al ingresar con cuenta `ADMIN`, disponés del panel **Backoffice** (`/admin`) y del tablero global de **Estadísticas** (`/analytics`).

### 6.1. Panel Backoffice (`/admin`)
Las secciones se eligen desde las pestañas de arriba; la abierta se ve más grande y en verde.

1. **Tablero:** energía entregada en las últimas 24 h, usuarios por rol, estaciones operativas, demanda de la red, mapa de estaciones, conectores por estado (disponibles, ocupados y fuera de servicio) y precios vigentes. Tocar un pin del mapa abre esa estación en **Estaciones**.
2. **Usuarios:**
   * Listado con buscador, filtro por rol y cambio de rol (`CONDUCTOR` / `CPO` / `ADMIN`) o **"Dar de baja"** (baja lógica).
   * Por seguridad, toda modificación de rol o baja de usuario exige confirmar ingresando tu **contraseña de administrador**. Las cuentas que ya son `ADMIN` están protegidas contra cambios o bajas desde la interfaz web.
3. **Estaciones y Conectores:** podés crear, editar y dar de baja cualquier estación de la plataforma (`"Crear estación"`, `"Dar de baja"`), agregarle conectores, y desde **Conectores** cambiar su estado, editarlos o eliminarlos.
4. **Precios de la red:**
   * Desde la sección **"Precios"** en `/admin` definís los valores vigentes para toda la red: tarifa de carga normal (`<= 22 kW`), tarifa de carga rápida (`> 22 kW`) y multa por minuto de exceso. La franja de hora pico se muestra como referencia.
   * *(Recordá que el monto de seña y si aplica hora pico los define cada CPO en su estación desde `/stations`.)*

### 6.2. Tablero Global de Estadísticas (`/analytics`)
1. Ingresá a `/analytics`.
2. Como administrador visualizás la **vista completa de toda la red**: energía acumulada, sesiones, demanda pico simultánea por franja de 15 minutos, desglose por todas las estaciones y conectores en el período elegido (hasta 31 días).

---

## 7. Solución de Problemas y Preguntas Frecuentes (FAQ)

* **No me deja reservar ni iniciar carga porque tengo tarjeta vencida o rechazada:**
  Ingresá a `/profile/payment-methods`, registrá una nueva tarjeta vigente y volvé a intentar la reserva o habilitación.
* **Me aparece un aviso de bloqueo por deuda pendiente:**
  Entrá a `/profile/payment-methods`, ubicá el cartel de deuda pendiente arriba de tus tarjetas, tocá **"Pagar ahora"** y aboná el saldo. Apenas se acredite, se habilitan nuevamente tus reservas y cargas.
* **Mi cuenta quedó bloqueada al intentar iniciar sesión:**
  Ocurre automáticamente tras 5 intentos con contraseña incorrecta en un lapso de 15 minutos. Esperá 15 minutos antes de volver a ingresar con tu contraseña válida.
* **No puedo iniciar carga sin reserva (*walk-in*) en un conector que parece libre:**
  El sistema exige al menos **15 minutos libres** antes de la próxima reserva confirmada sobre ese conector. Elegí otro conector disponible en el mapa o reservá un turno posterior.
* **El cargador falló o la red eléctrica rechazó la carga rápida al iniciar:**
  El sistema cancela el intento, protege tu cuenta y procesa automáticamente el **reintegro del 100% de la seña** sin penalización alguna.
* **Cancelé una reserva y no me devolvieron la seña:**
  Verificá el horario en `/profile/reservations`: las devoluciones automáticas aplican únicamente cuando cancelás con **5 horas o más** de anticipación.

---

## 8. Glosario

* **CPO (Charge Point Operator):** Operador propietario o gestor de estaciones y conectores de recarga.
* **Nodo eléctrico (`nodeId`):** Identificador de subestación/nodo de la distribuidora necesario en una estación para habilitar cargas rápidas (`> 22 kW`).
* **Hold (Retención):** Bloqueo temporal de **10 minutos** sobre un horario antes de confirmar el pago de la seña.
* **Tolerancia (*No-show*):** Ventana de **15 minutos** desde el inicio del turno para iniciar la carga antes de su cancelación automática.
* **Walk-in:** Carga espontánea sin reserva previa en un conector libre con al menos 15 minutos hasta el próximo turno.
* **Código de terminal / QR:** Clave numérica de **6 dígitos** (o código QR equivalente) propia de cada conector físico para habilitar la carga en `/charge`.
* **SoC / % Objetivo:** Porcentaje de batería del vehículo configurado para el corte automático de la sesión.

---

## 9. Registro de Validación contra el Sistema (`MAN-01` a `MAN-20` / `ECO-160`)

| ID | Rol | Tarea | Ruta | Resultado post-corrección |
| --- | --- | --- | --- | --- |
| `MAN-01` | Visitante | Registro de nueva cuenta (mín. 8 chars, rol `CONDUCTOR`) | `/register` | OK (`DEF-DOC-01` corregido) |
| `MAN-02` | Visitante | Inicio de sesión (`Entrar`) y bloqueo tras 5 fallos | `/login` | OK (`DEF-DOC-01` corregido) |
| `MAN-03` | `CONDUCTOR` | Edición de perfil, modelo de vehículo y cambio de clave | `/profile/edit` | OK (`DEF-DOC-05` corregido) |
| `MAN-04` | `CONDUCTOR` | Cambio de tema claro/oscuro/sistema | `/profile/settings` | OK (`DEF-DOC-05` corregido) |
| `MAN-05` | `CONDUCTOR` | Alta y baja de tarjeta de pago | `/profile/payment-methods` | OK |
| `MAN-06` | `CONDUCTOR` | Consulta y pago de deuda pendiente (`Pagar ahora`) | `/profile/payment-methods` | OK (`DEF-DOC-05` corregido) |
| `MAN-07` | `CONDUCTOR` | Búsqueda en mapa, filtros y ruta | `/stations/map` | OK |
| `MAN-08` | `CONDUCTOR` | Consulta al asistente conversacional y límite 10/min | Chat en app | OK (`DEF-DOC-05` corregido) |
| `MAN-09` | `CONDUCTOR` | Retención 10 min, tolerancia 15 min y checkout de seña | `/stations/map` | OK |
| `MAN-10` | `CONDUCTOR` | Consulta y cancelación (regla 5 horas) | `/profile/reservations` | OK (`DEF-DOC-02` corregido) |
| `MAN-11` | `CONDUCTOR` | Lectura y borrado de notificaciones | `/profile/notifications` | OK (`DEF-DOC-05` corregido) |
| `MAN-12` | `CONDUCTOR` | Carga con PIN 6 dígitos / QR (reserva y walk-in) y corte | `/charge` | OK (`DEF-DOC-03`/`05` corregidos) |
| `MAN-13` | `CPO` | Alta/edición de estación con `nodeId` eléctrico | `/stations` | OK (`DEF-DOC-05` corregido) |
| `MAN-14` | `CPO` | Conectores, estado y consulta de PIN 6 dígitos / QR | `/stations` | OK (`DEF-DOC-03` corregido) |
| `MAN-15` | `CPO` | Configuración de seña y hora pico por estación | `/stations` | OK (`DEF-DOC-04` corregido) |
| `MAN-16` | `CPO` | Tablero de estadísticas de sus propias estaciones | `/analytics` | OK (`DEF-DOC-05` corregido) |
| `MAN-17` | `ADMIN` | Tablero backoffice y auditoría | `/admin` | OK |
| `MAN-18` | `ADMIN` | Configuración de tarifas globales de red | `/admin` | OK (`DEF-DOC-04` corregido) |
| `MAN-19` | `ADMIN` | Baja de usuario/estación con confirmación de contraseña | `/admin` | OK |
| `MAN-20` | `ADMIN` | Tablero de estadísticas global de toda la red | `/analytics` | OK (`DEF-DOC-05` corregido) |
