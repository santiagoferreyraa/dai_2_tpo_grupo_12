#!/usr/bin/env bash
# Pruebas de contrato (QA-25): Schemathesis genera pedidos desde el OpenAPI de cada módulo
# (/v3/api-docs) y valida cada respuesta contra ese mismo contrato.
#
# Necesita el sistema corriendo (qa/ci/start-stack.sh) y, en el entorno, QA_ADMIN_EMAIL y
# QA_ADMIN_PASSWORD (los deja start-stack.sh). Schemathesis tiene que estar instalado:
#   pip install schemathesis==4.34.2
#
# Uso:  qa/ci/schemathesis.sh          (desde la raíz del repo)
#
# Qué se valida en cada respuesta:
#   - not_a_server_error            ningún 500
#   - status_code_conformance       el código está declarado en el contrato
#   - content_type_conformance      el tipo de contenido es el declarado
#   - response_schema_conformance   el cuerpo tiene la forma declarada
#   - response_headers_conformance  las cabeceras declaradas están
#
# Core se prueba con el ADMIN (llega a todo el ABM) y los otros tres con un conductor nuevo, que
# es el rol de casi todas sus rutas. Va después de Newman: Schemathesis crea y borra datos al azar.
#
# Ojo al correrlo dos veces contra el mismo core: las bajas y los cambios de rol piden la
# contraseña del ADMIN, Schemathesis manda contraseñas al azar y cuentan como intentos fallidos
# (#54), así que el ADMIN queda bloqueado 15 minutos y el segundo login da 429. Reiniciar core.
#
# La semilla es fija para que un rojo se pueda reproducir con el mismo pedido (SCHEMATHESIS_SEED
# la cambia), y sin la base de ejemplos de Schemathesis, que haría depender una corrida de las
# anteriores. Los informes JUnit quedan en qa/schemathesis/.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/qa/schemathesis"
mkdir -p "$OUT"

CORE_URL="${CORE_URL:-http://localhost:8081}"
CHARGING_URL="${CHARGING_URL:-http://localhost:8082}"
INTEGRATION_URL="${INTEGRATION_URL:-http://localhost:8083}"
ASYNC_URL="${ASYNC_URL:-http://localhost:8084}"
SEED="${SCHEMATHESIS_SEED:-20261125}"
SCHEMATHESIS="${SCHEMATHESIS:-schemathesis}"

: "${QA_ADMIN_EMAIL:?Falta QA_ADMIN_EMAIL}"
: "${QA_ADMIN_PASSWORD:?Falta QA_ADMIN_PASSWORD}"

# El token sale del JSON del login; node está en el job de CI y en las máquinas del equipo.
token_from() {
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const t=JSON.parse(s).token;if(!t){process.exit(1)}process.stdout.write(t)})'
}

login() {
  local email="$1" password="$2"
  node -e 'process.stdout.write(JSON.stringify({email:process.argv[1],password:process.argv[2]}))' "$email" "$password" \
    | curl -sf -H 'Content-Type: application/json' --data-binary @- "$CORE_URL/api/auth/login" \
    | token_from
}

ADMIN_TOKEN="$(login "$QA_ADMIN_EMAIL" "$QA_ADMIN_PASSWORD")"

DRIVER_EMAIL="conductor.contrato.$RANDOM@ecopedia.test"
DRIVER_PASSWORD="$(openssl rand -hex 12 | tr -d '\r\n')"
node -e 'process.stdout.write(JSON.stringify({email:process.argv[1],password:process.argv[2],fullName:"Conductor contrato"}))' \
  "$DRIVER_EMAIL" "$DRIVER_PASSWORD" \
  | curl -sf -o /dev/null -H 'Content-Type: application/json' --data-binary @- "$CORE_URL/api/auth/register"
DRIVER_TOKEN="$(login "$DRIVER_EMAIL" "$DRIVER_PASSWORD")"

if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "::add-mask::$ADMIN_TOKEN"
  echo "::add-mask::$DRIVER_TOKEN"
fi

status=0
run() {
  local name="$1" url="$2" token="$3"
  echo "=== Contrato de $name ($url/v3/api-docs) ==="
  "$SCHEMATHESIS" run "$url/v3/api-docs" \
    --header "Authorization: Bearer $token" \
    --checks not_a_server_error,status_code_conformance,content_type_conformance,response_schema_conformance,response_headers_conformance \
    --phases examples,coverage,fuzzing \
    --max-examples 20 \
    --workers 4 \
    --seed "$SEED" \
    --generation-database none \
    --no-color \
    --report junit --report-junit-path "$OUT/$name.xml" \
    || status=1
}

run core "$CORE_URL" "$ADMIN_TOKEN"
run charging "$CHARGING_URL" "$DRIVER_TOKEN"
run integration "$INTEGRATION_URL" "$DRIVER_TOKEN"
run async "$ASYNC_URL" "$DRIVER_TOKEN"

exit "$status"
