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
| `pom.xml` (JaCoCo y perfil `mutation`) | QA-22/23 | Cobertura en cada `mvn verify`; mutación con PIT |
| `qa/ci/coverage-summary.py`, `mutation-summary.py` | QA-22/23 | Los números de dominio y servicio, en el resumen del run |
| `.github/workflows/ci.yml` (jobs `api` y `sonar`) | QA-05/17 | Newman y SonarCloud en cada PR |
| `.github/workflows/qa-nightly.yml` | QA-06/08/23 | gitleaks, Trivy, ZAP, k6 y PIT: a mano, cada noche y en cada push a `main` |

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

## Cobertura (QA-22) y mutación (QA-23)

```bash
mvn -B verify                         # tests + cobertura
python3 qa/ci/coverage-summary.py     # % de líneas y ramas de dominio y servicio
# el detalle, clase por clase: backend/<módulo>/target/site/jacoco/index.html

mvn -B -Pmutation -Dspotless.check.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
python3 qa/ci/mutation-summary.py     # mutation score por módulo
# qué mutante sobrevivió y dónde: backend/<módulo>/target/pit-reports/index.html
```

**Umbrales.** La cobertura ya bloquea: `coverage.lines.minimum` (0.80) y
`coverage.branches.minimum` (0.70), en el `pom.xml` raíz, hacen fallar `mvn verify` si un módulo
baja de ahí. La mutación todavía no: `mutation.threshold` está en 0 hasta la primera corrida de
PIT, y después se sube a 60, que es lo que pide el eje B.

## SonarCloud (QA-17)

El job `sonar` del CI se saltea solo mientras no exista el secreto `SONAR_TOKEN`. Para
activarlo hace falta quien administra el repo (hoy, SantiagoF). Son 5 minutos:

1. Entrar a [sonarcloud.io](https://sonarcloud.io) con la cuenta de GitHub dueña del repo.
2. **+ → Analyze new project**, elegir `dai_2_tpo_grupo_12` y aceptar el plan gratuito (el repo es
   público).
3. En el proyecto: **Administration → Analysis Method** → apagar **Automatic Analysis**. Si queda
   prendido, el análisis del CI falla con "CI analysis while Automatic Analysis is enabled".
4. **My Account → Security** → generar un token.
5. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**, nombre
   `SONAR_TOKEN` y el token como valor.
6. Si SonarCloud le puso otra organización o clave de proyecto que `santiagoferreyraa` y
   `santiagoferreyraa_dai_2_tpo_grupo_12`, cargarlas en la pestaña **Variables** como
   `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`.

En la próxima corrida el job analiza el código, espera el veredicto del Quality Gate y queda en
rojo si no aprueba. La captura del gate (página del proyecto en SonarCloud) va al informe de
ejecución.

## El 403 que los criterios piden como 401

Los `ApiAttackTest` afirman el código de HOY (403) en una sola constante,
`REJECTED_AS_ANONYMOUS`, y la colección hace lo mismo en los casos marcados con DEF-02. Cuando se
resuelva QA-33, se cambia esa constante a 401 en los 4 módulos y los casos de la colección.

## Antes de commitear

`mvn spotless:apply`, y `mvn -B verify` para ver que todo sigue en verde.
