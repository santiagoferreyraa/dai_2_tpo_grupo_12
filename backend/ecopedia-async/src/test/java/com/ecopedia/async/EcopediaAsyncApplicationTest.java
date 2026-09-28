package com.ecopedia.async;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * El artefacto arranca entero: base, Flyway, seguridad y broker.
 *
 * <p>Parece una prueba trivial y no lo es: hasta ECO-41 este módulo no levantaba nada, y CI no
 * tenía cómo enterarse si una dependencia nueva lo dejaba sin arrancar.
 */
@SpringBootTest
@ActiveProfiles("dev")
class EcopediaAsyncApplicationTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Test
    @DisplayName("En los tests la conexión va al broker embebido, no al de docker-compose")
    void connectsToTheEmbeddedBrokerInTests() throws Exception {
        /*
         * Si esto falla, alguien fijó `spring.artemis.mode` en el application.yml y los tests
         * pasaron a buscar el broker de Docker: en una máquina con Docker andan, y en CI no.
         *
         * Se mira la dirección de la conexión y no si existe el bean del broker embebido,
         * porque ese bean se crea igual en modo `native`: habría broker adentro de la JVM y la
         * conexión saldría igual a buscar el de afuera.
         */
        ConnectionFactory target = connectionFactory instanceof CachingConnectionFactory caching
                ? caching.getTargetConnectionFactory()
                : connectionFactory;

        assertThat(target).isInstanceOf(ActiveMQConnectionFactory.class);
        assertThat(((ActiveMQConnectionFactory) target).toURI().toString()).startsWith("vm:");
    }
}
