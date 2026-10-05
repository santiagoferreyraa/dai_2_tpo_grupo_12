package com.ecopedia.charging.checkout.web.dto;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta HTTP consolidada emitida tras un checkout exitoso (ECO-35).
 */
public record CheckoutBookingHttpResponse(
        Long bookingId,
        Long connectorId,
        Instant start,
        Instant end,
        BookingStatus status,
        BigDecimal depositAmount,
        String paymentSummary,
        Instant checkedOutAt) {

    public static CheckoutBookingHttpResponse fromDomain(CheckoutResult result) {
        Booking booking = result.booking();
        return new CheckoutBookingHttpResponse(
                booking.getId(),
                booking.getConnectorId(),
                booking.getWindow().start(),
                booking.getWindow().end(),
                booking.getStatus(),
                result.depositAmount(),
                result.paymentSummary(),
                result.checkedOutAt());
    }
}
