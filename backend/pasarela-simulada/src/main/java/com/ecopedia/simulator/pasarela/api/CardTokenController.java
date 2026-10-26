package com.ecopedia.simulator.pasarela.api;

import com.ecopedia.simulator.pasarela.domain.Ledger;
import com.ecopedia.simulator.pasarela.domain.Ledger.CardToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tokenización y verificación de tarjetas.
 *
 * <p>Los nombres de los campos son los de la pasarela ({@code card_number},
 * {@code expiration_month}, {@code payment_method_id}...), no los de Ecopedia. El JSON sale en
 * snake_case por la configuración de Jackson del módulo.
 */
@RestController
@RequestMapping("/v1/card_tokens")
public class CardTokenController {

    private final Ledger ledger;

    public CardTokenController(Ledger ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardTokenResponse create(@Valid @RequestBody CardTokenRequest request) {
        CardToken token = ledger.issueToken(request.cardNumber(), request.expirationMonth(), request.expirationYear());
        return CardTokenResponse.from(token);
    }

    @PostMapping("/{id}/verify")
    public VerificationResponse verify(@PathVariable String id) {
        Optional<String> rejection = ledger.verify(id);
        return rejection
                .map(code -> new VerificationResponse(id, "rejected", code))
                .orElseGet(() -> new VerificationResponse(id, "approved", "card_verified"));
    }

    public record CardTokenRequest(
            @NotBlank String cardNumber,
            @NotNull Integer expirationMonth,
            @NotNull Integer expirationYear,
            @Valid @NotNull Cardholder cardholder) {}

    public record Cardholder(@NotBlank String name) {}

    public record CardTokenResponse(
            String id,
            String paymentMethodId,
            String firstSixDigits,
            String lastFourDigits,
            int expirationMonth,
            int expirationYear,
            Instant dateCreated) {

        static CardTokenResponse from(CardToken token) {
            return new CardTokenResponse(
                    token.id(),
                    token.card().network(),
                    token.card().firstSix(),
                    token.card().lastFour(),
                    token.card().expirationMonth(),
                    token.card().expirationYear(),
                    token.createdAt());
        }
    }

    /** {@code status} es {@code approved} o {@code rejected}; {@code status_detail} dice por qué. */
    public record VerificationResponse(String cardToken, String status, String statusDetail) {}
}
