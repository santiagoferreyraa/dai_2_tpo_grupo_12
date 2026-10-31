package com.ecopedia.charging.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.CardDeclinedException;
import com.ecopedia.charging.checkout.domain.CheckoutBookingRequest;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import com.ecopedia.charging.checkout.domain.DepositCharge;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyRequiredException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyReusedException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas unitarias para el patrón Facade de Checkout (ECO-35, BookingCheckoutFacadeImpl).
 *
 * <p>Verifica que la fachada coordine los subsistemas en el orden correcto, que ninguna reserva se
 * confirme si falla una validación previa o el cobro de la seña (ECO-53), que una seña cobrada sin
 * reserva se devuelva, y que un pedido repetido con la misma clave no cobre dos veces (QA-29).
 */
class BookingCheckoutFacadeImplTest {

    private static final Long DRIVER_ID = 101L;
    private static final Long CONNECTOR_ID = 42L;
    private static final String AUTH_TOKEN = "test-jwt-token";
    private static final String KEY = "9b2f4c1e-checkout";
    private static final BigDecimal DEPOSIT = new BigDecimal("1500.00");

    private BookingService bookingService;
    private TariffDirectory tariffDirectory;
    private PaymentDirectory paymentDirectory;
    private DepositGateway depositGateway;

    private BookingCheckoutFacadeImpl facade;

    private UUID holdId;
    private Instant now;
    private Hold validHold;
    private CheckoutBookingRequest request;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        tariffDirectory = mock(TariffDirectory.class);
        paymentDirectory = mock(PaymentDirectory.class);
        depositGateway = mock(DepositGateway.class);

        now = Instant.parse("2026-09-28T15:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        facade =
                new BookingCheckoutFacadeImpl(bookingService, tariffDirectory, paymentDirectory, depositGateway, clock);

        holdId = UUID.randomUUID();
        TimeWindow window = new TimeWindow(now.plusSeconds(3600 * 8), now.plusSeconds(3600 * 9));
        validHold = new Hold(holdId, CONNECTOR_ID, DRIVER_ID, window, now.plusSeconds(600));
        request = new CheckoutBookingRequest(holdId, 1L, true);

        when(bookingService.findByCheckoutKey(DRIVER_ID, KEY)).thenReturn(Optional.empty());
    }

    /** Las validaciones previas en orden, y un cobro que sale. */
    private void readyToCharge(BigDecimal deposit) {
        when(bookingService.findHold(holdId)).thenReturn(Optional.of(validHold));
        when(paymentDirectory.hasUsablePaymentMethod(DRIVER_ID, AUTH_TOKEN)).thenReturn(true);
        when(tariffDirectory.calculateDeposit(CONNECTOR_ID)).thenReturn(deposit);
        when(depositGateway.charge(anyLong(), any(), any(), any(), any())).thenReturn(new DepositCharge(9L, "4242"));
    }

    private Booking bookingWith(BookingDeposit deposit, UUID confirmedHold) {
        return new Booking(
                CONNECTOR_ID,
                DRIVER_ID,
                validHold.window(),
                now,
                deposit,
                validHold.window().start().minusSeconds(5 * 3600),
                confirmedHold,
                KEY);
    }

    private void neverConfirmed() {
        verify(bookingService, never()).confirmBooking(any(), any(), any(), any());
    }

    @Nested
    class TheHappyPath {

        @Test
        @DisplayName("Cobra la seña con la tarjeta elegida y recién después confirma la reserva")
        void chargesTheDepositThenConfirms() {
            readyToCharge(DEPOSIT);
            BookingDeposit paid = new BookingDeposit(DEPOSIT, 9L, "4242");
            Booking confirmed = bookingWith(paid, holdId);
            when(bookingService.confirmBooking(holdId, DRIVER_ID, paid, KEY)).thenReturn(confirmed);

            CheckoutResult result = facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN);

            assertThat(result.booking()).isEqualTo(confirmed);
            assertThat(result.depositAmount()).isEqualByComparingTo("1500.00");
            assertThat(result.paymentSummary()).contains("1500.00").contains("4242");
            // La clave del cobro sale de la del checkout: un reintento del pedido no cobra dos veces.
            verify(depositGateway).charge(DRIVER_ID, 1L, DEPOSIT, "hold-" + holdId, "deposit-" + DRIVER_ID + "-" + KEY);
            verify(bookingService).confirmBooking(holdId, DRIVER_ID, paid, KEY);
        }

        @Test
        @DisplayName("Con seña 0 (conector sin tarifa) confirma sin pasar por la pasarela")
        void withoutDepositDoesNotCharge() {
            readyToCharge(BigDecimal.ZERO);
            Booking confirmed = bookingWith(BookingDeposit.none(), holdId);
            when(bookingService.confirmBooking(holdId, DRIVER_ID, BookingDeposit.none(), KEY))
                    .thenReturn(confirmed);

            CheckoutResult result = facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN);

            assertThat(result.depositAmount()).isEqualByComparingTo("0");
            assertThat(result.paymentSummary()).isEqualTo("Sin seña");
            verifyNoInteractions(depositGateway);
        }
    }

    @Nested
    class TheDepositFails {

        @Test
        @DisplayName("Una tarjeta rechazada no confirma, y la retención queda para probar con otra")
        void declinedCardDoesNotConfirm() {
            readyToCharge(DEPOSIT);
            when(depositGateway.charge(anyLong(), any(), any(), any(), any())).thenThrow(new CardDeclinedException());

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(CardDeclinedException.class);

            neverConfirmed();
        }

        @Test
        @DisplayName("Con Pagos caído no confirma")
        void paymentsDownDoesNotConfirm() {
            readyToCharge(DEPOSIT);
            when(depositGateway.charge(anyLong(), any(), any(), any(), any()))
                    .thenThrow(new DepositPaymentUnavailableException("caído", new RuntimeException()));

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(DepositPaymentUnavailableException.class);

            neverConfirmed();
        }

        @Test
        @DisplayName("Con Tarificación caída no cobra ni confirma: ya no se toma la seña como 0")
        void pricingDownDoesNotConfirm() {
            readyToCharge(DEPOSIT);
            when(tariffDirectory.calculateDeposit(CONNECTOR_ID))
                    .thenThrow(new PricingUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(PricingUnavailableException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }

        @Test
        @DisplayName("Si la confirmación falla después de cobrar, devuelve la seña y avisa el motivo original")
        void refundsWhenConfirmationFailsAfterCharging() {
            readyToCharge(DEPOSIT);
            when(bookingService.confirmBooking(any(), any(), any(), any())).thenThrow(new HoldExpiredException(holdId));

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(HoldExpiredException.class);

            verify(depositGateway).refund(9L);
        }

        @Test
        @DisplayName("Si tampoco sale la devolución, el conductor recibe el motivo original y no un error de Pagos")
        void refundFailureDoesNotHideTheOriginalProblem() {
            readyToCharge(DEPOSIT);
            when(bookingService.confirmBooking(any(), any(), any(), any())).thenThrow(new HoldExpiredException(holdId));
            doThrow(new DepositPaymentUnavailableException("caído", new RuntimeException()))
                    .when(depositGateway)
                    .refund(9L);

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(HoldExpiredException.class);
        }
    }

    /** QA-29: el mismo pedido dos veces es una sola reserva y un solo cobro. */
    @Nested
    class Idempotency {

        @Test
        @DisplayName("Un pedido repetido devuelve la reserva ya confirmada, sin cobrar ni confirmar otra vez")
        void repeatedRequestReturnsTheSameBooking() {
            Booking already = bookingWith(new BookingDeposit(DEPOSIT, 9L, "4242"), holdId);
            when(bookingService.findByCheckoutKey(DRIVER_ID, KEY)).thenReturn(Optional.of(already));

            CheckoutResult result = facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN);

            assertThat(result.booking()).isSameAs(already);
            assertThat(result.depositAmount()).isEqualByComparingTo("1500.00");
            verifyNoInteractions(depositGateway, tariffDirectory, paymentDirectory);
            verify(bookingService, never()).findHold(any());
            neverConfirmed();
        }

        @Test
        @DisplayName("La misma clave con otra retención es otro pedido: se rechaza")
        void sameKeyForAnotherHoldIsRejected() {
            Booking already = bookingWith(BookingDeposit.none(), UUID.randomUUID());
            when(bookingService.findByCheckoutKey(DRIVER_ID, KEY)).thenReturn(Optional.of(already));

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(IdempotencyKeyReusedException.class);

            verifyNoInteractions(depositGateway);
        }

        @Test
        @DisplayName("Si un pedido gemelo ganó la confirmación, devuelve esa reserva y no le saca la seña")
        void twinRequestKeepsTheDeposit() {
            readyToCharge(DEPOSIT);
            Booking twin = bookingWith(new BookingDeposit(DEPOSIT, 9L, "4242"), holdId);
            when(bookingService.findByCheckoutKey(DRIVER_ID, KEY))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(twin));
            when(bookingService.confirmBooking(any(), any(), any(), any()))
                    .thenThrow(new HoldNotFoundException(holdId));

            CheckoutResult result = facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN);

            assertThat(result.booking()).isSameAs(twin);
            verify(depositGateway, never()).refund(anyLong());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"short", "con espacios adentro", "tiene/barra-0000"})
        @DisplayName("Sin clave, o con una que no tiene la forma esperada, no se procesa nada")
        void requiresAWellFormedKey(String key) {
            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, key, AUTH_TOKEN))
                    .isInstanceOf(IdempotencyKeyRequiredException.class);

            verify(bookingService, never()).findHold(any());
            verifyNoInteractions(depositGateway);
        }
    }

    @Nested
    class TheChecksBeforeCharging {

        @Test
        @DisplayName("Rechaza el checkout si el conductor no aceptó la tolerancia de 15 minutos (RF09)")
        void rejectsWhenGracePeriodNotAccepted() {
            CheckoutBookingRequest notAccepted = new CheckoutBookingRequest(holdId, 1L, false);

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, notAccepted, KEY, AUTH_TOKEN))
                    .isInstanceOf(GracePeriodNotAcceptedException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }

        @Test
        @DisplayName("Rechaza el checkout si la retención no existe (RF08)")
        void rejectsWhenHoldNotFound() {
            when(bookingService.findHold(holdId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(HoldNotFoundException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }

        @Test
        @DisplayName("Rechaza el checkout si la retención es de otro conductor (Seguridad)")
        void rejectsWhenHoldBelongsToAnotherDriver() {
            Hold otherDriverHold = new Hold(holdId, CONNECTOR_ID, 999L, validHold.window(), now.plusSeconds(600));
            when(bookingService.findHold(holdId)).thenReturn(Optional.of(otherDriverHold));

            // La de otro se contesta como inexistente (SEC-06).
            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(HoldNotFoundException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }

        @Test
        @DisplayName("Rechaza el checkout si la retención expiró antes de confirmar (RF08)")
        void rejectsWhenHoldExpired() {
            Hold expiredHold = new Hold(holdId, CONNECTOR_ID, DRIVER_ID, validHold.window(), now.minusSeconds(10));
            when(bookingService.findHold(holdId)).thenReturn(Optional.of(expiredHold));

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(HoldExpiredException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }

        @Test
        @DisplayName("Rechaza el checkout si el conductor no tiene medio de pago utilizable (RF02)")
        void rejectsWhenPaymentMethodMissing() {
            when(bookingService.findHold(holdId)).thenReturn(Optional.of(validHold));
            when(paymentDirectory.hasUsablePaymentMethod(DRIVER_ID, AUTH_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, KEY, AUTH_TOKEN))
                    .isInstanceOf(PaymentMethodRequiredException.class);

            verifyNoInteractions(depositGateway);
            neverConfirmed();
        }
    }

    @Test
    @DisplayName("La clave del cobro no depende de la tarjeta: cambiar de tarjeta pide una clave nueva al front")
    void chargeKeyDependsOnlyOnTheCheckoutKey() {
        readyToCharge(DEPOSIT);
        when(bookingService.confirmBooking(any(), any(), any(), any()))
                .thenReturn(bookingWith(new BookingDeposit(DEPOSIT, 9L, "4242"), holdId));

        facade.processBookingCheckout(DRIVER_ID, new CheckoutBookingRequest(holdId, 7L, true), KEY, AUTH_TOKEN);

        verify(depositGateway).charge(eq(DRIVER_ID), eq(7L), any(), any(), eq("deposit-" + DRIVER_ID + "-" + KEY));
    }
}
