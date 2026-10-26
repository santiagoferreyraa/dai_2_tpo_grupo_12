package com.ecopedia.simulator.pasarela;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Pasarela de pagos simulada (ECO-50).
 *
 * <p><b>Es un sistema externo, no un módulo de Ecopedia.</b> Representa al partner de pagos con el
 * que la plataforma se integra por REST/JSON (RNF04), y por eso corre como proceso aparte, en su
 * propio puerto (8091), con su propia API y su propio vocabulario. Ecopedia no comparte con ella ni
 * una clase: si mañana se la reemplazara por una pasarela real, este módulo se borra y del lado de
 * Ecopedia cambia solo el Adapter.
 *
 * <p><b>Lo que expone</b>, todo bajo {@code /v1} y con una clave secreta en el header
 * {@code Authorization}:
 *
 * <ul>
 *   <li>{@code POST /v1/card_tokens}: cambia los datos de una tarjeta por un token.
 *   <li>{@code POST /v1/card_tokens/{id}/verify}: dice si la tarjeta es cobrable, sin mover plata.
 *   <li>{@code POST /v1/payments}: cobra un importe, en centavos, contra un token.
 *   <li>{@code POST /v1/payments/{id}/refunds}: reintegra todo o parte de un pago.
 * </ul>
 *
 * <p><b>Guarda todo en memoria.</b> Una pasarela real tiene su base, pero acá lo que importa es el
 * contrato que ve Ecopedia, no la persistencia del partner. Reiniciar el proceso borra los tokens:
 * las tarjetas ya registradas en Ecopedia quedan con un token que esta pasarela ya no conoce, igual
 * que pasaría si el partner las diera de baja.
 */
@SpringBootApplication
public class PasarelaSimuladaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PasarelaSimuladaApplication.class, args);
    }

    /** Inyectado para que el vencimiento de las tarjetas se pueda probar con una fecha fija. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
