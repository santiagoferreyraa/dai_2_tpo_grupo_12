package com.ecopedia.charging.booking.internal;

import com.ecopedia.charging.booking.domain.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las rutas internas de Reservas: las llama Pagos, con la clave de servicio ({@code InternalTokenFilter}).
 *
 * <p>Sin {@code @PreAuthorize}: quien llama no es un usuario sino otro artefacto, y la puerta es el
 * filtro de la clave, no un rol. Es el mismo criterio que las rutas internas de integration.
 */
@RestController
@RequestMapping("/internal/bookings")
public class InternalBookingController {

    private final BookingService bookingService;

    public InternalBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Anula las reservas confirmadas de un conductor cuya cuenta quedó marcada por deuda (ECO-62).
     * Idempotente: pedirlo de nuevo devuelve 0.
     */
    @PostMapping("/debt-cancellations")
    public ResponseEntity<DebtCancellationResponse> cancelForDebt(@Valid @RequestBody DebtCancellationRequest request) {
        int cancelled = bookingService.cancelConfirmedBookingsForDebt(request.driverId());
        return ResponseEntity.ok(new DebtCancellationResponse(cancelled));
    }

    /** De quién son las reservas a anular. */
    public record DebtCancellationRequest(@NotNull @Positive Long driverId) {}

    /** Cuántas se anularon en este pedido. */
    public record DebtCancellationResponse(int cancelled) {}
}
