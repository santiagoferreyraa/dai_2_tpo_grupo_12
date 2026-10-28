package com.ecopedia.integration.powergrid.ws;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

/**
 * El cliente SOAP hacia la distribuidora: dirección, credenciales y timeouts.
 *
 * <p><b>Las clases del contrato</b> las genera JAXB desde la copia del contrato que vive en este
 * módulo ({@code src/main/resources/wsdl/}), como pasaría con un sistema legado de verdad: el
 * cliente tiene el WSDL que le dieron, no el código del otro lado.
 *
 * <p><b>El UsernameToken</b> de WS-Security va en cada pedido, con el usuario y la contraseña del
 * .env ({@code ECOPEDIA_POWER_GRID_PASSWORD}), los mismos que exige la distribuidora.
 *
 * <p><b>Los timeouts</b>, igual que con la pasarela: 2 s para conectar y 5 s para responder, sin
 * reintentos. Una distribuidora colgada no puede dejar un hilo esperando para siempre, y un
 * reintento solo estira la espera del conductor parado frente al cargador.
 */
@Configuration
public class PowerGridClientConfig {

    /** El paquete de las clases que genera JAXB (ver el POM). */
    static final String CONTRACT_PACKAGE = "com.ecopedia.integration.powergrid.ws.contract";

    @Bean
    public WebServiceTemplate powerGridWebServiceTemplate(
            @Value("${ecopedia.power-grid.url}") String url,
            @Value("${ecopedia.power-grid.username}") String username,
            @Value("${ecopedia.power-grid.password}") String password,
            @Value("${ecopedia.power-grid.connect-timeout:2s}") Duration connectTimeout,
            @Value("${ecopedia.power-grid.read-timeout:5s}") Duration readTimeout)
            throws Exception {
        return buildTemplate(url, username, password, connectTimeout, readTimeout);
    }

    /** Fuera del bean para que el test contra el proceso real arme el mismo cliente. */
    static WebServiceTemplate buildTemplate(
            String url, String username, String password, Duration connectTimeout, Duration readTimeout)
            throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTRACT_PACKAGE);
        marshaller.afterPropertiesSet();

        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(connectTimeout);
        sender.setReadTimeout(readTimeout);

        Wss4jSecurityInterceptor security = new Wss4jSecurityInterceptor();
        security.setSecurementActions("UsernameToken");
        security.setSecurementUsername(username);
        security.setSecurementPassword(password);
        security.setSecurementPasswordType("PasswordText");
        security.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri(url);
        template.setMessageSender(sender);
        template.setInterceptors(new ClientInterceptor[] {security});
        return template;
    }
}
