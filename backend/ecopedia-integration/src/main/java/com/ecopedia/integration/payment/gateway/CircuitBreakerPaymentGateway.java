package com.ecopedia.integration.payment.gateway;

import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.GatewayRefund;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * El circuit breaker delante de la pasarela (ECO-68): un Decorator sobre el Adapter.
 *
 * <p>Implementa el mismo {@link PaymentGatewayPort} que {@link PaymentGatewayRestClient} y lo
 * envuelve. Pagos sigue hablando con el puerto y no se entera de que hay un circuito, igual que no se
 * enteró cuando se cambió la pasarela local por la remota.
 *
 * <p><b>Con el circuito abierto no se llama a la pasarela</b> y se tira la misma
 * {@link PaymentGatewayUnavailableException} de siempre. Así todo lo que ya sabía manejar una
 * pasarela caída —el 503 del checkout, el cobro del consumo que queda pendiente, la devolución que se
 * reintenta— sigue igual, solo que sin esperar el timeout en cada pedido.
 */
public class CircuitBreakerPaymentGateway implements PaymentGatewayPort {

    private final PaymentGatewayPort delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerPaymentGateway(PaymentGatewayPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public TokenizedCard tokenize(CardData card) {
        return call(() -> delegate.tokenize(card));
    }

    @Override
    public GatewayCharge charge(
            String gatewayToken, BigDecimal amount, String description, String reference, String idempotencyKey) {
        return call(() -> delegate.charge(gatewayToken, amount, description, reference, idempotencyKey));
    }

    @Override
    public GatewayRefund refund(String paymentId, BigDecimal amount, String idempotencyKey) {
        return call(() -> delegate.refund(paymentId, amount, idempotencyKey));
    }

    @Override
    public CardValidation validate(String gatewayToken) {
        return call(() -> delegate.validate(gatewayToken));
    }

    private <T> T call(Supplier<T> operation) {
        try {
            return circuitBreaker.executeSupplier(operation);
        } catch (CallNotPermittedException open) {
            throw new PaymentGatewayUnavailableException(open);
        }
    }
}
