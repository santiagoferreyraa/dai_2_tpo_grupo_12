package com.ecopedia.integration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración de integration contra contenedores (QA-24, clases *IT).
 *
 * <p>Sin el perfil `dev`: integration arranca como en producción, contra un PostgreSQL de verdad (la misma
 * imagen que `docker-compose.yml`), y Flyway le aplica las migraciones reales. `@ServiceConnection`
 * le pasa a Spring la dirección y las credenciales del contenedor; las demás variables que el
 * perfil de producción exige van con valores de prueba. Los barridos programados quedan apagados:
 * corren sobre la misma base y reclamarían las deudas de una prueba a mitad de camino.
 *
 * <p>El contenedor es uno para todas las clases que heredan de esta, y se levanta recién cuando
 * Spring lo pide, después de que {@link RequiresDocker} decidió que hay Docker.
 */
@SpringBootTest(
        properties = {
            "ECOPEDIA_DB_PASSWORD=la-del-contenedor",
            "ECOPEDIA_JWT_SECRET=secreto-de-las-pruebas-de-integracion-con-contenedores",
            "ECOPEDIA_INTERNAL_TOKEN=clave-de-servicio-de-prueba",
            "ECOPEDIA_BROKER_PASSWORD=sin-broker-en-estas-pruebas",
            "ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN=clave-de-la-pasarela-de-prueba",
            "ECOPEDIA_POWER_GRID_PASSWORD=clave-de-la-distribuidora-de-prueba",
            "ecopedia.scheduling.enabled=false"
        })
@ExtendWith(RequiresDocker.class)
public abstract class ContainersSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = Containers.POSTGRES;

    /** Los contenedores, en una clase aparte para que se creen y arranquen una sola vez por JVM. */
    static final class Containers {

        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

        static {
            POSTGRES.start();
        }

        private Containers() {}
    }
}
