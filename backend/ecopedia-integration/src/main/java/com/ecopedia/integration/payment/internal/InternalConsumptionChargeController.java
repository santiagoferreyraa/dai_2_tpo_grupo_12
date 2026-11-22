package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.ConsumptionChargeRequest;
import com.ecopedia.integration.payment.domain.ConsumptionChargeResult;
import com.ecopedia.integration.payment.domain.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno para cobrar el saldo de consumo real al cerrar una sesión de carga (RF14, RNF06, ECO-55).
 *
 * <p>Protegido por {@code InternalTokenFilter} bajo {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/payments/consumption-charges")
public class InternalConsumptionChargeController {

    private final PaymentService paymentService;

    public InternalConsumptionChargeController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ConsumptionChargeResponse> chargeConsumption(@Valid @RequestBody ConsumptionChargeBody body) {
        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                body.driverId(), body.paymentMethodId(), body.amount(), body.sessionId(), body.idempotencyKey()));
        return ResponseEntity.ok(ConsumptionChargeResponse.from(result));
    }
}
