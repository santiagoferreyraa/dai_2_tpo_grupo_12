package com.ecopedia.core.terminal.web.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Compara la potencia pedida con el tope de la norma del tipo de conector. Si falta el tipo o la
 * potencia no dice nada: de eso se ocupan los {@code @NotNull} de cada campo.
 */
public class PowerWithinConnectorStandardValidator
        implements ConstraintValidator<PowerWithinConnectorStandard, ConfigureConnectorRequest> {

    @Override
    public boolean isValid(ConfigureConnectorRequest request, ConstraintValidatorContext context) {
        if (request == null || request.connectorType() == null || request.maxPowerKw() == null) {
            return true;
        }
        if (request.maxPowerKw().compareTo(request.connectorType().maxPowerKw()) <= 0) {
            return true;
        }
        // Sin llaves en el texto: el validador las tomaría como variables del mensaje.
        String message = "Un conector %s entrega como máximo %s kW"
                .formatted(
                        request.connectorType().displayName(),
                        request.connectorType().maxPowerKw().toPlainString());
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("maxPowerKw")
                .addConstraintViolation();
        return false;
    }
}
