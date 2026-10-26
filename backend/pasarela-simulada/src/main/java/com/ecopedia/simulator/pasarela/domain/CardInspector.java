package com.ecopedia.simulator.pasarela.domain;

import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/**
 * Lo que la pasarela verifica de una tarjeta antes de darle un token.
 *
 * <p>Es la lógica que antes vivía en {@code LocalPaymentGateway}, dentro de Ecopedia. Se mudó acá
 * porque validar una tarjeta es trabajo del que la cobra: Ecopedia no tiene la tabla de rangos de
 * cada red ni sabe si un número existe, y no tiene por qué saberlo.
 *
 * <p><b>Reconoce más redes de las que Ecopedia acepta</b> (Diners y Discover, además de VISA,
 * Mastercard y AMEX), como cualquier pasarela de verdad. Qué marcas toma la plataforma es una
 * regla de negocio de Ecopedia y se resuelve del otro lado, en el Adapter.
 */
@Component
public class CardInspector {

    private static final int MINIMUM_DIGITS = 13;
    private static final int MAXIMUM_DIGITS = 19;

    /*
     * Tarjetas de prueba con comportamiento fijo, como las que publican las pasarelas reales en su
     * documentación de sandbox. Se tokenizan bien, pero cualquier cobro o verificación posterior se
     * rechaza. Sirven para probar a mano el camino del rechazo sin tocar código.
     */
    static final String TEST_CARD_INSUFFICIENT_FUNDS = "4000000000009995";
    static final String TEST_CARD_REJECTED = "4000000000000002";

    private final Clock clock;

    public CardInspector(Clock clock) {
        this.clock = clock;
    }

    /**
     * Valida número y vencimiento, y deduce la red.
     *
     * @throws GatewayException con tipo {@code card_error} si la tarjeta no sirve.
     */
    public InspectedCard inspect(String cardNumber, int expirationMonth, int expirationYear) {
        String digits = cardNumber == null ? "" : cardNumber;

        // La API es estricta: solo dígitos. Sacar espacios y guiones es trabajo del que llama.
        if (!digits.matches("\\d+") || digits.length() < MINIMUM_DIGITS || digits.length() > MAXIMUM_DIGITS) {
            throw GatewayException.cardError(
                    "invalid_number", "card_number must contain between 13 and 19 digits and nothing else");
        }
        if (!passesLuhn(digits)) {
            throw GatewayException.cardError("incorrect_number", "card_number failed the check digit");
        }

        requireValidExpiry(expirationMonth, expirationYear);

        return new InspectedCard(
                networkOf(digits),
                digits.substring(0, 6),
                digits.substring(digits.length() - 4),
                expirationMonth,
                expirationYear,
                behaviorOf(digits));
    }

    /** Vencimiento válido y no pasado. Se vuelve a mirar al cobrar: un token no rejuvenece la tarjeta. */
    public void requireValidExpiry(int expirationMonth, int expirationYear) {
        if (expirationMonth < 1 || expirationMonth > 12) {
            throw GatewayException.cardError("invalid_expiration_month", "expiration_month must be between 1 and 12");
        }
        // Vence el último día del mes impreso: la comparación es entre meses, no entre días.
        if (YearMonth.of(expirationYear, expirationMonth).isBefore(YearMonth.now(clock))) {
            throw GatewayException.cardError("expired_card", "The card has expired");
        }
    }

    /** Algoritmo de Luhn: el dígito verificador que llevan todas las tarjetas. */
    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubling = false;

        for (int position = digits.length() - 1; position >= 0; position--) {
            int digit = digits.charAt(position) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubling = !doubling;
        }

        return sum % 10 == 0;
    }

    /** La red por el rango del número. Lo que no se reconoce se rechaza: no se sabría a quién cobrarle. */
    private String networkOf(String digits) {
        int firstTwo = Integer.parseInt(digits.substring(0, 2));
        int firstThree = Integer.parseInt(digits.substring(0, 3));
        int firstFour = Integer.parseInt(digits.substring(0, 4));

        if (digits.startsWith("4")) return "visa";
        if (firstTwo >= 51 && firstTwo <= 55) return "master";
        if (firstFour >= 2221 && firstFour <= 2720) return "master";
        if (firstTwo == 34 || firstTwo == 37) return "amex";
        if ((firstThree >= 300 && firstThree <= 305) || firstTwo == 36 || firstTwo == 38) return "diners";
        if (firstFour == 6011 || firstTwo == 65) return "discover";

        throw GatewayException.cardError("unsupported_network", "The card network is not supported");
    }

    private CardBehavior behaviorOf(String digits) {
        if (digits.equals(TEST_CARD_INSUFFICIENT_FUNDS)) return CardBehavior.INSUFFICIENT_FUNDS;
        if (digits.equals(TEST_CARD_REJECTED)) return CardBehavior.REJECTED;
        return CardBehavior.APPROVED;
    }

    /** Cómo responde la tarjeta cuando se le quiere cobrar. */
    public enum CardBehavior {
        APPROVED,
        INSUFFICIENT_FUNDS,
        REJECTED
    }

    /**
     * Lo que queda de la tarjeta después de inspeccionarla. El número completo no está: ni siquiera
     * el simulador lo guarda.
     */
    public record InspectedCard(
            String network,
            String firstSix,
            String lastFour,
            int expirationMonth,
            int expirationYear,
            CardBehavior behavior) {}
}
