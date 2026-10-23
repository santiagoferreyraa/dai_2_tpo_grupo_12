package com.ecopedia.charging.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingAccessDeniedException;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.CheckoutBookingRequest;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias para el patrón Facade de Checkout (ECO-35, BookingCheckoutFacadeImpl).
 *
 * <p>Verifica que la fachada coordine los subsistemas en el orden correcto y que ninguna reserva
 * se confirme si falla una validación previa de tolerancia de gracia o de medios de pago.
 */
class BookingCheckoutFacadeImplTest {

    private static final Long DRIVER_ID = 101L;
    private static final Long CONNECTOR_ID = 42L;
    private static final String AUTH_TOKEN = "test-jwt-token";

    private BookingService bookingService;
    private TariffDirectory tariffDirectory;
    private PaymentDirectory paymentDirectory;
    private Clock clock;

    private BookingCheckoutFacadeImpl facade;

    private UUID holdId;
    private Instant now;
    private Hold validHold;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        tariffDirectory = mock(TariffDirectory.class);
        paymentDirectory = mock(PaymentDirectory.class);

        now = Instant.parse("2026-09-28T15:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);

        facade = new BookingCheckoutFacadeImpl(bookingService, tariffDirectory, paymentDirectory, clock);

        holdId = UUID.randomUUID();
        TimeWindow window = new TimeWindow(now.plusSeconds(3600), now.plusSeconds(7200));
        validHold = new Hold(holdId, CONNECTOR_ID, DRIVER_ID, window, now.plusSeconds(600));
    }

    @Test
    @DisplayName("El checkout coordina los 4 subsistemas exitosamente y confirma la reserva")
    void successfulCheckout() {
        when(bookingService.findHold(holdId)).thenReturn(Optional.of(validHold));
        when(paymentDirectory.hasUsablePaymentMethod(DRIVER_ID, AUTH_TOKEN)).thenReturn(true);
        when(tariffDirectory.calculateDeposit(CONNECTOR_ID)).thenReturn(new BigDecimal("1500.00"));

        Booking confirmedBooking = new Booking(CONNECTOR_ID, DRIVER_ID, validHold.window(), now);
        when(bookingService.confirmBooking(holdId, DRIVER_ID)).thenReturn(confirmedBooking);

        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, true);

        CheckoutResult result = facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.booking()).isEqualTo(confirmedBooking);
        assertThat(result.depositAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.paymentSummary()).contains("1500.00");

        // Verifica que se invocó la confirmación del slot en el componente de Reservas
        verify(bookingService).confirmBooking(holdId, DRIVER_ID);
        // Verifica que se consultó la tarifa
        verify(tariffDirectory).calculateDeposit(CONNECTOR_ID);
        // Verifica que se verificó la tarjeta en el componente de Pagos
        verify(paymentDirectory).hasUsablePaymentMethod(DRIVER_ID, AUTH_TOKEN);
    }

    @Test
    @DisplayName("Rechaza el checkout si el conductor no aceptó la tolerancia de 15 minutos (RF09)")
    void rejectsWhenGracePeriodNotAccepted() {
        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, false);

        assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN))
                .isInstanceOf(GracePeriodNotAcceptedException.class);

        // Ningún subsistema debe haber sido comprometido
        verify(bookingService, never()).confirmBooking(any(), any());
    }

    @Test
    @DisplayName("Rechaza el checkout si la retención no existe (RF08)")
    void rejectsWhenHoldNotFound() {
        when(bookingService.findHold(holdId)).thenReturn(Optional.empty());

        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, true);

        assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN))
                .isInstanceOf(HoldNotFoundException.class);

        verify(bookingService, never()).confirmBooking(any(), any());
    }

    @Test
    @DisplayName("Rechaza el checkout si la retención es de otro conductor (Seguridad)")
    void rejectsWhenHoldBelongsToAnotherDriver() {
        Hold otherDriverHold = new Hold(holdId, CONNECTOR_ID, 999L, validHold.window(), now.plusSeconds(600));
        when(bookingService.findHold(holdId)).thenReturn(Optional.of(otherDriverHold));

        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, true);

        assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN))
                .isInstanceOf(BookingAccessDeniedException.class);

        verify(bookingService, never()).confirmBooking(any(), any());
    }

    @Test
    @DisplayName("Rechaza el checkout si la retención expiró antes de confirmar (RF08)")
    void rejectsWhenHoldExpired() {
        Hold expiredHold = new Hold(holdId, CONNECTOR_ID, DRIVER_ID, validHold.window(), now.minusSeconds(10));
        when(bookingService.findHold(holdId)).thenReturn(Optional.of(expiredHold));

        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, true);

        assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN))
                .isInstanceOf(HoldExpiredException.class);

        verify(bookingService, never()).confirmBooking(any(), any());
    }

    @Test
    @DisplayName("Rechaza el checkout si el conductor no tiene medio de pago utilizable (RF02)")
    void rejectsWhenPaymentMethodMissing() {
        when(bookingService.findHold(holdId)).thenReturn(Optional.of(validHold));
        when(paymentDirectory.hasUsablePaymentMethod(DRIVER_ID, AUTH_TOKEN)).thenReturn(false);

        CheckoutBookingRequest request = new CheckoutBookingRequest(holdId, 1L, true);

        assertThatThrownBy(() -> facade.processBookingCheckout(DRIVER_ID, request, AUTH_TOKEN))
                .isInstanceOf(PaymentMethodRequiredException.class);

        // Importante: No debe confirmarse la reserva si no hay medio de pago
        verify(bookingService, never()).confirmBooking(any(), any());
    }
}
