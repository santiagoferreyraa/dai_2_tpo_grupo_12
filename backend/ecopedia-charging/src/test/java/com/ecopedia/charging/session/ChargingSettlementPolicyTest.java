package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.booking.domain.DepositStatus;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargeSettlement.Outcome;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.service.ChargingSettlementPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * La regla de falla en la habilitación y liquidación sin piso de seña (RF13, RF14, ECO-59).
 */
class ChargingSettlementPolicyTest {

    private static final Instant START = Instant.parse("2026-11-02T18:00:00Z");

    private BookingService bookingService;
    private ConnectorCatalog connectorCatalog;
    private ChargingSettlementPolicy policy;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        connectorCatalog = mock(ConnectorCatalog.class);
        policy = new ChargingSettlementPolicy(bookingService, connectorCatalog);
    }

    private static Booking bookingWithDeposit(BigDecimal depositAmount) {
        Booking booking = new Booking(
                7L,
                15L,
                new TimeWindow(START, START.plusSeconds(3600)),
                START.minusSeconds(7200),
                new BookingDeposit(depositAmount, 99L, "4242"),
                START.minusSeconds(5 * 3600),
                UUID.randomUUID(),
                "checkout-key-eco59");
        ReflectionTestUtils.setField(booking, "id", 50L);
        return booking;
    }

    @Test
    @DisplayName("Conector fuera de servicio al presentarse el conductor se detecta como falla en la habilitación")
    void detectsOutOfServiceConnector() {
        when(connectorCatalog.findConnector(7L))
                .thenReturn(Optional.of(new ConnectorSnapshot(7L, 3L, "OUT_OF_SERVICE")));
        PowerDecision authorized = new PowerDecision(new BigDecimal("150"), PowerDecision.Outcome.FAST_AUTHORIZED);

        assertThat(policy.checkActivation(7L, authorized)).contains(ActivationFailureReason.CONNECTOR_OUT_OF_SERVICE);
    }

    @Test
    @DisplayName("Rechazo de la distribuidora con tope 0 se detecta como falla en la habilitación")
    void detectsGridRejection() {
        ConnectorSnapshot available = new ConnectorSnapshot(7L, 3L, "AVAILABLE");
        PowerDecision rejected = new PowerDecision(BigDecimal.ZERO, PowerDecision.Outcome.REJECTED_BY_GRID);

        assertThat(policy.checkActivation(available, rejected)).contains(ActivationFailureReason.REJECTED_BY_GRID);
    }

    @Test
    @DisplayName("Potencia limitada por la red o caída del servicio eléctrico NO son fallas en la habilitación")
    void limitedPowerOrGridDownAreNotActivationFailures() {
        ConnectorSnapshot available = new ConnectorSnapshot(7L, 3L, "AVAILABLE");
        PowerDecision limited = new PowerDecision(new BigDecimal("50"), PowerDecision.Outcome.LIMITED_BY_GRID);
        PowerDecision gridDown = new PowerDecision(new BigDecimal("22"), PowerDecision.Outcome.GRID_UNAVAILABLE);

        assertThat(policy.checkActivation(available, limited)).isEmpty();
        assertThat(policy.checkActivation(available, gridDown)).isEmpty();
    }

    @Test
    @DisplayName(
            "Falla en la habilitación (conector fuera de servicio, cargador sin respuesta o rechazo de red) libera la reserva y reintegra toda la seña")
    void failActivationReleasesBookingAndRefundsFullDeposit() {
        for (ActivationFailureReason reason : ActivationFailureReason.values()) {
            Booking booking = bookingWithDeposit(new BigDecimal("8000"));
            booking.failActivation();
            when(bookingService.releaseForActivationFailure(50L)).thenReturn(booking);

            ChargeSettlement settlement = policy.failActivation(50L, reason);

            assertThat(settlement.outcome()).isEqualTo(Outcome.FAILED_AT_ACTIVATION);
            assertThat(settlement.depositAmount()).isEqualByComparingTo("8000");
            assertThat(settlement.realCost()).isEqualByComparingTo("0");
            assertThat(settlement.finalBilledAmount()).isEqualByComparingTo("0");
            assertThat(settlement.amountToCharge()).isEqualByComparingTo("0");
            assertThat(settlement.amountToRefund()).isEqualByComparingTo("8000");
            assertThat(settlement.requiresRefund()).isTrue();
            assertThat(settlement.requiresAdditionalCharge()).isFalse();
        }
    }

    @Test
    @DisplayName(
            "Falla con la carga en curso y consumo menor que la seña: liquida el consumo real sin piso y reintegra la diferencia")
    void midSessionFaultWithConsumptionBelowDepositRefundsDifference() {
        Booking booking = bookingWithDeposit(new BigDecimal("8000"));
        booking.settleFaultedCharge(new BigDecimal("3200"), START.plusSeconds(1200));
        when(bookingService.settleFaultedBooking(50L, new BigDecimal("3200"))).thenReturn(booking);

        ChargeSettlement settlement = policy.settleFaultedSession(50L, new BigDecimal("3200"));

        verify(bookingService).settleFaultedBooking(50L, new BigDecimal("3200"));
        assertThat(settlement.outcome()).isEqualTo(Outcome.INTERRUPTED_BY_FAULT);
        assertThat(settlement.depositAmount()).isEqualByComparingTo("8000");
        assertThat(settlement.realCost()).isEqualByComparingTo("3200");
        assertThat(settlement.finalBilledAmount()).isEqualByComparingTo("3200");
        assertThat(settlement.amountToRefund()).isEqualByComparingTo("4800");
        assertThat(settlement.amountToCharge()).isEqualByComparingTo("0");
        assertThat(settlement.requiresRefund()).isTrue();
        assertThat(settlement.requiresAdditionalCharge()).isFalse();
    }

    @Test
    @DisplayName("Falla en curso sin consumo acumulado equivale a falla en la habilitación y reintegra el 100%")
    void midSessionFaultWithZeroConsumptionRefundsFullDeposit() {
        Booking booking = bookingWithDeposit(new BigDecimal("8000"));
        booking.settleFaultedCharge(BigDecimal.ZERO, START.plusSeconds(60));
        when(bookingService.settleFaultedBooking(50L, BigDecimal.ZERO)).thenReturn(booking);

        ChargeSettlement settlement = policy.settleFaultedSession(50L, BigDecimal.ZERO);

        assertThat(settlement.outcome()).isEqualTo(Outcome.FAILED_AT_ACTIVATION);
        assertThat(settlement.amountToRefund()).isEqualByComparingTo("8000");
        assertThat(settlement.finalBilledAmount()).isEqualByComparingTo("0");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getDepositStatus()).isEqualTo(DepositStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("Falla con la carga en curso y consumo mayor que la seña: cobra solo el excedente y no reintegra")
    void midSessionFaultWithConsumptionAboveDepositChargesExcessOnly() {
        Booking booking = bookingWithDeposit(new BigDecimal("8000"));
        booking.settleFaultedCharge(new BigDecimal("11500"), START.plusSeconds(2400));
        when(bookingService.settleFaultedBooking(50L, new BigDecimal("11500"))).thenReturn(booking);

        ChargeSettlement settlement = policy.settleFaultedSession(50L, new BigDecimal("11500"));

        assertThat(settlement.outcome()).isEqualTo(Outcome.INTERRUPTED_BY_FAULT);
        assertThat(settlement.finalBilledAmount()).isEqualByComparingTo("11500");
        assertThat(settlement.amountToCharge()).isEqualByComparingTo("3500");
        assertThat(settlement.amountToRefund()).isEqualByComparingTo("0");
        assertThat(settlement.requiresRefund()).isFalse();
        assertThat(settlement.requiresAdditionalCharge()).isTrue();
    }

    @Test
    @DisplayName(
            "Carga completada normalmente (RF14): la seña opera como piso de facturación y nunca reintegra diferencia")
    void completedSessionAppliesDepositFloorWithNoRefund() {
        ChargeSettlement belowFloor = policy.settleCompletedSession(new BigDecimal("8000"), new BigDecimal("3200"));

        assertThat(belowFloor.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(belowFloor.finalBilledAmount()).isEqualByComparingTo("8000");
        assertThat(belowFloor.amountToCharge()).isEqualByComparingTo("0");
        assertThat(belowFloor.amountToRefund()).isEqualByComparingTo("0");
        assertThat(belowFloor.requiresRefund()).isFalse();

        ChargeSettlement aboveFloor = policy.settleCompletedSession(new BigDecimal("8000"), new BigDecimal("10500"));

        assertThat(aboveFloor.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(aboveFloor.finalBilledAmount()).isEqualByComparingTo("10500");
        assertThat(aboveFloor.amountToCharge()).isEqualByComparingTo("2500");
        assertThat(aboveFloor.amountToRefund()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> policy.settleCompletedSession(new BigDecimal("8000"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
