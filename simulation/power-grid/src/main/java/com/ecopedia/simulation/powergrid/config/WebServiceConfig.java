package com.ecopedia.simulation.powergrid.config;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SimplePasswordValidationCallbackHandler;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * El servicio SOAP: el WSDL que se genera del XSD y los dos controles que pasa cada pedido.
 *
 * <p><b>El contrato</b> se publica en dos archivos, cada uno con el nombre de su bean: el WSDL en
 * {@code /ws/powerGrid.wsdl} y el XSD que importa en {@code /ws/power-grid.xsd}. El WSDL está escrito
 * a mano y no se genera: el porqué está en el propio archivo. Los tipos siguen teniendo una sola
 * fuente, el XSD, que es también del que salen las clases Java y contra el que se valida.
 *
 * <p><b>Los controles, en este orden:</b>
 *
 * <ol>
 *   <li><b>WS-Security, UsernameToken.</b> Sin la cabecera, o con usuario o contraseña
 *       equivocados, el pedido no llega al servicio y vuelve un Fault de seguridad. La contraseña
 *       sale del .env ({@code ECOPEDIA_POWER_GRID_PASSWORD}) y es la misma que manda RedEléctrica.
 *       Va en texto plano dentro del token ({@code PasswordText}): la distribuidora escucha solo en
 *       127.0.0.1, y el modo digest no evita que este lado tenga que conocer la contraseña.
 *   <li><b>Validación contra el XSD</b>, del pedido y de la respuesta. Un pedido mal formado se
 *       rechaza antes de tocar el negocio, y una respuesta que no cumpla el contrato no sale.
 * </ol>
 *
 * <p>Antes de todo eso, {@link IncomingMessageFilter} corta los pedidos demasiado grandes y convierte
 * en un Fault los que no se pueden leer; el parser de SAAJ rechaza cualquier {@code DOCTYPE}, así que
 * no hay entidades externas.
 */
@Configuration
public class WebServiceConfig implements WsConfigurer {

    private final String username;
    private final String password;

    public WebServiceConfig(
            @Value("${simulation.power-grid.username}") String username,
            @Value("${simulation.power-grid.password}") String password) {
        this.username = username;
        this.password = password;
    }

    @Bean(name = "power-grid")
    public XsdSchema powerGridSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/power-grid.xsd"));
    }

    @Bean(name = "powerGrid")
    public SimpleWsdl11Definition powerGridWsdl() {
        return new SimpleWsdl11Definition(new ClassPathResource("wsdl/powerGrid.wsdl"));
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(securityInterceptor());
        interceptors.add(validatingInterceptor());
    }

    private Wss4jSecurityInterceptor securityInterceptor() {
        SimplePasswordValidationCallbackHandler users = new SimplePasswordValidationCallbackHandler();
        users.setUsersMap(Map.of(username, password));

        Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
        interceptor.setValidationActions("UsernameToken");
        interceptor.setValidationCallbackHandler(users);
        return interceptor;
    }

    private PayloadValidatingInterceptor validatingInterceptor() {
        PayloadValidatingInterceptor interceptor = new PayloadValidatingInterceptor();
        interceptor.setXsdSchema(powerGridSchema());
        interceptor.setValidateRequest(true);
        interceptor.setValidateResponse(true);
        return interceptor;
    }
}
