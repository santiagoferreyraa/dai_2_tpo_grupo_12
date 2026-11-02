package com.ecopedia.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import com.ecopedia.core.vehicle.data.JpaVehicleModelRepository;
import com.ecopedia.core.vehicle.domain.VehicleModel;
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
 * El conductor elige su auto del catálogo, y el perfil se lo devuelve con la ficha entera.
 *
 * <p><b>Va de punta a punta y no contra el servicio.</b> Lo que hay que sostener acá no es una
 * regla de negocio complicada —el servicio hace una búsqueda y una asignación—, sino un contrato
 * que cruza tres capas y dos componentes: el id que manda la pantalla, el catálogo que lo
 * resuelve, y el JSON del perfil que vuelve con marca, modelo, conector y potencias. Un test con
 * mocks daría verde con el catálogo desconectado del perfil, que es justo lo que puede romperse.
 *
 * <p>El catálogo sale de la migración, así que estos casos corren contra los mismos datos que ve
 * cualquiera que levante el proyecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class VehicleSelectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaVehicleModelRepository modelRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    /**
     * El token se firma para el usuario recién creado y no con un id fijo: el controlador busca
     * el perfil por el email que viaja adentro, así que un token a nombre de alguien que no está
     * en la base falla antes de llegar a lo que se quiere probar.
     */
    private String bearerFor(User user) {
        return "Bearer " + tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    private User givenDriver() {
        return userService.register(
                new RegistrationData("conductor@ecopedia.test", "unaClave123", "Juana Conductora", Role.CONDUCTOR));
    }

    private VehicleModel anyModel() {
        return modelRepository.findByActiveTrueOrderByBrandAscNameAsc().getFirst();
    }

    private String profileBody(Long vehicleModelId) {
        String vehicle = vehicleModelId == null ? "null" : vehicleModelId.toString();
        return "{\"fullName\":\"Juana Conductora\",\"vehicleModelId\":" + vehicle + "}";
    }

    @Test
    @DisplayName("El catálogo se puede leer sin sesión y viene ordenado por marca")
    void servesTheCatalogToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/vehicle-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(19))
                .andExpect(jsonPath("$[0].brand").value("BMW"))
                .andExpect(jsonPath("$[0].connectorType").value("CCS2"))
                /*
                 * El nombre de la foto viaja siempre, aunque el archivo pueda no estar todavía
                 * subido: son dos cosas distintas y el selector cuenta con la primera para
                 * armar la dirección. Ver la migración que agregó la columna.
                 */
                .andExpect(jsonPath("$[0].imageSlug").value("bmw-i3"));

        List<VehicleModel> catalog = modelRepository.findByActiveTrueOrderByBrandAscNameAsc();
        /*
         * Que estén los tres conectores no es un detalle del seed: si fueran todos CCS2, el
         * conteo de estaciones compatibles de la portada diría siempre "todas" y el filtro por
         * conector del mapa no tendría contra qué probarse.
         */
        assertTrue(
                catalog.stream().map(VehicleModel::getConnectorType).distinct().count() == 3,
                "El catálogo tiene que cubrir los tres tipos de conector");
    }

    @Test
    @DisplayName("Una cuenta nueva no tiene auto, y eso se ve como null y no como una ficha vacía")
    void startsWithoutAVehicle() throws Exception {
        User driver = givenDriver();

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearerFor(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle").doesNotExist());
    }

    @Test
    @DisplayName("Al elegir un modelo, el perfil vuelve con la ficha entera y no con un id suelto")
    void storesTheChosenModelAndAnswersWithItsSpecs() throws Exception {
        User driver = givenDriver();
        VehicleModel model = anyModel();

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(model.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.id").value(model.getId()))
                .andExpect(jsonPath("$.vehicle.brand").value(model.getBrand()))
                .andExpect(jsonPath("$.vehicle.name").value(model.getName()))
                .andExpect(jsonPath("$.vehicle.connectorType")
                        .value(model.getConnectorType().name()))
                .andExpect(jsonPath("$.vehicle.maxChargeKw").exists())
                .andExpect(jsonPath("$.vehicle.batteryKwh").exists());

        // Y sobrevive al pedido: lo que se probó arriba es la respuesta del PUT, no la fila.
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearerFor(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.id").value(model.getId()));
    }

    /**
     * Sacarse el auto tiene que ser posible, y es lo que hace que {@code null} signifique
     * "borralo" en vez de "no lo toques". Ver {@code ProfileData}.
     */
    @Test
    @DisplayName("Mandar el vehículo en null lo saca del perfil")
    void clearsTheVehicleWhenTheIdComesNull() throws Exception {
        User driver = givenDriver();
        String token = bearerFor(driver);

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(anyModel().getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle").doesNotExist());
    }

    @Test
    @DisplayName("Un id que no está en el catálogo es un 400, no un 500 de la clave foránea")
    void rejectsAnUnknownModel() throws Exception {
        User driver = givenDriver();

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(999_999L)))
                .andExpect(status().isBadRequest());
    }

    /**
     * El caso que la clave foránea sola NO atajaría: la fila existe, así que la base la aceptaría
     * sin chistar y el conductor quedaría con un auto que el selector ya no ofrece.
     */
    @Test
    @DisplayName("Un modelo dado de baja tampoco se puede elegir")
    void rejectsADiscontinuedModel() throws Exception {
        User driver = givenDriver();
        VehicleModel model = anyModel();
        model.setActive(false);
        modelRepository.save(model);

        try {
            mockMvc.perform(put("/api/users/profile")
                            .header(HttpHeaders.AUTHORIZATION, bearerFor(driver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(profileBody(model.getId())))
                    .andExpect(status().isBadRequest());
        } finally {
            // El catálogo sale de la migración y lo comparten todos los casos de la clase.
            model.setActive(true);
            modelRepository.save(model);
        }
    }
}
