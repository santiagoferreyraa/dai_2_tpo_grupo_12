package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.DepositCharge;
import com.ecopedia.integration.payment.domain.PaymentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La puerta de la seña para el checkout, que vive en ecopedia-charging (ECO-53).
 *
 * <p><b>Ruta interna con la clave de servicio, no una ruta del conductor</b> (decidido por Manuel el
 * 31/10). Con el token del conductor, cualquiera podría pedir "cobrame X a mi tarjeta" con el importe
 * que quisiera; el importe lo decide Tarificación, y quien lo trae es el checkout. La protege
 * {@code InternalTokenFilter}, como la de RedEléctrica, y el navegador no llega.
 *
 * <p>Las respuestas: un cobro aprobado es 201; uno que la pasarela rechazó, 422 con
 * {@code CARD_DECLINED} —se guarda igual, y repetir el pedido vuelve a dar 422—; sin tarjeta con la
 * que cobrar, 422 con {@code PAYMENT_METHOD_INVALID}; pasarela caída, 503.
 */
@RestController
@RequestMapping("/internal/payments/deposits")
public class InternalDepositController {

    private final PaymentService paymentService;

    public InternalDepositController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> charge(@Valid @RequestBody DepositChargeBody body) {
        DepositCharge charge = paymentService.chargeDeposit(body.toDomain());
        if (!charge.isApproved()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("code", "CARD_DECLINED"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(DepositChargeResponse.from(charge));
    }

    @PostMapping("/{chargeId}/refund")
    public DepositChargeResponse refund(@PathVariable Long chargeId) {
        return DepositChargeResponse.from(paymentService.refundDeposit(chargeId));
    }
}
