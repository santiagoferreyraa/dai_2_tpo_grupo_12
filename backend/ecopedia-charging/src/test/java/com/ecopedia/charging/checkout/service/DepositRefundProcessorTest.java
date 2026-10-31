package com.ecopedia.charging.checkout.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.TimeWindow;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.DepositRefundRefusedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** El barrido de las señas por devolver, sin esperar al reloj. */
class DepositRefundProcessorTest {

    private BookingService bookingService;
    private DepositGateway depositGateway;
    private DepositRefundProcessor processor;
    private Booking pending;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        depositGateway = mock(DepositGateway.class);
        processor = new DepositRefundProcessor(bookingService, depositGateway);

        Instant start = Instant.parse("2026-11-02T18:00:00Z");
        pending = new Booking(
                12L,
                5L,
                new TimeWindow(start, start.plusSeconds(3600)),
                Instant.parse("2026-10-31T12:00:00Z"),
                new BookingDeposit(new BigDecimal("8000"), 9L, "4242"),
                start.minusSeconds(5 * 3600),
                UUID.randomUUID(),
                "key-12345678");
        ReflectionTestUtils.setField(pending, "id", 33L);
        pending.cancel(Instant.parse("2026-10-31T13:00:00Z"));
        when(bookingService.findPendingDepositRefunds()).thenReturn(List.of(pending));
    }

    @Test
    @DisplayName("Pide la devolución del cobro y la anota en la reserva")
    void refundsAndRecords() {
        processor.refundPending();

        verify(depositGateway).refund(9L);
        verify(bookingService).markDepositRefunded(33L);
    }

    @Test
    @DisplayName("Con Pagos caído no anota nada: queda pendiente para el próximo barrido")
    void keepsItPendingWhenPaymentsIsDown() {
        doThrow(new DepositPaymentUnavailableException("caído", new RuntimeException()))
                .when(depositGateway)
                .refund(anyLong());

        processor.refundPending();

        verify(bookingService, never()).markDepositRefunded(anyLong());
    }

    @Test
    @DisplayName("Si Pagos no acepta devolverla, tampoco se anota como devuelta")
    void doesNotRecordARefusedRefund() {
        doThrow(new DepositRefundRefusedException("DEPOSIT_NOT_REFUNDABLE"))
                .when(depositGateway)
                .refund(anyLong());

        processor.refundPending();

        verify(bookingService, never()).markDepositRefunded(anyLong());
    }

    @Test
    @DisplayName("La base caída no rompe el barrido")
    void survivesTheDatabaseBeingDown() {
        when(bookingService.findPendingDepositRefunds()).thenThrow(new RuntimeException("Connection refused"));

        processor.refundPending();

        verify(depositGateway, never()).refund(anyLong());
    }
}
