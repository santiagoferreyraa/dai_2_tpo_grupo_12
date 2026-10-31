package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.CardDeclinedException;
import com.ecopedia.charging.checkout.domain.DepositCharge;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.DepositRefundRefusedException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyReusedException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link DepositGateway} contra Pagos, por sus rutas internas en ecopedia-integration.
 *
 * <p>Usa el mismo cliente que RedEléctrica ({@code integrationInternalHttpClient}): la clave de
 * servicio y una lectura de 8 s, que cubre los 5 s que Pagos espera a la pasarela más el viaje. Con
 * menos, este lado se rendiría antes de que Pagos contestara "no disponible", y un cobro que sí se
 * hizo quedaría sin respuesta.
 *
 * <p>Un 422 con código es una respuesta del negocio; cualquier otra cosa que no sea un cobro —503,
 * la clave rechazada, integration caído— es "no disponible". Un 401 deja ERROR: es la clave mal
 * configurada, y no se arregla sola.
 */
@Component
public class IntegrationDepositGateway implements DepositGateway {

    private static final Logger log = LoggerFactory.getLogger(IntegrationDepositGateway.class);

    private final RestClient restClient;

    public IntegrationDepositGateway(@Qualifier("integrationInternalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public DepositCharge charge(
            Long driverId, Long paymentMethodId, BigDecimal amount, String reference, String idempotencyKey) {
        try {
            ChargeAnswer answer = restClient
                    .post()
                    .uri("/internal/payments/deposits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChargeBody(driverId, paymentMethodId, amount, reference, idempotencyKey))
                    .retrieve()
                    .body(ChargeAnswer.class);
            return new DepositCharge(answer.chargeId(), answer.cardLastFour());
        } catch (RestClientResponseException response) {
            if (response.getStatusCode().value() == 422) {
                String code = codeOf(response);
                switch (code) {
                    case "CARD_DECLINED" -> throw new CardDeclinedException();
                    case "PAYMENT_METHOD_INVALID" -> throw new PaymentMethodInvalidException();
                    case "IDEMPOTENCY_KEY_REUSED" -> throw new IdempotencyKeyReusedException();
                    default -> {
                        // Un código que este lado no conoce: no se sabe qué pasó, se trata como sin respuesta.
                    }
                }
            }
            throw unavailable("cobrar la seña", response);
        } catch (RestClientException noAnswer) {
            throw new DepositPaymentUnavailableException(
                    "ecopedia-integration no contestó al cobrar la seña", noAnswer);
        }
    }

    @Override
    public void refund(Long chargeId) {
        try {
            restClient
                    .post()
                    .uri("/internal/payments/deposits/{id}/refund", chargeId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 404 || status == 409) {
                throw new DepositRefundRefusedException(codeOf(response));
            }
            throw unavailable("devolver la seña", response);
        } catch (RestClientException noAnswer) {
            throw new DepositPaymentUnavailableException(
                    "ecopedia-integration no contestó al devolver la seña", noAnswer);
        }
    }

    private static DepositPaymentUnavailableException unavailable(
            String operation, RestClientResponseException response) {
        int status = response.getStatusCode().value();
        if (status == 401) {
            log.error("ecopedia-integration rechazó la clave de servicio: revisar ECOPEDIA_INTERNAL_TOKEN");
        }
        return new DepositPaymentUnavailableException("Pagos contestó " + status + " al " + operation, response);
    }

    private static String codeOf(RestClientResponseException response) {
        try {
            ErrorBody error = response.getResponseBodyAs(ErrorBody.class);
            if (error != null && error.code() != null) return error.code();
        } catch (RuntimeException unreadable) {
            // Un cuerpo que no es el esperado se trata como un rechazo sin código.
        }
        return "UNKNOWN";
    }

    record ChargeBody(
            Long driverId, Long paymentMethodId, BigDecimal amount, String reference, String idempotencyKey) {}

    record ChargeAnswer(Long chargeId, String cardLastFour) {}

    record ErrorBody(String code) {}
}
