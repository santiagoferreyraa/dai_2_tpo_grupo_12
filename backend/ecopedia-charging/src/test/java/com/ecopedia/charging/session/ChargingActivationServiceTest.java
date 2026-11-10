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
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.session.domain.AccessCodeDirectory;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.DriverAlreadyChargingException;
import com.ecopedia.charging.session.domain.InvalidAccessCodeException;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.TooManyAccessCodeAttemptsException;
import com.ecopedia.charging.session.service.AccessCodeAttemptLimiter;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.service.ChargingPowerPolicy;
import com.ecopedia.charging.session.service.ChargingSettlementPolicy;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChargingActivationServiceTest {

    private final AccessCodeDirectory accessCodes = mock(AccessCodeDirectory.class);
    private final ChargingSessionRepository sessionRepository = mock(ChargingSessionRepository.class);
    private final ChargingPointCatalog catalog = mock(ChargingPointCatalog.class);
    private final ChargingPowerPolicy powerPolicy = mock(ChargingPowerPolicy.class);
    private final PaymentDirectory paymentDirectory = mock(PaymentDirectory.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final ChargingSettlementPolicy settlementPolicy = mock(ChargingSettlementPolicy.class);
    private final ChargingTransactionalCoordinator coordinator = mock(ChargingTransactionalCoordinator.class);
    private final PhysicalChargerPort chargerPort = mock(PhysicalChargerPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);

    private AccessCodeAttemptLimiter attemptLimiter;
    private ChargingActivationService activationService;

    private static final Long CONNECTOR_ID = 10L;
    private static final Long DRIVER_ID = 5L;
    private static final Long BOOKING_ID = 42L;

    @BeforeEach
    void setUp() {
        attemptLimiter = new AccessCodeAttemptLimiter(3, Duration.ofMinutes(10), clock);
        activationService = new ChargingActivationService(
                accessCodes,
                attemptLimiter,
                sessionRepository,
                catalog,
                powerPolicy,
                paymentDirectory,
                bookingService,
                settlementPolicy,
                coordinator,
                chargerPort,
                clock);
        stubBooking(BOOKING_ID, DRIVER_ID, CONNECTOR_ID);
        // El código de la terminal de todos los pedidos de abajo abre el conector CONNECTOR_ID.
        when(accessCodes.findConnectorId("123456")).thenReturn(Optional.of(CONNECTOR_ID));
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
        // La ventana corre desde 10 minutos antes del reloj de la prueba hasta 50 después.
        Instant now = clock.instant();
        when(booking.getWindow()).thenReturn(new TimeWindow(now.minusSeconds(600), now.plusSeconds(3000)));
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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");
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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request =
                    new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "ecopedia:connector:123456");

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

            ActivationRequest request =
                    new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "ecopedia:connector:123456");

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

            ActivationRequest request =
                    new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "ecopedia:connector:123456");

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

            ActivationRequest request =
                    new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "ecopedia:connector:123456");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(SlotUnavailableException.class);

            verify(settlementPolicy).failActivation(walkInBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            verify(chargerPort, never()).activate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Reserva ajena: rechaza con BookingNotFoundException antes de cualquier llamada externa")
        void foreignBooking_isRejectedAsNotFound_beforeAnyExternalCall() {
            stubBooking(BOOKING_ID, 999L, CONNECTOR_ID);

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(BookingNotFoundException.class);

            verify(settlementPolicy, never()).failActivation(any(), any());
        }

        @Test
        @DisplayName(
                "Reserva propia de otro conector: rechaza con InvalidBookingRequestException antes de llamadas externas")
        void bookingOfAnotherConnector_isRejectedBeforeExternalCalls() {
            stubBooking(BOOKING_ID, DRIVER_ID, 999L);

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

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

            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, BOOKING_ID, 80, "123456");

            assertThatThrownBy(() -> activationService.activate(request, "token"))
                    .isInstanceOf(InvalidBookingRequestException.class);

            verifyNoInteractions(catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
            verify(settlementPolicy, never()).failActivation(any(), any());
        }
    }

    @Nested
    @DisplayName("Escanear: el código de la terminal acredita la presencia (RF11, ECO-44)")
    class AccessCode {

        private void stubHappyPath(Long bookingId) {
            ChargingPoint point = new ChargingPoint(CONNECTOR_ID, 1L, new BigDecimal("22.00"), null);
            when(catalog.find(CONNECTOR_ID)).thenReturn(Optional.of(point));
            when(powerPolicy.decide(point))
                    .thenReturn(new PowerDecision(new BigDecimal("22.00"), PowerDecision.Outcome.STANDARD_CHARGE));
            when(coordinator.persistLocalActivation(
                            any(), eq(bookingId), eq(CONNECTOR_ID), eq(DRIVER_ID), eq(80), any(), any()))
                    .thenAnswer(inv -> ChargingSession.start(
                            inv.getArgument(0),
                            bookingId,
                            CONNECTOR_ID,
                            DRIVER_ID,
                            80,
                            new BigDecimal("22.00"),
                            clock.instant()));
        }

        @Test
        @DisplayName("Sin decir el conector: el código lo resuelve y la carga arranca ahí")
        void codeAloneResolvesConnector() {
            stubHappyPath(BOOKING_ID);

            ActivationResult result =
                    activationService.activate(new ActivationRequest(null, DRIVER_ID, BOOKING_ID, 80, "123456"), "t");

            assertThat(result.connectorId()).isEqualTo(CONNECTOR_ID);
            verify(chargerPort).activate(eq(CONNECTOR_ID), any(), any());
        }

        @Test
        @DisplayName("Un código mal escrito no llega a core ni a nada externo")
        void malformedCodeIsRejectedLocally() {
            ActivationRequest request = new ActivationRequest(null, DRIVER_ID, null, 80, "12-34");

            assertThatThrownBy(() -> activationService.activate(request, "t"))
                    .isInstanceOf(InvalidAccessCodeException.class);

            verifyNoInteractions(accessCodes, catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
        }

        @Test
        @DisplayName("Un código que no es de nadie se rechaza sin tocar reservas ni el cargador")
        void unknownCodeIsRejected() {
            when(accessCodes.findConnectorId("999999")).thenReturn(Optional.empty());
            ActivationRequest request = new ActivationRequest(null, DRIVER_ID, null, 80, "999999");

            assertThatThrownBy(() -> activationService.activate(request, "t"))
                    .isInstanceOf(InvalidAccessCodeException.class);

            verifyNoInteractions(catalog, powerPolicy, paymentDirectory, coordinator, chargerPort);
            verify(bookingService, never()).registerWalkIn(any(), any());
        }

        @Test
        @DisplayName("El código de la terminal de al lado: rechazado aunque sea un código válido")
        void codeOfAnotherConnectorIsRejected() {
            when(accessCodes.findConnectorId("654321")).thenReturn(Optional.of(77L));
            ActivationRequest request = new ActivationRequest(CONNECTOR_ID, DRIVER_ID, null, 80, "654321");

            assertThatThrownBy(() -> activationService.activate(request, "t"))
                    .isInstanceOf(InvalidAccessCodeException.class);

            verifyNoInteractions(catalog, coordinator, chargerPort);
        }

        @Test
        @DisplayName("Después de errar el tope de veces, ni el código correcto pasa hasta que termine la espera")
        void tooManyFailuresLockTheDriverOut() {
            when(accessCodes.findConnectorId("000000")).thenReturn(Optional.empty());
            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> activationService.activate(
                                new ActivationRequest(null, DRIVER_ID, null, 80, "000000"), "t"))
                        .isInstanceOf(InvalidAccessCodeException.class);
            }

            assertThatThrownBy(() ->
                            activationService.activate(new ActivationRequest(null, DRIVER_ID, null, 80, "123456"), "t"))
                    .isInstanceOf(TooManyAccessCodeAttemptsException.class);
            verify(accessCodes, never()).findConnectorId("123456");
        }

        @Test
        @DisplayName("Con una sesión activa no se inicia otra")
        void driverAlreadyChargingIsRejected() {
            ChargingSession active =
                    ChargingSession.start("ses-1", 1L, 3L, DRIVER_ID, 80, new BigDecimal("22"), clock.instant());
            when(sessionRepository.findActiveByDriverId(DRIVER_ID)).thenReturn(Optional.of(active));

            assertThatThrownBy(() ->
                            activationService.activate(new ActivationRequest(null, DRIVER_ID, null, 80, "123456"), "t"))
                    .isInstanceOf(DriverAlreadyChargingException.class);

            verifyNoInteractions(catalog, coordinator, chargerPort);
        }

        @Test
        @DisplayName("Sin elegir reserva, si tiene una para este conector y en hora, la usa en vez de un walk-in")
        void ownBookingIsFoundWithoutBeingChosen() {
            Booking own = mock(Booking.class);
            when(own.getId()).thenReturn(BOOKING_ID);
            when(bookingService.findChargeableBooking(eq(DRIVER_ID), eq(CONNECTOR_ID), any()))
                    .thenReturn(Optional.of(own));
            stubHappyPath(BOOKING_ID);

            ActivationResult result =
                    activationService.activate(new ActivationRequest(null, DRIVER_ID, null, 80, "123456"), "t");

            assertThat(result.bookingId()).isEqualTo(BOOKING_ID);
            verify(bookingService, never()).registerWalkIn(any(), any());
        }

        @Test
        @DisplayName("Una reserva que todavía no empezó no habilita la carga")
        void bookingNotStartedYetIsRejected() {
            Booking future = mock(Booking.class);
            Instant now = clock.instant();
            when(future.getId()).thenReturn(BOOKING_ID);
            when(future.belongsTo(DRIVER_ID)).thenReturn(true);
            when(future.getConnectorId()).thenReturn(CONNECTOR_ID);
            when(future.isConfirmed()).thenReturn(true);
            when(future.getWindow()).thenReturn(new TimeWindow(now.plusSeconds(3600), now.plusSeconds(7200)));
            when(bookingService.getBooking(BOOKING_ID)).thenReturn(future);

            assertThatThrownBy(() -> activationService.activate(
                            new ActivationRequest(null, DRIVER_ID, BOOKING_ID, 80, "123456"), "t"))
                    .isInstanceOf(InvalidBookingRequestException.class)
                    .hasMessageContaining("todavía no empezó");

            verifyNoInteractions(catalog, coordinator, chargerPort);
        }
    }
}
