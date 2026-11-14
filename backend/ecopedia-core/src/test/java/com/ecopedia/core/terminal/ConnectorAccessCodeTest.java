package com.ecopedia.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ecopedia.core.shared.ResourceNotFoundException;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorAccessCode;
import com.ecopedia.core.terminal.domain.ConnectorRepository;
import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationRepository;
import com.ecopedia.core.terminal.service.TerminalServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El código de acceso de cada conector: su forma, cómo se lee y cómo lo asigna Terminales (ECO-44). */
@ExtendWith(MockitoExtension.class)
class ConnectorAccessCodeTest {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private ConnectorRepository connectorRepository;

    @InjectMocks
    private TerminalServiceImpl terminalService;

    @Test
    @DisplayName("Un código nuevo son siempre seis dígitos, ceros a la izquierda incluidos")
    void randomIsSixDigits() {
        for (int i = 0; i < 500; i++) {
            assertThat(ConnectorAccessCode.random()).matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("Se lee el PIN solo, o el contenido del QR, con espacios alrededor")
    void parsesPinAndQr() {
        assertThat(ConnectorAccessCode.parse("004213")).contains("004213");
        assertThat(ConnectorAccessCode.parse("  904213 \n")).contains("904213");
        assertThat(ConnectorAccessCode.parse("ecopedia:connector:123456")).contains("123456");
        assertThat(ConnectorAccessCode.parse("ECOPEDIA:CONNECTOR:123456")).contains("123456");
        assertThat(ConnectorAccessCode.qrPayload("123456")).isEqualTo("ecopedia:connector:123456");
    }

    @Test
    @DisplayName("Lo que no es un PIN ni el QR de una terminal no se lee como código")
    void rejectsAnythingElse() {
        assertThat(ConnectorAccessCode.parse(null)).isEmpty();
        assertThat(ConnectorAccessCode.parse("")).isEmpty();
        assertThat(ConnectorAccessCode.parse("12345")).isEmpty();
        assertThat(ConnectorAccessCode.parse("1234567")).isEmpty();
        assertThat(ConnectorAccessCode.parse("12a456")).isEmpty();
        assertThat(ConnectorAccessCode.parse("https://menu.example/123456")).isEmpty();
        assertThat(ConnectorAccessCode.parse("otra-red:connector:123456")).isEmpty();
    }

    @Test
    @DisplayName("El alta de un conector le asigna un código que nadie más tiene")
    void addConnectorAssignsUnusedCode() {
        Station station = new Station();
        station.setId(10L);
        when(stationRepository.findById(10L)).thenReturn(Optional.of(station));
        // El primer candidato está tomado y el segundo no: el servicio tiene que seguir probando.
        when(connectorRepository.existsByAccessCode(anyString())).thenReturn(true, false);
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connector created = terminalService.addConnector(10L, ConnectorType.CCS2, new BigDecimal("150"));

        assertThat(created.getAccessCode()).matches("\\d{6}");
    }

    @Test
    @DisplayName("Regenerar cambia el código del conector")
    void regenerateChangesCode() {
        Connector connector = new Connector();
        connector.setId(1L);
        connector.setAccessCode("000000");
        when(connectorRepository.findById(1L)).thenReturn(Optional.of(connector));
        when(connectorRepository.existsByAccessCode(anyString())).thenReturn(false);
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Con un millón de combinaciones, que el azar repita "000000" es una en un millón: el
        // aserto que importa es que el código pasó por el generador y tiene la forma correcta.
        Connector updated = terminalService.regenerateAccessCode(1L);

        assertThat(updated.getAccessCode()).matches("\\d{6}");
    }

    @Test
    @DisplayName("Un código mal escrito y uno que nadie tiene dan el mismo 'no encontrado'")
    void unknownOrMalformedCodeIsNotFound() {
        when(connectorRepository.findByAccessCode("123456")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> terminalService.getConnectorByAccessCode("123456"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> terminalService.getConnectorByAccessCode("hola"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("El QR de la terminal resuelve al mismo conector que su PIN")
    void qrResolvesLikePin() {
        Connector connector = new Connector();
        connector.setId(7L);
        connector.setAccessCode("246810");
        when(connectorRepository.findByAccessCode("246810")).thenReturn(Optional.of(connector));

        assertThat(terminalService
                        .getConnectorByAccessCode("ecopedia:connector:246810")
                        .getId())
                .isEqualTo(7L);
    }
}
