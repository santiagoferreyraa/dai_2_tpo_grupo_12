package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargerReading;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionNotFoundException;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.ConsumptionPaymentUnavailableException;
import com.ecopedia.charging.session.domain.ConsumptionPayments;
import com.ecopedia.charging.session.domain.ConsumptionPricing;
import com.ecopedia.charging.session.domain.ConsumptionPricingUnavailableException;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import com.ecopedia.charging.session.service.ChargingSessionServiceImpl;
import com.ecopedia.charging.session.service.ChargingSettlementPolicy;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Detener una carga (RF11, RF14, RNF06, ECO-44): el orden apagar → cerrar → liquidar → cobrar, y que
 * nada externo deje el conector tomado.
 */
class ChargingSessionServiceTest {

    private static final Long DRIVER_ID = 5L;
    private static final Long CONNECTOR_ID = 10L;
    private static final Long BOOKING_ID = 42L;
    private static final Instant START = Instant.parse("2026-11-02T15:00:00Z");

    private final ChargingSessionRepository sessionRepository = mock(ChargingSessionRepository.class);
    private final PhysicalChargerPort chargerPort = mock(PhysicalChargerPort.class);
    private final ChargingTransactionalCoordinator coordinator = mock(ChargingTransactionalCoordinator.class);
    private final ConsumptionPricing pricing = mock(ConsumptionPricing.class);
    private final ConsumptionPayments payments = mock(ConsumptionPayments.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final Clock clock = Clock.fixed(START.plusSeconds(1800), ZoneOffset.UTC);

    private ChargingSessionServiceImpl service;
    private ChargingSession session;

    @BeforeEach
    void setUp() {
        // La regla de liquidación es la de verdad: es pura y es justamente lo que se quiere ver aplicado.
        ChargingSettlementPolicy settlementPolicy = new ChargingSettlementPolicy(bookingService, null);
        service = new ChargingSessionServiceImpl(
                sessionRepository,
                chargerPort,
                coordinator,
                settlementPolicy,
                pricing,
                payments,
                bookingService,
                clock);

        session = ChargingSession.start("sess-1", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50"), START);
        when(sessionRepository.findBySessionId("sess-1")).thenReturn(Optional.of(session));

        Booking booking = mock(Booking.class);
        when(booking.getDepositAmount()).thenReturn(new BigDecimal("3000.00"));
        when(bookingService.getBooking(BOOKING_ID)).thenReturn(booking);

        // El cierre local hace lo mismo que la entidad, sin base: cierra y devuelve la sesión.
        when(coordinator.persistLocalCompletion(eq("sess-1"), any(), any(), any()))
                .thenAnswer(inv -> {
                    session.complete(inv.getArgument(3), inv.getArgument(1), inv.getArgument(2));
                    return Optional.of(session);
                });
        when(coordinator.recordSettlement(eq("sess-1"), any(), any())).thenAnswer(inv -> {
            ChargeSettlement settlement = inv.getArgument(1);
            SessionPaymentStatus status = inv.getArgument(2);
            if (settlement != null) session.recordSettlement(settlement);
            if (status != null) session.recordPayment(status);
            return session;
        });
    }

    @Test
    @DisplayName("Consumo mayor que la seña: apaga, cierra, liquida y cobra la diferencia")
    void stopChargesDifferenceOverDeposit() {
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("20.000"), new BigDecimal("76"))));
        when(pricing.costOf(CONNECTOR_ID, new BigDecimal("20.000"), START)).thenReturn(new BigDecimal("7000.00"));
        when(payments.charge(eq(DRIVER_ID), any(), eq("session-sess-1"))).thenReturn(SessionPaymentStatus.CHARGED);

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.endReason()).isEqualTo(SessionEndReason.DRIVER_STOPPED);
        assertThat(view.kwhDelivered()).isEqualByComparingTo("20");
        assertThat(view.realCost()).isEqualByComparingTo("7000.00");
        assertThat(view.finalAmount()).isEqualByComparingTo("7000.00");
        assertThat(view.amountToCharge()).isEqualByComparingTo("4000.00");
        assertThat(view.paymentStatus()).isEqualTo(SessionPaymentStatus.CHARGED);

        ArgumentCaptor<BigDecimal> charged = ArgumentCaptor.forClass(BigDecimal.class);
        verify(payments).charge(eq(DRIVER_ID), charged.capture(), eq("session-sess-1"));
        assertThat(charged.getValue()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Consumo menor que la seña: la seña es el piso y no se cobra nada más")
    void depositIsTheFloor() {
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("2"), null)));
        when(pricing.costOf(any(), any(), any())).thenReturn(new BigDecimal("700.00"));

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.finalAmount()).isEqualByComparingTo("3000.00");
        assertThat(view.paymentStatus()).isEqualTo(SessionPaymentStatus.NOT_REQUIRED);
        verifyNoInteractions(payments);
    }

    @Test
    @DisplayName("Carga sin reserva: no hay seña ni piso, se cobra el consumo entero")
    void walkInHasNoFloor() {
        Booking walkIn = mock(Booking.class);
        when(walkIn.getDepositAmount()).thenReturn(BigDecimal.ZERO);
        when(bookingService.getBooking(BOOKING_ID)).thenReturn(walkIn);
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("2"), null)));
        when(pricing.costOf(any(), any(), any())).thenReturn(new BigDecimal("700.00"));
        when(payments.charge(any(), any(), any())).thenReturn(SessionPaymentStatus.CHARGED);

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.finalAmount()).isEqualByComparingTo("700.00");
        assertThat(view.amountToCharge()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("El cargador no contesta al apagar: la sesión se cierra igual y el conector se libera (RNF06)")
    void chargerDownStillCloses() {
        when(chargerPort.deactivate(CONNECTOR_ID)).thenThrow(new PhysicalChargerUnavailableException("timeout"));
        when(pricing.costOf(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.endReason()).isEqualTo(SessionEndReason.DRIVER_STOPPED);
        verify(coordinator).persistLocalCompletion(eq("sess-1"), isNull(), eq(SessionEndReason.DRIVER_STOPPED), any());
    }

    @Test
    @DisplayName("Tarificación no contesta: cerrada igual, con el cobro pendiente y sin llamar a Pagos")
    void pricingDownLeavesPaymentPending() {
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("12"), null)));
        when(pricing.costOf(any(), any(), any()))
                .thenThrow(new ConsumptionPricingUnavailableException(new RuntimeException("core caído")));

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.paymentStatus()).isEqualTo(SessionPaymentStatus.PENDING);
        verifyNoInteractions(payments);
    }

    @Test
    @DisplayName("Pagos no contesta: cerrada igual, con el cobro pendiente")
    void paymentsDownLeavesPaymentPending() {
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("30"), null)));
        when(pricing.costOf(any(), any(), any())).thenReturn(new BigDecimal("10000.00"));
        when(payments.charge(any(), any(), any()))
                .thenThrow(new ConsumptionPaymentUnavailableException(new RuntimeException("timeout")));

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.paymentStatus()).isEqualTo(SessionPaymentStatus.PENDING);
        assertThat(view.amountToCharge()).isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("La pasarela rechaza: queda como deuda, y la sesión cerrada")
    void declinedBecomesDebt() {
        when(chargerPort.deactivate(CONNECTOR_ID))
                .thenReturn(Optional.of(new ChargerReading(new BigDecimal("30"), null)));
        when(pricing.costOf(any(), any(), any())).thenReturn(new BigDecimal("10000.00"));
        when(payments.charge(any(), any(), any())).thenReturn(SessionPaymentStatus.DEBT_REGISTERED);

        assertThat(service.stop("sess-1", DRIVER_ID).paymentStatus()).isEqualTo(SessionPaymentStatus.DEBT_REGISTERED);
    }

    @Test
    @DisplayName("Detener una sesión ya terminada devuelve cómo terminó, sin apagar ni cobrar de nuevo")
    void stopIsIdempotent() {
        session.complete(START.plusSeconds(600), null, SessionEndReason.TARGET_REACHED);

        ChargingSessionView view = service.stop("sess-1", DRIVER_ID);

        assertThat(view.endReason()).isEqualTo(SessionEndReason.TARGET_REACHED);
        verifyNoInteractions(chargerPort, pricing, payments);
        verify(coordinator, never()).persistLocalCompletion(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Otro cierre se cruzó y confirmó primero: no se liquida dos veces")
    void concurrentCloseDoesNotSettleTwice() {
        when(chargerPort.deactivate(anyLong())).thenReturn(Optional.empty());
        when(coordinator.persistLocalCompletion(eq("sess-1"), any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("versión vieja"));

        service.stop("sess-1", DRIVER_ID);

        verifyNoInteractions(pricing, payments);
    }

    @Test
    @DisplayName("La sesión de otro conductor es el mismo 'no encontrada' que una que no existe")
    void foreignSessionIsNotFound() {
        assertThatThrownBy(() -> service.stop("sess-1", 999L)).isInstanceOf(ChargingSessionNotFoundException.class);
        assertThatThrownBy(() -> service.getSession("sess-1", 999L))
                .isInstanceOf(ChargingSessionNotFoundException.class);
        verifyNoInteractions(chargerPort);
    }
}
