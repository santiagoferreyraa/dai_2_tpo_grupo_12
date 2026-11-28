package com.ecopedia.integration.payment.bookings;

import com.ecopedia.integration.payment.domain.BookingsUnavailableException;
import com.ecopedia.integration.payment.domain.DriverBookingsPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link DriverBookingsPort} contra Reservas, por su ruta interna en ecopedia-charging (ECO-62), con la
 * clave de servicio.
 *
 * <p>Cualquier cosa que no sea un 200 —charging caído, lento o la clave rechazada— es "no disponible":
 * la deuda queda marcada sin las reservas anuladas y el barrido lo vuelve a pedir. Un 401 deja ERROR:
 * es la clave mal configurada, y no se arregla sola.
 */
@Component
public class ChargingBookingsClient implements DriverBookingsPort {

    private static final Logger log = LoggerFactory.getLogger(ChargingBookingsClient.class);

    private final RestClient restClient;

    @Autowired
    public ChargingBookingsClient(
            RestClient.Builder builder,
            @Value("${ecopedia.charging.url}") String chargingUrl,
            @Value("${ecopedia.internal.token}") String serviceToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.clone()
                .baseUrl(chargingUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .build();
    }

    /** Para los tests, con un cliente enlazado a {@code MockRestServiceServer}. */
    ChargingBookingsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public int cancelConfirmedBookingsForDebt(Long driverId) {
        try {
            CancellationAnswer answer = restClient
                    .post()
                    .uri("/internal/bookings/debt-cancellations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CancellationBody(driverId))
                    .retrieve()
                    .body(CancellationAnswer.class);
            if (answer == null) {
                throw new BookingsUnavailableException("ecopedia-charging contestó sin cuerpo", null);
            }
            return answer.cancelled();
        } catch (RestClientResponseException response) {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                log.error("ecopedia-charging rechazó la clave de servicio: revisar ECOPEDIA_INTERNAL_TOKEN");
            }
            throw new BookingsUnavailableException(
                    "Reservas contestó " + response.getStatusCode().value(), response);
        } catch (RestClientException noAnswer) {
            throw new BookingsUnavailableException("ecopedia-charging no contestó", noAnswer);
        }
    }

    record CancellationBody(Long driverId) {}

    record CancellationAnswer(int cancelled) {}
}
