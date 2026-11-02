package com.ecopedia.charging.checkout.web;

import com.ecopedia.charging.checkout.domain.CheckoutFacade;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import com.ecopedia.charging.checkout.web.dto.CheckoutBookingHttpRequest;
import com.ecopedia.charging.checkout.web.dto.CheckoutBookingHttpResponse;
import com.ecopedia.charging.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del Checkout de Carga y Reservas (ECO-35).
 *
 * <p>Aplica el principio de <b>Thin Controller</b> en la Arquitectura en Capas: no contiene lógica
 * de negocio ni orquesta servicios, sino que delega directamente en {@link CheckoutFacade}.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final CheckoutFacade checkoutFacade;

    public CheckoutController(CheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }

    /**
     * Procesa el checkout completo de una reserva (ECO-35, Patrón Facade).
     *
     * <p><b>Pide la cabecera {@code Idempotency-Key}</b> (QA-29): el mismo valor en cada reintento del
     * mismo pedido. Con ella, un doble clic o una respuesta perdida devuelven la reserva ya confirmada
     * en vez de cobrar la seña dos veces. Sin ella, 400 con {@code IDEMPOTENCY_KEY_REQUIRED}.
     *
     * @param driver Conductor autenticado que origina la solicitud
     * @param authHeader Encabezado Authorization con el JWT para propagación si corresponde
     * @param idempotencyKey La clave del pedido; la valida el Facade
     * @param request Datos del checkout (retención, medio de pago, consentimiento)
     */
    @PostMapping("/booking")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<CheckoutBookingHttpResponse> processBookingCheckout(
            @AuthenticationPrincipal AuthenticatedUser driver,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutBookingHttpRequest request) {

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;

        CheckoutResult result =
                checkoutFacade.processBookingCheckout(driver.id(), request.toDomain(), idempotencyKey, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(CheckoutBookingHttpResponse.fromDomain(result));
    }
}
