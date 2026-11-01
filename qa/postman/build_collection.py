#!/usr/bin/env python3
"""
Genera la colección de Postman de Ecopedia (QA-05, ECO-155).

    python3 qa/postman/build_collection.py

Escribe ecopedia.postman_collection.json al lado de este archivo. La colección se versiona
generada, pero se edita acá: un caso nuevo es una línea más en la lista, y así cada endpoint
queda con su caso de éxito y su caso de error uno al lado del otro.

Convenciones de cada caso:
- `expect`: el código esperado. Es el comportamiento ACTUAL. Donde difiere de lo que piden los
  criterios, `defect` nombra el defecto registrado (QA-04) para que se vea en el reporte.
- `token`: qué sesión manda (admin, cpo, driver, other) o None para ir sin token.
- `save`: variables de colección que se guardan desde la respuesta (nombre -> expresión JS).
- `check`: aserciones JS extra sobre `json` (la respuesta ya parseada).
"""

import json
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent

CORE, CHARGING, INTEGRATION, ASYNC = "{{coreUrl}}", "{{chargingUrl}}", "{{integrationUrl}}", "{{asyncUrl}}"


def case(name, method, url, expect, token=None, body=None, save=None, check=None, pre=None, defect=None,
         poll=None, headers=None):
    return dict(name=name, method=method, url=url, expect=expect, token=token, body=body,
                save=save or {}, check=check or [], pre=pre or [], defect=defect, poll=poll,
                headers=headers or {})


FOLDERS = [
    ("00 · Preparación", [
        case("Login ADMIN de prueba", "POST", f"{CORE}/api/auth/login", 200,
             body={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
             save={"adminToken": "json.token", "adminId": "json.userId"},
             check=["pm.expect(json.role).to.eql('ADMIN')"]),
        case("Login CPO de prueba", "POST", f"{CORE}/api/auth/login", 200,
             body={"email": "{{cpoEmail}}", "password": "{{cpoPassword}}"},
             save={"cpoToken": "json.token", "cpoId": "json.userId"},
             check=["pm.expect(json.role).to.eql('CPO')"]),
    ]),
    ("01 · Core · Registro y login", [
        case("Registro de conductor", "POST", f"{CORE}/api/auth/register", 201,
             pre=["pm.collectionVariables.set('driverEmail', `qa.${Date.now()}@ecopedia.test`)",
                  "pm.collectionVariables.set('driverPassword', 'Clave-' + Math.random().toString(36).slice(2, 10))"],
             body={"email": "{{driverEmail}}", "password": "{{driverPassword}}", "fullName": "Conductor QA"},
             save={"driverId": "json.id"},
             check=["pm.expect(json.role).to.eql('CONDUCTOR')", "pm.expect(json).to.not.have.property('passwordHash')"]),
        case("Registro con email repetido", "POST", f"{CORE}/api/auth/register", 400,
             body={"email": "{{driverEmail}}", "password": "otraClave123", "fullName": "Duplicado"}),
        case("Registro de un segundo conductor (para el backoffice)", "POST", f"{CORE}/api/auth/register", 201,
             pre=["pm.collectionVariables.set('otherEmail', `qa.otro.${Date.now()}@ecopedia.test`)"],
             body={"email": "{{otherEmail}}", "password": "unaClave123", "fullName": "Otro QA"},
             save={"otherId": "json.id"}),
        case("Registro que intenta elegir rol ADMIN (SEC-01)", "POST", f"{CORE}/api/auth/register", 201,
             pre=["pm.collectionVariables.set('roleEmail', `qa.rol.${Date.now()}@ecopedia.test`)"],
             body={"email": "{{roleEmail}}", "password": "unaClave123", "fullName": "Quiere ser admin", "role": "ADMIN"},
             check=["pm.expect(json.role).to.eql('CONDUCTOR')"]),
        case("Login de conductor", "POST", f"{CORE}/api/auth/login", 200,
             body={"email": "{{driverEmail}}", "password": "{{driverPassword}}"},
             save={"driverToken": "json.token"},
             check=["pm.expect(json.expiresInSeconds).to.be.above(0)"]),
        case("Login con contraseña equivocada", "POST", f"{CORE}/api/auth/login", 400,
             body={"email": "{{driverEmail}}", "password": "noEsLaClave"},
             check=["pm.expect(json).to.not.have.property('token')"],
             defect="DEF-06: los criterios piden 401 ante credenciales inválidas"),
    ]),
    ("02 · Core · Perfil", [
        case("Ver mi perfil", "GET", f"{CORE}/api/users/profile", 200, token="driver",
             check=["pm.expect(json.email).to.eql(pm.collectionVariables.get('driverEmail'))"]),
        case("Ver mi perfil sin token", "GET", f"{CORE}/api/users/profile", 403,
             defect="DEF-02: los criterios piden 401 sin sesión válida"),
        case("Editar mi perfil", "PUT", f"{CORE}/api/users/profile", 200, token="driver",
             body={"fullName": "Conductor QA Editado", "vehicleModelId": None},
             check=["pm.expect(json.fullName).to.eql('Conductor QA Editado')"]),
        case("Editar mi perfil con el nombre vacío", "PUT", f"{CORE}/api/users/profile", 400, token="driver",
             body={"fullName": "", "vehicleModelId": None}),
        case("Cambiar mi contraseña", "PUT", f"{CORE}/api/users/profile/password", 204, token="driver",
             pre=["pm.collectionVariables.set('newDriverPassword', 'Nueva-' + Math.random().toString(36).slice(2, 10))"],
             body={"currentPassword": "{{driverPassword}}", "newPassword": "{{newDriverPassword}}"},
             save={"driverPassword": "pm.collectionVariables.get('newDriverPassword')"}),
        case("Cambiar mi contraseña con la actual equivocada", "PUT", f"{CORE}/api/users/profile/password", 400,
             token="driver", body={"currentPassword": "noEsLaActual", "newPassword": "otraNueva123"}),
    ]),
    ("03 · Core · Catálogo y búsqueda (público)", [
        case("Catálogo de vehículos", "GET", f"{CORE}/api/vehicle-models", 200,
             check=["pm.expect(json).to.be.an('array').that.is.not.empty"]),
        case("Catálogo de vehículos con un método que no existe", "POST", f"{CORE}/api/vehicle-models", 405, body={}),
        case("Listado de estaciones", "GET", f"{CORE}/api/stations", 200,
             check=["pm.expect(json.length).to.be.at.least(15)"]),
        case("Listado de estaciones con un método que no existe", "PATCH", f"{CORE}/api/stations", 405, body={}),
        case("Estación por id", "GET", f"{CORE}/api/stations/1", 200,
             check=["pm.expect(json.id).to.eql(1)", "pm.expect(json.connectors).to.be.an('array')"]),
        case("Estación inexistente", "GET", f"{CORE}/api/stations/999999", 404),
        case("Búsqueda por cercanía", "GET", f"{CORE}/api/search?lat=-34.6037&lon=-58.3816&radiusKm=10", 200,
             check=["pm.expect(json).to.be.an('array').that.is.not.empty"]),
        case("Búsqueda con radio cero", "GET", f"{CORE}/api/search?lat=-34.6037&lon=-58.3816&radiusKm=0", 400),
        case("Listado de conectores", "GET", f"{CORE}/api/connectors", 200,
             save={"seedConnectorId": "json.find(c => c.stationId !== 15 && c.operationalStatus !== 'OUT_OF_SERVICE').id"},
             check=["pm.expect(json).to.be.an('array').that.is.not.empty"]),
        case("Listado de conectores con stationId no numérico", "GET", f"{CORE}/api/connectors?stationId=abc", 404,
             defect="DEF-12: un parámetro con formato inválido responde 404 y no 400"),
        case("Conector por id", "GET", f"{CORE}/api/connectors/{{{{seedConnectorId}}}}", 200,
             check=["pm.expect(json.id).to.eql(Number(pm.collectionVariables.get('seedConnectorId')))"]),
        case("Conector inexistente", "GET", f"{CORE}/api/connectors/999999", 404),
    ]),
    ("04 · Core · ABM de terminales (CPO)", [
        case("Alta de estación", "POST", f"{CORE}/api/stations", 201, token="cpo",
             body={"name": "Estación QA", "address": "Av. Corrientes 1234, CABA",
                   "latitude": -34.6037, "longitude": -58.3816, "photoUrls": []},
             save={"newStationId": "json.id"},
             check=["pm.expect(json.ownerId).to.eql(Number(pm.collectionVariables.get('cpoId')))"]),
        case("Alta de estación con token de conductor (SEC-05)", "POST", f"{CORE}/api/stations", 404, token="driver",
             body={"name": "No", "address": "No", "latitude": -34.6, "longitude": -58.38, "photoUrls": []}),
        case("Edición de estación", "PUT", f"{CORE}/api/stations/{{{{newStationId}}}}", 200, token="cpo",
             body={"name": "Estación QA Editada", "address": "Av. Corrientes 1234, CABA",
                   "latitude": -34.6037, "longitude": -58.3816, "photoUrls": []},
             check=["pm.expect(json.name).to.eql('Estación QA Editada')"]),
        case("Edición de estación sin nombre", "PUT", f"{CORE}/api/stations/{{{{newStationId}}}}", 400, token="cpo",
             body={"address": "Av. Corrientes 1234, CABA", "latitude": -34.6, "longitude": -58.38, "photoUrls": []}),
        case("Alta de conector", "POST", f"{CORE}/api/stations/{{{{newStationId}}}}/connectors", 201, token="cpo",
             body={"connectorType": "CCS2", "maxPowerKw": 50},
             save={"newConnectorId": "json.id"}),
        case("Alta de conector con potencia cero", "POST", f"{CORE}/api/stations/{{{{newStationId}}}}/connectors", 400,
             token="cpo", body={"connectorType": "CCS2", "maxPowerKw": 0}),
        case("Reconfiguración de conector", "POST", f"{CORE}/api/connectors/{{{{newConnectorId}}}}/configure", 200,
             token="cpo", body={"connectorType": "TYPE_2", "maxPowerKw": 22},
             check=["pm.expect(json.connectorType).to.eql('TYPE_2')"]),
        case("Reconfiguración con potencia negativa", "POST", f"{CORE}/api/connectors/{{{{newConnectorId}}}}/configure",
             400, token="cpo", body={"connectorType": "TYPE_2", "maxPowerKw": -5}),
        case("Cambio de estado operativo", "PATCH", f"{CORE}/api/connectors/{{{{newConnectorId}}}}/status", 200,
             token="cpo", body={"operationalStatus": "AVAILABLE"}),
        case("Cambio de estado a un valor que no existe", "PATCH", f"{CORE}/api/connectors/{{{{newConnectorId}}}}/status",
             400, token="cpo", body={"operationalStatus": "EXPLOTADO"}),
    ]),
    # Desde el 31/10 los precios son de la red (los fija el ADMIN) y la seña y la hora pico, de cada
    # estación (su CPO). Las rutas viejas por conector ya no existen.
    ("05 · Core · Tarifas", [
        case("Precios de la red", "GET", f"{CORE}/api/pricing/rates", 200,
             check=["pm.expect(Number(json.normalKwhRate)).to.be.above(0)",
                    "pm.expect(Number(json.fastKwhRate)).to.be.above(0)"]),
        case("Cambiar los precios con token de CPO (SEC-05)", "PUT", f"{CORE}/api/pricing/rates", 404, token="cpo",
             body={"normalKwhRate": 1, "fastKwhRate": 1, "excessPenaltyPerMin": 0}),
        case("Cambiar los precios de la red", "PUT", f"{CORE}/api/pricing/rates", 200, token="admin",
             body={"normalKwhRate": 350, "fastKwhRate": 550, "excessPenaltyPerMin": 200},
             check=["pm.expect(Number(json.fastKwhRate)).to.eql(550)"]),
        case("Cambiar los precios a un valor negativo", "PUT", f"{CORE}/api/pricing/rates", 400, token="admin",
             body={"normalKwhRate": -1, "fastKwhRate": 550, "excessPenaltyPerMin": 200}),
        case("Seña y hora pico de la estación propia", "PUT", f"{CORE}/api/pricing/stations/{{{{newStationId}}}}", 200,
             token="cpo", body={"depositAmount": 1200, "peakPricingEnabled": True},
             check=["pm.expect(Number(json.depositAmount)).to.eql(1200)"]),
        case("Seña negativa", "PUT", f"{CORE}/api/pricing/stations/{{{{newStationId}}}}", 400, token="cpo",
             body={"depositAmount": -1, "peakPricingEnabled": True}),
        case("Consultar la seña y la hora pico de la estación", "GET",
             f"{CORE}/api/pricing/stations/{{{{newStationId}}}}", 200,
             check=["pm.expect(json.peakPricingEnabled).to.be.true"]),
        case("Consultar una estación inexistente", "GET", f"{CORE}/api/pricing/stations/999999", 400,
             defect="DEF-07: un recurso inexistente responde 400 y no 404"),
        case("Seña de un conector", "GET", f"{CORE}/api/pricing/deposit?connectorId={{{{newConnectorId}}}}", 200,
             check=["pm.expect(Number(json.depositAmount)).to.eql(1200)"]),
        case("Estimar costo", "GET", f"{CORE}/api/pricing/estimate?connectorId={{{{newConnectorId}}}}&estimatedKwh=30", 200,
             check=["pm.expect(Number(json.estimatedCost)).to.be.above(0)"]),
        case("Estimar costo sin kWh", "GET", f"{CORE}/api/pricing/estimate?connectorId={{{{newConnectorId}}}}", 400),
    ]),
    ("06 · Core · Backoffice (ADMIN)", [
        case("Tablero del administrador", "GET", f"{CORE}/api/admin/dashboard", 200, token="admin"),
        case("Tablero con token de conductor (SEC-05)", "GET", f"{CORE}/api/admin/dashboard", 404, token="driver"),
        case("Listado de usuarios", "GET", f"{CORE}/api/users?role=CONDUCTOR", 200, token="admin",
             check=["pm.expect(json.every(u => u.role === 'CONDUCTOR')).to.be.true"]),
        case("Listado de usuarios con token de conductor", "GET", f"{CORE}/api/users", 404, token="driver"),
        case("Usuario por id", "GET", f"{CORE}/api/users/{{{{otherId}}}}", 200, token="admin"),
        case("Usuario inexistente", "GET", f"{CORE}/api/users/999999", 400, token="admin",
             defect="DEF-07: un recurso inexistente responde 400 y no 404"),
        case("Cambio de rol", "PATCH", f"{CORE}/api/users/{{{{otherId}}}}/role", 200, token="admin",
             body={"role": "CPO"}, check=["pm.expect(json.role).to.eql('CPO')"]),
        case("Cambio a un rol que no existe", "PATCH", f"{CORE}/api/users/{{{{otherId}}}}/role", 400, token="admin",
             body={"role": "SUPERADMIN"}),
        case("Baja de usuario", "DELETE", f"{CORE}/api/users/{{{{otherId}}}}", 204, token="admin"),
        case("Baja de usuario con token de conductor", "DELETE", f"{CORE}/api/users/{{{{otherId}}}}", 404, token="driver"),
        case("Reactivación de usuario", "POST", f"{CORE}/api/users/{{{{otherId}}}}/activate", 204, token="admin"),
        case("Reactivación con token de conductor", "POST", f"{CORE}/api/users/{{{{otherId}}}}/activate", 404, token="driver"),
        case("Backoffice: baja de usuario", "DELETE", f"{CORE}/api/admin/users/{{{{otherId}}}}", 204, token="admin"),
        case("Backoffice: baja de usuario con token de conductor", "DELETE", f"{CORE}/api/admin/users/{{{{otherId}}}}", 404,
             token="driver"),
        case("Backoffice: reactivación de usuario", "POST", f"{CORE}/api/admin/users/{{{{otherId}}}}/activate", 204,
             token="admin"),
        case("Backoffice: reactivación con token de conductor", "POST", f"{CORE}/api/admin/users/{{{{otherId}}}}/activate",
             404, token="driver"),
        case("Backoffice: baja de estación", "DELETE", f"{CORE}/api/admin/stations/15", 204, token="admin"),
        case("Backoffice: baja de estación con token de CPO", "DELETE", f"{CORE}/api/admin/stations/15", 404, token="cpo"),
    ]),
    ("07 · Pagos · Medios de pago", [
        case("Alta de tarjeta", "POST", f"{INTEGRATION}/api/payment-methods", 201, token="driver",
             body={"number": "4111111111111111", "expiryMonth": 9, "expiryYear": 2029,
                   "holderName": "CONDUCTOR QA", "label": "Tarjeta QA"},
             save={"cardId": "json.id"},
             check=["pm.expect(json.brand).to.eql('VISA')", "pm.expect(json.lastFour).to.eql('1111')",
                    "pm.expect(JSON.stringify(json)).to.not.include('4111111111111111')"]),
        case("Alta de tarjeta con número inválido", "POST", f"{INTEGRATION}/api/payment-methods", 400, token="driver",
             body={"number": "4111111111111112", "expiryMonth": 9, "expiryYear": 2029, "holderName": "X"}),
        case("Listado de mis tarjetas", "GET", f"{INTEGRATION}/api/payment-methods", 200, token="driver",
             check=["pm.expect(json.map(c => c.id)).to.include(Number(pm.collectionVariables.get('cardId')))"]),
        case("Listado de tarjetas sin token", "GET", f"{INTEGRATION}/api/payment-methods", 403,
             defect="DEF-02: los criterios piden 401 sin sesión válida"),
    ]),
    ("08 · Reservas y checkout", [
        case("Disponibilidad de un conector", "GET",
             f"{CHARGING}/api/bookings/availability?connectorId={{{{seedConnectorId}}}}&from={{{{availFrom}}}}&to={{{{availTo}}}}",
             200, token="driver",
             pre=["const from = new Date(Date.now() + 24 * 3600e3); from.setUTCMinutes(0, 0, 0);",
                  "pm.collectionVariables.set('availFrom', from.toISOString());",
                  "pm.collectionVariables.set('availTo', new Date(from.getTime() + 4 * 3600e3).toISOString());"],
             check=["pm.expect(json).to.be.an('array')"]),
        case("Disponibilidad con el rango invertido", "GET",
             f"{CHARGING}/api/bookings/availability?connectorId={{{{seedConnectorId}}}}&from={{{{availTo}}}}&to={{{{availFrom}}}}",
             400, token="driver"),
        case("Retener un slot", "POST", f"{CHARGING}/api/bookings/holds", 201, token="driver",
             pre=["const start = new Date(Date.now() + 2 * 24 * 3600e3); start.setUTCMinutes(0, 0, 0);",
                  "pm.collectionVariables.set('holdStart', start.toISOString());",
                  "pm.collectionVariables.set('holdEnd', new Date(start.getTime() + 3600e3).toISOString());"],
             body={"connectorId": "{{seedConnectorId}}", "start": "{{holdStart}}", "end": "{{holdEnd}}"},
             save={"holdId": "json.id"},
             check=["pm.expect(json.expiresAt).to.be.a('string')"]),
        case("Retener con el fin antes que el inicio", "POST", f"{CHARGING}/api/bookings/holds", 400, token="driver",
             body={"connectorId": "{{seedConnectorId}}", "start": "{{holdEnd}}", "end": "{{holdStart}}"}),
        # El checkout pide Idempotency-Key desde ECO-53 / QA-29: cobra la seña, y la clave es lo que
        # evita cobrarla dos veces si el pedido se repite.
        case("Checkout sin Idempotency-Key", "POST", f"{CHARGING}/api/checkout/booking", 400, token="driver",
             body={"holdId": "{{holdId}}", "paymentMethodId": "{{cardId}}", "acceptGracePeriod": True},
             check=["pm.expect(json.code).to.eql('IDEMPOTENCY_KEY_REQUIRED')"]),
        case("Checkout sin aceptar la tolerancia", "POST", f"{CHARGING}/api/checkout/booking", 400, token="driver",
             headers={"Idempotency-Key": "{{$guid}}"},
             body={"holdId": "{{holdId}}", "paymentMethodId": "{{cardId}}", "acceptGracePeriod": False},
             check=["pm.expect(json.code).to.eql('GRACE_PERIOD_NOT_ACCEPTED')"]),
        case("Checkout de la reserva", "POST", f"{CHARGING}/api/checkout/booking", 201, token="driver",
             pre=["pm.collectionVariables.set('checkoutKey', pm.variables.replaceIn('{{$guid}}'))"],
             headers={"Idempotency-Key": "{{checkoutKey}}"},
             body={"holdId": "{{holdId}}", "paymentMethodId": "{{cardId}}", "acceptGracePeriod": True},
             save={"bookingId": "json.bookingId"},
             check=["pm.expect(json.status).to.eql('CONFIRMED')"]),
        case("Checkout repetido con la misma clave (QA-29)", "POST", f"{CHARGING}/api/checkout/booking", 201,
             token="driver", headers={"Idempotency-Key": "{{checkoutKey}}"},
             body={"holdId": "{{holdId}}", "paymentMethodId": "{{cardId}}", "acceptGracePeriod": True},
             check=["pm.expect(json.bookingId).to.eql(Number(pm.collectionVariables.get('bookingId')))"]),
        case("Mis reservas", "GET", f"{CHARGING}/api/bookings/mine", 200, token="driver",
             check=["pm.expect(json.map(b => b.id)).to.include(Number(pm.collectionVariables.get('bookingId')))"]),
        case("Mis reservas con token de CPO (SEC-05)", "GET", f"{CHARGING}/api/bookings/mine", 404, token="cpo"),
    ]),
    ("09 · Avisos", [
        case("Mis avisos (espera el de la reserva confirmada)", "GET", f"{ASYNC}/api/notifications", 200, token="driver",
             save={"notificationId": "json.items.length ? json.items[0].id : ''"},
             check=["pm.expect(json.items).to.be.an('array').that.is.not.empty"],
             poll="json.items.length === 0"),
        case("Mis avisos sin token", "GET", f"{ASYNC}/api/notifications", 403,
             defect="DEF-02: los criterios piden 401 sin sesión válida"),
        case("Marcar un aviso como leído", "POST", f"{ASYNC}/api/notifications/{{{{notificationId}}}}/read", 204,
             token="driver"),
        case("Marcar como leído un aviso inexistente", "POST", f"{ASYNC}/api/notifications/999999/read", 404,
             token="driver"),
        case("Marcar todos como leídos", "POST", f"{ASYNC}/api/notifications/read-all", 204, token="driver"),
        case("Marcar todos sin token", "POST", f"{ASYNC}/api/notifications/read-all", 403,
             defect="DEF-02: los criterios piden 401 sin sesión válida"),
        case("Borrar un aviso", "DELETE", f"{ASYNC}/api/notifications/{{{{notificationId}}}}", 204, token="driver"),
        case("Borrar un aviso inexistente", "DELETE", f"{ASYNC}/api/notifications/999999", 404, token="driver"),
        case("Borrar todos", "DELETE", f"{ASYNC}/api/notifications", 204, token="driver"),
        case("Borrar todos sin token", "DELETE", f"{ASYNC}/api/notifications", 403,
             defect="DEF-02: los criterios piden 401 sin sesión válida"),
    ]),
    ("10 · Bajas y limpieza", [
        case("Cancelar una reserva inexistente", "DELETE", f"{CHARGING}/api/bookings/999999", 404, token="driver"),
        case("Cancelar mi reserva", "DELETE", f"{CHARGING}/api/bookings/{{{{bookingId}}}}", 204, token="driver"),
        case("Baja de conector con token de conductor", "DELETE", f"{CORE}/api/connectors/{{{{newConnectorId}}}}", 404,
             token="driver"),
        case("Baja de conector", "DELETE", f"{CORE}/api/connectors/{{{{newConnectorId}}}}", 204, token="cpo"),
        case("Baja de estación con token de conductor", "DELETE", f"{CORE}/api/stations/{{{{newStationId}}}}", 404,
             token="driver"),
        case("Baja de estación", "DELETE", f"{CORE}/api/stations/{{{{newStationId}}}}", 204, token="cpo"),
        case("Baja de una tarjeta inexistente", "DELETE", f"{INTEGRATION}/api/payment-methods/999999", 400,
             token="driver", defect="DEF-07: un recurso inexistente responde 400 y no 404"),
        case("Baja de mi tarjeta", "DELETE", f"{INTEGRATION}/api/payment-methods/{{{{cardId}}}}", 204, token="driver"),
    ]),
]

TOKENS = {"admin": "{{adminToken}}", "cpo": "{{cpoToken}}", "driver": "{{driverToken}}"}


def build_item(c):
    headers = [{"key": "Accept", "value": "application/json"}]
    if c["token"]:
        headers.append({"key": "Authorization", "value": "Bearer " + TOKENS[c["token"]]})
    for key, value in c["headers"].items():
        headers.append({"key": key, "value": value})
    request = {"method": c["method"], "header": headers, "url": c["url"]}
    if c["body"] is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        raw = json.dumps(c["body"], ensure_ascii=False, indent=2)
        # Las variables numéricas viajan sin comillas: "{{cardId}}" -> {{cardId}}
        for var in ("newConnectorId", "seedConnectorId", "cardId"):
            raw = raw.replace(f'"{{{{{var}}}}}"', f"{{{{{var}}}}}")
        request["body"] = {"mode": "raw", "raw": raw}

    title = f"{c['name']} → {c['expect']}"
    tests = [
        "let json = {};",
        "try { json = pm.response.json(); } catch (e) { /* 204 o cuerpo que no es JSON */ }",
    ]
    body_lines = [f"pm.test('responde {c['expect']}', () => pm.response.to.have.status({c['expect']}));"]
    if c["expect"] >= 400:
        body_lines.append("pm.test('sin stack trace en el cuerpo', () => "
                          "pm.expect(pm.response.text()).to.not.match(/(at [a-z]+\\.[\\w.$]+\\(|Exception:|\\.java:\\d+)/));")
    for assertion in c["check"]:
        body_lines.append(f"pm.test({json.dumps(assertion)}, () => {{ {assertion}; }});")
    for var, expr in c["save"].items():
        body_lines.append(f"if (pm.response.code === {c['expect']}) pm.collectionVariables.set('{var}', {expr});")

    if c["poll"]:
        # Los avisos llegan por la cola: se reintenta hasta 15 veces, uno por segundo, y recién
        # después se evalúa. Sin el reintento, el test dependería de cuánto tarda el broker.
        tests += [
            "const tries = Number(pm.collectionVariables.get('pollTries') || 0);",
            f"const waiting = pm.response.code === 200 && ({c['poll']}) && tries < 15;",
            "if (waiting) {",
            "  pm.collectionVariables.set('pollTries', tries + 1);",
            "  setTimeout(() => {}, 1000);",
            "  postman.setNextRequest(pm.info.requestName);",
            "} else {",
            "  pm.collectionVariables.set('pollTries', 0);",
        ] + ["  " + line for line in body_lines] + ["}"]
    else:
        tests += body_lines
    if c["defect"]:
        tests.append(f"// Defecto conocido: {c['defect']}")

    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}]
    if c["pre"]:
        events.insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": c["pre"]}})

    item = {"name": title, "event": events, "request": request}
    if c["defect"]:
        item["description"] = c["defect"]
    return item


def main():
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "ecopedia/qa/postman")),
            "name": "Ecopedia · API (QA-05)",
            "description": "Un caso de éxito y uno de error por endpoint. Generada por build_collection.py: "
                           "editá el script, no este JSON.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": [{"name": folder, "item": [build_item(c) for c in cases]} for folder, cases in FOLDERS],
        "variable": [{"key": k, "value": ""} for k in (
            "adminToken", "adminId", "cpoToken", "cpoId", "driverEmail", "driverPassword", "newDriverPassword",
            "driverId", "driverToken", "otherEmail", "otherId", "roleEmail", "seedConnectorId", "newStationId",
            "newConnectorId", "cardId", "availFrom", "availTo", "holdStart", "holdEnd", "holdId", "bookingId",
            "notificationId", "pollTries", "checkoutKey")],
    }
    out = HERE / "ecopedia.postman_collection.json"
    out.write_text(json.dumps(collection, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total = sum(len(cases) for _, cases in FOLDERS)
    print(f"{out.name}: {total} casos en {len(FOLDERS)} carpetas")


if __name__ == "__main__":
    main()
