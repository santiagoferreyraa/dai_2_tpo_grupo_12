package com.ecopedia.core.terminal.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La potencia de un conector no supera la que permite la norma de su tipo
 * ({@link com.ecopedia.core.terminal.domain.ConnectorType#maxPowerKw()}).
 *
 * <p>Va sobre el pedido entero y no sobre el campo: el tope depende del tipo elegido. El mensaje lo
 * arma {@link PowerWithinConnectorStandardValidator} con el tipo y su máximo.
 */
@Documented
@Constraint(validatedBy = PowerWithinConnectorStandardValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PowerWithinConnectorStandard {

    String message() default "La potencia supera la que permite el tipo de conector";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
