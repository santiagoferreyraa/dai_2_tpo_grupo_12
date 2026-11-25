package com.ecopedia.integration.payment.web;

import com.ecopedia.integration.payment.domain.DebtCollectionService;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.web.dto.DebtResponse;
import com.ecopedia.integration.payment.web.dto.PayDebtRequest;
import com.ecopedia.integration.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La deuda del conductor, vista y pagada desde el perfil (ECO-62).
 *
 * <p>Las dos operaciones son del rol {@code CONDUCTOR}, como las tarjetas, y el dueño sale del token:
 * ninguna recibe el id del conductor. Ver {@link PaymentMethodController}.
 */
@RestController
@RequestMapping("/api/payment-debts")
public class DebtController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** La misma forma que en el checkout: la que genera el front (un UUID) entra holgada. */
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final DebtCollectionService debtCollection;

    public DebtController(DebtCollectionService debtCollection) {
        this.debtCollection = debtCollection;
    }

    /** Lo que el conductor debe: vacío si está al día. */
    @GetMapping
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<List<DebtResponse>> listDebts(Authentication authentication) {
        List<DebtResponse> debts = debtCollection.listOpenDebts(AuthenticatedUser.idOf(authentication)).stream()
                .map(DebtResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(debts);
    }

    /**
     * Paga una deuda con la tarjeta elegida.
     *
     * <p><b>Pide la cabecera {@code Idempotency-Key}</b>, como el checkout (QA-29): el mismo valor en cada
     * reintento del mismo pago. Además, pagar una deuda ya saldada devuelve la deuda saldada sin
     * cobrar, así que ni siquiera con otra clave se cobra dos veces.
     */
    @PostMapping("/{id}/payment")
    @PreAuthorize("hasRole('CONDUCTOR')")
    // El cuerpo es Object porque el 400 sale de acá mismo: el contrato dice qué trae el 200.
    @ApiResponse(
            responseCode = "200",
            description = "La deuda quedó pagada.",
            content = @Content(schema = @Schema(implementation = DebtResponse.class)))
    public ResponseEntity<Object> payDebt(
            @PathVariable Long id,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) PayDebtRequest request,
            Authentication authentication) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Falta la cabecera Idempotency-Key o no tiene la forma esperada");
            problem.setProperty("code", "IDEMPOTENCY_KEY_REQUIRED");
            return ResponseEntity.badRequest().body(problem);
        }
        Long paymentMethodId = request != null ? request.paymentMethodId() : null;
        PendingPayment paid =
                debtCollection.payDebt(AuthenticatedUser.idOf(authentication), id, paymentMethodId, idempotencyKey);
        return ResponseEntity.ok(DebtResponse.fromDomain(paid));
    }
}
