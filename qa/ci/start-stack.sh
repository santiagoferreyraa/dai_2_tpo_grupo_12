#!/usr/bin/env bash
# Levanta los cuatro backends de Ecopedia y los tres simuladores (pasarela de pagos, distribuidora
# SOAP y cargador) para las pruebas de QA que necesitan el sistema corriendo de verdad: la
# colección de API (QA-05), ZAP (QA-08) y la prueba de carga (QA-06).
#
# Necesita un Artemis escuchando en localhost:61616 y ECOPEDIA_BROKER_PASSWORD en el entorno.
#
# Uso:  qa/ci/start-stack.sh            (desde la raíz del repo, con los JAR ya construidos)
#
# Perfil `dev` en los siete: H2 en memoria, así cada corrida arranca de cero y no pisa datos de
# nadie, y cada cliente y su simulador comparten las credenciales de desarrollo del perfil.
#
# **Usuarios ADMIN y CPO.** El registro público siempre crea CONDUCTOR (SEC-01) y no hay usuarios
# sembrados, así que para probar el backoffice y el ABM este script genera una migración extra
# de Flyway, solo para esta corrida, en un directorio temporal. Las contraseñas salen de las
# variables QA_ADMIN_PASSWORD y QA_CPO_PASSWORD; si no están, se inventan al azar. **No queda
# ninguna contraseña en el repo** (G6).
#
# El CPO se crea primero, así le toca el id 1 y queda dueño de las 15 estaciones del seed.
#
# Deja los PID en qa/ci/.pids y los logs en qa/ci/logs/. Para bajarlo: qa/ci/stop-stack.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CI_DIR="$ROOT/qa/ci"
LOG_DIR="$CI_DIR/logs"
SEED_DIR="$(mktemp -d)"
mkdir -p "$LOG_DIR"
: > "$CI_DIR/.pids"

export ECOPEDIA_JWT_SECRET="${ECOPEDIA_JWT_SECRET:-$(openssl rand -hex 32)}"
export QA_ADMIN_EMAIL="${QA_ADMIN_EMAIL:-admin.qa@ecopedia.test}"
export QA_CPO_EMAIL="${QA_CPO_EMAIL:-cpo.qa@ecopedia.test}"
export QA_ADMIN_PASSWORD="${QA_ADMIN_PASSWORD:-$(openssl rand -hex 12)}"
export QA_CPO_PASSWORD="${QA_CPO_PASSWORD:-$(openssl rand -hex 12)}"

# --- Usuarios de prueba, como migración de Flyway temporal ---------------------------------
python3 - "$SEED_DIR" <<'PY'
import os, sys, bcrypt

def hashed(password):
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()

rows = [
    (os.environ["QA_CPO_EMAIL"], hashed(os.environ["QA_CPO_PASSWORD"]), "Operador QA", "CPO"),
    (os.environ["QA_ADMIN_EMAIL"], hashed(os.environ["QA_ADMIN_PASSWORD"]), "Admin QA", "ADMIN"),
]
# Versión lejana a propósito: siempre corre después de las migraciones reales.
with open(os.path.join(sys.argv[1], "V209912312359__qa_ci_users.sql"), "w") as sql:
    for email, password_hash, name, role in rows:
        sql.write(
            "INSERT INTO users (email, password_hash, full_name, role, active) "
            f"VALUES ('{email}', '{password_hash}', '{name}', '{role}', TRUE);\n"
        )
PY

start() {
  local name="$1" jar="$2" port="$3"; shift 3
  java -jar "$ROOT/$jar" --spring.profiles.active=dev --server.port="$port" "$@" \
    > "$LOG_DIR/$name.log" 2>&1 &
  echo $! >> "$CI_DIR/.pids"
}

# Los simuladores primero: los backends les hablan apenas reciben un pedido.
start gateway  simulation/payment-gateway/target/payment-gateway-simulator-0.1.0-SNAPSHOT.jar 8086
start grid     simulation/power-grid/target/power-grid-simulator-0.1.0-SNAPSHOT.jar 8087
start charger  simulation/charger/target/charger-simulator-0.1.0-SNAPSHOT.jar 8088

start core        backend/ecopedia-core/target/ecopedia-core-0.1.0-SNAPSHOT.jar 8081 \
  "--spring.flyway.locations=classpath:db/migration,filesystem:$SEED_DIR"
start integration backend/ecopedia-integration/target/ecopedia-integration-0.1.0-SNAPSHOT.jar 8083
start charging    backend/ecopedia-charging/target/ecopedia-charging-0.1.0-SNAPSHOT.jar 8082
start async       backend/ecopedia-async/target/ecopedia-async-0.1.0-SNAPSHOT.jar 8084

# --- Esperar a que los siete contesten ------------------------------------------------------
# Sin actuator: alcanza con que el puerto devuelva cualquier código HTTP.
wait_for() {
  local name="$1" url="$2"
  for _ in $(seq 1 90); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)"
    if [ "$code" != "000" ]; then echo "$name listo ($code)"; return 0; fi
    sleep 2
  done
  echo "$name no arrancó en 3 minutos. Últimas líneas del log:" >&2
  tail -n 60 "$LOG_DIR/$name.log" >&2
  return 1
}

wait_for gateway     http://localhost:8086/
wait_for grid        http://localhost:8087/ws
wait_for charger     http://localhost:8088/
wait_for core        http://localhost:8081/api/vehicle-models
wait_for integration http://localhost:8083/api/payment-methods
wait_for charging    http://localhost:8082/api/bookings/mine
wait_for async       http://localhost:8084/api/notifications

# Para los pasos siguientes del job (GitHub Actions) o para la terminal que lo llamó.
if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "ECOPEDIA_JWT_SECRET=$ECOPEDIA_JWT_SECRET"
    echo "QA_ADMIN_EMAIL=$QA_ADMIN_EMAIL"
    echo "QA_CPO_EMAIL=$QA_CPO_EMAIL"
    echo "QA_ADMIN_PASSWORD=$QA_ADMIN_PASSWORD"
    echo "QA_CPO_PASSWORD=$QA_CPO_PASSWORD"
  } >> "$GITHUB_ENV"
  echo "::add-mask::$QA_ADMIN_PASSWORD"
  echo "::add-mask::$QA_CPO_PASSWORD"
  echo "::add-mask::$ECOPEDIA_JWT_SECRET"
else
  # En una terminal, las contraseñas al azar no vuelven al shell que llamó al script: para usar
  # los usuarios después, exportá QA_ADMIN_PASSWORD y QA_CPO_PASSWORD antes de correrlo.
  echo "Usuarios de prueba: $QA_ADMIN_EMAIL (ADMIN) y $QA_CPO_EMAIL (CPO)."
fi
