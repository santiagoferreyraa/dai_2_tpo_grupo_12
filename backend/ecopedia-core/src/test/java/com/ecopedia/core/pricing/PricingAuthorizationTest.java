package com.ecopedia.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.pricing.data.JpaStationPricingRepository;
import com.ecopedia.core.pricing.domain.NetworkRates;
import com.ecopedia.core.pricing.domain.NetworkRatesRepository;
import com.ecopedia.core.pricing.domain.StationPricingRepository;
import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
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
 * Quién puede tocar qué de Tarificación (Manuel, 31/10): los precios de la red, solo el {@code ADMIN}; la
 * seña y la hora pico de una estación, el {@code ADMIN} o el {@code CPO} dueño. Leer es público.
 *
 * <p>Como en los tests de Terminales, el token se firma de verdad y el usuario existe en la base: el dueño
 * se resuelve por el usuario del token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PricingAuthorizationTest {

    private static final String RATES =
            """
            {"normalKwhRate":360,"fastKwhRate":560,"excessPenaltyPerMin":210}
            """;
    private static final String STATION_PRICING =
            """
            {"depositAmount":4000,"peakPricingEnabled":false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private UserService userService;

    @Autowired
    private NetworkRatesRepository networkRatesRepository;

    @Autowired
    private StationPricingRepository stationPricing;

    @Autowired
    private JpaStationPricingRepository stationPricingRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        stationPricingRepository.deleteAll();
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
        // Los precios sembrados, como los dejó la migración, para que ninguna otra prueba dependa del orden.
        NetworkRates rates =
                networkRatesRepository.findById(NetworkRates.SINGLETON_ID).orElseThrow();
        rates.update(new BigDecimal("350"), new BigDecimal("550"), new BigDecimal("200"), rates.getUpdatedAt());
        networkRatesRepository.save(rates);
    }

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, "unaClave123", "Usuario " + role, role));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    private Station givenStationOwnedBy(User owner) {
        return terminalService.createStation(
                owner.getId(),
                new StationData("Estación tarifada", "Av. San Juan 2901", -34.6037, -58.3816, List.of(), null));
    }

    @Test
    @DisplayName("Los precios de la red se leen sin sesión, con los sembrados y las reglas")
    void ratesArePublic() throws Exception {
        mockMvc.perform(get("/api/pricing/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalKwhRate").value(350.00))
                .andExpect(jsonPath("$.fastKwhRate").value(550.00))
                .andExpect(jsonPath("$.excessPenaltyPerMin").value(200.00))
                .andExpect(jsonPath("$.peakStartHour").value(18))
                .andExpect(jsonPath("$.peakEndHour").value(22))
                .andExpect(jsonPath("$.peakMultiplier").value(1.2));
    }

    @Test
    @DisplayName("Solo el ADMIN cambia los precios de la red: un CPO recibe 404 y no cambia nada")
    void onlyAdminsChangeTheRates() throws Exception {
        User cpo = givenUser("cpo@ecopedia.test", Role.CPO);
        User admin = givenUser("admin@ecopedia.test", Role.ADMIN);

        mockMvc.perform(put("/api/pricing/rates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RATES))
                .andExpect(status().isNotFound());
        assertThat(networkRatesRepository
                        .findById(NetworkRates.SINGLETON_ID)
                        .orElseThrow()
                        .getNormalKwhRate())
                .isEqualByComparingTo("350");

        mockMvc.perform(put("/api/pricing/rates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RATES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalKwhRate").value(360));
    }

    @Test
    @DisplayName("Un precio en cero o negativo se rechaza con 400")
    void rejectsNonPositiveRates() throws Exception {
        User admin = givenUser("admin@ecopedia.test", Role.ADMIN);

        mockMvc.perform(put("/api/pricing/rates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"normalKwhRate\":0,\"fastKwhRate\":560,\"excessPenaltyPerMin\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un CPO no puede tocar la seña de la estación de otro CPO")
    void rejectsAnotherOperatorsStation() throws Exception {
        User owner = givenUser("duenio@ecopedia.test", Role.CPO);
        User other = givenUser("otro@ecopedia.test", Role.CPO);
        Station station = givenStationOwnedBy(owner);

        mockMvc.perform(put("/api/pricing/stations/{id}", station.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_PRICING))
                .andExpect(status().isNotFound());

        assertThat(stationPricing.findById(station.getId())).isEmpty();
    }

    @Test
    @DisplayName("El CPO dueño y el ADMIN sí configuran la estación; sin configurar, no pide seña y cobra pico")
    void ownerAndAdminConfigureTheStation() throws Exception {
        User owner = givenUser("duenio@ecopedia.test", Role.CPO);
        User admin = givenUser("admin@ecopedia.test", Role.ADMIN);
        Station station = givenStationOwnedBy(owner);

        mockMvc.perform(get("/api/pricing/stations/{id}", station.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").value(0))
                .andExpect(jsonPath("$.peakPricingEnabled").value(true));

        mockMvc.perform(put("/api/pricing/stations/{id}", station.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_PRICING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").value(4000))
                .andExpect(jsonPath("$.peakPricingEnabled").value(false));

        mockMvc.perform(put("/api/pricing/stations/{id}", station.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositAmount\":2500,\"peakPricingEnabled\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pricing/stations/{id}", station.getId()))
                .andExpect(jsonPath("$.depositAmount").value(2500.00))
                .andExpect(jsonPath("$.peakPricingEnabled").value(true));
    }

    @Test
    @DisplayName("Sin sesión no se cambia nada: 403")
    void anonymousCannotChangeAnything() throws Exception {
        mockMvc.perform(put("/api/pricing/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RATES))
                .andExpect(status().isForbidden());
    }
}
