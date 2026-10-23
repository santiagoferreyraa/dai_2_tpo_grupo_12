package com.ecopedia.charging.booking.web;

import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.web.dto.BookingResponse;
import com.ecopedia.charging.booking.web.dto.FreeWindowResponse;
import com.ecopedia.charging.booking.web.dto.HoldRequest;
import com.ecopedia.charging.booking.web.dto.HoldResponse;
import com.ecopedia.charging.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capa de presentación del componente <i>Reservas</i>.
 *
 * <p>Las reglas de acceso van acá y no en el servicio, igual que en Terminales: SesionesDeCarga
 * va a consumir {@link BookingService} para operaciones internas que no origina un usuario, y
 * con la regla en el servicio habría que inventarles un rol técnico.
 *
 * <p>{@code 'CONDUCTOR'} y no {@code 'DRIVER'}: es el nombre del rol en el enum de core, que es
 * el que viaja en el token. El documento dice {@code DRIVER} y la divergencia es deliberada.
 *
 * <p><b>De quién es cada reserva lo decide el token, nunca la URL.</b> El rol autoriza a operar,
 * no a operar sobre lo ajeno: el id del conductor sale siempre del principal y el servicio
 * verifica que la reserva le pertenezca. Por eso no hay ninguna ruta con el conductor adentro.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** RF08: retener un slot mientras el conductor confirma. */
    @PostMapping("/holds")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<HoldResponse> startHold(
            @AuthenticationPrincipal AuthenticatedUser driver, @Valid @RequestBody HoldRequest request) {
        Hold hold = bookingService.startHold(request.connectorId(), request.toWindow(), driver.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(HoldResponse.fromDomain(hold));
    }

    /** Las reservas del conductor que pregunta. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal AuthenticatedUser driver) {
        return bookingService.getDriverBookings(driver.id()).stream()
                .map(BookingResponse::fromDomain)
                .toList();
    }

    /**
     * Los huecos libres de un conector entre {@code from} y {@code to} (ECO-33), en ISO-8601 con
     * zona, igual que la retención.
     *
     * <p><b>Cualquier usuario con sesión, no solo el conductor.</b> Mirar la agenda no compromete
     * nada, y al operador le sirve para ver la de sus conectores. Anónimo no: la búsqueda en el
     * mapa es pública, pero la agenda es el primer paso de reservar, y reservar pide sesión.
     *
     * <p>Las respuestas que el front tiene que distinguir: 200 con lista vacía es "funciona y está
     * todo tomado"; 409 es "fuera de servicio"; 404, "ese conector no existe".
     */
    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public List<FreeWindowResponse> availability(
            @RequestParam Long connectorId, @RequestParam Instant from, @RequestParam Instant to) {
        return bookingService.getAvailability(connectorId, from, to).stream()
                .map(FreeWindowResponse::fromDomain)
                .toList();
    }

    /**
     * Cancelar una reserva propia y liberar su ventana.
     *
     * <p>{@code DELETE} sobre la reserva y 204 sin cuerpo, aunque por dentro la fila no se borre
     * sino que pase a {@code CANCELLED}: para el conductor la reserva deja de existir, y cómo se
     * guarda es asunto nuestro. Que el registro quede es lo que después deja explicar qué pasó
     * con ese slot.
     */
    @DeleteMapping("/{bookingId}")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser driver, @PathVariable Long bookingId) {
        bookingService.cancelBooking(bookingId, driver.id());
        return ResponseEntity.noContent().build();
    }
}
