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
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.service.ChargingPowerPolicy;
import com.ecopedia.charging.session.service.ChargingSettlementPolicy;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChargingActivationServiceTest {

    private final ChargingPointCatalog catalog = mock(ChargingPointCatalog.class);
    private final ChargingPowerPolicy powerPolicy = mock(ChargingPowerPolicy.class);
    private final PaymentDirectory paymentDirectory = mock(PaymentDirectory.class);
    private final ChargingSettlementPolicy settlementPolicy = mock(ChargingSettlementPolicy.class);
    private final ChargingTransactionalCoordinator coordinator = mock(ChargingTransactionalCoordinator.class);
    private final PhysicalChargerPort chargerPort = mock(PhysicalChargerPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC);

    private ChargingActivationService activationService;

    private static final Long CONNECTOR_ID = 10L;
    private static final Long DRIVER_ID = 5L;
    private static final Long BOOKING_ID = 42L;

    @BeforeEach
    void setUp() {
        activationService = new ChargingActivationService(
                catalog, powerPolicy, paymentDirectory, settlementPolicy, coordinator, chargerPort, clock);
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
    }
}
