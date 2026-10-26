package com.ecopedia.integration.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP con el que el Adapter le habla a la pasarela.
 *
 * <p>Se arma acá y no adentro del Adapter para que el test del Adapter pueda darle uno propio,
 * conectado a un servidor simulado, sin levantar Spring ni la pasarela.
 */
@Configuration
public class PaymentGatewayClientConfig {

    /*
     * Plazos cortos. Del otro lado hay un conductor esperando que su tarjeta se registre: si la
     * pasarela está colgada, es mejor decirle en unos segundos que pruebe más tarde que dejarlo
     * mirando un spinner hasta que el sistema operativo se rinda.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient paymentGatewayRestClient(
            RestClient.Builder builder,
            @Value("${ecopedia.payment-gateway.url}") String url,
            @Value("${ecopedia.payment-gateway.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return builder.baseUrl(url)
                .requestFactory(requestFactory)
                // La credencial del comercio viaja en todos los pedidos: ver ApiKeyFilter en la pasarela.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }
}
