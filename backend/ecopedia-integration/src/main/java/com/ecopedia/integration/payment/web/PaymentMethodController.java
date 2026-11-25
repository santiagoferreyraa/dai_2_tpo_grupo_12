package com.ecopedia.integration.payment.web;

import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentService;
import com.ecopedia.integration.payment.web.dto.PaymentMethodResponse;
import com.ecopedia.integration.payment.web.dto.RegisterCardRequest;
import com.ecopedia.integration.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capa de presentación del componente {@code Pagos} para los medios de pago del conductor (RF02).
 *
 * <p><b>Las tres operaciones son del rol {@code CONDUCTOR} y de nadie más</b>, ni siquiera del
 * administrador. No es un olvido: RF03 le da al administrador la baja de usuarios y estaciones, no
 * el manejo de las tarjetas ajenas. Un backoffice que pueda registrar o eliminar el medio de pago de
 * otra persona es un poder que el dominio no le dio.
 *
 * <p><b>Ninguna operación recibe el id del dueño.</b> Sale siempre del token, vía
 * {@link AuthenticatedUser}. Es lo que hace que "mis tarjetas" signifique las mías: si el conductor
 * viajara en la URL o en el cuerpo, cambiar un número bastaría para leer o borrar las de otro, y el
 * {@code @PreAuthorize} no lo notaría porque quien pregunta sigue siendo un conductor válido.
 *
 * <p><b>Por qué la autorización va acá y no en el servicio.</b> Mismo criterio que en
 * {@code TerminalController}: cuando Reservas consuma este componente para verificar la precondición
 * de RF02, la llamada no la va a originar un usuario sino el sistema, y una regla puesta en el
 * servicio le exigiría inventarse un rol técnico para poder pasar. En la puerta HTTP el alcance
 * queda donde corresponde.
 */
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentService paymentService;
    private final Clock clock;

    public PaymentMethodController(PaymentService paymentService, Clock clock) {
        this.paymentService = paymentService;
        this.clock = clock;
    }

    /** RF02: alta de una tarjeta. El número va a la pasarela y no se guarda. */
    @PostMapping
    @PreAuthorize("hasRole('CONDUCTOR')")
    @ApiResponse(responseCode = "201", description = "Creado.")
    public ResponseEntity<PaymentMethodResponse> registerCard(
            @Valid @RequestBody RegisterCardRequest request, Authentication authentication) {

        PaymentMethod registered =
                paymentService.registerCard(AuthenticatedUser.idOf(authentication), request.toDomainData());

        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMethodResponse.fromDomain(registered, today()));
    }

    /** RF02: consulta de las tarjetas vigentes del conductor. */
    @GetMapping
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<List<PaymentMethodResponse>> listCards(Authentication authentication) {
        YearMonth today = today();

        List<PaymentMethodResponse> cards = paymentService.listCards(AuthenticatedUser.idOf(authentication)).stream()
                .map(card -> PaymentMethodResponse.fromDomain(card, today))
                .toList();

        return ResponseEntity.ok(cards);
    }

    /**
     * RF02: baja de una tarjeta del conductor.
     *
     * <p>Que sea el dueño lo verifica el servicio, no esta anotación: {@code hasRole('CONDUCTOR')}
     * sabe decir que quien pregunta es un conductor, pero no que sea el conductor de esta tarjeta.
     * Para eso hay que ir a buscarla, y eso es negocio. Ver {@code PaymentService#removeCard}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CONDUCTOR')")
    @ApiResponse(responseCode = "204", description = "Hecho, sin cuerpo.")
    public ResponseEntity<Void> removeCard(@PathVariable Long id, Authentication authentication) {
        paymentService.removeCard(AuthenticatedUser.idOf(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * El mes contra el que se decide si una tarjeta está vencida.
     *
     * <p>Se lee una vez por petición y se pasa a todas las respuestas de esa petición. Leer la hora
     * dentro del bucle abriría la posibilidad —chica pero real— de que un listado que cruza el
     * cambio de mes marque dos tarjetas idénticas con distinto vencimiento.
     */
    private YearMonth today() {
        return YearMonth.now(clock);
    }
}
