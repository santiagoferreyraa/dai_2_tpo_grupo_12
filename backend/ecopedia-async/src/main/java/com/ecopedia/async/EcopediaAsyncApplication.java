package com.ecopedia.async;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Artefacto desplegable {@code ecopedia-async}.
 *
 * <p>Aloja {@code NotificationService}. <b>Su entrada es el broker:</b> los avisos le llegan por la
 * cola {@code notifications.dispatch}, y ningún otro componente lo llama directamente. Por eso el
 * envío de un aviso no puede demorar ni hacer fallar una reserva (RNF05).
 *
 * <p>Desde ECO-41 levanta además un servidor web, en el 8084, pero solo de salida: el conductor
 * consulta ahí los avisos que ya se le guardaron. El navegador no se conecta al broker, así que
 * esa consulta es la única forma de que un aviso llegue a la pantalla. Ver ARQUITECTURA §3.3.
 */
@SpringBootApplication
public class EcopediaAsyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcopediaAsyncApplication.class, args);
    }
}
