package com.ecopedia.integration.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.integration.security.HostileTokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Dos pedidos mal formados que encontró Schemathesis (QA-25) y que no salían como los demás errores:
 * un {@code multipart/form-data} sin {@code boundary} daba 500 en cualquier ruta, y una URL que
 * rechaza el firewall de Spring Security salía con el JSON del {@code /error} de Spring Boot.
 *
 * <p>Va contra un puerto de verdad y no con MockMvc: el pedido simulado no lee el cuerpo ni arma la
 * URL como Tomcat, así que con MockMvc los dos errores no aparecen y el test pasaría sin el arreglo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MalformedRequestTest {

    @LocalServerPort
    private int port;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Un multipart sin boundary es una solicitud inválida (400), no un error del servidor")
    void multipartWithoutBoundary() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/payment-methods"))
                .header("Authorization", HostileTokens.valid(secret, 7L, "CONDUCTOR"))
                .header("Content-Type", "multipart/form-data")
                .method("GET", HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"detail\":\"La solicitud no es válida.\"");
    }

    @Test
    @DisplayName("Una URL que rechaza el firewall es un 400 en Problem Details")
    void urlRejectedByTheFirewall() throws Exception {
        // %E2%80%A8 es el separador de línea de Unicode: el firewall no deja pasar caracteres de control.
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/payment-methods/%E2%80%A8"))
                .GET()
                .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"detail\":\"La solicitud no es válida.\"");
    }
}
