package com.ecopedia.integration.payment.gateway;

import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.resilience.CircuitBreakerSettings;
import com.ecopedia.integration.resilience.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP hacia la pasarela: dirección, clave de acceso y timeouts.
 *
 * <p><b>Los timeouts son la mitad de la integración.</b> Sin ellos, una pasarela colgada deja el
 * hilo de Tomcat esperando para siempre, y con unos cuantos conductores esperando a la vez Pagos
 * deja de contestar también lo que no tiene nada que ver con cobrar. Con 2 s para conectar y 5 s
 * para responder, una pasarela lenta se convierte en un {@code PaymentGatewayUnavailableException}
 * que el que llama sabe manejar.
 */
@Configuration
public class PaymentGatewayClientConfig {

    @Bean
    public RestClient paymentGatewayHttpClient(
            RestClient.Builder builder,
            @Value("${ecopedia.payment-gateway.url}") String url,
            @Value("${ecopedia.payment-gateway.access-token}") String accessToken,
            @Value("${ecopedia.payment-gateway.connect-timeout:2s}") Duration connectTimeout,
            @Value("${ecopedia.payment-gateway.read-timeout:5s}") Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder.baseUrl(url)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    /**
     * El puerto que usa Pagos: el adaptador REST envuelto en el circuit breaker (ECO-68).
     *
     * <p>Es el único bean de {@link PaymentGatewayPort}. Los valores del circuito están en
     * {@code ecopedia.payment-gateway.circuit-breaker}; ver {@link CircuitBreakerSettings}.
     */
    @Bean
    public PaymentGatewayPort paymentGateway(
            @Qualifier("paymentGatewayHttpClient") RestClient paymentGatewayHttpClient, Environment environment) {
        CircuitBreaker circuitBreaker = CircuitBreakers.create(
                "payment-gateway",
                "la pasarela de pagos",
                CircuitBreakers.settings(environment, "ecopedia.payment-gateway.circuit-breaker"),
                PaymentGatewayUnavailableException.class);
        return new CircuitBreakerPaymentGateway(new PaymentGatewayRestClient(paymentGatewayHttpClient), circuitBreaker);
    }
}
