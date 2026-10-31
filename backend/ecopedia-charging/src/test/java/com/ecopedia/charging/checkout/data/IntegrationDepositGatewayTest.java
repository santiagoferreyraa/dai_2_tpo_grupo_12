package com.ecopedia.charging.checkout.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.charging.checkout.domain.CardDeclinedException;
import com.ecopedia.charging.checkout.domain.DepositCharge;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.DepositRefundRefusedException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyReusedException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador de la seña sin integration: {@link MockRestServiceServer} contesta lo que contestaría
 * su ruta interna. Que integration conteste así lo prueba {@code InternalDepositApiTest} de su lado.
 */
class IntegrationDepositGatewayTest {

    private static final String CHARGES = "http://integration.test/internal/payments/deposits";

    private MockRestServiceServer integration;
    private IntegrationDepositGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://integration.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer clave-de-servicio");
        integration = MockRestServiceServer.bindTo(builder).build();
        gateway = new IntegrationDepositGateway(builder.build());
    }

    private void answers422(String code) {
        integration
                .expect(requestTo(CHARGES))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"" + code + "\"}"));
    }

    private void charge() {
        gateway.charge(5L, 3L, new BigDecimal("8000"), "hold-x", "deposit-5-key");
    }

    @Test
    @DisplayName("Cobra con la clave de servicio, manda el pedido entero y lee el cobro")
    void charges() {
        integration
                .expect(requestTo(CHARGES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer clave-de-servicio"))
                .andExpect(content()
                        .json("{\"driverId\":5,\"paymentMethodId\":3,\"amount\":8000,"
                                + "\"reference\":\"hold-x\",\"idempotencyKey\":\"deposit-5-key\"}"))
                .andRespond(withSuccess(
                        "{\"chargeId\":9,\"status\":\"APPROVED\",\"amount\":8000,\"cardBrand\":\"VISA\",\"cardLastFour\":\"4242\"}",
                        MediaType.APPLICATION_JSON));

        DepositCharge charge = gateway.charge(5L, 3L, new BigDecimal("8000"), "hold-x", "deposit-5-key");

        assertThat(charge).isEqualTo(new DepositCharge(9L, "4242"));
        integration.verify();
    }

    @Test
    @DisplayName("Cada 422 con código es su excepción del negocio")
    void businessRejections() {
        answers422("CARD_DECLINED");
        assertThatThrownBy(this::charge).isInstanceOf(CardDeclinedException.class);

        integration.reset();
        answers422("PAYMENT_METHOD_INVALID");
        assertThatThrownBy(this::charge).isInstanceOf(PaymentMethodInvalidException.class);

        integration.reset();
        answers422("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(this::charge).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("Un 503, un 401, un 422 sin código conocido o sin conexión: no disponible")
    void unavailable() {
        integration.expect(requestTo(CHARGES)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(this::charge).isInstanceOf(DepositPaymentUnavailableException.class);

        integration.reset();
        integration.expect(requestTo(CHARGES)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(this::charge).isInstanceOf(DepositPaymentUnavailableException.class);

        integration.reset();
        answers422("ALGO_NUEVO");
        assertThatThrownBy(this::charge).isInstanceOf(DepositPaymentUnavailableException.class);

        integration.reset();
        integration.expect(requestTo(CHARGES)).andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(this::charge).isInstanceOf(DepositPaymentUnavailableException.class);
    }

    @Test
    @DisplayName("Devolver: 200 sale; 409 o 404 es un rechazo que no se arregla reintentando; 503, reintentar")
    void refunds() {
        String refund = CHARGES + "/9/refund";
        integration
                .expect(requestTo(refund))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"chargeId\":9,\"status\":\"REFUNDED\"}", MediaType.APPLICATION_JSON));
        gateway.refund(9L);
        integration.verify();

        integration.reset();
        integration
                .expect(requestTo(refund))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"DEPOSIT_NOT_REFUNDABLE\"}"));
        assertThatThrownBy(() -> gateway.refund(9L)).isInstanceOf(DepositRefundRefusedException.class);

        integration.reset();
        integration.expect(requestTo(refund)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> gateway.refund(9L)).isInstanceOf(DepositPaymentUnavailableException.class);
    }
}
