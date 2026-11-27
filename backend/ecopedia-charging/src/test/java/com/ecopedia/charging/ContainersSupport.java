package com.ecopedia.charging;

import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base de las pruebas de integración de charging contra contenedores (QA-24, clases *IT).
 *
 * <p>Sin el perfil `dev`: charging arranca como en producción, contra un PostgreSQL y un Artemis de
 * verdad, con las mismas imágenes que `docker-compose.yml`. Flyway aplica las migraciones reales y
 * el broker arranca con el `broker.properties` del repo, igual que en el compose.
 * `@ServiceConnection` le pasa a Spring las direcciones y credenciales de los contenedores; las
 * demás variables que el perfil de producción exige van con valores de prueba.
 *
 * <p>Los barridos programados quedan apagados: corren cada pocos segundos sobre la misma base y
 * tocarían los datos de una prueba a mitad de camino. El broker embebido de los tests unitarios
 * también: los mensajes tienen que pasar por el del contenedor.
 *
 * <p>Los contenedores son uno para todas las clases que heredan de esta, y se levantan recién
 * cuando Spring los pide, después de que {@link RequiresDocker} decidió que hay Docker.
 */
@SpringBootTest(
        properties = {
            "ECOPEDIA_DB_PASSWORD=la-del-contenedor",
            "ECOPEDIA_BROKER_PASSWORD=la-del-contenedor",
            "ECOPEDIA_JWT_SECRET=secreto-de-las-pruebas-de-integracion-con-contenedores",
            "ECOPEDIA_INTERNAL_TOKEN=clave-de-servicio-de-prueba",
            "ECOPEDIA_CHARGER_ACCESS_TOKEN=clave-del-cargador-de-prueba",
            "ecopedia.scheduling.enabled=false",
            "spring.artemis.embedded.enabled=false"
        })
@ExtendWith(RequiresDocker.class)
public abstract class ContainersSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = Containers.POSTGRES;

    @ServiceConnection
    static final ArtemisContainer ARTEMIS = Containers.ARTEMIS;

    /** Los contenedores, en una clase aparte para que se creen y arranquen una sola vez por JVM. */
    static final class Containers {

        /** El mismo archivo que monta el compose, desde la raíz del repo. */
        private static final Path BROKER_PROPERTIES = Path.of("..", "..", "docker", "artemis", "broker.properties");

        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

        static final ArtemisContainer ARTEMIS = new ArtemisContainer("apache/activemq-artemis:2.42.0")
                .withUser("ecopedia")
                .withPassword("la-del-contenedor")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(
                                BROKER_PROPERTIES.toAbsolutePath().normalize()),
                        "/opt/ecopedia/broker.properties")
                .withCommand("run", "--properties=/opt/ecopedia/broker.properties");

        static {
            POSTGRES.start();
            ARTEMIS.start();
        }

        private Containers() {}
    }
}
