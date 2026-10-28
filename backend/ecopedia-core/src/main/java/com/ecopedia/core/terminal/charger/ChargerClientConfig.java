package com.ecopedia.core.terminal.charger;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP hacia los cargadores: dirección, clave de acceso y timeouts.
 *
 * <p>Los mismos 2 s para conectar y 5 s para responder que con la pasarela y la distribuidora, y
 * por lo mismo: el conductor está parado frente al cargador, y un cargador colgado no puede dejar
 * colgado también un hilo de core. Sin reintentos: encender dos veces la misma sesión no hace daño,
 * pero el que decide si reintentar es quien llama.
 */
@Configuration
public class ChargerClientConfig {

    @Bean
    public RestClient chargerHttpClient(
            RestClient.Builder builder,
            @Value("${ecopedia.charger.url}") String url,
            @Value("${ecopedia.charger.access-token}") String accessToken,
            @Value("${ecopedia.charger.connect-timeout:2s}") Duration connectTimeout,
            @Value("${ecopedia.charger.read-timeout:5s}") Duration readTimeout) {
        return build(builder, url, accessToken, connectTimeout, readTimeout);
    }

    /** Aparte del bean para que el test en vivo arme el mismo cliente sin levantar core. */
    static RestClient build(
            RestClient.Builder builder, String url, String accessToken, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder.baseUrl(url)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }
}
