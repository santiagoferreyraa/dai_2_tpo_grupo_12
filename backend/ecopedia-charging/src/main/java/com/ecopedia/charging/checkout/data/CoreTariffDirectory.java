package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador de {@link TariffDirectory} que consulta el componente de Tarificación en {@code ecopedia-core}.
 *
 * <p><b>Pregunta por {@code /api/pricing/deposit}, no por la estimación</b> (ECO-53). Antes leía la seña
 * de {@code /estimate} con un consumo fijo de 10 kWh, y eso tenía dos defectos que se encontraron
 * probando en vivo: el campo se buscaba como {@code deposit} y core lo manda como {@code depositAmount},
 * así que la seña daba siempre 0; y la estimación rechaza con 400 un consumo que no supera la seña.
 * Los tests no lo veían porque simulan este puerto: por eso ahora lo prueba {@code CoreTariffDirectoryTest}
 * contra la forma real de la respuesta.
 *
 * <p><b>Dos respuestas que no son una seña, y no son lo mismo:</b>
 *
 * <ul>
 *   <li>Un conector <b>sin esquema tarifario</b> no pide seña: el operador no le fijó tarifa, y eso es
 *       una respuesta del negocio. Core la contesta con un 4xx —un 400, porque su controlador traduce el
 *       "no se encontró esquema" como dato inválido—, y acá vale 0.
 *   <li>Core <b>caído o lento</b> no es "seña 0": no se sabe cuánto es. Hasta ECO-53 se confirmaba igual
 *       con 0; con la seña cobrada de verdad, eso regalaba reservas sin seña con solo tener core
 *       apagado. Ahora es {@link PricingUnavailableException}.
 * </ul>
 *
 * <p>Usa el cliente de las lecturas públicas de core ({@code coreTerminalHttpClient}: 2 s para
 * conectar, 3 s para responder), el mismo que consulta los conectores.
 */
@Component
public class CoreTariffDirectory implements TariffDirectory {

    private static final Logger log = LoggerFactory.getLogger(CoreTariffDirectory.class);

    private final RestClient restClient;

    public CoreTariffDirectory(@Qualifier("coreTerminalHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BigDecimal calculateDeposit(Long connectorId) {
        try {
            DepositAnswer answer = restClient
                    .get()
                    .uri("/api/pricing/deposit?connectorId={id}", connectorId)
                    .retrieve()
                    .body(DepositAnswer.class);
            if (answer == null || answer.depositAmount() == null) {
                // Un 200 sin la seña es core contestando otra cosa: no se sabe cuánto es.
                throw new PricingUnavailableException(new IllegalStateException("Respuesta sin depositAmount"));
            }
            return answer.depositAmount();
        } catch (HttpClientErrorException noScheme) {
            log.info("El conector {} no tiene esquema tarifario: la reserva va sin seña", connectorId);
            return BigDecimal.ZERO;
        } catch (RestClientException noAnswer) {
            log.warn("No se pudo consultar la seña a core para el conector {}: {}", connectorId, noAnswer.getMessage());
            throw new PricingUnavailableException(noAnswer);
        }
    }

    record DepositAnswer(Long connectorId, BigDecimal depositAmount) {}
}
