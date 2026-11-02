package com.ecopedia.simulation.paymentgateway.web.dto;

import com.ecopedia.simulation.paymentgateway.domain.CardToken;
import java.time.Instant;

/** El token emitido, con lo que Pagos puede guardar: marca, últimos cuatro y vencimiento. */
public record CardTokenResponse(
        String id,
        String paymentMethodId,
        String lastFourDigits,
        int expirationMonth,
        int expirationYear,
        Instant dateCreated) {

    public static CardTokenResponse from(CardToken token) {
        return new CardTokenResponse(
                token.getId(),
                token.getBrand().apiId(),
                token.getLastFour(),
                token.getExpiryMonth(),
                token.getExpiryYear(),
                token.getCreatedAt());
    }
}
