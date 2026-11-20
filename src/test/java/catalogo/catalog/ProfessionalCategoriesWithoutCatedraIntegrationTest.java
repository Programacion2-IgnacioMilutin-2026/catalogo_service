package catalogo.catalog;

import catalogo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// El listado responde igual sin integración con la cátedra.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "catalogo.catedra.base-url=")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ProfessionalCategoriesWithoutCatedraIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theListIsServedFromTheLocalCopyWithoutTheCatedra(CapturedOutput output) {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.insertCategory(2, "Kinesiología", null, false);
        tables.initializeAt(57);

        restTestClient.get().uri("/api/professional-categories").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].name").isEqualTo(List.of("Kinesiología", "Pediatría"));

        assertThat(output).contains("Technical login disabled: missing configuration properties")
                .contains("catalogo.catedra.base-url");
    }
}
