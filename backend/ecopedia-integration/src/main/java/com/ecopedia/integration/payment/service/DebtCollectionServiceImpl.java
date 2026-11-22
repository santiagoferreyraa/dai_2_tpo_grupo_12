package com.ecopedia.integration.payment.service;

import com.ecopedia.integration.payment.domain.BookingsUnavailableException;
import com.ecopedia.integration.payment.domain.DebtCollectionService;
import com.ecopedia.integration.payment.domain.DebtNotFoundException;
import com.ecopedia.integration.payment.domain.DebtPaymentDeclinedException;
import com.ecopedia.integration.payment.domain.DebtPaymentInProgressException;
import com.ecopedia.integration.payment.domain.DriverBookingsPort;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.NoUsablePaymentMethodException;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reintentos de la deuda y pago desde el perfil (ECO-62). Ver {@link DebtCollectionService}.
 *
 * <p><b>Sin transacción propia</b>, como el cobro de la seña: cada lectura y cada guardado va en la
 * suya, para no tener una conexión a la base tomada mientras se espera a la pasarela (hasta 5 s).
 *
 * <p><b>Un solo cobro a la vez por deuda.</b> El barrido y el conductor desde el perfil pueden querer
 * cobrar la misma deuda en el mismo segundo, con claves distintas: la pasarela cobraría dos veces.
 * Antes de cobrar, cada uno la reclama con una sentencia condicional ({@code claim}); el que no la
 * consigue se aparta. El reclamo vence solo a los dos minutos, por si el proceso se cae a mitad.
 */
@Service
public class DebtCollectionServiceImpl implements DebtCollectionService {

    private static final Logger log = LoggerFactory.getLogger(DebtCollectionServiceImpl.class);

    /** Holgado contra los 5 s que se espera a la pasarela; corto si el proceso se cae con la deuda reclamada. */
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(2);

    private final PendingPaymentRepository debts;
    private final PaymentMethodRepository cards;
    private final PaymentGatewayPort paymentGateway;
    private final DriverBookingsPort driverBookings;
    private final Clock clock;
    private final Duration retryInterval;
    private final int maxRetries;

    public DebtCollectionServiceImpl(
            PendingPaymentRepository debts,
            PaymentMethodRepository cards,
            PaymentGatewayPort paymentGateway,
            DriverBookingsPort driverBookings,
            Clock clock,
            @Value("${ecopedia.payments.debt.retry-interval}") Duration retryInterval,
            @Value("${ecopedia.payments.debt.max-retries}") int maxRetries) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException("ecopedia.payments.debt.max-retries tiene que ser al menos 1");
        }
        this.debts = debts;
        this.cards = cards;
        this.paymentGateway = paymentGateway;
        this.driverBookings = driverBookings;
        this.clock = clock;
        this.retryInterval = retryInterval;
        this.maxRetries = maxRetries;
    }

    @Override
    public List<PendingPayment> listOpenDebts(Long driverId) {
        return debts.findByDriverIdAndStatusInOrderByCreatedAtAsc(driverId, PendingPaymentStatus.OPEN);
    }

    @Override
    public PendingPayment payDebt(Long driverId, Long debtId, Long paymentMethodId, String idempotencyKey) {
        PendingPayment debt = debts.findById(debtId)
                .filter(found -> {
                    boolean mine = found.getDriverId().equals(driverId);
                    if (!mine) {
                        log.warn(
                                "Acceso denegado: el conductor {} quiso pagar la deuda {}, que es de otro",
                                driverId,
                                debtId);
                    }
                    return mine;
                })
                .orElseThrow(() -> new DebtNotFoundException(debtId));
        if (!debt.isOpen()) {
            // Ya saldada: un pedido repetido, o la saldó un reintento mientras el conductor miraba.
            return debt;
        }

        PaymentMethod card = chosenCard(driverId, paymentMethodId);

        Instant now = clock.instant();
        if (!debts.claim(debtId, now, now.plus(CLAIM_DURATION))) {
            throw new DebtPaymentInProgressException(debtId);
        }
        try {
            PendingPayment claimed = debts.findById(debtId).orElseThrow(() -> new DebtNotFoundException(debtId));
            if (!claimed.isOpen()) {
                return claimed;
            }
            GatewayCharge answer = paymentGateway.charge(
                    card.getGatewayToken(),
                    claimed.getAmount(),
                    "Pago de deuda de carga",
                    claimed.getReference(),
                    "debt-" + debtId + "-pay-" + idempotencyKey);
            if (!answer.approved()) {
                // El conductor eligió la tarjeta: un rechazo acá no suma a los reintentos del barrido.
                log.info(
                        "La pasarela rechazó el pago de la deuda {} del conductor {}: {}",
                        debtId,
                        driverId,
                        answer.statusDetail());
                throw new DebtPaymentDeclinedException(debtId, answer.statusDetail());
            }
            boolean wasDefaulted = claimed.isDefaulted();
            claimed.markSettled(clock.instant(), card.getId(), answer.paymentId());
            PendingPayment saved = debts.save(claimed);
            log.info(
                    "Deuda {} (${}) saldada por el conductor {} desde el perfil{}: pago {}",
                    debtId,
                    saved.getAmount(),
                    driverId,
                    wasDefaulted ? ", estaba vencida" : "",
                    answer.paymentId());
            return saved;
        } finally {
            debts.releaseClaim(debtId);
        }
    }

    @Override
    public int retryDueDebts() {
        int settled = 0;
        for (PendingPayment due : debts.findDueForRetry(clock.instant())) {
            try {
                if (retry(due.getId())) {
                    settled++;
                }
            } catch (RuntimeException failure) {
                // Una deuda que falla no frena las demás; el próximo barrido la vuelve a tomar.
                log.warn("No se pudo reintentar la deuda {}: {}", due.getId(), failure.getMessage());
            }
        }
        return settled;
    }

    @Override
    public int cancelBookingsOfDefaultedDebts() {
        int cancelled = 0;
        for (PendingPayment debt : debts.findDefaultedWithBookingsToCancel()) {
            if (cancelBookings(debt)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    /** Un reintento de una deuda. Devuelve si quedó saldada. */
    private boolean retry(Long debtId) {
        Instant now = clock.instant();
        if (!debts.claim(debtId, now, now.plus(CLAIM_DURATION))) {
            // La está cobrando el conductor desde el perfil, o el barrido de otra instancia.
            return false;
        }
        PendingPayment debt;
        try {
            debt = debts.findById(debtId).orElse(null);
            if (debt == null || !debt.isPending()) {
                return false;
            }
            boolean settled = chargeRetry(debt, now);
            debt = debts.save(debt);
            if (settled) {
                return true;
            }
        } finally {
            debts.releaseClaim(debtId);
        }
        if (debt.isDefaulted()) {
            log.warn(
                    "Deuda {} (${}) del conductor {} vencida: fallaron los {} reintentos. Se anulan sus reservas"
                            + " confirmadas y solo se salda desde el perfil",
                    debt.getId(),
                    debt.getAmount(),
                    debt.getDriverId(),
                    maxRetries);
            cancelBookings(debt);
        }
        return false;
    }

    /** Cobra un reintento y deja la deuda como quedó. Devuelve si se cobró. */
    private boolean chargeRetry(PendingPayment debt, Instant now) {
        Optional<PaymentMethod> card = mostRecentUsableCard(debt.getDriverId());
        if (card.isEmpty()) {
            boolean defaulted = debt.recordFailedRetry(
                    now, null, "El conductor no posee tarjeta vigente", maxRetries, retryInterval);
            log.info(
                    "Reintento {} de la deuda {}: el conductor {} no tiene tarjeta que sirva{}",
                    debt.getRetryCount(),
                    debt.getId(),
                    debt.getDriverId(),
                    defaulted ? "" : ". Próximo a las " + debt.getNextAttemptAt());
            return false;
        }

        GatewayCharge answer;
        try {
            answer = paymentGateway.charge(
                    card.get().getGatewayToken(),
                    debt.getAmount(),
                    "Reintento de cobro de carga",
                    debt.getReference(),
                    debt.nextRetryKey());
        } catch (PaymentGatewayUnavailableException down) {
            debt.postponeRetry(now, retryInterval);
            log.warn(
                    "Pasarela no disponible al reintentar la deuda {}: no cuenta como reintento. Próximo a las {}",
                    debt.getId(),
                    debt.getNextAttemptAt());
            return false;
        }

        if (answer.approved()) {
            debt.markSettled(clock.instant(), card.get().getId(), answer.paymentId());
            log.info(
                    "Deuda {} (${}) del conductor {} saldada en el reintento {}: pago {}",
                    debt.getId(),
                    debt.getAmount(),
                    debt.getDriverId(),
                    debt.getRetryCount() + 1,
                    answer.paymentId());
            return true;
        }
        boolean defaulted = debt.recordFailedRetry(
                now, card.get().getId(), "Reintento rechazado por la pasarela", maxRetries, retryInterval);
        log.info(
                "Reintento {} de la deuda {} rechazado por la pasarela ({}){}",
                debt.getRetryCount(),
                debt.getId(),
                answer.statusDetail(),
                defaulted ? "" : ". Próximo a las " + debt.getNextAttemptAt());
        return false;
    }

    /** Pide a Reservas que anule las reservas del conductor. Si no contesta, lo repite el barrido. */
    private boolean cancelBookings(PendingPayment debt) {
        int cancelled;
        try {
            cancelled = driverBookings.cancelConfirmedBookingsForDebt(debt.getDriverId());
        } catch (BookingsUnavailableException later) {
            log.warn(
                    "No se pudo pedir la anulación de las reservas del conductor {} (deuda {}): {}. Se reintenta",
                    debt.getDriverId(),
                    debt.getId(),
                    later.getMessage());
            return false;
        }
        PendingPayment fresh = debts.findById(debt.getId()).orElse(debt);
        fresh.markBookingsCancelled();
        debts.save(fresh);
        log.info(
                "Reservas del conductor {} anuladas por la deuda vencida {}: {}",
                debt.getDriverId(),
                debt.getId(),
                cancelled);
        return true;
    }

    /** La tarjeta elegida por el conductor, o la más reciente que sirva. */
    private PaymentMethod chosenCard(Long driverId, Long paymentMethodId) {
        if (paymentMethodId == null) {
            return mostRecentUsableCard(driverId)
                    .orElseThrow(() -> new NoUsablePaymentMethodException(
                            "El conductor " + driverId + " no tiene ninguna tarjeta con la que pagar la deuda"));
        }
        YearMonth today = YearMonth.now(clock);
        return cards.findById(paymentMethodId)
                .filter(card -> {
                    boolean mine = card.getDriverId().equals(driverId);
                    if (!mine) {
                        log.warn(
                                "Acceso denegado: el conductor {} quiso pagar su deuda con la tarjeta {}, que es de otro",
                                driverId,
                                paymentMethodId);
                    }
                    return mine;
                })
                .filter(card -> card.isUsable(today))
                .orElseThrow(() -> new NoUsablePaymentMethodException(
                        "La tarjeta " + paymentMethodId + " no se puede usar para el conductor " + driverId));
    }

    private Optional<PaymentMethod> mostRecentUsableCard(Long driverId) {
        YearMonth today = YearMonth.now(clock);
        return cards.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId).stream()
                .filter(card -> card.isUsable(today))
                .findFirst();
    }
}
