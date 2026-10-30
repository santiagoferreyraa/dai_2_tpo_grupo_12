# QA · Entrega 4 (prueba del 16/11)

Todo lo que el QA necesita para correr las pruebas de los criterios de la Clase 15. El informe
(plan, matriz, casos, ejecución, defectos, seguridad, IA y lecciones) vive en el doc
`Clase15_Grupo12_Ecopedia_Testing`, que se exporta al PDF de Teams.

| Carpeta / archivo | Ticket | Qué es |
| --- | --- | --- |
| `backend/*/src/test/.../security/ApiAttackTest.java` | QA-07 | JWT hostil, mass assignment e inyección, en los 4 módulos |
| `backend/*/src/test/.../security/HostileTokens.java` | QA-07 | Tokens vencidos, alterados, `alg=none` y de otro emisor |
| `backend/ecopedia-core/.../admin/AdminErrorResponseTest.java` | QA-04 | Regresión de DEF-05, deshabilitada hasta la corrección |
| `qa/postman/` | QA-05 | Colección (90 casos) y su generador |
| `qa/k6/load-critical.js` | QA-06 | 50 usuarios, 5 minutos, 3 endpoints críticos |
| `qa/zap/zap-plan.yaml` | QA-08 | Escaneo pasivo + activo autenticado |
| `qa/ci/start-stack.sh`, `stop-stack.sh` | QA-05/06/08 | Levanta los 4 backends y los 3 simuladores, con usuarios ADMIN y CPO de prueba |
| `qa/jira/*.csv` | QA-03/04 | Defectos y casos para importar en Jira |
| `.github/workflows/ci.yml` (job `api`) | QA-05 | Newman en cada PR |
| `.github/workflows/qa-nightly.yml` | QA-06/08 | gitleaks, Trivy, ZAP y k6: a mano, cada noche y en cada push a `main` |

## Correr todo en tu máquina

```bash
mvn -B -Dmaven.test.skip=true package          # los JAR
pip install bcrypt
export QA_ADMIN_PASSWORD=... QA_CPO_PASSWORD=...  # las que quieras, para usarlas después
docker compose up -d activemq                  # el broker (necesita el .env)
set -a; source .env; set +a                    # la contraseña del broker para los backends
bash qa/ci/start-stack.sh

# Colección de API
npx newman run qa/postman/ecopedia.postman_collection.json \
  --env-var coreUrl=http://localhost:8081 --env-var chargingUrl=http://localhost:8082 \
  --env-var integrationUrl=http://localhost:8083 --env-var asyncUrl=http://localhost:8084 \
  --env-var adminEmail=admin.qa@ecopedia.test --env-var adminPassword="$QA_ADMIN_PASSWORD" \
  --env-var cpoEmail=cpo.qa@ecopedia.test --env-var cpoPassword="$QA_CPO_PASSWORD"

# Carga (el número que va al informe se mide en la máquina de la demo)
k6 run qa/k6/load-critical.js

bash qa/ci/stop-stack.sh
```

Para cambiar la colección se edita `qa/postman/build_collection.py` y se regenera con
`python3 qa/postman/build_collection.py`. El JSON no se toca a mano.

## El 403 que los criterios piden como 401

Los `ApiAttackTest` afirman el código de HOY (403) en una sola constante,
`REJECTED_AS_ANONYMOUS`, y la colección hace lo mismo en los casos marcados con DEF-02. Cuando se
resuelva QA-33, se cambia esa constante a 401 en los 4 módulos y los casos de la colección.

## Antes de commitear

`mvn spotless:apply`: los tests nuevos se escribieron sin poder correr el formateador.
