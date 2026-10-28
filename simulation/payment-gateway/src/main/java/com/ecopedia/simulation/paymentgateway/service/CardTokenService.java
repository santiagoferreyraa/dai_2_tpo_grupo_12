package com.ecopedia.simulation.paymentgateway.service;

import com.ecopedia.simulation.paymentgateway.data.CardTokenRepository;
import com.ecopedia.simulation.paymentgateway.domain.CardBehavior;
import com.ecopedia.simulation.paymentgateway.domain.CardToken;
import com.ecopedia.simulation.paymentgateway.domain.GatewayException;
import com.ecopedia.simulation.paymentgateway.domain.PaymentStatus;
import com.ecopedia.simulation.paymentgateway.domain.TestCardCatalog;
import com.ecopedia.simulation.paymentgateway.domain.TestCardCatalog.TestCard;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokeniza tarjetas y las valida sin cobrarles.
 *
 * <p>Las validaciones de forma (largo, dígito verificador, vencimiento) son las que antes hacía
 * {@code LocalPaymentGateway} adentro de Ecopedia. Se mudaron acá porque es la pasarela la que
 * tiene que decir si una tarjeta sirve: Pagos solo traduce la respuesta.
 */
@Service
@Transactional
public class CardTokenService {

    /* Los límites que abarcan a las tres marcas: AMEX tiene 15 dígitos y VISA hasta 19. */
    private static final int MINIMUM_DIGITS = 13;
    private static final int MAXIMUM_DIGITS = 19;

    private final CardTokenRepository cardTokens;
    private final Clock clock;
    private final Duration slowCardDelay;

    public CardTokenService(
            CardTokenRepository cardTokens,
            Clock clock,
            @Value("${simulation.payment-gateway.slow-card-delay:8s}") Duration slowCardDelay) {
        this.cardTokens = cardTokens;
        this.clock = clock;
        this.slowCardDelay = slowCardDelay;
    }

    /**
     * Cambia el número por un token.
     *
     * <p>El orden de los controles va de lo que más se equivoca la gente —un dígito de menos, uno
     * mal copiado— a lo más específico. El catálogo de prueba va último: así una tarjeta mal
     * tipeada recibe "revisá el número" y no "no es de prueba".
     */
    public CardToken tokenize(CardInput card) {
        String digits = digitsOf(card.number());

        if (digits.length() < MINIMUM_DIGITS || digits.length() > MAXIMUM_DIGITS) {
            throw GatewayException.badRequest("invalid_card_number", "Card number has an invalid length");
        }
        if (!passesLuhn(digits)) {
            throw GatewayException.badRequest("invalid_check_digit", "Card number check digit is invalid");
        }
        if (card.expiryMonth() < 1 || card.expiryMonth() > 12) {
            throw GatewayException.badRequest("invalid_expiration_month", "Expiration month must be 1 to 12");
        }
        if (YearMonth.of(card.expiryYear(), card.expiryMonth()).isBefore(YearMonth.now(clock))) {
            throw GatewayException.badRequest("card_expired", "Card is expired");
        }
        if (card.holderName() == null || card.holderName().isBlank()) {
            throw GatewayException.badRequest("invalid_cardholder_name", "Cardholder name is required");
        }

        TestCard testCard = TestCardCatalog.find(digits)
                .orElseThrow(() -> GatewayException.badRequest(
                        "not_a_test_card", "Only test cards are accepted in this environment"));

        // Desde acá el número no se usa más: lo que se guarda son los últimos cuatro.
        return cardTokens.save(new CardToken(
                testCard.brand(),
                digits.substring(digits.length() - 4),
                card.expiryMonth(),
                card.expiryYear(),
                testCard.behavior(),
                clock.instant()));
    }

    /**
     * Dice si a la tarjeta se le puede cobrar, sin mover plata (RF11).
     *
     * <p>La de fondos insuficientes da que sí, a propósito: una validación no consulta el saldo, y
     * es justamente el caso en que el cobro final falla después de haber entregado la energía.
     */
    @Transactional(readOnly = true)
    public CardValidation validate(String tokenId) {
        CardToken token = find(tokenId);
        if (token.getBehavior() == CardBehavior.SLOW) {
            Pause.of(slowCardDelay);
        }
        if (token.isExpiredAt(YearMonth.now(clock))) {
            return new CardValidation(PaymentStatus.REJECTED, "cc_rejected_card_expired");
        }
        if (token.getBehavior() == CardBehavior.DECLINE) {
            return new CardValidation(PaymentStatus.REJECTED, "cc_rejected_other_reason");
        }
        return new CardValidation(PaymentStatus.APPROVED, "chargeable");
    }

    @Transactional(readOnly = true)
    public CardToken find(String tokenId) {
        return cardTokens
                .findById(tokenId)
                .orElseThrow(() -> GatewayException.notFound("card_token_not_found", "Card token not found"));
    }

    /** Saca espacios y guiones, que es como la gente copia el número de la tarjeta física. */
    private static String digitsOf(String number) {
        if (number == null) {
            throw GatewayException.badRequest("invalid_card_number", "Card number is required");
        }
        String digits = number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d+")) {
            throw GatewayException.badRequest("invalid_card_number", "Card number must contain only digits");
        }
        return digits;
    }

    /** El algoritmo de Luhn: atrapa el dígito mal copiado y la transposición de dos dígitos. */
    private static boolean passesLuhn(String digits) {
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
}
