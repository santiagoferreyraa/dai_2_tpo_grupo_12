package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ConsumptionPaymentUnavailableException;
import com.ecopedia.charging.session.domain.ConsumptionPayments;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ConsumptionPayments} contra Pagos: {@code POST /internal/payments/consumptions}, con la clave
 * de servicio (ECO-44). Pagos decide si cobró o si lo anotó como deuda; acá solo se traduce.
 */
@Component
public class IntegrationConsumptionPayments implements ConsumptionPayments {

    private final RestClient restClient;

    public IntegrationConsumptionPayments(@Qualifier("integrationInternalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SessionPaymentStatus charge(Long driverId, BigDecimal amount, String reference) {
        try {
            ConsumptionAnswer answer = restClient
                    .post()
                    .uri("/internal/payments/consumptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ConsumptionBody(driverId, amount, reference))
                    .retrieve()
                    .body(ConsumptionAnswer.class);
            if (answer == null || answer.status() == null) {
                throw new ConsumptionPaymentUnavailableException(new IllegalStateException("Respuesta sin status"));
            }
            return switch (answer.status()) {
                case "CHARGED" -> SessionPaymentStatus.CHARGED;
                case "DEBT_REGISTERED" -> SessionPaymentStatus.DEBT_REGISTERED;
                default ->
                    throw new ConsumptionPaymentUnavailableException(
                            new IllegalStateException("Status desconocido: " + answer.status()));
            };
        } catch (RestClientException noAnswer) {
            throw new ConsumptionPaymentUnavailableException(noAnswer);
        }
    }

    record ConsumptionBody(Long driverId, BigDecimal amount, String reference) {}

    record ConsumptionAnswer(String status) {}
}
