package com.ecopedia.async.notification.mail;

import com.ecopedia.async.notification.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * El canal de mail, simulado: escribe en el log el mail que se habría mandado.
 *
 * <p>Es ficticio a propósito, como la pasarela de pagos y la distribuidora: lo que la consigna pide
 * real en esta entrega es el broker, y el broker sí lo es. La línea tiene un formato fijo y
 * reconocible porque es la que se muestra en la demo.
 *
 * <p>El destinatario va por id y no por dirección: Notificaciones no conoce los mails de los
 * usuarios —viven en core, y leerlos exige rol {@code ADMIN}—. Un envío real resolvería la
 * dirección en esta implementación, sin tocar el servicio.
 */
@Component
public class SimulatedEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailSender.class);

    @Override
    public void send(Long recipientId, String subject, String body) {
        log.info("[MAIL SIMULADO] Para: conductor {} | Asunto: {} | {}", recipientId, subject, body);
    }
}
