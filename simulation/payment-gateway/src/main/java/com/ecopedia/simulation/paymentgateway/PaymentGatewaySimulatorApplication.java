package com.ecopedia.simulation.paymentgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * La pasarela de pagos simulada.
 *
 * <p><b>No es parte de Ecopedia.</b> Es el partner moderno con el que Ecopedia se integra por
 * REST (RNF04): corre como proceso propio, en su puerto, con su base y su contrato. Pagos no la
 * conoce por dentro; le habla por HTTP con una clave de acceso, igual que le hablaría a una
 * pasarela de verdad. Si se cae, Ecopedia se entera como se enteraría de cualquier caída: la
 * llamada no contesta.
 *
 * <p>La forma de la API imita la de Mercado Pago —los recursos {@code card_tokens},
 * {@code payments} y {@code refunds}, los estados {@code approved} y {@code rejected}, la clave
 * de idempotencia en la cabecera—, para que se vea como una integración real. El porqué de no
 * usar el sandbox de Mercado Pago está en ARQUITECTURA §6.1.
 */
@SpringBootApplication
public class PaymentGatewaySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewaySimulatorApplication.class, args);
    }
}
