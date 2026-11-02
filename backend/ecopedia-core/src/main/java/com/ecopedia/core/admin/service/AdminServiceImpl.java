package com.ecopedia.core.admin.service;

import com.ecopedia.core.admin.domain.AdminDashboard;
import com.ecopedia.core.admin.domain.AdminService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de administración del sistema (RF03 / ECO-27).
 */
@Service
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final ConnectorRepository connectorRepository;
    private final UserService userService;
    private final TerminalService terminalService;

    public AdminServiceImpl(
            UserRepository userRepository,
            StationRepository stationRepository,
            ConnectorRepository connectorRepository,
            UserService userService,
            TerminalService terminalService) {
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.connectorRepository = connectorRepository;
        this.userService = userService;
        this.terminalService = terminalService;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboard getDashboardSummary() {
        List<User> users = userRepository.findAll();
        long activeUsers = users.stream().filter(User::isActive).count();
        long inactiveUsers = users.size() - activeUsers;
        long conductors =
                users.stream().filter(u -> u.getRole() == Role.CONDUCTOR).count();
        long cpos = users.stream().filter(u -> u.getRole() == Role.CPO).count();
        long admins = users.stream().filter(u -> u.getRole() == Role.ADMIN).count();

        AdminDashboard.UsersSummary usersSummary =
                new AdminDashboard.UsersSummary(users.size(), activeUsers, inactiveUsers, conductors, cpos, admins);

        List<Station> stations = stationRepository.findAll();
        long activeStations = stations.stream().filter(Station::isActive).count();
        long inactiveStations = stations.size() - activeStations;

        AdminDashboard.StationsSummary stationsSummary =
                new AdminDashboard.StationsSummary(stations.size(), activeStations, inactiveStations);

        List<Connector> connectors = connectorRepository.findAll();
        long available = connectors.stream()
                .filter(c -> c.getOperationalStatus() == OperationalStatus.AVAILABLE)
                .count();
        long occupied = connectors.stream()
                .filter(c -> c.getOperationalStatus() == OperationalStatus.OCCUPIED)
                .count();
        long outOfService = connectors.stream()
                .filter(c -> c.getOperationalStatus() == OperationalStatus.OUT_OF_SERVICE)
                .count();

        AdminDashboard.ConnectorsSummary connectorsSummary =
                new AdminDashboard.ConnectorsSummary(connectors.size(), available, occupied, outOfService);

        return new AdminDashboard(usersSummary, stationsSummary, connectorsSummary);
    }

    @Override
    public void activateUser(Long userId) {
        userService.activateUser(userId);
    }

    @Override
    public void deactivateStation(Long stationId) {
        terminalService.deactivateStation(stationId);
    }
}
