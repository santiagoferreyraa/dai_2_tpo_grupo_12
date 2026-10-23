package com.ecopedia.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.pricing.data.JpaPricingSchemeRepository;
import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quién puede ponerle precio a un conector (RF06).
 *
 * <p>La regla pedía ser {@code CPO} o {@code ADMIN} y nada más, así que cualquier operador le
 * tarifaba el conector a otro. Ahora el {@code CPO} tiene que ser el dueño de la estación de la
 * que cuelga el conector; el {@code ADMIN} sigue pudiendo con todos.
 *
 * <p>Como en los tests de Terminales, el token se firma de verdad y el usuario existe en la base:
 * el dueño se resuelve por el usuario del token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PricingAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaPricingSchemeRepository pricingSchemeRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        pricingSchemeRepository.deleteAll();
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, "unaClave123", "Usuario " + role, role));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    private Connector givenConnectorOwnedBy(User owner) {
        Station station = terminalService.createStation(
                owner.getId(),
                new StationData("Estación tarifada", "Av. San Juan 2901", -34.6037, -58.3816, List.of()));
        return terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50"));
    }

    private static String schemeFor(Connector connector) {
        return """
                {"connectorId":%d,"strategyType":"FLAT_RATE","kwhRate":120,"depositAmount":500}
                """
                .formatted(connector.getId());
    }

    @Test
    @DisplayName("Un CPO no puede tarifar el conector de otro CPO")
    void rejectsPricingAConnectorOfAnotherOperator() throws Exception {
        User owner = givenUser("duenio@ecopedia.test", Role.CPO);
        User other = givenUser("otro@ecopedia.test", Role.CPO);
        Connector connector = givenConnectorOwnedBy(owner);

        mockMvc.perform(post("/api/pricing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schemeFor(connector)))
                .andExpect(status().isForbidden());

        assertThat(pricingSchemeRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("El CPO dueño y el ADMIN sí tarifan el conector")
    void allowsTheOwnerAndAdmins() throws Exception {
        User owner = givenUser("duenio@ecopedia.test", Role.CPO);
        User admin = givenUser("admin@ecopedia.test", Role.ADMIN);
        Connector connector = givenConnectorOwnedBy(owner);

        mockMvc.perform(post("/api/pricing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schemeFor(connector)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pricing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schemeFor(connector)))
                .andExpect(status().isCreated());
    }
}
