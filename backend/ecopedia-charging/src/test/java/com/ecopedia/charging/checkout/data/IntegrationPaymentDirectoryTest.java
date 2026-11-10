package com.ecopedia.charging.checkout.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pruebas unitarias para el adaptador hacia Pagos en ecopedia-integration (ECO-54).
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

    @Test
    @DisplayName("Validación para carga exitosa: no lanza excepción ante 200 OK")
    void validateForCharging_success() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("http://integration/internal/payments/validations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"valid\":true,\"reason\":\"Tarjeta ok\",\"code\":\"NONE\"}", MediaType.APPLICATION_JSON));

        IntegrationPaymentDirectory directory = new IntegrationPaymentDirectory(client, client);

        assertThatCode(() -> directory.validatePaymentMethodForCharging(10L, 5L))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("Validación para carga: conductor con deuda lanza PaymentMethodInvalidException")
    void validateForCharging_driverWithDebt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("http://integration/internal/payments/validations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"DRIVER_HAS_DEBT\",\"detail\":\"Posee deudas\"}"));

        IntegrationPaymentDirectory directory = new IntegrationPaymentDirectory(client, client);

        assertThatThrownBy(() -> directory.validatePaymentMethodForCharging(10L, null))
                .isInstanceOf(PaymentMethodInvalidException.class)
                .hasMessageContaining("deuda");
        server.verify();
    }

    @Test
    @DisplayName("Validación para carga: tarjeta declinada lanza PaymentMethodInvalidException")
    void validateForCharging_cardDeclined() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("http://integration/internal/payments/validations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"CARD_DECLINED\",\"detail\":\"Rechazada\"}"));

        IntegrationPaymentDirectory directory = new IntegrationPaymentDirectory(client, client);

        assertThatThrownBy(() -> directory.validatePaymentMethodForCharging(10L, 2L))
                .isInstanceOf(PaymentMethodInvalidException.class)
                .hasMessageContaining("rechazada");
        server.verify();
    }

    @Test
    @DisplayName("Validación para carga: pasarela caída o 503 lanza PaymentMethodsUnavailableException")
    void validateForCharging_gatewayUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("http://integration/internal/payments/validations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        IntegrationPaymentDirectory directory = new IntegrationPaymentDirectory(client, client);

        assertThatThrownBy(() -> directory.validatePaymentMethodForCharging(10L, null))
                .isInstanceOf(PaymentMethodsUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Cobro de consumo: devuelve CHARGED o DEBT_RECORDED según la respuesta y falla si integration cae")
    void chargeConsumption_mapsStatusAndHandlesUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://integration");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        IntegrationPaymentDirectory directory = new IntegrationPaymentDirectory(client, client);

        server.expect(requestTo("http://integration/internal/payments/consumption-charges"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"CHARGED\"}", MediaType.APPLICATION_JSON));
        assertThat(directory.chargeConsumption(10L, "sess-1", new BigDecimal("1200.00")))
                .isEqualTo(ConsumptionPaymentStatus.CHARGED);

        server.reset();
        server.expect(requestTo("http://integration/internal/payments/consumption-charges"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"DEBT_RECORDED\"}", MediaType.APPLICATION_JSON));
        assertThat(directory.chargeConsumption(10L, "sess-2", new BigDecimal("800.00")))
                .isEqualTo(ConsumptionPaymentStatus.DEBT_RECORDED);

        server.reset();
        server.expect(requestTo("http://integration/internal/payments/consumption-charges"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> directory.chargeConsumption(10L, "sess-3", new BigDecimal("500.00")))
                .isInstanceOf(PaymentMethodsUnavailableException.class);
    }

    /** Un puerto que estuvo libre hace un instante: se abre, se anota y se cierra. */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
