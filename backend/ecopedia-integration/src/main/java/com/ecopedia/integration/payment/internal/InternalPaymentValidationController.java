package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.PaymentService;
import com.ecopedia.integration.payment.domain.PaymentValidationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno para la validación previa de medios de pago y deuda (RF11, RF14, ECO-54).
 *
 * <p>Invocado por {@code ecopedia-charging} con la clave de servicio interna antes de habilitar
 * físicamente el cargador y persistir la sesión local. No mueve dinero ni genera transacciones.
 */
@RestController
@RequestMapping("/internal/payments/validations")
public class InternalPaymentValidationController {

    private final PaymentService paymentService;

    public InternalPaymentValidationController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> validatePaymentMethod(@Valid @RequestBody PaymentValidationRequestBody body) {
        PaymentValidationResult result = paymentService.validatePaymentMethod(body.driverId(), body.paymentMethodId());

        if (!result.valid()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, result.reason());
            problem.setProperty("code", result.errorCode().name());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
        }

        return ResponseEntity.ok(PaymentValidationResponseBody.from(result));
    }
}
