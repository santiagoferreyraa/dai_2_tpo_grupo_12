package com.ecopedia.async;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El broker embebido de los tests, con las mismas reglas de reentrega y DLQ que el de Docker.
 *
 * <p>No es una copia de las reglas: carga el mismo {@code docker/artemis/broker.properties} que
 * usa el contenedor, así que un test que pase acá prueba el archivo que se despliega. Lo único que
 * cambia es la espera entre intentos, que en los tests es cero para no esperar 30 segundos por
 * mensaje.
 *
 * <p>Vive en {@code src/test} y en el paquete raíz, así que el escaneo de componentes la encuentra
 * en todos los {@code @SpringBootTest} del módulo, y en la aplicación no existe: ahí el broker es
 * el de Docker y este customizer no se usaría igual (Spring Boot solo lo aplica al embebido).
 */
@Configuration
public class EmbeddedBrokerSettings {

    /** Relativa al módulo, que es donde Maven corre los tests. */
    public static final Path BROKER_PROPERTIES = Path.of("..", "..", "docker", "artemis", "broker.properties");

    @Bean
    public ArtemisConfigurationCustomizer dockerBrokerSettings() {
        return configuration -> {
            Path file = BROKER_PROPERTIES.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("No está la configuración del broker en " + file);
            }
            try {
                configuration.parseProperties(file.toString());
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo leer " + file, e);
            }
            AddressSettings all = configuration.getAddressSettings().get("#");
            all.setRedeliveryDelay(0L);
        };
    }
}
