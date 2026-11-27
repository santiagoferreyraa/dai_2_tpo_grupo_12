package com.ecopedia.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Las pruebas contra contenedores (QA-24) necesitan Docker.
 *
 * <p>En la máquina de alguien del equipo, sin Docker abierto, se saltean: así `mvn verify` sigue
 * andando sin Docker, que no todos tienen prendido. **En CI no**: ahí la falta de Docker es una
 * falla, porque una prueba salteada en silencio daría el check en verde sin haber probado nada.
 * GitHub Actions define la variable `CI`.
 */
public class RequiresDocker implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker disponible");
        }
        if (System.getenv("CI") != null) {
            throw new IllegalStateException("En CI las pruebas *IT necesitan Docker, y no está disponible");
        }
        return ConditionEvaluationResult.disabled("Sin Docker: las pruebas contra contenedores se saltean");
    }
}
