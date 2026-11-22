package com.ecopedia.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.integration.payment.domain.BookingsUnavailableException;
import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.DebtEvent;
import com.ecopedia.integration.payment.domain.DebtEventType;
import com.ecopedia.integration.payment.domain.DebtNotFoundException;
import com.ecopedia.integration.payment.domain.DebtPaymentDeclinedException;
import com.ecopedia.integration.payment.domain.DebtPaymentInProgressException;
import com.ecopedia.integration.payment.domain.DriverBookingsPort;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import com.ecopedia.integration.payment.service.DebtCollectionServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * La deuda del conductor (ECO-62, decidido por Manuel el 20/11): tres reintentos cada 20 minutos
 * después del cobro original; si fallan los tres, la cuenta queda marcada y se anulan sus reservas.
 */
class DebtCollectionServiceTest {

    private static final Long DRIVER = 7L;
    private static final Duration INTERVAL = Duration.ofMinutes(20);
    private static final Instant CLOSED_AT = Instant.parse("2026-11-20T12:00:00Z");

    private PendingPaymentRepository debts;
    private PaymentMethodRepository cards;
    private PaymentGatewayPort gateway;
    private DriverBookingsPort bookings;
    private MovingClock clock;
    private DebtCollectionServiceImpl service;
    private PendingPayment debt;
    private PaymentMethod card;
    /** Los avisos que publicó el servicio, en orden. */
    private List<DebtEvent> events;

    @BeforeEach
    void setUp() {
        debts = mock(PendingPaymentRepository.class);
        cards = mock(PaymentMethodRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        bookings = mock(DriverBookingsPort.class);
        clock = new MovingClock(CLOSED_AT);
        events = new ArrayList<>();
        service = new DebtCollectionServiceImpl(debts, cards, gateway, bookings, events::add, clock, INTERVAL, 3);

        // La carga se cerró a las 12:00 y el cobro original falló: primer reintento a las 12:20.
        debt = new PendingPayment(DRIVER, 1L, new BigDecimal("4320.00"), "rechazada", "session-9", CLOSED_AT);
        ReflectionTestUtils.setField(debt, "id", 50L);
        debt.scheduleFirstRetry(INTERVAL);

        card = new PaymentMethod();
        card.setId(3L);
        card.setDriverId(DRIVER);
        card.setBrand(CardBrand.VISA);
        card.setLastFour("4242");
        card.setGatewayToken("tok_ok");
        card.setExpiryMonth(12);
        card.setExpiryYear(2099);
        card.setActive(true);

        when(debts.findById(50L)).thenReturn(Optional.of(debt));
        when(debts.claim(eq(50L), any(), any())).thenReturn(true);
        when(debts.save(any(PendingPayment.class))).thenAnswer(call -> call.getArgument(0));
        when(cards.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER)).thenReturn(List.of(card));
    }

    /** El barrido a la hora del próximo reintento de la deuda. */
    private void sweepAtNextAttempt() {
        clock.set(debt.getNextAttemptAt());
        when(debts.findDueForRetry(any())).thenReturn(List.of(debt));
        service.retryDueDebts();
    }

    private void gatewayAnswers(boolean approved) {
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new GatewayCharge("pay-1", approved, approved ? "accredited" : "cc_rejected"));
    }

    @Test
    @DisplayName("El primer reintento es a los 20 minutos del cobro que falló")
    void firstRetryIsTwentyMinutesAfterTheOriginalCharge() {
        assertThat(debt.getNextAttemptAt()).isEqualTo(CLOSED_AT.plus(INTERVAL));
    }

    @Test
    @DisplayName("Un reintento aprobado salda la deuda con la tarjeta y el cobro, y suelta el reclamo")
    void approvedRetrySettlesTheDebt() {
        gatewayAnswers(true);

        sweepAtNextAttempt();

        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        assertThat(debt.getPaymentMethodId()).isEqualTo(3L);
        assertThat(debt.getGatewayPaymentId()).isEqualTo("pay-1");
        verify(gateway)
                .charge(
                        eq("tok_ok"),
                        eq(new BigDecimal("4320.00")),
                        anyString(),
                        eq("session-9"),
                        eq("debt-50-retry-1"));
        verify(debts).releaseClaim(50L);
        verify(bookings, never()).cancelConfirmedBookingsForDebt(anyLong());
        assertThat(events).extracting(DebtEvent::type).containsExactly(DebtEventType.DEBT_SETTLED);
        assertThat(events.get(0).eventId()).isEqualTo("debt-50-settled");
    }

    @Test
    @DisplayName("Tres rechazos, cada uno a los 20 minutos del anterior; al tercero la cuenta queda marcada")
    void threeRejectedRetriesDefaultTheDebtAndCancelTheBookings() {
        gatewayAnswers(false);

        sweepAtNextAttempt(); // 12:20
        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(debt.getRetryCount()).isEqualTo(1);
        assertThat(debt.getNextAttemptAt()).isEqualTo(CLOSED_AT.plus(INTERVAL.multipliedBy(2)));

        sweepAtNextAttempt(); // 12:40
        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(debt.getRetryCount()).isEqualTo(2);
        verify(bookings, never()).cancelConfirmedBookingsForDebt(anyLong());

        sweepAtNextAttempt(); // 13:00
        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.DEFAULTED);
        assertThat(debt.getRetryCount()).isEqualTo(3);
        assertThat(debt.getNextAttemptAt()).isNull();
        verify(bookings).cancelConfirmedBookingsForDebt(DRIVER);
        assertThat(debt.isBookingsCancelled()).isTrue();

        // Cada reintento con su clave: con la misma, la pasarela devolvería el mismo rechazo.
        verify(gateway).charge(anyString(), any(), anyString(), anyString(), eq("debt-50-retry-1"));
        verify(gateway).charge(anyString(), any(), anyString(), anyString(), eq("debt-50-retry-2"));
        verify(gateway).charge(anyString(), any(), anyString(), anyString(), eq("debt-50-retry-3"));

        // Un aviso por reintento; el tercero es además la marca de la cuenta: uno solo (Manuel, 22/11).
        assertThat(events)
                .extracting(DebtEvent::eventId)
                .containsExactly("debt-50-retry-1-failed", "debt-50-retry-2-failed", "debt-50-defaulted");
        assertThat(events)
                .extracting(DebtEvent::type)
                .containsExactly(
                        DebtEventType.DEBT_RETRY_FAILED, DebtEventType.DEBT_RETRY_FAILED, DebtEventType.DEBT_DEFAULTED);
        assertThat(events).extracting(DebtEvent::attempt).containsExactly(1, 2, 3);
        assertThat(events).extracting(DebtEvent::maxAttempts).containsOnly(3);
        assertThat(events.get(0).nextAttemptAt()).isEqualTo(CLOSED_AT.plus(INTERVAL.multipliedBy(2)));
        assertThat(events.get(2).nextAttemptAt()).isNull();
        assertThat(events).extracting(DebtEvent::incurredAt).containsOnly(CLOSED_AT);
    }

    @Test
    @DisplayName("Con la pasarela caída el reintento no cuenta, y el próximo repite la misma clave")
    void gatewayDownDoesNotCountAsARetry() {
        when(gateway.charge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        sweepAtNextAttempt();

        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(debt.getRetryCount()).isZero();
        assertThat(debt.getNextAttemptAt()).isEqualTo(CLOSED_AT.plus(INTERVAL.multipliedBy(2)));
        assertThat(debt.nextRetryKey()).isEqualTo("debt-50-retry-1");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Sin tarjeta que sirva el reintento cuenta como fallido, sin ir a la pasarela")
    void noUsableCardCountsAsAFailedRetry() {
        when(cards.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER)).thenReturn(List.of());

        sweepAtNextAttempt();

        assertThat(debt.getRetryCount()).isEqualTo(1);
        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
        assertThat(events).extracting(DebtEvent::type).containsExactly(DebtEventType.DEBT_RETRY_FAILED);
    }

    @Test
    @DisplayName("Si otro la tiene reclamada (el conductor pagando), el barrido no la cobra")
    void doesNotChargeADebtSomeoneElseClaimed() {
        when(debts.claim(eq(50L), any(), any())).thenReturn(false);

        sweepAtNextAttempt();

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
        assertThat(debt.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("Si Reservas no contesta, la deuda queda vencida igual y el barrido vuelve a pedir la anulación")
    void bookingsDownIsRetriedLater() {
        gatewayAnswers(false);
        when(bookings.cancelConfirmedBookingsForDebt(DRIVER))
                .thenThrow(new BookingsUnavailableException("caído", null))
                .thenReturn(2);

        sweepAtNextAttempt();
        sweepAtNextAttempt();
        sweepAtNextAttempt();
        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.DEFAULTED);
        assertThat(debt.isBookingsCancelled()).isFalse();

        when(debts.findDefaultedWithBookingsToCancel()).thenReturn(List.of(debt));
        assertThat(service.cancelBookingsOfDefaultedDebts()).isEqualTo(1);
        assertThat(debt.isBookingsCancelled()).isTrue();
    }

    @Test
    @DisplayName("El conductor paga una deuda vencida desde el perfil: queda saldada, sin sumar reintentos")
    void driverPaysADefaultedDebt() {
        gatewayAnswers(false);
        sweepAtNextAttempt();
        sweepAtNextAttempt();
        sweepAtNextAttempt();
        gatewayAnswers(true);
        when(cards.findById(3L)).thenReturn(Optional.of(card));

        PendingPayment paid = service.payDebt(DRIVER, 50L, 3L, "key-abcdef12");

        assertThat(paid.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        verify(gateway).charge(anyString(), any(), anyString(), anyString(), eq("debt-50-pay-key-abcdef12"));
        assertThat(events).last().extracting(DebtEvent::type).isEqualTo(DebtEventType.DEBT_SETTLED);
    }

    @Test
    @DisplayName("Un rechazo al pagar desde el perfil no suma a los reintentos y suelta el reclamo")
    void declinedPaymentFromTheProfileKeepsTheDebtOpen() {
        gatewayAnswers(false);

        assertThatThrownBy(() -> service.payDebt(DRIVER, 50L, null, "key-abcdef12"))
                .isInstanceOf(DebtPaymentDeclinedException.class);

        assertThat(debt.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(debt.getRetryCount()).isZero();
        verify(debts).releaseClaim(50L);
        // El conductor lo ve en el diálogo: no hace falta además un aviso.
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("La deuda de otro conductor se contesta como inexistente, sin cobrar")
    void anotherDriversDebtIsNotFound() {
        assertThatThrownBy(() -> service.payDebt(99L, 50L, null, "key-abcdef12"))
                .isInstanceOf(DebtNotFoundException.class);

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Pagar una deuda ya saldada la devuelve como está, sin volver a cobrar")
    void payingASettledDebtDoesNotChargeAgain() {
        debt.markSettled(CLOSED_AT, 3L, "pay-0");

        PendingPayment paid = service.payDebt(DRIVER, 50L, null, "key-abcdef12");

        assertThat(paid.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Si el barrido la está cobrando, el pago desde el perfil se rechaza sin cobrar")
    void paymentWhileTheSweepChargesIsRejected() {
        when(debts.claim(eq(50L), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.payDebt(DRIVER, 50L, null, "key-abcdef12"))
                .isInstanceOf(DebtPaymentInProgressException.class);

        verify(gateway, never()).charge(anyString(), any(), anyString(), anyString(), anyString());
    }

    /** Un reloj que la prueba mueve a mano. */
    private static final class MovingClock extends Clock {

        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void set(Instant at) {
            this.now = at;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
