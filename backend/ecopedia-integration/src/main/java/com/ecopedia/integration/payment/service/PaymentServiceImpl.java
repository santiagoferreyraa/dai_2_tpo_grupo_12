package com.ecopedia.integration.payment.service;

import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.ConsumptionChargeRequest;
import com.ecopedia.integration.payment.domain.ConsumptionChargeResult;
import com.ecopedia.integration.payment.domain.DepositCharge;
import com.ecopedia.integration.payment.domain.DepositChargeNotFoundException;
import com.ecopedia.integration.payment.domain.DepositChargeRepository;
import com.ecopedia.integration.payment.domain.DepositChargeRequest;
import com.ecopedia.integration.payment.domain.DepositNotRefundableException;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.GatewayRefund;
import com.ecopedia.integration.payment.domain.IdempotencyKeyReusedException;
import com.ecopedia.integration.payment.domain.NoUsablePaymentMethodException;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodNotFoundException;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PaymentService;
import com.ecopedia.integration.payment.domain.PaymentValidationResult;
import com.ecopedia.integration.payment.domain.PaymentValidationResult.ValidationErrorCode;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementación del componente {@code Pagos} para las operaciones de RF02 (ECO-26). */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /**
     * Tope de tarjetas vigentes por conductor.
     *
     * <p>No lo pide el requisito. Está porque el alta es gratis y no tiene ningún costo para quien
     * la hace: sin un tope, una pantalla con un botón que repite la llamada llena la tabla sin que
     * nada la frene. Diez es holgado para el caso real —una personal, una del trabajo— y sigue
     * siendo un número que un conductor de verdad no alcanza por accidente.
     */
    private static final int MAXIMUM_ACTIVE_CARDS = 10;

    private final PaymentMethodRepository paymentMethodRepository;
    private final DepositChargeRepository depositChargeRepository;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final Clock clock;

    public PaymentServiceImpl(
            PaymentMethodRepository paymentMethodRepository,
            DepositChargeRepository depositChargeRepository,
            PendingPaymentRepository pendingPaymentRepository,
            PaymentGatewayPort paymentGateway,
            Clock clock) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.depositChargeRepository = depositChargeRepository;
        this.pendingPaymentRepository = pendingPaymentRepository;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    @Override
    public PaymentMethod registerCard(Long driverId, CardData data) {
        List<PaymentMethod> current = paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId);

        if (current.size() >= MAXIMUM_ACTIVE_CARDS) {
            throw new IllegalArgumentException(
                    "Llegaste al máximo de " + MAXIMUM_ACTIVE_CARDS + " tarjetas. Eliminá alguna para agregar otra");
        }

        /*
         * La tokenización va PRIMERO, y el orden no es casual: si la pasarela rechaza la tarjeta,
         * no queremos haber escrito nada. Al revés —guardar y después tokenizar— dejaría filas sin
         * token cada vez que un número viene mal tipeado, que es el error más común de esta
         * pantalla.
         *
         * Es también el único punto del sistema por el que pasa el número completo. De acá sale un
         * TokenizedCard y el número queda atrás.
         */
        TokenizedCard tokenized = paymentGateway.tokenize(data);

        rejectDuplicate(current, tokenized, data);

        PaymentMethod card = new PaymentMethod();
        card.setDriverId(driverId);
        card.setBrand(tokenized.brand());
        card.setLastFour(tokenized.lastFour());
        card.setGatewayToken(tokenized.gatewayToken());
        card.setExpiryMonth(data.expiryMonth());
        card.setExpiryYear(data.expiryYear());
        card.setLabel(normalizedLabel(data.label()));
        card.setActive(true);

        return paymentMethodRepository.save(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethod> listCards(Long driverId) {
        return paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId);
    }

    @Override
    public void removeCard(Long driverId, Long cardId) {
        PaymentMethod card = paymentMethodRepository
                .findById(cardId)
                .filter(found -> {
                    boolean mine = found.getDriverId().equals(driverId);
                    if (!mine) {
                        // La respuesta no lo dice, el log sí (SEC-06).
                        log.warn(
                                "Acceso denegado: el conductor {} quiso dar de baja la tarjeta {}, que es de otro",
                                driverId,
                                cardId);
                    }
                    return mine;
                })
                .filter(PaymentMethod::isActive)
                /*
                 * "No encontrada" también cuando existe pero es de otro, y es a propósito. Un
                 * mensaje distinto para "existe pero no es tuya" le confirma a cualquiera que ese
                 * id corresponde a una tarjeta real: probando ids consecutivos se puede contar
                 * cuántas tarjetas tiene la plataforma. Como no hay nada que el dueño legítimo
                 * pueda hacer distinto según el caso, las dos situaciones se contestan igual.
                 */
                .orElseThrow(() -> new PaymentMethodNotFoundException(cardId));

        // Baja lógica: los cobros y las deudas que cuelgan del token tienen que seguir existiendo.
        card.setActive(false);
        paymentMethodRepository.save(card);
    }

    /*
     * Sin transacción propia, a diferencia del resto de la clase: cada consulta y cada guardado va en
     * la suya. Así la conexión a la base no queda tomada mientras se espera a la pasarela (hasta 5 s),
     * y un choque de la restricción UNIQUE no deja marcada para rollback una transacción que después
     * hay que seguir usando para leer el cobro del otro pedido.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DepositCharge chargeDeposit(DepositChargeRequest request) {
        Optional<DepositCharge> previous = depositChargeRepository.findByIdempotencyKey(request.idempotencyKey());
        if (previous.isPresent()) {
            return repeated(previous.get(), request);
        }

        PaymentMethod card = cardToCharge(request.driverId(), request.paymentMethodId());

        /*
         * El cobro va antes de guardar, y si la pasarela no contesta no se guarda nada: no se sabe si
         * cobró. Una fila "pendiente" no ayudaría, porque el único que puede contestar esa pregunta
         * es la pasarela, y se la contesta al reintento con la misma clave.
         */
        GatewayCharge answer = paymentGateway.charge(
                card.getGatewayToken(),
                request.amount(),
                "Seña de la reserva",
                request.reference(),
                request.idempotencyKey());

        DepositCharge charge;
        try {
            charge = depositChargeRepository.save(new DepositCharge(request, card, answer, clock.instant()));
        } catch (DataIntegrityViolationException sameKeyAtOnce) {
            /*
             * Dos pedidos con la misma clave al mismo tiempo: los dos pasaron la consulta de arriba y la
             * pasarela, por su propia idempotencia, les contestó el mismo cobro. La restricción UNIQUE
             * dejó guardar a uno; el otro devuelve lo que guardó el primero.
             */
            return depositChargeRepository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .map(saved -> repeated(saved, request))
                    .orElseThrow(() -> sameKeyAtOnce);
        }

        if (charge.isApproved()) {
            log.info(
                    "Seña cobrada: cobro {} de ${} al conductor {} ({})",
                    charge.getId(),
                    charge.getAmount(),
                    charge.getDriverId(),
                    request.reference());
        } else {
            log.info(
                    "La pasarela rechazó la seña de ${} al conductor {}: {}",
                    charge.getAmount(),
                    charge.getDriverId(),
                    charge.getStatusDetail());
        }
        return charge;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DepositCharge refundDeposit(Long chargeId) {
        return refundDeposit(chargeId, null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DepositCharge refundDeposit(Long chargeId, BigDecimal amount) {
        DepositCharge charge = depositChargeRepository
                .findById(chargeId)
                .orElseThrow(() -> new DepositChargeNotFoundException(chargeId));
        if (amount != null && (amount.signum() <= 0 || amount.compareTo(charge.getAmount()) > 0)) {
            throw new DepositNotRefundableException(chargeId);
        }
        if (charge.isRefunded()) {
            BigDecimal alreadyRefunded =
                    charge.getRefundedAmount() != null ? charge.getRefundedAmount() : charge.getAmount();
            BigDecimal requested = amount != null ? amount : charge.getAmount();
            if (requested.compareTo(alreadyRefunded) != 0) {
                throw new DepositNotRefundableException(chargeId);
            }
            return charge;
        }
        if (!charge.isApproved()) {
            throw new DepositNotRefundableException(chargeId);
        }

        BigDecimal gatewayAmount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);

        /*
         * La clave del reintegro sale de la del cobro, que es única para siempre. No del id de la
         * fila: con `pnpm dev:mem` los ids vuelven a empezar en cada arranque y la pasarela, que
         * guarda en archivo, reconocería "refund-deposit-1" como un reintegro viejo de otro cobro.
         */
        GatewayRefund refund = paymentGateway.refund(
                charge.getGatewayPaymentId(), gatewayAmount, "refund-" + charge.getIdempotencyKey());
        charge.markRefunded(refund, clock.instant());
        log.info(
                "Seña devuelta: cobro {} (${} de ${}) al conductor {}",
                charge.getId(),
                charge.getRefundedAmount(),
                charge.getAmount(),
                charge.getDriverId());
        return depositChargeRepository.save(charge);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentValidationResult validatePaymentMethod(Long driverId, Long paymentMethodId) {
        YearMonth today = YearMonth.now(clock);
        PaymentMethod card;
        if (paymentMethodId != null) {
            Optional<PaymentMethod> found = paymentMethodRepository
                    .findById(paymentMethodId)
                    .filter(c -> {
                        boolean mine = c.getDriverId().equals(driverId);
                        if (!mine) {
                            log.warn(
                                    "Acceso denegado: se intentó validar la tarjeta {} con el conductor {}, que es de otro",
                                    paymentMethodId,
                                    driverId);
                        }
                        return mine;
                    });
            if (found.isEmpty() || !found.get().isActive()) {
                return PaymentValidationResult.rejected(
                        "El conductor no posee tarjetas de pago registradas", ValidationErrorCode.NO_PAYMENT_METHOD);
            }
            if (found.get().isExpired(today)) {
                return PaymentValidationResult.rejected(
                        "La tarjeta registrada se encuentra vencida", ValidationErrorCode.CARD_EXPIRED);
            }
            card = found.get();
        } else {
            List<PaymentMethod> activeCards =
                    paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId);
            if (activeCards.isEmpty()) {
                return PaymentValidationResult.rejected(
                        "El conductor no posee tarjetas de pago registradas", ValidationErrorCode.NO_PAYMENT_METHOD);
            }
            Optional<PaymentMethod> usable =
                    activeCards.stream().filter(c -> c.isUsable(today)).findFirst();
            if (usable.isEmpty()) {
                return PaymentValidationResult.rejected(
                        "La tarjeta registrada se encuentra vencida", ValidationErrorCode.CARD_EXPIRED);
            }
            card = usable.get();
        }

        if (hasOutstandingDebt(driverId)) {
            return PaymentValidationResult.rejected(
                    "El conductor posee cobros pendientes de pago", ValidationErrorCode.DRIVER_HAS_DEBT);
        }

        CardValidation validation = paymentGateway.validate(card.getGatewayToken());
        if (!validation.chargeable()) {
            return PaymentValidationResult.rejected(
                    "La pasarela de pagos rechazó la tarjeta del conductor", ValidationErrorCode.CARD_DECLINED);
        }

        return PaymentValidationResult.ok();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOutstandingDebt(Long driverId) {
        return pendingPaymentRepository.existsByDriverIdAndStatus(driverId, PendingPaymentStatus.PENDING);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ConsumptionChargeResult chargeConsumption(ConsumptionChargeRequest request) {
        Optional<PendingPayment> existingDebt = pendingPaymentRepository.findByReference(request.sessionId());
        if (existingDebt.isPresent()) {
            PendingPayment debt = existingDebt.get();
            return ConsumptionChargeResult.debtRecorded(
                    debt.getAmount(), request.sessionId(), debt.getId(), debt.getReason());
        }

        PaymentMethod card;
        try {
            card = cardToCharge(request.driverId(), request.paymentMethodId());
        } catch (NoUsablePaymentMethodException noCard) {
            log.warn(
                    "Conductor {} sin tarjeta vigente al liquidar sesión {}: se registra deuda pendiente",
                    request.driverId(),
                    request.sessionId());
            return recordDebt(
                    request.driverId(),
                    null,
                    request.amount(),
                    "El conductor no posee tarjeta vigente al cerrar la sesión",
                    request.sessionId());
        }

        try {
            GatewayCharge answer = paymentGateway.charge(
                    card.getGatewayToken(),
                    request.amount(),
                    "Consumo de sesión de carga",
                    request.sessionId(),
                    request.idempotencyKey());
            if (answer.approved()) {
                log.info(
                        "Consumo de sesión {} cobrado (${}) al conductor {}: pago {}",
                        request.sessionId(),
                        request.amount(),
                        request.driverId(),
                        answer.paymentId());
                return ConsumptionChargeResult.charged(
                        request.amount(), request.sessionId(), answer.paymentId(), answer.statusDetail());
            }
            log.warn(
                    "Pasarela rechazó cobro de sesión {} al conductor {}: {}",
                    request.sessionId(),
                    request.driverId(),
                    answer.statusDetail());
            return recordDebt(
                    request.driverId(),
                    card.getId(),
                    request.amount(),
                    truncatedReason(answer.statusDetail(), "Cobro rechazado por la pasarela"),
                    request.sessionId());
        } catch (PaymentGatewayUnavailableException unavailable) {
            log.warn(
                    "Pasarela no disponible al liquidar sesión {} del conductor {}: se registra deuda pendiente",
                    request.sessionId(),
                    request.driverId(),
                    unavailable);
            return recordDebt(
                    request.driverId(),
                    card.getId(),
                    request.amount(),
                    "Pasarela de pagos no disponible al liquidar sesión",
                    request.sessionId());
        }
    }

    private ConsumptionChargeResult recordDebt(
            Long driverId, Long paymentMethodId, BigDecimal amount, String reason, String sessionId) {
        PendingPayment saved = pendingPaymentRepository.save(
                new PendingPayment(driverId, paymentMethodId, amount, reason, sessionId, clock.instant()));
        return ConsumptionChargeResult.debtRecorded(amount, sessionId, saved.getId(), reason);
    }

    private static String truncatedReason(String detail, String fallback) {
        if (detail == null || detail.isBlank()) {
            return fallback;
        }
        String trimmed = detail.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private static DepositCharge repeated(DepositCharge previous, DepositChargeRequest request) {
        if (!previous.answers(request)) {
            log.warn(
                    "La clave {} ya nombra el cobro {}, de otro conductor o importe: se rechaza",
                    request.idempotencyKey(),
                    previous.getId());
            throw new IdempotencyKeyReusedException(request.idempotencyKey());
        }
        log.info(
                "Cobro de seña repetido con la clave {}: se devuelve el {}",
                request.idempotencyKey(),
                previous.getId());
        return previous;
    }

    /**
     * La tarjeta con la que se cobra: la elegida, o la más reciente que se pueda usar.
     *
     * <p>"Que se pueda usar" es {@link PaymentMethod#isUsable}: activa y no vencida. Una vencida no
     * se manda a la pasarela para que la rechace: el conductor tiene otra cosa que hacer —elegir otra
     * o cargar una nueva—, y eso se le puede decir sin gastar un cobro rechazado.
     */
    private PaymentMethod cardToCharge(Long driverId, Long paymentMethodId) {
        YearMonth today = YearMonth.now(clock);
        if (paymentMethodId == null) {
            return paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId).stream()
                    .filter(card -> card.isUsable(today))
                    .findFirst()
                    .orElseThrow(() -> new NoUsablePaymentMethodException(
                            "El conductor " + driverId + " no tiene ninguna tarjeta con la que cobrar"));
        }
        return paymentMethodRepository
                .findById(paymentMethodId)
                .filter(card -> {
                    boolean mine = card.getDriverId().equals(driverId);
                    if (!mine) {
                        log.warn(
                                "Acceso denegado: se quiso cobrar la seña del conductor {} con la tarjeta {}, que es de otro",
                                driverId,
                                paymentMethodId);
                    }
                    return mine;
                })
                .filter(card -> card.isUsable(today))
                .orElseThrow(() -> new NoUsablePaymentMethodException(
                        "La tarjeta " + paymentMethodId + " no se puede usar para el conductor " + driverId));
    }

    /**
     * Rechaza registrar dos veces la misma tarjeta.
     *
     * <p><b>La comparación es aproximada, y no se puede hacer mejor.</b> Lo exacto sería comparar
     * números, pero el número no se guarda (RF02) y la pasarela emite un token distinto en cada
     * tokenización. Así que se compara por lo que sí tenemos: misma marca, mismos
     * cuatro últimos dígitos y mismo vencimiento. Dos tarjetas distintas que coincidan en las tres
     * cosas existen en teoría; en el conjunto de tarjetas de una persona, no.
     *
     * <p>El costo de equivocarse es asimétrico y por eso se elige este lado: un falso positivo le
     * niega un alta a alguien que puede usar la tarjeta que ya tiene registrada, mientras que un
     * falso negativo le deja dos filas idénticas en la pantalla sin forma de saber cuál es cuál.
     */
    private void rejectDuplicate(List<PaymentMethod> current, TokenizedCard tokenized, CardData data) {
        boolean alreadyRegistered = current.stream()
                .anyMatch(card -> card.getBrand() == tokenized.brand()
                        && card.getLastFour().equals(tokenized.lastFour())
                        && card.getExpiryMonth() == data.expiryMonth()
                        && card.getExpiryYear() == data.expiryYear());

        if (alreadyRegistered) {
            throw new IllegalArgumentException("Esa tarjeta ya está registrada");
        }
    }

    /** Una etiqueta en blanco es lo mismo que no haber puesto ninguna: se guarda como ausente. */
    private String normalizedLabel(String label) {
        if (label == null || label.isBlank()) return null;
        return label.trim();
    }
}
