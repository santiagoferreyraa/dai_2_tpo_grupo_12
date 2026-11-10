package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.BookingStateConflictException;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopResult;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.service.ChargingPowerPolicy;
import com.ecopedia.charging.session.service.ChargingSettlementPolicy;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator.CompletedSessionData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChargingActivationServiceTest {

    private final ChargingPointCatalog catalog = mock(ChargingPointCatalog.class);
    private final ChargingPowerPolicy powerPolicy = mock(ChargingPowerPolicy.class);
    private final PaymentDirectory paymentDirectory = mock(PaymentDirectory.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final ChargingSettlementPolicy settlementPolicy = mock(ChargingSettlementPolicy.class);
    private final ChargingTransactionalCoordinator coordinator = mock(ChargingTransactionalCoordinator.class);
    private final PhysicalChargerPort chargerPort = mock(PhysicalChargerPort.class);
    private final ChargingSessionRepository sessionRepository = mock(ChargingSessionRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final TariffDirectory tariffDirectory = mock(TariffDirectory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);

    private ChargingActivationService activationService;

    private static final Long CONNECTOR_ID = 10L;
    private static final Long DRIVER_ID = 5L;
    private static final Long BOOKING_ID = 42L;

    @BeforeEach
    void setUp() {
        activationService = new ChargingActivationService(
                catalog,
                powerPolicy,
                paymentDirectory,
                bookingService,
                settlementPolicy,
                coordinator,
                chargerPort,
                sessionRepository,
                bookingRepository,
                tariffDirectory,
                clock);
        stubBooking(BOOKING_ID, DRIVER_ID, CONNECTOR_ID);
    }

    private void stubBooking(Long bookingId, Long ownerDriverId, Long connectorId) {
        stubBooking(bookingId, ownerDriverId, connectorId, true);
    }

    private void stubBooking(Long bookingId, Long ownerDriverId, Long connectorId, boolean confirmed) {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.belongsTo(anyLong())).thenAnswer(inv -> ownerDriverId.equals(inv.getArgument(0)));
        when(booking.getConnectorId()).thenReturn(connectorId);
        when(booking.isConfirmed()).thenReturn(confirmed);
        when(bookingService.getBooking(bookingId)).thenReturn(booking);
    }

    @Nested
    @DisplayName("Flujo de habilitación (Flujo B, RNF06, ECO-60)")
    class ActivationFlow {

        @Test
        @DisplayName("Camino feliz: validaciones externas ok, transacción local confirmada y cargador físico encendido")
        void successfulActivationFlow() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-CENTRO");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("150.00"), PowerDecision.Outcome.FAST_AUTHORIZED));

            when(coordinator.persistLocalActivation(
                            any(), eq(BOOKING_ID), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenAnswer(inv -> ChargingSession.start(
                            inv.getArgument(0),
                            BOOKING_ID,
                            CONNECTOR_ID,
                            DRIVER_ID,
                            80,
                            new BigDecimal("150.00"),
                            clock.instant()));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");
            ActivationResult result = activationService.activate(request, "bearer-token");

            assertThat(result.sessionId()).isNotBlank();
            assertThat(result.status()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(result.powerLimitKw()).isEqualByComparingTo("150.00");

            verify(paymentDirectory).validatePaymentMethodForCharging(eq(DRIVER_ID), isNull());
            verify(chargerPort).activate(eq(CONNECTOR_ID), eq(result.sessionId()), eq(new BigDecimal("150.00")));
            verify(settlementPolicy, never()).failActivation(any(), any());
        }

        @Test
        @DisplayName(
                "Rechazo de distribuidora (SOAP externo): falla antes de la transacción y encola reintegro sin penalización")
        void gridRejectionFailsBeforeLocalTransactionAndRefundsDeposit() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-CENTRO");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(BigDecimal.ZERO, PowerDecision.Outcome.REJECTED_BY_GRID));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(ConnectorNotBookableException.class);

            verify(settlementPolicy).failActivation(BOOKING_ID, ActivationFailureReason.REJECTED_BY_GRID);
            verify(coordinator, never()).persistLocalActivation(any(), any(), any(), any(), anyInt(), any(), any());
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Medio de pago no utilizable (REST externo): rechaza antes de persistir nada local")
        void unusablePaymentMethodRejectsBeforeTransaction() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("22.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("22.00"), PowerDecision.Outcome.STANDARD_CHARGE));
            doThrow(new PaymentMethodInvalidException("Tarjeta rechazada"))
                    .when(paymentDirectory)
                    .validatePaymentMethodForCharging(eq(DRIVER_ID), any());

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(PaymentMethodInvalidException.class);

            verify(coordinator, never()).persistLocalActivation(any(), any(), any(), any(), anyInt(), any(), any());
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Pasarela de pagos o integration no disponible: propaga 503 sin tocar hardware ni DB")
        void paymentServiceUnavailableRejectsBeforeTransaction() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("22.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("22.00"), PowerDecision.Outcome.STANDARD_CHARGE));
            doThrow(new PaymentMethodsUnavailableException(new RuntimeException("timeout")))
                    .when(paymentDirectory)
                    .validatePaymentMethodForCharging(eq(DRIVER_ID), any());

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(PaymentMethodsUnavailableException.class);

            verify(coordinator, never()).persistLocalActivation(any(), any(), any(), any(), anyInt(), any(), any());
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Falla del cargador físico externo: no hace rollback externo sino compensación (RNF06 / RF13)")
        void physicalChargerFailureExecutesCompensation() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("22.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("22.00"), PowerDecision.Outcome.STANDARD_CHARGE));

            when(coordinator.persistLocalActivation(
                            any(), eq(BOOKING_ID), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenAnswer(inv -> ChargingSession.start(
                            inv.getArgument(0),
                            BOOKING_ID,
                            CONNECTOR_ID,
                            DRIVER_ID,
                            80,
                            new BigDecimal("22.00"),
                            clock.instant()));

            doThrow(new PhysicalChargerUnavailableException("El cargador no respondió"))
                    .when(chargerPort)
                    .activate(anyLong(), any(), any());

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(PhysicalChargerUnavailableException.class);

            // Se ejecuta la compensación local: conector liberado y sesión marcada como FAULTED
            verify(coordinator).compensateLocalFailure(eq(CONNECTOR_ID), any(), any());

            // Y la compensación económica de RF13 / ECO-59: reintegro íntegro de la seña sin penalización
            verify(settlementPolicy).failActivation(BOOKING_ID, ActivationFailureReason.CHARGER_UNAVAILABLE);
        }

        @Test
        @DisplayName(
                "Carga sin reserva (walk-in, RF10, ECO-58): registra reserva instantánea y vincula su id en la sesión")
        void walkInSuccessfulActivation() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-CENTRO");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("150.00"), PowerDecision.Outcome.STANDARD_CHARGE));

            Long walkInBookingId = 99L;
            Booking walkInBooking = mock(Booking.class);
            when(walkInBooking.getId()).thenReturn(walkInBookingId);
            when(bookingService.registerWalkIn(CONNECTOR_ID, DRIVER_ID)).thenReturn(walkInBooking);

            when(coordinator.persistLocalActivation(
                            any(), eq(walkInBookingId), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenAnswer(inv -> ChargingSession.start(
                            inv.getArgument(0),
                            walkInBookingId,
                            CONNECTOR_ID,
                            DRIVER_ID,
                            80,
                            new BigDecimal("150.00"),
                            clock.instant()));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "QR-WALKIN");

            ActivationResult result = activationService.activate(request, "token");

            assertThat(result).isNotNull();
            assertThat(result.bookingId()).isEqualTo(walkInBookingId);
            verify(bookingService).registerWalkIn(CONNECTOR_ID, DRIVER_ID);
            verify(coordinator)
                    .persistLocalActivation(
                            any(), eq(walkInBookingId), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any());
            verify(chargerPort).activate(eq(CONNECTOR_ID), any(), eq(new BigDecimal("150.00")));
        }

        @Test
        @DisplayName(
                "Carga sin reserva rechazada por buffer o slot ocupado: no persiste sesión ni activa cargador físico")
        void walkInRejectedWhenBufferOrSlotUnavailable() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-CENTRO");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("150.00"), PowerDecision.Outcome.STANDARD_CHARGE));

            when(bookingService.registerWalkIn(CONNECTOR_ID, DRIVER_ID))
                    .thenThrow(new SlotUnavailableException(CONNECTOR_ID));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "QR-WALKIN");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(SlotUnavailableException.class);

            verify(coordinator, never()).persistLocalActivation(any(), any(), any(), any(), anyInt(), any(), any());
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Carga sin reserva con fallo en cargador físico: compensa y libera la reserva walk-in")
        void walkInPhysicalChargerFailureCompensatesWalkInBooking() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("50.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("50.00"), PowerDecision.Outcome.STANDARD_CHARGE));

            Long walkInBookingId = 88L;
            Booking walkInBooking = mock(Booking.class);
            when(walkInBooking.getId()).thenReturn(walkInBookingId);
            when(bookingService.registerWalkIn(CONNECTOR_ID, DRIVER_ID)).thenReturn(walkInBooking);

            when(coordinator.persistLocalActivation(
                            any(), eq(walkInBookingId), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenAnswer(inv -> ChargingSession.start(
                            inv.getArgument(0),
                            walkInBookingId,
                            CONNECTOR_ID,
                            DRIVER_ID,
                            80,
                            new BigDecimal("50.00"),
                            clock.instant()));

            doThrow(new PhysicalChargerUnavailableException("Cargador desconectado"))
                    .when(chargerPort)
                    .activate(anyLong(), any(), any());

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "QR-WALKIN");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(PhysicalChargerUnavailableException.class);

            verify(coordinator).compensateLocalFailure(eq(CONNECTOR_ID), any(), any());
            verify(settlementPolicy).failActivation(walkInBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
        }

        @Test
        @DisplayName("Carga sin reserva con fallo en transacción local: cancela la reserva walk-in creada")
        void walkInPersistLocalActivationFailureCompensatesWalkInBooking() {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("50.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("50.00"), PowerDecision.Outcome.STANDARD_CHARGE));

            Long walkInBookingId = 77L;
            Booking walkInBooking = mock(Booking.class);
            when(walkInBooking.getId()).thenReturn(walkInBookingId);
            when(bookingService.registerWalkIn(CONNECTOR_ID, DRIVER_ID)).thenReturn(walkInBooking);

            when(coordinator.persistLocalActivation(
                            any(), eq(walkInBookingId), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenThrow(new SlotUnavailableException(CONNECTOR_ID));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "QR-WALKIN");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(SlotUnavailableException.class);

            verify(settlementPolicy).failActivation(walkInBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Reserva ajena: rechaza con BookingNotFoundException antes de cualquier llamada externa")
        void foreignBooking_isRejectedAsNotFound_beforeAnyExternalCall() {
            stubBooking(BOOKING_ID, 999L, CONNECTOR_ID);

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(BookingNotFoundException.class);

            verifyNoInteractions(catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
            verify(settlementPolicy, never()).failActivation(any(), any());
        }

        @Test
        @DisplayName("Reserva ajena con distribuidora configurada para rechazar: no cancela la reserva de otro")
        void foreignBooking_withGridRejection_doesNotCancelOthersBooking() {
            stubBooking(BOOKING_ID, 999L, CONNECTOR_ID);
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-SUR");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(BigDecimal.ZERO, PowerDecision.Outcome.REJECTED_BY_GRID));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(BookingNotFoundException.class);

            verify(settlementPolicy, never()).failActivation(any(), any());
        }

        @Test
        @DisplayName(
                "Reserva propia de otro conector: rechaza con InvalidBookingRequestException antes de llamadas externas")
        void bookingOfAnotherConnector_isRejectedBeforeExternalCalls() {
            stubBooking(BOOKING_ID, DRIVER_ID, 999L);

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(InvalidBookingRequestException.class);

            verifyNoInteractions(catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
            verify(settlementPolicy, never()).failActivation(any(), any());
        }

        @Test
        @DisplayName(
                "Reserva propia no confirmada (IN_PROGRESS o CANCELLED): rechaza antes de llamadas externas sin disparar failActivation")
        void unconfirmedBooking_isRejectedBeforeExternalCalls_andDoesNotFailActivation() {
            stubBooking(BOOKING_ID, DRIVER_ID, CONNECTOR_ID, false);
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("150.00"), "NODO-SUR");
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(BigDecimal.ZERO, PowerDecision.Outcome.REJECTED_BY_GRID));

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "QR123");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(BookingStateConflictException.class);

            verifyNoInteractions(catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
            verify(settlementPolicy, never()).failActivation(any(), any());
        }
    }

    @Nested
    @DisplayName("Flujo C: cierre de sesión, liquidación con descuento de seña y corte automático (ECO-55 / ECO-58)")
    class StopAndSettlementFlow {

        @Test
        @DisplayName("Cierre con reserva donde realCost > seña: cobra solo la diferencia y libera el conector")
        void stopSession_withBookingAndCostExceedingDeposit_chargesDifference() {
            Instant startedAt = clock.instant().minusSeconds(1800);
            ChargingSession active = ChargingSession.start(
                    "sess-1", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), startedAt);
            ChargingSession closed = ChargingSession.start(
                    "sess-1", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), startedAt);
            closed.stop(clock.instant(), new BigDecimal("25.000"));

            when(sessionRepository.findBySessionId("sess-1")).thenReturn(Optional.of(active));
            when(chargerPort.deactivate(CONNECTOR_ID)).thenReturn(Optional.of(new BigDecimal("25.000")));
            when(coordinator.completeLocalSession("sess-1", new BigDecimal("25.000"), clock.instant()))
                    .thenReturn(new CompletedSessionData(
                            closed, new BigDecimal("5000.00"), clock.instant().plusSeconds(1800)));
            when(tariffDirectory.calculateRealCost(
                            CONNECTOR_ID, new BigDecimal("25.000"), new BigDecimal("50.00"), startedAt, 0L))
                    .thenReturn(new BigDecimal("7200.00"));
            when(settlementPolicy.settleCompletedSession(new BigDecimal("5000.00"), new BigDecimal("7200.00")))
                    .thenReturn(new ChargeSettlement(
                            new BigDecimal("5000.00"),
                            new BigDecimal("7200.00"),
                            new BigDecimal("7200.00"),
                            new BigDecimal("2200.00"),
                            BigDecimal.ZERO,
                            ChargeSettlement.Outcome.COMPLETED));
            when(paymentDirectory.chargeConsumption(DRIVER_ID, "sess-1", new BigDecimal("2200.00")))
                    .thenReturn(ConsumptionPaymentStatus.CHARGED);

            SessionStopResult result = activationService.stopSession("sess-1", DRIVER_ID, null);

            assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(result.kwhDelivered()).isEqualByComparingTo("25.000");
            assertThat(result.depositAmount()).isEqualByComparingTo("5000.00");
            assertThat(result.realCost()).isEqualByComparingTo("7200.00");
            assertThat(result.finalBilled()).isEqualByComparingTo("7200.00");
            assertThat(result.additionalCharge()).isEqualByComparingTo("2200.00");
            assertThat(result.paymentStatus()).isEqualTo(ConsumptionPaymentStatus.CHARGED);
            verify(paymentDirectory).chargeConsumption(DRIVER_ID, "sess-1", new BigDecimal("2200.00"));
        }

        @Test
        @DisplayName("Cierre con reserva donde realCost <= seña: aplica piso de seña y no cobra saldo adicional")
        void stopSession_withBookingAndCostWithinDeposit_appliesDepositFloor() {
            Instant startedAt = clock.instant().minusSeconds(600);
            ChargingSession active = ChargingSession.start(
                    "sess-2", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), startedAt);
            ChargingSession closed = ChargingSession.start(
                    "sess-2", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), startedAt);
            closed.stop(clock.instant(), new BigDecimal("4.000"));

            when(sessionRepository.findBySessionId("sess-2")).thenReturn(Optional.of(active));
            when(chargerPort.deactivate(CONNECTOR_ID)).thenReturn(Optional.empty());
            when(coordinator.completeLocalSession("sess-2", new BigDecimal("4.000"), clock.instant()))
                    .thenReturn(new CompletedSessionData(
                            closed, new BigDecimal("5000.00"), clock.instant().plusSeconds(1800)));
            when(tariffDirectory.calculateRealCost(
                            CONNECTOR_ID, new BigDecimal("4.000"), new BigDecimal("22.00"), startedAt, 0L))
                    .thenReturn(new BigDecimal("1800.00"));
            when(settlementPolicy.settleCompletedSession(new BigDecimal("5000.00"), new BigDecimal("1800.00")))
                    .thenReturn(new ChargeSettlement(
                            new BigDecimal("5000.00"),
                            new BigDecimal("1800.00"),
                            new BigDecimal("5000.00"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            ChargeSettlement.Outcome.COMPLETED));

            SessionStopResult result = activationService.stopSession("sess-2", DRIVER_ID, new BigDecimal("4.000"));

            assertThat(result.additionalCharge()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.finalBilled()).isEqualByComparingTo("5000.00");
            assertThat(result.paymentStatus()).isEqualTo(ConsumptionPaymentStatus.NOT_REQUIRED);
            verify(paymentDirectory, never()).chargeConsumption(any(), any(), any());
        }

        @Test
        @DisplayName(
                "Cierre walk-in con cobro fallido: retorna DEBT_RECORDED sin revertir el cierre ni retener conector")
        void stopSession_walkIn_returnsDebtRecordedWhenGatewayDeclines() {
            Instant startedAt = clock.instant().minusSeconds(1200);
            ChargingSession active = ChargingSession.start(
                    "sess-walkin", 77L, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), startedAt);
            ChargingSession closed = ChargingSession.start(
                    "sess-walkin", 77L, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), startedAt);
            closed.stop(clock.instant(), new BigDecimal("10.000"));

            when(sessionRepository.findBySessionId("sess-walkin")).thenReturn(Optional.of(active));
            when(chargerPort.deactivate(CONNECTOR_ID)).thenReturn(Optional.of(new BigDecimal("10.000")));
            when(coordinator.completeLocalSession("sess-walkin", new BigDecimal("10.000"), clock.instant()))
                    .thenReturn(new CompletedSessionData(
                            closed, BigDecimal.ZERO, clock.instant().plusSeconds(600)));
            when(tariffDirectory.calculateRealCost(
                            CONNECTOR_ID, new BigDecimal("10.000"), new BigDecimal("50.00"), startedAt, 0L))
                    .thenReturn(new BigDecimal("2500.00"));
            when(settlementPolicy.settleCompletedSession(BigDecimal.ZERO, new BigDecimal("2500.00")))
                    .thenReturn(new ChargeSettlement(
                            BigDecimal.ZERO,
                            new BigDecimal("2500.00"),
                            new BigDecimal("2500.00"),
                            new BigDecimal("2500.00"),
                            BigDecimal.ZERO,
                            ChargeSettlement.Outcome.COMPLETED));
            when(paymentDirectory.chargeConsumption(DRIVER_ID, "sess-walkin", new BigDecimal("2500.00")))
                    .thenReturn(ConsumptionPaymentStatus.DEBT_RECORDED);

            SessionStopResult result = activationService.stopSession("sess-walkin", DRIVER_ID, null);

            assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(result.additionalCharge()).isEqualByComparingTo("2500.00");
            assertThat(result.paymentStatus()).isEqualTo(ConsumptionPaymentStatus.DEBT_RECORDED);
        }

        @Test
        @DisplayName("Intentar detener sesión ajena o inexistente lanza SessionNotFoundException (SEC-06)")
        void stopSession_foreignOrMissing_throwsSessionNotFound() {
            ChargingSession othersSession = ChargingSession.start(
                    "sess-other", BOOKING_ID, CONNECTOR_ID, 999L, 80, new BigDecimal("50.00"), clock.instant());
            when(sessionRepository.findBySessionId("sess-other")).thenReturn(Optional.of(othersSession));
            when(sessionRepository.findBySessionId("sess-missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> activationService.stopSession("sess-other", DRIVER_ID, null))
                    .isInstanceOf(SessionNotFoundException.class);
            assertThatThrownBy(() -> activationService.stopSession("sess-missing", DRIVER_ID, null))
                    .isInstanceOf(SessionNotFoundException.class);

            verifyNoInteractions(chargerPort, coordinator, tariffDirectory, paymentDirectory);
        }

        @Test
        @DisplayName(
                "Si cargador físico y core/pagos están caídos al cerrar: cierra localmente igual con PENDING_SETTLEMENT")
        void stopSession_whenExternalServicesFail_closesLocallyWithPendingSettlement() {
            Instant startedAt = clock.instant().minusSeconds(600);
            ChargingSession active = ChargingSession.start(
                    "sess-resilient", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), startedAt);
            ChargingSession closed = ChargingSession.start(
                    "sess-resilient", BOOKING_ID, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("22.00"), startedAt);
            closed.stop(clock.instant(), BigDecimal.ZERO);

            when(sessionRepository.findBySessionId("sess-resilient")).thenReturn(Optional.of(active));
            when(chargerPort.deactivate(CONNECTOR_ID))
                    .thenThrow(new PhysicalChargerUnavailableException("Cargador desconectado"));
            when(coordinator.completeLocalSession("sess-resilient", BigDecimal.ZERO, clock.instant()))
                    .thenReturn(new CompletedSessionData(
                            closed, new BigDecimal("3000.00"), clock.instant().plusSeconds(600)));
            when(tariffDirectory.calculateRealCost(any(), any(), any(), any(), anyLong()))
                    .thenThrow(new PricingUnavailableException(new RuntimeException("core down")));

            SessionStopResult result = activationService.stopSession("sess-resilient", DRIVER_ID, null);

            assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(result.paymentStatus()).isEqualTo(ConsumptionPaymentStatus.PENDING_SETTLEMENT);
            verify(coordinator).completeLocalSession("sess-resilient", BigDecimal.ZERO, clock.instant());
        }

        @Test
        @DisplayName("Corte automático (stopExpiredSessions): detiene solo sesiones cuya ventana ya venció")
        void stopExpiredSessions_cutsOnlyExpiredWindows() {
            Instant now = clock.instant();
            Instant expiredEnd = now;
            Instant activeEnd = now.plusSeconds(900);

            ChargingSession expired = ChargingSession.start(
                    "sess-exp", 101L, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), now.minusSeconds(1800));
            ChargingSession stillValid = ChargingSession.start(
                    "sess-ok",
                    102L,
                    CONNECTOR_ID + 1,
                    DRIVER_ID + 1,
                    80,
                    new BigDecimal("50.00"),
                    now.minusSeconds(600));
            ChargingSession expiredClosed = ChargingSession.start(
                    "sess-exp", 101L, CONNECTOR_ID, DRIVER_ID, 80, new BigDecimal("50.00"), now.minusSeconds(1800));
            expiredClosed.stop(now, new BigDecimal("12.000"));

            Booking expiredBooking = new Booking(
                    CONNECTOR_ID,
                    DRIVER_ID,
                    new TimeWindow(now.minusSeconds(1800), expiredEnd),
                    now.minusSeconds(1800));
            Booking validBooking = new Booking(
                    CONNECTOR_ID + 1,
                    DRIVER_ID + 1,
                    new TimeWindow(now.minusSeconds(600), activeEnd),
                    now.minusSeconds(600));

            when(sessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of(expired, stillValid));
            when(bookingRepository.findById(101L)).thenReturn(Optional.of(expiredBooking));
            when(bookingRepository.findById(102L)).thenReturn(Optional.of(validBooking));
            when(chargerPort.deactivate(CONNECTOR_ID)).thenReturn(Optional.of(new BigDecimal("12.000")));
            when(coordinator.completeLocalSession("sess-exp", new BigDecimal("12.000"), now))
                    .thenReturn(new CompletedSessionData(expiredClosed, BigDecimal.ZERO, expiredEnd));
            when(tariffDirectory.calculateRealCost(
                            CONNECTOR_ID,
                            new BigDecimal("12.000"),
                            new BigDecimal("50.00"),
                            now.minusSeconds(1800),
                            0L))
                    .thenReturn(new BigDecimal("2400.00"));
            when(settlementPolicy.settleCompletedSession(BigDecimal.ZERO, new BigDecimal("2400.00")))
                    .thenReturn(new ChargeSettlement(
                            BigDecimal.ZERO,
                            new BigDecimal("2400.00"),
                            new BigDecimal("2400.00"),
                            new BigDecimal("2400.00"),
                            BigDecimal.ZERO,
                            ChargeSettlement.Outcome.COMPLETED));
            when(paymentDirectory.chargeConsumption(DRIVER_ID, "sess-exp", new BigDecimal("2400.00")))
                    .thenReturn(ConsumptionPaymentStatus.CHARGED);

            int stopped = activationService.stopExpiredSessions();

            assertThat(stopped).isEqualTo(1);
            verify(coordinator).completeLocalSession("sess-exp", new BigDecimal("12.000"), now);
            verify(coordinator, never()).completeLocalSession(eq("sess-ok"), any(), any());
        }
    }
}
