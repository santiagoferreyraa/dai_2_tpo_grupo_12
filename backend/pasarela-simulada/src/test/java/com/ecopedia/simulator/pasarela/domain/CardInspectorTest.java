package com.ecopedia.simulator.pasarela.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ecopedia.simulator.pasarela.domain.CardInspector.CardBehavior;
import com.ecopedia.simulator.pasarela.domain.CardInspector.InspectedCard;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que la pasarela acepta y rechaza al tokenizar.
 *
 * <p>Son los casos que antes probaba {@code LocalPaymentGatewayTest} dentro de Ecopedia: la regla
 * se mudó con la lógica. El reloj está fijo en enero de 2026 para que los casos de vencimiento no
 * caduquen con el tiempo.
 */
class CardInspectorTest {

    /* Números de prueba estándar. Pasan Luhn y no corresponden a ninguna tarjeta real. */
    private static final String VISA = "4111111111111111";
    private static final String MASTERCARD = "5555555555554444";
    private static final String MASTERCARD_NEW_RANGE = "2223003122003222";
    private static final String AMEX = "378282246310005";
    private static final String DINERS = "30569309025904";

    private final Clock january2026 = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    private final CardInspector inspector = new CardInspector(january2026);

    private InspectedCard inspect(String number) {
        return inspector.inspect(number, 9, 2029);
    }

    private String rejectionCode(Runnable call) {
        return assertThrows(GatewayException.class, call::run).code();
    }

    @Test
    @DisplayName("Devuelve red, primeros seis y últimos cuatro")
    void inspectsAValidCard() {
        InspectedCard card = inspect(VISA);

        assertEquals("visa", card.network());
        assertEquals("411111", card.firstSix());
        assertEquals("1111", card.lastFour());
        assertEquals(CardBehavior.APPROVED, card.behavior());
    }

    @Test
    @DisplayName("Reconoce las redes por el rango, incluidas las que Ecopedia no acepta")
    void detectsTheNetwork() {
        assertEquals("master", inspect(MASTERCARD).network());
        assertEquals("master", inspect(MASTERCARD_NEW_RANGE).network());
        assertEquals("amex", inspect(AMEX).network());
        // La pasarela la acepta: rechazarla es una regla de Ecopedia, y la aplica el Adapter.
        assertEquals("diners", inspect(DINERS).network());
    }

    @Test
    @DisplayName("Es estricta con el formato: separadores los saca el que llama")
    void rejectsSeparators() {
        assertEquals("invalid_number", rejectionCode(() -> inspect("4111 1111 1111 1111")));
        assertEquals("invalid_number", rejectionCode(() -> inspect("4111abcd11111111")));
        assertEquals("invalid_number", rejectionCode(() -> inspect("411111")));
    }

    @Test
    @DisplayName("Rechaza un número que no pasa el dígito verificador")
    void rejectsATypo() {
        assertEquals("incorrect_number", rejectionCode(() -> inspect("4111111111111112")));
    }

    @Test
    @DisplayName("Rechaza una tarjeta vencida el mes pasado y un mes imposible")
    void rejectsBadExpiry() {
        assertEquals("expired_card", rejectionCode(() -> inspector.inspect(VISA, 12, 2025)));
        assertEquals("invalid_expiration_month", rejectionCode(() -> inspector.inspect(VISA, 13, 2029)));
    }

    @Test
    @DisplayName("Acepta una tarjeta que vence este mismo mes")
    void acceptsACardExpiringThisMonth() {
        assertEquals("visa", inspector.inspect(VISA, 1, 2026).network());
    }

    @Test
    @DisplayName("Las tarjetas de prueba de rechazo se tokenizan pero quedan marcadas")
    void marksTheTestCards() {
        assertEquals(
                CardBehavior.INSUFFICIENT_FUNDS,
                inspect(CardInspector.TEST_CARD_INSUFFICIENT_FUNDS).behavior());
        assertEquals(
                CardBehavior.REJECTED, inspect(CardInspector.TEST_CARD_REJECTED).behavior());
    }
}
