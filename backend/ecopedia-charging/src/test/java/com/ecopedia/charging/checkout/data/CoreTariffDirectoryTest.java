package com.ecopedia.charging.checkout.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * La seña que el checkout le pregunta a Tarificación, contra la forma real de la respuesta de core
 * ({@code DepositResponse}: {@code connectorId} y {@code depositAmount}).
 *
 * <p>Existe porque los demás tests simulan el puerto entero, y así no vieron que el adaptador leía un
 * campo que core nunca mandó: la seña daba siempre 0. Que core conteste así lo prueba
 * {@code PricingDepositApiTest} de su lado.
 */
class CoreTariffDirectoryTest {

    private static final String URL = "http://core.test/api/pricing/deposit?connectorId=12";

    private MockRestServiceServer core;
    private CoreTariffDirectory tariffs;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
        core = MockRestServiceServer.bindTo(builder).build();
        tariffs = new CoreTariffDirectory(builder.build());
    }

    @Test
    @DisplayName("Lee la seña del campo que manda core")
    void readsTheDeposit() {
        core.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"connectorId\":12,\"depositAmount\":8000.00}", MediaType.APPLICATION_JSON));

        assertThat(tariffs.calculateDeposit(12L)).isEqualByComparingTo("8000");
        core.verify();
    }

    @Test
    @DisplayName("Un conector sin esquema tarifario (400 de core) no pide seña")
    void noSchemeIsZero() {
        core.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("No se encontró esquema tarifario para el conector: 12"));

        assertThat(tariffs.calculateDeposit(12L)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Core caído, con error o contestando otra cosa: no se sabe la seña")
    void unavailable() {
        core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> tariffs.calculateDeposit(12L)).isInstanceOf(PricingUnavailableException.class);

        core.reset();
        core.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> tariffs.calculateDeposit(12L)).isInstanceOf(PricingUnavailableException.class);

        core.reset();
        core.expect(requestTo(URL)).andRespond(withSuccess("{\"deposit\":8000}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tariffs.calculateDeposit(12L)).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    @DisplayName("Calcula el costo real contra GET /api/pricing/real-cost y propaga error si core cae")
    void calculatesRealCost() {
        Instant startedAt = Instant.parse("2026-11-10T18:00:00Z");
        String realCostUrl = "http://core.test/api/pricing/real-cost?connectorId=12&kwhConsumed=15.50"
                + "&deliveredPowerKw=150.00&start=2026-11-10T18:00:00Z&excessMinutes=5";

        core.expect(requestTo(realCostUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"totalCost\":3500.00}", MediaType.APPLICATION_JSON));

        BigDecimal total =
                tariffs.calculateRealCost(12L, new BigDecimal("15.50"), new BigDecimal("150.00"), startedAt, 5L);
        assertThat(total).isEqualByComparingTo("3500.00");
        core.verify();

        core.reset();
        core.expect(requestTo(realCostUrl)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> tariffs.calculateRealCost(
                        12L, new BigDecimal("15.50"), new BigDecimal("150.00"), startedAt, 5L))
                .isInstanceOf(PricingUnavailableException.class);
    }
}
