package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Si el conductor debe algo (ECO-62). Lo pregunta el checkout de Reservas, con la clave de servicio,
 * antes de cobrar la seña: con deuda —pendiente o vencida— no se reserva (RF02).
 *
 * <p>Ruta interna y no la del conductor: el checkout no tiene por qué ver cuánto debe ni de qué carga,
 * solo si puede seguir.
 */
@RestController
@RequestMapping("/internal/payments/drivers")
public class InternalDebtController {

    private final PaymentService paymentService;

    public InternalDebtController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{driverId}/debt")
    public ResponseEntity<DebtStatusResponse> debtStatus(@PathVariable Long driverId) {
        return ResponseEntity.ok(new DebtStatusResponse(paymentService.hasOutstandingDebt(driverId)));
    }

    /** {@code hasDebt}: si tiene alguna deuda pendiente o vencida. */
    public record DebtStatusResponse(boolean hasDebt) {}
}
