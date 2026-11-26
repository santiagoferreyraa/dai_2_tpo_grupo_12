# Historias de Usuario, Prioridad MoSCoW y Criterios Gherkin (ECO-169 / QA-19)

Catálogo maestro de Historias de Usuario (`HU-01` a `HU-16`) del alcance de Ecopedia, clasificadas por prioridad **MoSCoW** y especificadas en formato **Gherkin** (`Dado / Cuando / Entonces`) con observables verificables y trazabilidad 1 a 1 a pruebas automatizadas y tickets de Jira (`manuelnuniezz.atlassian.net`).

---

## 1. Catálogo Maestro MoSCoW (`HU-01` a `HU-16`)

| HU | Jira | Épica | Título | Prioridad MoSCoW | Etiqueta Jira | Integración externa (criterio explícito ante falla) | Estado de criterios |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `HU-01` | `ECO-185` | `ECO-1` | Registro e ingreso | **Must** | `moscow-must` | — | Aprobada (`ECO-220` alineada a `DEF-06`) |
| `HU-02` | `ECO-186` | `ECO-1` | Perfil y vehículo del conductor | **Should** | `moscow-should` | — | Aprobada (`5/5` TCs) |
| `HU-03` | `ECO-187` | `ECO-1` | Seguridad por rol y por dueño | **Must** | `moscow-must` | — | Aprobada (`12/12` TCs) |
| `HU-04` | `ECO-188` | `ECO-2` | Búsqueda de estaciones en el mapa | **Must** | `moscow-must` | OSRM / Photon (rutas y geocodificación en mapa) | Aprobada (`4/4` TCs + fallback) |
| `HU-05` | `ECO-189` | `ECO-2` | ABM de estaciones y conectores | **Must** | `moscow-must` | — | Aprobada (`4/4` TCs) |
| `HU-06` | `ECO-190` | `ECO-5` | Esquemas tarifarios | **Must** | `moscow-must` | — | Aprobada (`4/4` TCs) |
| `HU-07` | `ECO-191` | `ECO-1` | Backoffice del administrador | **Should** | `moscow-should` | — | Aprobada (`3/3` TCs) |
| `HU-08` | `ECO-192` | `ECO-5` | Medios de pago del conductor | **Must** | `moscow-must` | Pasarela de pagos REST (`503 PAYMENT_GATEWAY_UNAVAILABLE`) | Aprobada (`5/5` TCs + `503`) |
| `HU-09` | `ECO-193` | `ECO-3` | Retención y reserva de un slot | **Must** | `moscow-must` | Catálogo de terminales en `ecopedia-core` (`503`) | Aprobada (`5/5` TCs + `ECO-261`) |
| `HU-10` | `ECO-194` | `ECO-5` | Checkout de la reserva | **Must** | `moscow-must` | Pasarela REST (`503 PAYMENT_UNAVAILABLE`) + Tarificación (`503 PRICING_UNAVAILABLE`) + Broker Artemis (outbox) | Aprobada (`4/4` TCs + `503`) |
| `HU-11` | `ECO-195` | `ECO-6` | Avisos al conductor | **Must** | `moscow-must` | Broker Artemis (outbox + DLQ `QA-28`) + `ecopedia-core` caído (degradación a `#conector`) | Aprobada (`4/4` TCs + DLQ) |
| `HU-12` | `ECO-196` | `ECO-4` | Habilitación de una carga | **Must** | `moscow-must` | Pasarela REST (`503 PAYMENT_CHECK_UNAVAILABLE`), Distribuidora SOAP (`503`/rechazo), Cargador físico (`503 CHARGER_UNAVAILABLE`/compensación) | Aprobada (`15/15` TCs + `ECO-285`) |
| `HU-13` | `ECO-197` | `ECO-4` | Cierre y cobro de la carga | **Must** | `moscow-must` | Cargador físico (fallback a telemetría), Tarificación/Pagos (`PENDING_SETTLEMENT` + barrido) | Aprobada (`9/9` TCs + fallback) |
| `HU-14` | `ECO-311` | `ECO-5` | Gestión y cobro de deudas pendientes (`ECO-62`) | **Should** | `moscow-should` | Pasarela de pagos REST (`503`/`402 CARD_DECLINED`, bloqueo tras 3 fallos) + Broker avisos | Aprobada (`DebtApiTest`, `Newman 07`) |
| `HU-15` | `ECO-312` | `ECO-2` | Asistente conversacional de búsqueda (`ECO-137`) | **Could** | `moscow-could` | API de Gemini (`503` saturado/sin clave, `504` timeout, `502` fallo) + `ecopedia-core` (`503`) | Aprobada (`agent/test/*.test.ts`) |
| `HU-16` | `ECO-313` | `ECO-6` | Estadísticas de uso por telemetría (`ECO-301`) | **Should** | `moscow-should` | Tópico `telemetry.charging` en Artemis (`ecopedia-stats` durable + DLQ) + `ecopedia-core` caído | Aprobada (`AnalyticsApiTest`, `StatsTelemetryFlowTest`) |

---

## 2. Historias de Usuario y Criterios de Aceptación (Gherkin)

---

### HU-01 · Registro e ingreso (`ECO-185`)

**Prioridad MoSCoW:** Must
**Historia:** Como visitante, quiero registrarme e ingresar a la plataforma, para operar en Ecopedia con una cuenta de conductor protegida.

#### Criterios de aceptación (Gherkin)

##### CA-01.1 · Camino feliz — Registro válido crea cuenta CONDUCTOR sin exponer hash
- **Dado** que un visitante no tiene una cuenta previa para el email `nuevo@ecopedia.test`
- **Cuando** envía `POST /api/auth/register` con `email`, `password` de al menos 8 caracteres, `fullName` y opcionalmente `role: "ADMIN"`
- **Entonces** el servicio responde `201 Created` con `role = "CONDUCTOR"`, persiste el usuario con rol `CONDUCTOR` ignorando el rol enviado y no incluye `passwordHash` en el cuerpo JSON.

##### CA-01.2 · Camino alternativo — Reintentos debajo del umbral no bloquean el login
- **Dado** que existe un usuario activo registrado con su contraseña actual
- **Cuando** envía `POST /api/auth/login` 4 veces consecutivas con contraseña errónea dentro de una ventana de 15 minutos y en el 5.º intento envía la contraseña válida
- **Entonces** el 5.º intento responde `200 OK` devolviendo `token` JWT y `expiresInSeconds > 0`.

##### CA-01.3 · Error / validación — Email duplicado, clave corta y credenciales inválidas con respuesta opaca
- **Dado** que existe una cuenta para `registrado@ecopedia.test`
- **Cuando** se envía `POST /api/auth/register` con el mismo email o una contraseña menor a 8 caracteres, o se envía `POST /api/auth/login` con email inexistente o contraseña equivocada
- **Entonces** el registro inválido responde `400 Bad Request` con cuerpo `application/problem+json` indicando el motivo, y ambos casos de login fallido responden exactamente el mismo estado `400 Bad Request` (`DEF-06` documentado en `qa/README.md`) y el mismo `detail = "Credenciales inválidas"` sin revelar si el email existe ni devolver `token`.

##### CA-01.4 · Borde de seguridad — Cinco intentos fallidos bloquean la cuenta 15 minutos
- **Dado** que un usuario activo acumula 5 intentos fallidos consecutivos en `POST /api/auth/login` dentro de 15 minutos
- **Cuando** envía un 6.º intento de `POST /api/auth/login` dentro de los siguientes 15 minutos, incluso con la contraseña correcta
- **Entonces** el servicio responde `429 Too Many Requests` con cabecera `Retry-After > 0` y no emite ningún token.

**Trazabilidad:** `TC-01` (`ECO-217`, Newman 01), `TC-02` (`ECO-218`, `AuthErrorResponseTest`, Newman 01), `TC-03` (`ECO-219`, `UserAuthorizationTest`, Newman 01), `TC-04` (`ECO-220`, `AuthErrorResponseTest` alineado a `DEF-06`), `TC-05` (`ECO-221`, `SessionHardeningTest`, `LoginAttemptsTest`), `TC-06` (`ECO-222`, `LoginAttemptsTest`).

---

### HU-02 · Perfil y vehículo del conductor (`ECO-186`)

**Prioridad MoSCoW:** Should
**Historia:** Como conductor, quiero mantener mi nombre, vehículo y contraseña desde mi perfil, para que las búsquedas y reservas reflejen mis datos vigentes.

#### Criterios de aceptación (Gherkin)

##### CA-02.1 · Camino feliz — Edición de nombre y selección de vehículo del catálogo con ETag
- **Dado** que un conductor autenticado consultó `GET /api/users/profile` recibiendo el encabezado `ETag` vigente y existe un modelo activo en `/api/vehicle-models`
- **Cuando** envía `PUT /api/users/profile` con `If-Match` igual al `ETag` vigente, `fullName` no vacío y el `vehicleModelId` elegido
- **Entonces** el servicio responde `200 OK` devolviendo la ficha del vehículo actualizada, un nuevo `ETag` incrementado y persiste los cambios en la base.

##### CA-02.2 · Camino alternativo — Cambio de contraseña y bloqueo de modificación concurrente obsoleta
- **Dado** que un conductor autenticado conoce su contraseña actual
- **Cuando** envía `PUT /api/users/profile/password` con `currentPassword` válida y `newPassword` de al menos 8 caracteres, o envía `PUT /api/users/profile` con un `If-Match` desactualizado
- **Entonces** el cambio de contraseña responde `204 No Content` habilitando el login con la nueva clave, mientras que la edición con `ETag` obsoleto responde `412 Precondition Failed` con `code = "STALE_VERSION"`.

##### CA-02.3 · Error / validación — Nombre vacío, vehículo inexistente y protección contra mass assignment
- **Dado** que un conductor autenticado opera sobre su perfil
- **Cuando** envía `PUT /api/users/profile` con `fullName` vacío o `vehicleModelId = 999999`, o intenta inyectar `role: "ADMIN"`, `active: false` y otro `email` en el cuerpo de una edición válida
- **Entonces** los datos inválidos o el vehículo inexistente responden `400 Bad Request` (`application/problem+json`, nunca `500`), y ante el intento de *mass assignment* responde `200 OK` manteniendo intactos en base `role = CONDUCTOR`, `active = true` y su email original.

**Trazabilidad:** `TC-07` (`ECO-223`, `VehicleSelectionTest`, Newman 02), `TC-08` (`ECO-224`, `UserAuthorizationTest`, Newman 02), `TC-09` (`ECO-225`, `VehicleSelectionTest`), `TC-10` (`ECO-226`, `UserAuthorizationTest`, Newman 02), `TC-11` (`ECO-227`, `ApiAttackTest` core), `EntityTagUpdateTest` (core), Newman 02.

---

### HU-03 · Seguridad por rol y por dueño (`ECO-187`)

**Prioridad MoSCoW:** Must
**Historia:** Como usuario de Ecopedia, quiero que cada operación valide mi rol vigente y la titularidad del recurso, para impedir accesos indebidos a datos ajenos o funciones administrativas.

#### Criterios de aceptación (Gherkin)

##### CA-03.1 · Camino feliz — Titularidad válida habilita modificación del propio recurso
- **Dado** que un usuario `CPO` autenticado es propietario de la estación `S1`
- **Cuando** envía `PUT /api/stations/{S1}` con datos válidos y su `ETag` vigente
- **Entonces** el servicio responde `200 OK` actualizando la estación `S1` bajo su `ownerId`.

##### CA-03.2 · Camino alternativo — Baja lógica y cambio de rol revocan permisos al instante desde base de datos
- **Dado** que un usuario posee un JWT vigente emitido con rol `ADMIN`, pero en base de datos fue dado de baja (`active = false`) o degradado a `CONDUCTOR`
- **Cuando** envía una petición con ese JWT aún no expirado a `GET /api/users/profile` o `GET /api/users`
- **Entonces** si fue dado de baja el filtro de seguridad responde `401 Unauthorized` rechazando el token de inmediato, y si fue degradado responde `404 Not Found` opaco sin ejecutar la operación.

##### CA-03.3 · Error / validación — BOLA, BFLA opacos (404) y JWT inválido/manipulado (401)
- **Dado** que un usuario intenta editar una estación de otro `CPO`, un `CONDUCTOR` llama a `GET /api/users`, o llega un JWT vencido, alterado, con `alg=none` o firmado con otra clave
- **Cuando** ejecuta la petición contra cualquiera de los 4 módulos backend
- **Entonces** los casos de recurso ajeno (BOLA) u operación de otro rol (BFLA) responden `404 Not Found` con el mismo cuerpo `application/problem+json` genérico (`detail = "No se encontró el recurso pedido."`, sin `code` ni IDs), y todo JWT ausente o inválido responde `401 Unauthorized` con cabecera `WWW-Authenticate: Bearer`.

**Trazabilidad:** `TC-12` (`ECO-228`), `TC-13` (`ECO-229`), `TC-14` (`ECO-230`), `TC-15`–`TC-18` (`ECO-231`–`ECO-234`), `TC-19` (`ECO-235`), `TC-34` (`ECO-250`), `TC-76`–`TC-78` (`ECO-292`–`ECO-294`), `ApiAttackTest` (4 módulos), `SessionHardeningTest`, `TerminalAuthorizationTest`, `UserAuthorizationTest`.

---

### HU-04 · Búsqueda de estaciones en el mapa (`ECO-188`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero buscar estaciones públicas cercanas y consultar sus conectores en el mapa, para elegir un punto de carga compatible y disponible.

#### Criterios de aceptación (Gherkin)

##### CA-04.1 · Camino feliz — Búsqueda geográfica devuelve estaciones activas ordenadas por distancia
- **Dado** que existen estaciones activas con conectores en `ecopedia-core`
- **Cuando** se envía `GET /api/search?lat=-34.6037&lon=-58.3816&radiusKm=10` sin token de sesión
- **Entonces** el servicio responde `200 OK` con un arreglo JSON de estaciones dentro del radio ordenadas de menor a mayor distancia, incluyendo sus conectores y estado operativo.

##### CA-04.2 · Camino alternativo — Lectura pública de listado completo y detalle por identificador
- **Dado** que existen estaciones y conectores sembrados en `ecopedia-core`
- **Cuando** se envía `GET /api/stations` o `GET /api/stations/{id}` sin token de sesión
- **Entonces** el servicio responde `200 OK` con las estaciones activas y el detalle completo de sus conectores, y `404 Not Found` cuando el `id` no existe.

##### CA-04.3 · Error / validación — Parámetros inválidos o inyección SQL rechazados con 400
- **Dado** que el catálogo público de estaciones está operativo
- **Cuando** se envía `GET /api/search?lat=-34.6037&lon=-58.3816&radiusKm=0`, `GET /api/search?lat=1 OR 1=1--&lon=-58.38`, o `GET /api/connectors?stationId=abc`
- **Entonces** el servicio responde `400 Bad Request` (`application/problem+json`), sin devolver resultados de base ni filtrar nombres de clases Java ni mensajes internos como `"For input string"`.

##### CA-04.4 · Falla de integración externa — Degradación ante caída de OSRM o Photon en la vista de mapa
- **Dado** que los servicios externos de geocodificación (`photon.komoot.io`) o cálculo de rutas (`router.project-osrm.org`) devuelven timeout o `5xx`
- **Cuando** el usuario utiliza la vista del mapa y consulta `/api/search` o `/api/stations`
- **Entonces** el backend `ecopedia-core` sigue respondiendo `200 OK` con las estaciones ordenadas por distancia geodésica sin depender de servicios externos, y el mapa conserva los pines y estado de conectores operativos.

**Trazabilidad:** `TC-20` (`ECO-236`, `TerminalServiceTest`, Newman 03), `TC-21` (`ECO-237`, `TerminalApiTest`, Newman 03), `TC-22` (`ECO-238`, `ApiAttackTest` core), `TC-23` (`ECO-239`, `TerminalAuthorizationTest`), `MalformedRequestTest` (core).

---

### HU-05 · ABM de estaciones y conectores (`ECO-189`)

**Prioridad MoSCoW:** Must
**Historia:** Como CPO, quiero dar de alta y mantener mis estaciones y sus conectores, para ofrecer infraestructura de carga con datos consistentes en la plataforma.

#### Criterios de aceptación (Gherkin)

##### CA-05.1 · Camino feliz — Alta de estación y conector asigna propiedad automática al CPO
- **Dado** que un usuario autenticado posee el rol `CPO`
- **Cuando** envía `POST /api/stations` con nombre, dirección y coordenadas válidas, y luego `POST /api/stations/{stationId}/connectors` con `connectorType = "CCS2"` y `maxPowerKw = 50`
- **Entonces** ambos responden `201 Created`, la estación queda persistida con `ownerId` igual al ID del token `CPO`, `active = true`, `ETag` inicial, y el conector asociado queda disponible para búsquedas y tarifas.

##### CA-05.2 · Camino alternativo — Reconfiguración y cambio de estado operativo del conector propio
- **Dado** que el `CPO` dueño creó un conector en su estación
- **Cuando** envía `POST /api/connectors/{id}/configure` con `connectorType = "TYPE_2"` y `maxPowerKw = 22`, y `PATCH /api/connectors/{id}/status` con `operationalStatus = "AVAILABLE"`
- **Entonces** ambos responden `200 OK` persistiendo los nuevos parámetros físicos y el estado operativo.

##### CA-05.3 · Error / validación — Potencia inválida, mass assignment y neutralización de texto hostil
- **Dado** que un `CPO` autenticado opera sobre sus estaciones o conectores
- **Cuando** envía `POST /api/stations` con `ownerId = 999999` y `active = false`, o con SQL/HTML en `name`/`address`, o envía `POST /connectors` / `/configure` con `maxPowerKw <= 0`
- **Entonces** el sistema ignora `ownerId` y `active` asignando al creador (`201 Created`), guarda el texto hostil literalmente sin ejecutar SQL ni HTML y con cabecera `X-Content-Type-Options: nosniff`, y rechaza `maxPowerKw <= 0` o estado inexistente con `400 Bad Request` (`application/problem+json`).

**Trazabilidad:** `TC-24` (`ECO-240`, `TerminalAuthorizationTest`, Newman 04), `TC-25` (`ECO-241`, `ApiAttackTest` core), `TC-26` (`ECO-242`, `TerminalApiTest`, Newman 04), `TC-27` (`ECO-243`, `ApiAttackTest` core).

---

### HU-06 · Esquemas tarifarios (`ECO-190`)

**Prioridad MoSCoW:** Must
**Historia:** Como administrador o CPO, quiero configurar las tarifas de la red y las señas/horarios pico por estación, para calcular estimaciones previas y liquidar cada carga.

#### Criterios de aceptación (Gherkin)

##### CA-06.1 · Camino feliz — ADMIN fija precios globales y CPO fija seña y hora pico de su estación
- **Dado** que existen un usuario `ADMIN`, un `CPO` dueño de la estación `S1` y un conector asociado `C1`
- **Cuando** `ADMIN` envía `PUT /api/pricing/rates` con valores positivos de `normalKwhRate`, `fastKwhRate` y `excessPenaltyPerMin`, y el `CPO` dueño envía `PUT /api/pricing/stations/{S1}` con `depositAmount = 1200` y `peakPricingEnabled = true`
- **Entonces** ambas operaciones responden `200 OK`, persisten la configuración y `GET /api/pricing/deposit?connectorId={C1}` devuelve `depositAmount = 1200`.

##### CA-06.2 · Camino alternativo — Estimación previa de costo respetando piso de seña
- **Dado** que un conector `C1` pertenece a una estación con `depositAmount = 1200`
- **Cuando** se consulta `GET /api/pricing/estimate?connectorId={C1}&estimatedKwh=1`
- **Entonces** el servicio responde `200 OK` (cubre `DEF-30`) informando `settledByDeposit = true` y aplicando la seña como monto mínimo liquidable.

##### CA-06.3 · Error / validación — Rol no autorizado, estación ajena/inexistente o tarifas negativas
- **Dado** que un `CPO` intenta modificar precios globales, otro `CPO` intenta editar `S1`, o `ADMIN`/`CPO` envían importes negativos o consultan una estación inexistente
- **Cuando** envían `PUT /api/pricing/rates`, `PUT /api/pricing/stations/{id}` o `GET /api/pricing/stations/999999`
- **Entonces** el acceso de `CPO` a `/api/pricing/rates` o a una estación ajena/inexistente responde `404 Not Found` opaco (`SEC-05`/`SEC-06`), y cualquier tarifa o seña negativa o ausencia de `estimatedKwh` responde `400 Bad Request` (`application/problem+json`).

**Trazabilidad:** `TC-28` (`ECO-244`, `PricingAuthorizationTest`, `PricingControllerTest`, Newman 05), `TC-29` (`ECO-245`, `PricingAuthorizationTest`), `TC-30` (`ECO-246`, `PricingControllerTest`, Newman 05), `TC-31` (`ECO-247`, `PricingControllerTest`, Newman 05), `PricingDepositApiTest` (`DEF-30`), `PricingServiceTest`.

---

### HU-07 · Backoffice del administrador (`ECO-191`)

**Prioridad MoSCoW:** Should
**Historia:** Como administrador, quiero listar, cambiar roles y dar de baja usuarios o estaciones con confirmación de contraseña, para administrar la plataforma de manera auditada.

#### Criterios de aceptación (Gherkin)

##### CA-07.1 · Camino feliz — Listado, cambio de rol y baja lógica de usuario no administrador
- **Dado** que un usuario `ADMIN` autenticado conoce su contraseña y existe otro usuario `U2` con rol `CONDUCTOR`
- **Cuando** ejecuta `GET /api/users?role=CONDUCTOR`, `PATCH /api/users/{U2}/role` con `{role: "CPO", password}` y `DELETE /api/users/{U2}` con `{password}`
- **Entonces** el listado responde `200 OK` filtrando solo `CONDUCTOR`, el cambio de rol responde `200 OK` con `role = "CPO"` y la baja lógica responde `204 No Content` marcando `active = false`.

##### CA-07.2 · Camino alternativo — Reactivación de usuario y baja de estación desde backoffice
- **Dado** que el usuario `U2` fue dado de baja y existe la estación `15`
- **Cuando** `ADMIN` ejecuta `POST /api/users/{U2}/activate` o `DELETE /api/admin/stations/15`
- **Entonces** el servicio responde `204 No Content`, reactivando a `U2` o desactivando la estación según corresponda.

##### CA-07.3 · Error / validación — Contraseña incorrecta, protección de cuentas ADMIN e IDs inexistentes
- **Dado** que `ADMIN` opera sobre cuentas del sistema
- **Cuando** intenta cambiar rol o dar de baja con contraseña errónea, intenta modificar/eliminar su propia cuenta `ADMIN` o solicita baja de un usuario/estación inexistente (`999999`)
- **Entonces** la contraseña inválida responde `400 Bad Request` (`code = "PASSWORD_INCORRECT"`), la modificación/baja de un `ADMIN` responde `409 Conflict` (`code = "ADMIN_PROTECTED"`), y un identificador inexistente responde `404 Not Found` (`application/problem+json`, nunca `500`, `DEF-05`).

**Trazabilidad:** `TC-32` (`ECO-248`, `UserAuthorizationTest`, Newman 06), `TC-33` (`ECO-249`, `AdminErrorResponseTest`, Newman 06), `TC-34` (`ECO-250`, `SessionHardeningTest`), `AdminAccountGuardTest`, `AdminServiceTest`.

---

### HU-08 · Medios de pago del conductor (`ECO-192`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero registrar, listar y eliminar mis tarjetas de crédito/débito, para abonar señas y consumos sin exponer los datos completos de mi tarjeta.

#### Criterios de aceptación (Gherkin)

##### CA-08.1 · Camino feliz — Tokenización y alta de tarjeta sin persistir ni devolver el PAN completo
- **Dado** que un conductor autenticado tiene menos tarjetas que el tope permitido y la pasarela externa está operativa
- **Cuando** envía `POST /api/payment-methods` con un número Luhn válido (`4111111111111111`), vencimiento futuro, titular y etiqueta (y aunque envíe `driverId`, `brand` o `gatewayToken`)
- **Entonces** el servicio responde `201 Created` devolviendo únicamente `brand = "VISA"`, `lastFour = "1111"` e `id` asociado al conductor del JWT, sin almacenar ni devolver el número completo de la tarjeta (`SEC-09`).

##### CA-08.2 · Camino alternativo — Listado y baja de una tarjeta propia
- **Dado** que el conductor tiene la tarjeta `T1` registrada a su nombre
- **Cuando** consulta `GET /api/payment-methods` y luego ejecuta `DELETE /api/payment-methods/{T1}`
- **Entonces** el listado responde `200 OK` incluyendo `T1`, y la eliminación responde `204 No Content` dejando la tarjeta inactiva.

##### CA-08.3 · Error / validación — Número inválido, tope superado y borrado de tarjeta ajena (BOLA)
- **Dado** que un conductor intenta registrar una tarjeta con dígito verificador inválido (`4111...1112`), superar el límite máximo de tarjetas vigentes o eliminar la tarjeta de otro conductor
- **Cuando** ejecuta `POST /api/payment-methods` o `DELETE /api/payment-methods/{idAjeno}`
- **Entonces** el número inválido o el exceso de tope responden `400 Bad Request` (`application/problem+json`), y el borrado de una tarjeta ajena responde `404 Not Found` opaco sin modificar el recurso.

##### CA-08.4 · Falla de integración externa — Pasarela de pagos caída o circuit breaker abierto
- **Dado** que la pasarela de pagos externa devuelve `5xx`, timeout o abrió el circuit breaker (`CircuitBreakerPaymentGateway`)
- **Cuando** el conductor envía `POST /api/payment-methods` para registrar una tarjeta
- **Entonces** el servicio responde `503 Service Unavailable` con `code = "PAYMENT_GATEWAY_UNAVAILABLE"` y no persiste ninguna tarjeta parcial en `ecopedia-integration`.

**Trazabilidad:** `TC-35` (`ECO-251`, `PaymentMethodApiTest`, Newman 07), `TC-36` (`ECO-252`, `PaymentMethodApiTest`, Newman 07), `TC-37` (`ECO-253`, `PaymentMethodApiTest`), `TC-38` (`ECO-254`, `PaymentServiceTest`), `TC-39` (`ECO-255`, `ApiAttackTest` integration), `PaymentGatewayRestClientTest`, `CircuitBreakerPaymentGatewayTest`.

---

### HU-09 · Retención y reserva de un slot (`ECO-193`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero consultar la disponibilidad de un conector y retener una franja horaria, para garantizar mi turno antes de pagar la seña.

#### Criterios de aceptación (Gherkin)

##### CA-09.1 · Camino feliz — Retención de un slot libre, incluso adyacente a otro turno
- **Dado** que un conector existe en estado `AVAILABLE` y tiene una reserva previa de `10:00` a `11:00`
- **Cuando** un conductor autenticado consulta `GET /api/bookings/availability` y envía `POST /api/bookings/holds` para la ventana `11:00` a `12:00`
- **Entonces** el servicio responde `201 Created` creando una retención (`Hold`) con `expiresAt` asignado sin colisionar con la ventana adyacente.

##### CA-09.2 · Camino alternativo — Liberación inmediata de retención y unicidad ante concurrencia
- **Dado** que un conductor creó una retención temporal o que 32 hilos solicitan concurrentemente `startHold` sobre la misma ventana del mismo conector
- **Cuando** el conductor ejecuta `DELETE /api/bookings/holds/{holdId}` o corren los 32 pedidos simultáneos
- **Entonces** la liberación responde `204 No Content` dejando el horario libre al instante (`DEF-27`), y bajo concurrencia exactamente `1` conductor obtiene la retención mientras los `31` restantes reciben `409 Conflict`.

##### CA-09.3 · Error / validación — Slot ocupado, ventana invertida o fuera de límite
- **Dado** que un conector ya tiene una retención o reserva superpuesta en la ventana solicitada, o el pedido envía `end <= start`
- **Cuando** el conductor envía `POST /api/bookings/holds`
- **Entonces** el slot ocupado responde `409 Conflict` (`application/problem+json`) y la ventana inválida responde `400 Bad Request` sin crear retenciones.

##### CA-09.4 · Falla de integración externa — Catálogo de terminales en `ecopedia-core` caído
- **Dado** que `ecopedia-core` no responde (timeout o conexión rechazada) al validar el conector
- **Cuando** un conductor autenticado envía `POST /api/bookings/holds`
- **Entonces** `ecopedia-charging` responde `503 Service Unavailable` (`application/problem+json`) con el detalle `"No se pudo verificar el conector. Probá de nuevo en un momento."` y no crea la retención.

**Trazabilidad:** `TC-40` (`ECO-256`, `BookingAuthorizationTest`, Newman 08), `TC-41` (`ECO-257`, `BookingAuthorizationTest`, `BookingApiTest`), `TC-42` (`ECO-258`, `BookingServiceImplTest`, `TimeWindowTest`), `TC-43` (`ECO-259`, `BookingConcurrencyTest`), `TC-44` (`ECO-260`, `BookingAuthorizationTest`, Newman 08), `TC-45` (`ECO-261` regularizado con `SessionClientsTest`, `BookingExceptionHandler` y prueba manual de integración).

---

### HU-10 · Checkout de la reserva (`ECO-194`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero confirmar mi retención pagando la seña de forma idempotente, para dejar firme mi reserva sin riesgo de doble cobro.

#### Criterios de aceptación (Gherkin)

##### CA-10.1 · Camino feliz — Checkout idempotente confirma reserva y cobra seña una sola vez
- **Dado** que el conductor posee una retención vigente `H1`, una tarjeta válida `M1` y ninguna deuda abierta
- **Cuando** envía `POST /api/checkout/booking` con cabecera `Idempotency-Key`, `holdId = H1`, `paymentMethodId = M1` y `acceptGracePeriod = true`, y repite exactamente el mismo pedido con la misma `Idempotency-Key`
- **Entonces** ambas respuestas devuelven `201 Created` con el mismo `bookingId`, estado `CONFIRMED`, se cobra la seña una única vez y la reserva aparece en `GET /api/bookings/mine`.

##### CA-10.2 · Camino alternativo — Cancelación de reserva dentro y fuera del plazo de devolución
- **Dado** que el conductor tiene una reserva `CONFIRMED` y faltan más de 5 horas para el inicio (o menos de 5 horas)
- **Cuando** envía `DELETE /api/bookings/{bookingId}`
- **Entonces** responde `204 No Content` pasando la reserva a `CANCELLED` y registrando el reintegro de la seña solo si está dentro del plazo anticipado de 5 horas.

##### CA-10.3 · Error / validación — Falta Idempotency-Key, sin tolerancia de 15 min, sin tarjeta o retención ajena
- **Dado** que el conductor omite `Idempotency-Key`, envía `acceptGracePeriod = false`, no tiene tarjetas ni envía `paymentMethodId`, o usa una retención expirada/ajena
- **Cuando** ejecuta `POST /api/checkout/booking`
- **Entonces** sin clave responde `400 Bad Request` (`code = "IDEMPOTENCY_KEY_REQUIRED"`), sin aceptar tolerancia responde `400 Bad Request` (`code = "GRACE_PERIOD_NOT_ACCEPTED"`), sin medio de pago responde `422` (`code = "PAYMENT_METHOD_INVALID"`) o `400`, con retención vencida responde `410 Gone`, manteniendo la retención intacta ante errores de validación.

##### CA-10.4 · Falla de integración externa — Caída de Pagos, Tarificación o Broker Artemis
- **Dado** que `ecopedia-integration` (Pagos) o `ecopedia-core` (Tarificación) responden `503`/timeout durante el checkout, o el broker Artemis está apagado al confirmar
- **Cuando** el conductor ejecuta `POST /api/checkout/booking`
- **Entonces** si cae Tarificación o Pagos responde `503 Service Unavailable` (`PRICING_UNAVAILABLE` o `PAYMENT_UNAVAILABLE`) conservando la retención vigente (`Hold`) sin confirmar reserva ni cobrar seña; si solo cae Artemis al confirmar, la reserva queda `CONFIRMED` y el evento se guarda en `pending_notifications` para su envío automático al volver el broker (`ECO-177`).

**Trazabilidad:** `TC-46` (`ECO-262`, `CheckoutApiTest`, Newman 08), `TC-47` (`ECO-263`, `CheckoutApiTest`, Newman 08), `TC-48` (`ECO-264`, `CheckoutApiTest`), `TC-49` (`ECO-265`, `CheckoutApiTest`), `BookingWithoutBrokerTest`, `PendingNotificationTest`.

---

### HU-11 · Avisos al conductor (`ECO-195`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero recibir avisos confiables sobre mis reservas y deudas, para enterarme de confirmaciones, cancelaciones o cobros fallidos aunque ocurran cortes temporales.

#### Criterios de aceptación (Gherkin)

##### CA-11.1 · Camino feliz — Evento de reserva genera aviso en huso horario local y permite lectura/borrado
- **Dado** que el broker Artemis y `ecopedia-async` están activos y una reserva se confirma o cancela
- **Cuando** `ecopedia-charging` publica el evento en la cola `notifications.dispatch` y el conductor consulta `GET /api/notifications`, marca leído con `POST /api/notifications/{id}/read` o borra con `DELETE /api/notifications/{id}`
- **Entonces** el aviso aparece en `GET /api/notifications` con fecha en zona `America/Argentina/Buenos_Aires`, sin exponer montos de seña, y las acciones de marcado/borrado responden `204 No Content`.

##### CA-11.2 · Camino alternativo — Idempotencia ante mensajes duplicados en la cola
- **Dado** que el broker Artemis reentrega un mensaje con el mismo `eventId` ya procesado previamente
- **Cuando** `BookingEventListener` recibe el mensaje duplicado
- **Entonces** `NotificationServiceImpl` detecta la existencia de `eventId` y persiste exactamente `1` único aviso en `notifications` (`SEC-08`).

##### CA-11.3 · Error / validación — Acceso a aviso ajeno (BOLA) y envío de mensaje corrupto a DLQ
- **Dado** que un conductor intenta leer o borrar un aviso de otro usuario, o llega a la cola un mensaje JSON malformado o con `_type` desconocido
- **Cuando** llama a `POST /api/notifications/{idAjeno}/read` / `DELETE /api/notifications/{idAjeno}` o el listener procesa el mensaje corrupto 5 veces (`max-delivery-attempts = 5`)
- **Entonces** la API responde `404 Not Found` opaco sin alterar el aviso ajeno, y Artemis traslada el mensaje corrupto a `DLQ.notifications.dispatch` sin detener el consumo del resto de la cola (`QA-28`).

##### CA-11.4 · Falla de integración externa — Outbox con broker caído y degradación ante `ecopedia-core` caído
- **Dado** que Artemis está apagado al confirmar una reserva (o `ecopedia-core` no responde al enriquecer el nombre de la estación)
- **Cuando** se confirma la reserva y posteriormente se levanta Artemis
- **Entonces** la confirmación no falla, el evento persiste en `pending_notifications` (`DEF-01` resuelto por `ECO-177`) y el barrido asincrónico lo publica al reconectar el broker; si `core` no responde, el aviso se guarda igual identificando al conector (`Conector #id`).

**Trazabilidad:** `TC-50` (`ECO-266`, `BookingNotificationFlowTest`, Newman 09), `TC-51` (`ECO-267`, `BookingNotificationFlowTest`, `NotificationServiceImplTest`), `TC-52` (`ECO-268`, `BookingWithoutBrokerTest`, `PendingNotificationTest`), `TC-53` (`ECO-269`, `NotificationApiTest`), `DeadLetterQueueTest`, `CoreStationDirectoryTest`.

---

### HU-12 · Habilitación de una carga (`ECO-196`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero habilitar un cargador por PIN o QR —con mi reserva o en modalidad walk-in—, para comenzar a cargar energía con el vehículo y red eléctrica validados.

#### Criterios de aceptación (Gherkin)

##### CA-12.1 · Camino feliz — Habilitación con reserva confirmada o walk-in con conector libre
- **Dado** que un conductor autenticado tiene una tarjeta válida y habilita el conector mediante `POST /api/sessions/activation` (con una reserva `CONFIRMED` propia dentro de la ventana, o `bookingId = null` con el conector libre y sin reserva en los próximos 15 minutos) desde `/charge` a través de Caddy o Vite
- **Cuando** la distribuidora (SOAP) y el cargador físico (REST) autorizan la activación
- **Entonces** el servicio responde `201 Created` devolviendo `sessionId` y `status = "ACTIVE"`, enciende el cargador y deja en base la sesión `ACTIVE`, el conector `OCCUPIED` y la reserva `IN_PROGRESS` (o creada para walk-in).

##### CA-12.2 · Camino alternativo — Carga rápida con potencia limitada por la distribuidora eléctrica
- **Dado** que un conductor habilita un conector de alta potencia (`> 22 kW`) y la distribuidora (SOAP) responde autorizando una potencia reducida (p. ej. `22.00 kW` en lugar de `50.00 kW`)
- **Cuando** `ChargingSessionServiceImpl` completa la habilitación
- **Entonces** responde `201 Created` creando la sesión y ordenando al cargador el límite de potencia reducido informado por la distribuidora.

##### CA-12.3 · Error / validación — Reserva ajena/sin confirmar, código de acceso erróneo o conductor ya activo
- **Dado** que el conductor envía un `bookingId` ajeno o ya cerrado, un PIN/QR incorrecto, un conector ocupado, un walk-in dentro de los 15 minutos previos a otro turno o ya tiene otra carga/reserva superpuesta
- **Cuando** envía `POST /api/sessions/activation`
- **Entonces** con reserva ajena responde `404 Not Found` opaco sin modificar esa reserva (`DEF-14`), con código inválido responde `422` (`INVALID_ACCESS_CODE`) o `429` tras varios fallos (`TOO_MANY_ACCESS_CODE_ATTEMPTS`), con reserva de otro estado responde `409 BOOKING_STATE_CONFLICT`, y con conector/conductor ocupado responde `409 Conflict` (`SLOT_UNAVAILABLE` o `DRIVER_ALREADY_BOOKED`) antes de contactar sistemas externos.

##### CA-12.4 · Falla de integración externa — Validación previa de tarjeta, caída/rechazo SOAP y compensación del cargador
- **Dado** que durante `POST /api/sessions/activation` falla `ecopedia-integration` (`503` o tarjeta inválida), la distribuidora SOAP rechaza/cae, o el cargador físico rechaza (`409`)/no responde (`503`)
- **Cuando** ocurre la falla externa en el paso correspondiente
- **Entonces** ante falla de Pagos (`503 PAYMENT_CHECK_UNAVAILABLE` / `422 PAYMENT_METHOD_INVALID`) no modifica reserva ni cargador; ante rechazo/caída SOAP o falla/rechazo del cargador revierte/cancela localmente, solicita el reintegro de la seña sin penalización, y responde `409 CHARGER_REJECTED` o `503 CHARGER_UNAVAILABLE` con textos fijos sin exponer mensajes internos del simulador (`DEF-18`).

**Trazabilidad:** `TC-54`–`TC-69` (`ECO-270`–`ECO-285`), `SessionApiTest`, `SessionLifecycleApiTest`, `ChargingSessionServiceImplTest`, `ChargingPowerPolicyTest`, `ChargingTransactionalCoordinatorTest`, `ChargerSimulatorAdapterTest`, `SessionClientsTest`, `PowerGridWsClientTest`, `CircuitBreakerPowerGridTest`, `DEF-14`/`DEF-16`/`DEF-17`/`DEF-18`/`DEF-22`.

---

### HU-13 · Cierre y cobro de la carga (`ECO-197`)

**Prioridad MoSCoW:** Must
**Historia:** Como conductor, quiero detener mi carga —o que se corte automáticamente al alcanzar mi objetivo o fin de turno— liquidando estrictamente lo medido, para pagar el importe exacto y liberar el cargador.

#### Criterios de aceptación (Gherkin)

##### CA-13.1 · Camino feliz — Corte manual o automático y liquidación descontando seña con piso mínimo
- **Dado** que una sesión `ACTIVE` registra consumo real del cargador o llega una lectura al tópico `telemetry.charging` con `batteryPercent >= targetBatteryPercent` o expira la ventana (o faltan 15 minutos para la siguiente reserva en walk-in)
- **Cuando** el dueño envía `POST /api/sessions/{sessionId}/stop` (incluso si envía `{"kwhDelivered": 0}` en el cuerpo) o actúa `TelemetryListener` / scheduler de vencimiento
- **Entonces** el sistema ignora cualquier kWh enviado por el cliente (`DEF-23`/`ECO-299`), desactiva el cargador físico, cierra la sesión (`COMPLETED`), libera conector (`AVAILABLE`) y reserva (`COMPLETED`), calcula `realCost` aplicando la seña como piso y cobra solo el excedente (`CHARGED` o `NOT_REQUIRED`), devolviendo `200 OK` con el resumen.

##### CA-13.2 · Camino alternativo — Falla a mitad de carga con consumo menor o mayor a la seña
- **Dado** que una carga termina por falla (`FAULT`) antes de completar el turno reservado
- **Cuando** `ChargingSettlementPolicy` liquida la sesión con consumo menor a la seña o mayor a la seña
- **Entonces** si `realCost < depositAmount` solicita reembolso por la diferencia (`refundAmount = depositAmount - realCost`), y si `realCost > depositAmount` liquida el excedente sin retener al conector ni al conductor.

##### CA-13.3 · Error / validación — Cierre sobre sesión ajena, inexistente o ya finalizada
- **Dado** que un conductor solicita `POST /api/sessions/{sessionId}/stop` sobre una sesión de otro conductor, inexistente o ya cerrada
- **Cuando** envía la petición sin o con cuerpo HTTP
- **Entonces** sobre sesión ajena o inexistente responde `404 Not Found` opaco sin detener ni modificar la sesión (`SEC-06`), y sobre una reserva ya cerrada responde `409 Conflict` (`BOOKING_STATE_CONFLICT`).

##### CA-13.4 · Falla de integración externa — Fallback de telemetría ante cargador caído y recuperación de liquidación pendiente
- **Dado** que al detener una sesión `PhysicalChargerPort.deactivate` falla (`503`/timeout) o `ecopedia-core`/`ecopedia-integration` están caídos
- **Cuando** se ejecuta el corte de sesión y posteriormente corre el barrido programado (`PendingSettlementSweepScheduler`)
- **Entonces** ante caída del cargador físico utiliza como energía los kWh acumulados por telemetría (no `0` arbitrario); el cierre local en base nunca se revierte, y ante caída de Tarificación o Pagos guarda `paymentStatus = "PENDING_SETTLEMENT"` hasta que el barrido asincrónico regulariza el cobro a `CHARGED`, `DEBT_RECORDED` o `NOT_REQUIRED` con `idempotencyKey` estable.

**Trazabilidad:** `TC-70`–`TC-75` (`ECO-286`–`ECO-291`), `TC-79`–`TC-81` (`ECO-296`, `ECO-297`, `ECO-300`), `SessionApiTest` (`stopSession_ignoresClientReportedZeroKwhBody`), `AutomaticCutoffTest`, `CutoffDecisionTest`, `ChargingSettlementPolicyTest`, `ChargingSessionServiceImplTest`, `PendingSettlementSweepSchedulerTest`.

---

### HU-14 · Gestión y cobro de deudas pendientes (`ECO-311` / `ECO-62`)

**Prioridad MoSCoW:** Should
**Historia:** Como conductor, quiero consultar mis deudas por cargas no cobradas y pagarlas desde mi perfil, para regularizar mi cuenta y recuperar la posibilidad de reservar o cargar.

#### Criterios de aceptación (Gherkin)

##### CA-14.1 · Camino feliz — Consulta de deudas abiertas y pago manual idempotente desde el perfil
- **Dado** que el conductor tiene una deuda abierta en estado `PENDING` originada por un cobro rechazado al cierre de carga, y tiene una tarjeta válida registrada
- **Cuando** consulta `GET /api/payment-debts` y luego envía `POST /api/payment-debts/{id}/payment` con cabecera `Idempotency-Key` y su `paymentMethodId`
- **Entonces** `GET /api/payment-debts` responde `200 OK` listando la deuda sin exponer el motivo técnico interno (`reason`), el pago responde `200 OK` con `status = "SETTLED"`, limpia `claimedUntil`, deja de figurar como deuda abierta en `/internal/payments/drivers/{id}/debt` y emite aviso `DEBT_SETTLED`.

##### CA-14.2 · Camino alternativo — Bloqueo de cuenta tras 3 reintentos automáticos fallidos y anulación de reservas
- **Dado** que una deuda vencida alcanza 3 reintentos automáticos rechazados en el barrido programado (`DebtCollectionService.retryDueDebts`)
- **Cuando** falla el 3.º reintento contra la pasarela
- **Entonces** la deuda pasa a estado `DEFAULTED`, se invoca a `ecopedia-charging` para anular las reservas confirmadas del conductor (`bookingsCancelled = true`) devolviendo sus señas, y se bloquea cualquier nuevo checkout o habilitación mientras persista la deuda.

##### CA-14.3 · Error / validación — Falta Idempotency-Key, deuda ajena, tarjeta rechazada o cobro en curso
- **Dado** que el conductor intenta pagar una deuda sin cabecera `Idempotency-Key`, pagar la deuda de otro conductor, usar una tarjeta rechazada por la pasarela, o pagar una deuda ya reclamada por el barrido
- **Cuando** ejecuta `POST /api/payment-debts/{id}/payment`
- **Entonces** sin `Idempotency-Key` responde `400 Bad Request` (`code = "IDEMPOTENCY_KEY_REQUIRED"`), sobre deuda ajena o inexistente responde `404 Not Found` opaco idéntico, con rechazo de tarjeta responde `402 Payment Required` (`code = "CARD_DECLINED"`), y con cobro en curso responde `409 Conflict` (`code = "DEBT_PAYMENT_IN_PROGRESS"`).

##### CA-14.4 · Falla de integración externa — Pasarela caída durante pago o reintento de deuda
- **Dado** que la pasarela de pagos externa no responde (`503` / timeout) durante el pago manual o el barrido automático
- **Cuando** `DebtCollectionService` intenta ejecutar el cargo contra `PaymentGatewayPort`
- **Entonces** libera el reclamo (`claimedUntil = null`) manteniendo la deuda abierta sin saldarla falsamente, el pago manual responde `503 Service Unavailable` (`code = "PAYMENT_GATEWAY_UNAVAILABLE"`), y el reintento automático reagenda la próxima tentativa si aún no alcanzó el tope.

**Trazabilidad:** `DebtApiTest`, `DebtCollectionServiceTest`, `PendingPaymentTest`, `DebtEventQueueTest`, `DebtNotificationFlowTest`, `DebtNotificationTemplatesTest`, Newman carpeta `07`.

---

### HU-15 · Asistente conversacional de búsqueda (`ECO-312` / `ECO-137`)

**Prioridad MoSCoW:** Could
**Historia:** Como conductor autenticado, quiero consultar al asistente conversacional en lenguaje natural sobre estaciones, conectores y disponibilidad, para identificar en una sola conversación dónde cargar sin navegar múltiples filtros.

#### Criterios de aceptación (Gherkin)

##### CA-15.1 · Camino feliz — Pregunta autenticada devuelve respuesta firmada con herramientas reales
- **Dado** que el conductor posee una sesión válida validada por `ecopedia-core`, la clave `GEMINI_API_KEY` está configurada y está dentro del cupo de 10 preguntas por minuto
- **Cuando** envía `POST /api/agent/chat` con `{ message, history }` y su cabecera `Authorization: Bearer <token>`
- **Entonces** el servicio `agent` (`8085`) valida el historial firmado descartando mensajes alterados sin firma válida, invoca a las herramientas contra `ecopedia-core`/`ecopedia-charging` y responde `200 OK` con `{ text, signature }`.

##### CA-15.2 · Camino alternativo — Límite de frecuencia por usuario (rate limiting) detrás de proxy
- **Dado** que un mismo cliente (identificado por `X-Forwarded-For` asignado por Caddy/Vite) realizó 10 consultas exitosas al asistente dentro de 1 minuto
- **Cuando** envía la consulta número 11 dentro del mismo minuto
- **Entonces** el servicio responde `429 Too Many Requests` con cabecera `Retry-After >= 1` antes de invocar al modelo Gemini.

##### CA-15.3 · Error / validación — Petición sin sesión, JSON malformado, mensaje excesivo o consulta muy amplia
- **Dado** que el cliente envía `POST /api/agent/chat` sin token Bearer (o con token rechazado por `core`), con mensaje vacío o mayor a 2000 caracteres, o la consulta excede el máximo de 5 vueltas de herramienta (`too-broad`)
- **Cuando** `server.ts` procesa el pedido
- **Entonces** sin token válido responde `401 Unauthorized` con `WWW-Authenticate: Bearer` sin llamar al modelo, con payload inválido responde `400 Bad Request`, y si supera las 5 vueltas responde `422 Unprocessable Entity` sugiriendo acotar la consulta a una estación.

##### CA-15.4 · Falla de integración externa — Caída, timeout o falta de clave de Gemini o caída de `ecopedia-core`
- **Dado** que falta `GEMINI_API_KEY`, la API de Gemini responde saturada (`busy`), falla (`failed`), excede los 45 s (`cancelled`/timeout), o `ecopedia-core` no responde al validar la sesión
- **Cuando** el conductor envía una pregunta a `POST /api/agent/chat`
- **Entonces** ante falta de clave, Gemini saturado o `core` inaccesible responde `503 Service Unavailable`, ante timeout de 45 s responde `504 Gateway Timeout` y ante error de modelo responde `502 Bad Gateway`, siempre con un mensaje accionable para el conductor y sin filtrar nombres de variables ni detalles internos de Gemini.

**Trazabilidad:** `agent/test/server.test.ts`, `agent/test/assistant.test.ts`, `agent/test/signature.test.ts`, `agent/test/rate-limit.test.ts`, `agent/test/geocoding.test.ts`, `agent/test/eval-set.test.ts`.

---

### HU-16 · Estadísticas de uso por telemetría (`ECO-313` / `ECO-301`)

**Prioridad MoSCoW:** Should
**Historia:** Como administrador o CPO, quiero consultar un tablero consolidado de demanda, energía entregada y sesiones a partir de la telemetría en tiempo real, para monitorear el uso de la red o de mis estaciones.

#### Criterios de aceptación (Gherkin)

##### CA-16.1 · Camino feliz — Segundo suscriptor independiente consume telemetría y expone tablero por rol
- **Dado** que el cargador publica eventos `_type = "telemetry-event"` en el tópico `telemetry.charging` de Artemis mientras `ecopedia-charging` mantiene su suscripción `ecopedia-charging-sessions`
- **Cuando** `StatsTelemetryListener` (suscripción durable `ecopedia-stats`) procesa los eventos y un `ADMIN` o `CPO` autenticado consulta `GET /api/analytics` (con `from` y `to` opcionales dentro de un máximo de 31 días)
- **Entonces** ambos suscriptores reciben todos los mensajes en paralelo sin competir entre sí; `ADMIN` recibe `200 OK` con `scope = "NETWORK"` viendo toda la red y `CPO` recibe `200 OK` con `scope = "OPERATOR"` viendo únicamente las estaciones cuyo `stationOwnerId` coincide con su ID.

##### CA-16.2 · Camino alternativo — Idempotencia en consumo y ventana por defecto de 24 horas
- **Dado** que Artemis reentrega una lectura de telemetría con el mismo `eventId` o un evento `READING` llega después de que la sesión ya cerró en `FINISHED`/`FAULTED`
- **Cuando** `StatsTelemetryListener` procesa las lecturas o se consulta `GET /api/analytics` sin parámetros `from`/`to`
- **Entonces** el consumidor descarta duplicados/regresiones sin duplicar `energyKwh` ni reabrir sesiones terminales (`SessionUsageStat`), y `GET /api/analytics` usa automáticamente la ventana de las últimas 24 horas agrupada en franjas de 15 minutos.

##### CA-16.3 · Error / validación — Acceso denegado a conductor (404 opaco), rango invertido y límite de 31 días
- **Dado** que un usuario con rol `CONDUCTOR` llama a `GET /api/analytics`, o un `ADMIN`/`CPO` envía `from > to`, un rango mayor a 31 días o una fecha ISO inválida
- **Cuando** ejecuta `GET /api/analytics`
- **Entonces** con rol `CONDUCTOR` responde `404 Not Found` opaco (`SEC-06`), sin token responde `401 Unauthorized`, con `from > to` o rango mayor a 31 días responde `400 Bad Request` (`code = "INVALID_DATE_RANGE"`), y con fecha malformada responde `400 Bad Request` (`code = "INVALID_PARAMETER"`).

##### CA-16.4 · Falla de integración externa — Caída de `ecopedia-core` al enriquecer y envío de corruptos a DLQ
- **Dado** que `ecopedia-core` responde `404`/timeout al consultar `/api/connectors/{id}` cuando llega una lectura de telemetría, o llega un mensaje JSON corrupto al tópico `telemetry.charging`
- **Cuando** `StatsTelemetryListener` procesa la lectura válida o el mensaje corrupto falla sus 5 intentos (`max-delivery-attempts = 5`)
- **Entonces** ante caída de `core`, `CoreStatsStationDirectory` degrada a `Optional.empty()` persistiendo igualmente la energía y franjas de demanda (y reintenta enriquecer en lecturas posteriores), mientras que el mensaje corrupto pasa a la DLQ de Artemis sin bloquear lecturas válidas.

**Trazabilidad:** `AnalyticsApiTest`, `AnalyticsServiceImplTest`, `StatsTelemetryFlowTest` (Pub/Sub con 2 suscriptores + deduplicación + DLQ), `SessionUsageStatTest`, `CoreStatsStationDirectoryTest`.

---

## 3. Resumen de Cumplimiento (`ECO-169 / QA-19`)

| Métrica | Regla `ECO-169` | Resultado medido | Estado |
| --- | --- | --- | --- |
| Total de Historias de Usuario en alcance | `HU-01` a `HU-16` | **16 HUs** (`11 Must`, `4 Should`, `1 Could`) | Completo |
| Formato Gherkin (`Dado / Cuando / Entonces`) | `≥ 3` criterios sin integración / `≥ 4` con integración | **16/16 HUs** cumplen el mínimo exacto con observables medibles | Cumple |
| Términos ambiguos prohibidos | `0` ocurrencias | **0** ocurrencias en criterios de aceptación | Cumple |
| Criterios fallando en HUs `Must` | `0` fallos (`100%` aprobados) | **0** fallos (al regularizar `ECO-261` y `ECO-285` y alinear `ECO-220` a `DEF-06`) | Cumple |
| Porcentaje global de criterios aprobados | `≥ 95%` | **100%** sobre criterios automatizados + regularización de `TC-04`, `TC-45`, `TC-69` | Cumple |

### Regularización de subtareas `TC` abiertas en Jira
1. **`ECO-220` (`TC-04` en `HU-01`):** Criterio `CA-01.3` alineado con la decisión registrada en [qa/README.md](README.md#L54-L55) (`DEF-06`: `400 Bad Request` con mensaje idéntico `"Credenciales inválidas"` verificado por `AuthErrorResponseTest`). Comentada en Jira (`riesgo-aceptado`) a la espera de confirmación final de QA para cierre.
2. **`ECO-261` (`TC-45` en `HU-09`):** Manejo `503 Service Unavailable` ante caída del catálogo de `core` cubierto en `BookingExceptionHandler.handleCoreDown` y `SessionClientsTest.chargingPointEdges`. Transicionada a `Listo` en Jira.
3. **`ECO-285` (`TC-69` en `HU-12`):** Flujo completo desde `/charge` detrás de Caddy/Vite hacia `ecopedia-charging` resuelto y verificado en `DEF-17` y `DEF-22` (PR `#74`). Transicionada a `Listo` en Jira.
