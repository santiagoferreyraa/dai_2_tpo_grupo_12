# Una sola imagen para los siete procesos Java: los cuatro módulos de Ecopedia y los tres
# simuladores. La construye `docker compose up` (ver docker-compose.yml) y cada servicio elige
# qué JAR arranca con su `command`.
#
# Por qué una imagen y no siete: el POM padre compila los siete módulos de una pasada, y siete
# imágenes serían siete compilaciones del mismo árbol. Así Maven corre una vez y el resto es
# copiar archivos.

# --- Compilación -------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml .
COPY backend backend
COPY simulation simulation

# Sin tests: los corre CI en cada PR. Esto arma lo que se despliega, no lo valida.
# La caché de ~/.m2 sobrevive entre builds, así que la segunda vez no se baja internet entero.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests \
 && mkdir /jars \
 && cp backend/ecopedia-core/target/ecopedia-core-*.jar /jars/ecopedia-core.jar \
 && cp backend/ecopedia-charging/target/ecopedia-charging-*.jar /jars/ecopedia-charging.jar \
 && cp backend/ecopedia-integration/target/ecopedia-integration-*.jar /jars/ecopedia-integration.jar \
 && cp backend/ecopedia-async/target/ecopedia-async-*.jar /jars/ecopedia-async.jar \
 && cp simulation/payment-gateway/target/payment-gateway-simulator-*.jar /jars/payment-gateway.jar \
 && cp simulation/power-grid/target/power-grid-simulator-*.jar /jars/power-grid.jar \
 && cp simulation/charger/target/charger-simulator-*.jar /jars/charger.jar

# --- Ejecución ---------------------------------------------------------------------------------
# La JDK y no la JRE a propósito: la imagen JRE de Temurin viene recortada y no trae el módulo
# jdk.random, y el simulador de cargadores lo usa (RandomGenerator.getDefault() pide el algoritmo
# L32X64MixRandom). Con la JRE, ese contenedor se cae al arrancar y se reinicia en bucle.
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Un usuario sin privilegios: si alguien llegara a ejecutar algo adentro, no es root.
# /app/data es donde la pasarela simulada guarda su H2; el volumen de compose hereda este dueño.
RUN useradd --system --create-home ecopedia \
 && mkdir /app/data \
 && chown ecopedia /app/data

COPY --from=build /jars/ /app/
USER ecopedia
