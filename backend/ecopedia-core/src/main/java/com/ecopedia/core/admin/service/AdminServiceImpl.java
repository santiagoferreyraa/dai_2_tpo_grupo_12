package com.ecopedia.core.admin.service;

import com.ecopedia.core.admin.domain.AdminDashboard;
import com.ecopedia.core.admin.domain.AdminService;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.OperationalStatus;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de administración del sistema (RF03 / ECO-27).
 *
 * <p><b>Lee a través de las interfaces de Usuarios y Terminales, no de sus repositorios</b> (RNF09):
 * el backoffice es un consumidor más de esos componentes. Hasta el 28/11 tomaba los repositorios
 * directo, el atajo que señalaba la nota al principio de §2 de ARQUITECTURA.
 */
@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserService userService;
    private final TerminalService terminalService;

    public AdminServiceImpl(UserService userService, TerminalService terminalService) {
        this.userService = userService;
        this.terminalService = terminalService;
    }

    @Override
    public AdminDashboard getDashboardSummary() {
        List<User> users = userService.listUsers(null);
        long activeUsers = users.stream().filter(User::isActive).count();
        long conductors =
                users.stream().filter(u -> u.getRole() == Role.CONDUCTOR).count();
        long cpos = users.stream().filter(u -> u.getRole() == Role.CPO).count();
        long admins = users.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        AdminDashboard.UsersSummary usersSummary = new AdminDashboard.UsersSummary(
                users.size(), activeUsers, users.size() - activeUsers, conductors, cpos, admins);

        List<Station> stations = terminalService.getStationsIncludingInactive();
        long activeStations = stations.stream().filter(Station::isActive).count();
        AdminDashboard.StationsSummary stationsSummary =
                new AdminDashboard.StationsSummary(stations.size(), activeStations, stations.size() - activeStations);

        List<Connector> connectors = terminalService.getAllConnectors();
        AdminDashboard.ConnectorsSummary connectorsSummary = new AdminDashboard.ConnectorsSummary(
                connectors.size(),
                count(connectors, OperationalStatus.AVAILABLE),
                count(connectors, OperationalStatus.OCCUPIED),
                count(connectors, OperationalStatus.OUT_OF_SERVICE));

        return new AdminDashboard(usersSummary, stationsSummary, connectorsSummary);
    }

    private static long count(List<Connector> connectors, OperationalStatus status) {
        return connectors.stream()
                .filter(c -> c.getOperationalStatus() == status)
                .count();
    }
}
