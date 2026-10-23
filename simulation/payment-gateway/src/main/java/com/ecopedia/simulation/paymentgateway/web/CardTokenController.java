package com.ecopedia.simulation.paymentgateway.web;

import com.ecopedia.simulation.paymentgateway.service.CardInput;
import com.ecopedia.simulation.paymentgateway.service.CardTokenService;
import com.ecopedia.simulation.paymentgateway.web.dto.CardTokenResponse;
import com.ecopedia.simulation.paymentgateway.web.dto.TokenizeCardRequest;
import com.ecopedia.simulation.paymentgateway.web.dto.ValidationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Las tarjetas: tokenizar y validar. */
@RestController
@RequestMapping("/v1/card_tokens")
public class CardTokenController {

    private final CardTokenService cardTokens;

    public CardTokenController(CardTokenService cardTokens) {
        this.cardTokens = cardTokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardTokenResponse tokenize(@Valid @RequestBody TokenizeCardRequest request) {
        return CardTokenResponse.from(cardTokens.tokenize(new CardInput(
                request.cardNumber(),
                request.expirationMonth(),
                request.expirationYear(),
                request.cardholder().name())));
    }

    /** Validar es preguntar, no crear nada: por eso 200 y no 201. */
    @PostMapping("/{tokenId}/validations")
    public ValidationResponse validate(@PathVariable String tokenId) {
        return ValidationResponse.from(cardTokens.validate(tokenId));
    }
}
