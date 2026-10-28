package com.ecopedia.integration.powergrid.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Que la copia del contrato de este módulo sea la misma que publica la distribuidora.
 *
 * <p>El cliente genera sus clases de su propia copia, como con un legado de verdad. El riesgo de
 * tener dos copias es que alguien cambie una y no la otra: el cliente seguiría compilando contra un
 * contrato viejo, y el error aparecería recién en ejecución. Como las dos viven en el mismo repo,
 * se comparan acá. Si este test falla, se copia el archivo de {@code simulation/power-grid}.
 *
 * <p>Maven corre los tests con el directorio del módulo como directorio de trabajo; de ahí las rutas.
 */
class PowerGridContractCopyTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource({
        "src/main/resources/wsdl/powerGrid.wsdl, ../../simulation/power-grid/src/main/resources/wsdl/powerGrid.wsdl",
        "src/main/resources/wsdl/power-grid.xsd, ../../simulation/power-grid/src/main/resources/xsd/power-grid.xsd"
    })
    @DisplayName("La copia del contrato coincide con la de la distribuidora")
    void copyMatchesPublishedContract(String copy, String published) throws Exception {
        String ours = Files.readString(Path.of(copy)).replace("\r\n", "\n");
        String theirs = Files.readString(Path.of(published)).replace("\r\n", "\n");

        assertThat(ours).isEqualTo(theirs);
    }
}
