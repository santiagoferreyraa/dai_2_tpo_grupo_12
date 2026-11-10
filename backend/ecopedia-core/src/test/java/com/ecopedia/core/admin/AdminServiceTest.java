package com.ecopedia.core.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ecopedia.core.admin.domain.AdminDashboard;
import com.ecopedia.core.admin.service.AdminServiceImpl;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorRepository;
import com.ecopedia.core.terminal.domain.OperationalStatus;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationRepository;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserRepository;
import com.ecopedia.core.user.domain.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private ConnectorRepository connectorRepository;

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

        when(userRepository.findAll()).thenReturn(List.of(mockConductor, mockAdmin, inactiveCpo));
        when(stationRepository.findAll()).thenReturn(List.of(mockStation, inactiveStation));
        when(connectorRepository.findAll())
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

    @Test
    void testActivateUser() {
        adminService.activateUser(5L);
        verify(userService, times(1)).activateUser(5L);
    }

    @Test
    void testDeactivateStation() {
        adminService.deactivateStation(10L);
        verify(terminalService, times(1)).deactivateStation(10L);
    }
}
