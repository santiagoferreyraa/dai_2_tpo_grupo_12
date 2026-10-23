package com.ecopedia.simulation.paymentgateway.web.dto;

import com.ecopedia.simulation.paymentgateway.service.CardValidation;

/** Si la tarjeta es cobrable, sin haberle cobrado nada (RF11). */
public record ValidationResponse(String status, String statusDetail) {

    public static ValidationResponse from(CardValidation validation) {
        return new ValidationResponse(validation.status().apiValue(), validation.statusDetail());
    }
}
