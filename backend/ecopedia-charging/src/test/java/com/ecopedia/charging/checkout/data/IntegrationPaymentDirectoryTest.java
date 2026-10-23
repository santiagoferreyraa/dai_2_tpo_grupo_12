package com.ecopedia.charging.checkout.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * El adaptador hacia Pagos cuando Pagos no está.
 *
 * <p>Sin Spring y sin servidor de mentira: se le da una dirección donde no escucha nadie, que es exactamente
 * lo que pasa con el 8083 apagado.
 */
class IntegrationPaymentDirectoryTest {

    @Test
    @DisplayName("Con Pagos apagado no asume que hay tarjeta: avisa que no pudo verificar")
    void paymentsDownIsNotAPass() throws IOException {
        IntegrationPaymentDirectory directory =
                new IntegrationPaymentDirectory(RestClient.builder(), "http://localhost:" + closedPort());

        assertThatThrownBy(() -> directory.hasUsablePaymentMethod(7L, "token"))
                .isInstanceOf(PaymentMethodsUnavailableException.class);
    }

    @Test
    @DisplayName("Sin token no pregunta: no hay con qué identificarse ante Pagos")
    void noTokenMeansNoCard() {
        IntegrationPaymentDirectory directory =
                new IntegrationPaymentDirectory(RestClient.builder(), "http://localhost:1");

        assertThat(directory.hasUsablePaymentMethod(7L, null)).isFalse();
    }

    /** Un puerto que estuvo libre hace un instante: se abre, se anota y se cierra. */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
