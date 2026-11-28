package com.ecopedia.core.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ecopedia.core.admin.domain.AdminDashboard;
import com.ecopedia.core.admin.service.AdminServiceImpl;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.OperationalStatus;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private TerminalService terminalService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private User mockConductor;
    private User mockAdmin;
    private Station mockStation;
    private Connector mockConnector;

    @BeforeEach
    void setUp() {
        mockConductor = new User();
        mockConductor.setId(1L);
        mockConductor.setEmail("conductor@ecopedia.com");
        mockConductor.setRole(Role.CONDUCTOR);
        mockConductor.setActive(true);

        mockAdmin = new User();
        mockAdmin.setId(2L);
        mockAdmin.setEmail("admin@ecopedia.com");
        mockAdmin.setRole(Role.ADMIN);
        mockAdmin.setActive(true);

        mockStation = new Station();
        mockStation.setId(10L);
        mockStation.setName("Estación Central");
        mockStation.setActive(true);

        mockConnector = new Connector();
        mockConnector.setId(100L);
        mockConnector.setOperationalStatus(OperationalStatus.AVAILABLE);
    }

    @Test
    void testGetDashboardSummary() {
        User inactiveCpo = new User();
        inactiveCpo.setId(3L);
        inactiveCpo.setEmail("cpo@ecopedia.com");
        inactiveCpo.setRole(Role.CPO);
        inactiveCpo.setActive(false);

        Station inactiveStation = new Station();
        inactiveStation.setId(11L);
        inactiveStation.setName("Estación Cerrada");
        inactiveStation.setActive(false);

        Connector occupiedConnector = new Connector();
        occupiedConnector.setId(101L);
        occupiedConnector.setOperationalStatus(OperationalStatus.OCCUPIED);

        Connector outOfServiceConnector = new Connector();
        outOfServiceConnector.setId(102L);
        outOfServiceConnector.setOperationalStatus(OperationalStatus.OUT_OF_SERVICE);

        when(userService.listUsers(null)).thenReturn(List.of(mockConductor, mockAdmin, inactiveCpo));
        when(terminalService.getStationsIncludingInactive()).thenReturn(List.of(mockStation, inactiveStation));
        when(terminalService.getAllConnectors())
                .thenReturn(List.of(mockConnector, occupiedConnector, outOfServiceConnector));

        AdminDashboard summary = adminService.getDashboardSummary();

        assertEquals(3, summary.usersSummary().total());
        assertEquals(2, summary.usersSummary().active());
        assertEquals(1, summary.usersSummary().inactive());
        assertEquals(1, summary.usersSummary().conductors());
        assertEquals(1, summary.usersSummary().cpos());
        assertEquals(1, summary.usersSummary().admins());

        assertEquals(2, summary.stationsSummary().total());
        assertEquals(1, summary.stationsSummary().active());
        assertEquals(1, summary.stationsSummary().inactive());

        assertEquals(3, summary.connectorsSummary().total());
        assertEquals(1, summary.connectorsSummary().available());
        assertEquals(1, summary.connectorsSummary().occupied());
        assertEquals(1, summary.connectorsSummary().outOfService());
    }

    /**
     * RNF09: el backoffice lee Usuarios y Terminales por sus interfaces públicas. Si vuelve a pedir
     * un repositorio de otro componente en el constructor, este test lo ve.
     */
    @Test
    @DisplayName("El backoffice no depende de ningún repositorio de otro componente (RNF09)")
    void dependsOnlyOnComponentInterfaces() {
        List<String> dependencies = Arrays.stream(AdminServiceImpl.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getSimpleName)
                .toList();

        assertEquals(List.of("UserService", "TerminalService"), dependencies);
    }
}
