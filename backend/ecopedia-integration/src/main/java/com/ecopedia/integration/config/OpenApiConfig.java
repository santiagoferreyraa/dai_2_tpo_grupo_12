package com.ecopedia.integration.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * El contrato OpenAPI de {@code ecopedia-integration}, en {@code /v3/api-docs} (QA-25).
 *
 * <p>springdoc lo arma solo desde los controladores. Lo que no puede deducir se agrega acá:
 *
 * <ul>
 *   <li><b>El token.</b> Una operación con {@code @PreAuthorize} pide el JWT (esquema {@code bearer})
 *       y puede contestar 401. Las públicas no lo piden.
 *   <li><b>Los errores.</b> Todos salen en Problem Details (RFC 9457) con el {@code code} como
 *       extensión, y el contrato los declara en cada operación. Sin esto, Schemathesis marcaría como
 *       "fuera de contrato" un 404 o un 409 que son la respuesta correcta.
 * </ul>
 *
 * <p>Solo entran las rutas de {@code /api}: las de {@code /internal} son entre módulos y no son
 * parte del contrato público ({@code springdoc.paths-to-match} en el {@code application.yml}).
 *
 * <p>Es la misma clase que en core, con los códigos de error de este módulo: los módulos no
 * comparten código (ver {@code ApiProblems}).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer";
    static final String PROBLEM = "Problem";
    static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    /** Los errores que puede devolver cualquier operación de este módulo, además del 401. */
    /** Un error que puede devolver cualquier operación del módulo, en el orden del contrato. */
    private record KnownError(String status, String description) {}

    private static final List<KnownError> ERRORS = List.of(
            new KnownError("400", "La solicitud no es válida."),
            new KnownError("402", "La tarjeta rechazó el cobro."),
            new KnownError("404", "No se encontró el recurso pedido, o la operación no es para este rol."),
            new KnownError("409", "El pedido choca con el estado actual."),
            new KnownError("422", "El pedido se entendió, pero no se puede cumplir."),
            new KnownError("503", "Un sistema del que depende no contesta: probar de nuevo en un momento."));

    @Bean
    public OpenAPI ecopediaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ecopedia — integration")
                        .version("v1")
                        .description("Pagos: medios de pago y deudas del conductor."))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSchemas(PROBLEM, problemSchema()));
    }

    @Bean
    public OperationCustomizer problemResponses() {
        return (operation, handlerMethod) -> {
            if (isSecured(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
                addProblem(operation, "401", "Hace falta una sesión válida.");
            }
            ERRORS.forEach(error -> addProblem(operation, error.status(), error.description()));
            return operation;
        };
    }

    /**
     * Un campo que no es obligatorio puede venir en {@code null}: Jackson manda los nulos, por ejemplo
     * {@code nodeId} de una estación sin nodo o {@code vehicle} de un conductor sin auto. springdoc no
     * lo declara solo, y sin esto Schemathesis toma cada {@code null} como fuera de contrato.
     */
    @Bean
    public OpenApiCustomizer nullableOptionalFields() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::markOptionalAsNullable);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void markOptionalAsNullable(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) return;
        List<String> required = schema.getRequired() == null ? List.of() : schema.getRequired();
        properties.replaceAll((name, property) -> {
            if (required.contains(name)) return property;
            // En OpenAPI 3.0 nada acompaña a un $ref: el nulo va en un allOf que lo envuelve.
            if (property.get$ref() != null) {
                return new Schema<>().nullable(true).allOf(List.of(new Schema<>().$ref(property.get$ref())));
            }
            return property.nullable(true);
        });
    }

    private static boolean isSecured(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }

    // ApiResponses es una clase de swagger-models sin interfaz propia: no hay otro tipo que usar.
    @SuppressWarnings("PMD.LooseCoupling")
    private static void addProblem(Operation operation, String status, String description) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        if (responses.containsKey(status)) return;
        responses.addApiResponse(
                status,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        PROBLEM_MEDIA_TYPE,
                                        new MediaType()
                                                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)))));
    }

    /** RFC 9457, con el {@code code} estable que lee el front y lugar para otras extensiones. */
    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("Error en Problem Details (RFC 9457).")
                .addProperty("type", new StringSchema())
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty("code", new StringSchema().description("Código estable para el front."))
                .additionalProperties(true);
    }
}
