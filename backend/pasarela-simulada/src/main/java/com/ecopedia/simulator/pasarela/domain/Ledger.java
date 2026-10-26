package com.ecopedia.simulator.pasarela.domain;

import com.ecopedia.simulator.pasarela.domain.CardInspector.CardBehavior;
import com.ecopedia.simulator.pasarela.domain.CardInspector.InspectedCard;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Los libros de la pasarela: tokens emitidos, pagos cobrados y reintegros hechos. Todo en memoria.
 *
 * <p>Las reglas que aplica son las que Ecopedia va a necesitar de una pasarela real:
 *
 * <ul>
 *   <li>Un token no deriva del número: es un identificador aleatorio (RF02).
 *   <li>Un pago con la misma clave de idempotencia devuelve el pago ya hecho, en vez de cobrar dos
 *       veces. Es lo que hace seguro reintentar un cobro cuando se cortó la red (ECO-62).
 *   <li>No se puede reintegrar más de lo que se cobró.
 * </ul>
 */
@Component
public class Ledger {

    private static final Logger log = LoggerFactory.getLogger(Ledger.class);

    private final Map<String, CardToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, Payment> payments = new ConcurrentHashMap<>();
    private final Map<String, String> paymentsByIdempotencyKey = new ConcurrentHashMap<>();

    private final CardInspector cardInspector;
    private final Clock clock;

    public Ledger(CardInspector cardInspector, Clock clock) {
        this.cardInspector = cardInspector;
        this.clock = clock;
    }

    public CardToken issueToken(String cardNumber, int expirationMonth, int expirationYear) {
        InspectedCard card = cardInspector.inspect(cardNumber, expirationMonth, expirationYear);
        CardToken token = new CardToken(newId("tok"), card, Instant.now(clock));
        tokens.put(token.id(), token);

        log.info("Token {} emitido para una {} terminada en {}", token.id(), card.network(), card.lastFour());
        return token;
    }

    /**
     * Si la tarjeta detrás del token se puede cobrar hoy. No mueve plata.
     *
     * @return vacío si es cobrable; si no, el código del motivo.
     */
    public Optional<String> verify(String tokenId) {
        CardToken token = requireToken(tokenId);
        try {
            cardInspector.requireValidExpiry(
                    token.card().expirationMonth(), token.card().expirationYear());
        } catch (GatewayException expired) {
            return Optional.of(expired.code());
        }
        return rejectionCode(token.card().behavior());
    }

    /**
     * Cobra {@code amount} centavos contra el token.
     *
     * @param idempotencyKey si ya hubo un pago con esta clave, se devuelve ese y no se cobra de nuevo.
     *     Sincronizado para que dos reintentos simultáneos con la misma clave no cobren los dos.
     */
    public synchronized Payment charge(
            String tokenId,
            long amount,
            String currencyId,
            String description,
            String externalReference,
            String idempotencyKey) {
        if (idempotencyKey != null) {
            String previous = paymentsByIdempotencyKey.get(idempotencyKey);
            if (previous != null) {
                log.info("Pago repetido con la clave {}: se devuelve {} sin cobrar otra vez", idempotencyKey, previous);
                return payments.get(previous);
            }
        }

        if (!"ARS".equals(currencyId)) {
            throw GatewayException.invalidRequest("unsupported_currency", "Only ARS is supported");
        }

        CardToken token = requireToken(tokenId);
        cardInspector.requireValidExpiry(
                token.card().expirationMonth(), token.card().expirationYear());

        Optional<String> rejection = rejectionCode(token.card().behavior());
        if (rejection.isPresent()) {
            log.info("Pago de {} centavos rechazado contra {}: {}", amount, tokenId, rejection.get());
            throw GatewayException.cardError(rejection.get(), "The payment was rejected by the issuer");
        }

        Payment payment = new Payment(
                newId("pay"), tokenId, amount, currencyId, description, externalReference, 0L, Instant.now(clock));
        payments.put(payment.id(), payment);
        if (idempotencyKey != null) {
            paymentsByIdempotencyKey.put(idempotencyKey, payment.id());
        }

        log.info("Pago {} aprobado: {} centavos contra {}", payment.id(), amount, tokenId);
        return payment;
    }

    public Payment findPayment(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) throw GatewayException.notFound("No payment with id " + paymentId);
        return payment;
    }

    /**
     * Reintegra {@code amount} centavos del pago, o todo lo que queda si viene vacío.
     *
     * <p>Sincronizado para que dos reintegros simultáneos no pasen juntos el control de saldo.
     */
    public synchronized Refund refund(String paymentId, Long amount) {
        Payment payment = findPayment(paymentId);
        long available = payment.amount() - payment.amountRefunded();
        long requested = amount == null ? available : amount;

        if (requested <= 0 || requested > available) {
            throw GatewayException.invalidRequest(
                    "amount_exceeds_payment", "Refund amount must be between 1 and " + available + " cents");
        }

        payments.put(paymentId, payment.withAmountRefunded(payment.amountRefunded() + requested));
        Refund refund = new Refund(newId("ref"), paymentId, requested, Instant.now(clock));

        log.info("Reintegro {} de {} centavos sobre el pago {}", refund.id(), requested, paymentId);
        return refund;
    }

    private CardToken requireToken(String tokenId) {
        CardToken token = tokens.get(tokenId);
        if (token == null) throw GatewayException.notFound("No card token with id " + tokenId);
        return token;
    }

    private Optional<String> rejectionCode(CardBehavior behavior) {
        return switch (behavior) {
            case APPROVED -> Optional.empty();
            case INSUFFICIENT_FUNDS -> Optional.of("cc_rejected_insufficient_amount");
            case REJECTED -> Optional.of("cc_rejected_other_reason");
        };
    }

    private static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public record CardToken(String id, InspectedCard card, Instant createdAt) {}

    public record Payment(
            String id,
            String cardToken,
            long amount,
            String currencyId,
            String description,
            String externalReference,
            long amountRefunded,
            Instant createdAt) {

        Payment withAmountRefunded(long refunded) {
            return new Payment(id, cardToken, amount, currencyId, description, externalReference, refunded, createdAt);
        }
    }

    public record Refund(String id, String paymentId, long amount, Instant createdAt) {}
}
