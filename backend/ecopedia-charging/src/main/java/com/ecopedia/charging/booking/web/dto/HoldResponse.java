package com.ecopedia.charging.booking.web.dto;

import com.ecopedia.charging.booking.domain.Hold;
import java.time.Instant;
import java.util.UUID;

/**
 * Una retención recién creada. {@code expiresAt} es lo que el front necesita para mostrar
 * cuánto tiempo le queda al conductor para confirmar.
 *
 * @param depositRefundableUntil hasta cuándo, una vez confirmada, cancelar devolvería la seña. Va
 *     antes de pagar a propósito: el conductor tiene que saber a qué se compromete (ECO-53).
 */
public record HoldResponse(
        UUID id, Long connectorId, Instant start, Instant end, Instant expiresAt, Instant depositRefundableUntil) {

    public static HoldResponse fromDomain(Hold hold, Instant depositRefundableUntil) {
        return new HoldResponse(
                hold.id(),
                hold.connectorId(),
                hold.window().start(),
                hold.window().end(),
                hold.expiresAt(),
                depositRefundableUntil);
    }
}
