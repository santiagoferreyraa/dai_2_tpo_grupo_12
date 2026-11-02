package com.ecopedia.charging.session.data;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Los clientes HTTP de SesionesDeCarga hacia los otros artefactos.
 *
 * <p>Como beans y no armados adentro de cada adaptador, para que los tests los reemplacen por uno
 * enlazado a {@code MockRestServiceServer}.
 */
@Configuration
public class SessionClientConfig {

    /**
     * Hacia las rutas internas de ecopedia-integration, con la clave de servicio.
     *
     * <p><b>Lectura de 8 s, no de 3 como los demás.</b> Del otro lado, RedEléctrica espera a la
     * distribuidora hasta 5 s y recién ahí contesta "no disponible"; con menos de eso, este lado se
     * rendiría antes y nunca vería la respuesta de allá. 8 s cubren los 5 más el viaje.
     */
    @Bean
    public RestClient integrationInternalHttpClient(
            RestClient.Builder builder,
            @Value("${ecopedia.integration.url}") String integrationUrl,
            @Value("${ecopedia.internal.token}") String serviceToken) {
        return builder.clone()
                .baseUrl(integrationUrl)
                .requestFactory(timeouts(Duration.ofSeconds(2), Duration.ofSeconds(8)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .build();
    }

    /** Hacia las lecturas públicas de Terminales en ecopedia-core: el conector y su estación. */
    @Bean
    public RestClient coreTerminalHttpClient(
            RestClient.Builder builder, @Value("${ecopedia.core.url}") String coreUrl) {
        return builder.clone()
                .baseUrl(coreUrl)
                .requestFactory(timeouts(Duration.ofSeconds(2), Duration.ofSeconds(3)))
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connect);
        requestFactory.setReadTimeout(read);
        return requestFactory;
    }
}
