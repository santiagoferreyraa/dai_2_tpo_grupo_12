package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.ConsumptionCharge;
import com.ecopedia.integration.payment.domain.PaymentService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El cobro del consumo al cerrar una sesión de carga, para SesionesDeCarga (RF14, RNF06, ECO-44).
 *
 * <p>Ruta interna con la clave de servicio, por lo mismo que la de la seña: el importe lo calcula
 * Tarificación y lo trae SesionesDeCarga, nunca el conductor.
 *
 * <p>Siempre 200 con el resultado, también cuando no se pudo cobrar: un rechazo de la pasarela es
 * una respuesta, y lo que hace Pagos con él —anotarlo como deuda— ya está hecho cuando contesta.
 */
@RestController
@RequestMapping("/internal/payments/consumptions")
public class InternalConsumptionController {

    private final PaymentService paymentService;

    public InternalConsumptionController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ConsumptionChargeResponse charge(@Valid @RequestBody ConsumptionChargeBody body) {
        return ConsumptionChargeResponse.from(paymentService.chargeConsumption(body.toDomain()));
    }

    public record ConsumptionChargeResponse(String status, BigDecimal amount, Long pendingPaymentId) {

        static ConsumptionChargeResponse from(ConsumptionCharge charge) {
            return new ConsumptionChargeResponse(charge.status().name(), charge.amount(), charge.pendingPaymentId());
        }
    }
}
