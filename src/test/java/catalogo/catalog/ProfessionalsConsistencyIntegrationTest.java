package catalogo.catalog;

import catalogo.TestTokens;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

// La búsqueda y la consulta de un profesional ven una sola foto de la base: un cambio confirmado entre dos de sus
// lecturas no se mezcla en la respuesta. El cambio se confirma entre dos lecturas, en el mismo hilo, así que no depende
// del tiempo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "catalogo.catedra.base-url=")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProfessionalsConsistencyIntegrationTest {

    private static final String PROFESSIONALS = "/api/professionals";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private ProfessionalRepository professionalRepository;

    @BeforeEach
    void copyWithTwoProfessionals() {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Kinesiología", null, true);
        tables.insertProfessional(601, 1, "Valentina", "Ibáñez", true);
        tables.insertProfessional(602, 1, "Tomás", "Ferreyra", true);
        tables.insertWeeklySchedule(701, 601, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.initializeAt(57);
    }

    @Test
    void aChangeConfirmedBetweenCountingAndReadingThePageIsNotMixedIntoTheResponse() {
        AtomicBoolean changed = new AtomicBoolean();
        doAnswer(invocation -> {
            confirmAChangeFromAnotherConnection();
            changed.set(true);
            return invocation.callRealMethod();
        }).doCallRealMethod().when(professionalRepository).findMatching(any(), anyInt(), anyInt());

        restTestClient.get().uri(PROFESSIONALS).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalItems").isEqualTo(2)
                .jsonPath("$.items[*].id").isEqualTo(List.of(602, 601))
                .jsonPath("$.items[1].weeklySchedules[0].startTime").isEqualTo("08:00:00");

        assertThat(changed).isTrue();
        restTestClient.get().uri(PROFESSIONALS).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalItems").isEqualTo(3)
                .jsonPath("$.items[*].id").isEqualTo(List.of(605, 602, 601))
                .jsonPath("$.items[2].weeklySchedules[0].startTime").isEqualTo("07:00:00");
    }

    @Test
    void aChangeConfirmedAfterReadingOneProfessionalIsNotMixedIntoItsSchedules() {
        AtomicBoolean changed = new AtomicBoolean();
        doAnswer(invocation -> {
            Object professional = invocation.callRealMethod();
            confirmAChangeOfOneProfessionalFromAnotherConnection();
            changed.set(true);
            return professional;
        }).doCallRealMethod().when(professionalRepository).findById(601L);

        getProfessional601().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastName").isEqualTo("Ibáñez")
                .jsonPath("$.weeklySchedules.length()").isEqualTo(1)
                .jsonPath("$.weeklySchedules[0].startTime").isEqualTo("08:00:00");

        assertThat(changed).isTrue();
        getProfessional601().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastName").isEqualTo("Ibáñez Acosta")
                .jsonPath("$.weeklySchedules.length()").isEqualTo(2)
                .jsonPath("$.weeklySchedules[0].startTime").isEqualTo("07:00:00");
    }

    private RestTestClient.ResponseSpec getProfessional601() {
        return restTestClient.get().uri(PROFESSIONALS + "/601")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid()).exchange();
    }

    // Una sincronización que cambia el profesional y sus horarios, confirmada entre la lectura de uno y la de los otros.
    private void confirmAChangeOfOneProfessionalFromAnotherConnection() throws SQLException {
        try (Connection synchronization = dataSource.getConnection();
             Statement statement = synchronization.createStatement()) {
            synchronization.setAutoCommit(false);
            statement.executeUpdate("UPDATE professionals SET last_name = 'Ibáñez Acosta' WHERE id = 601");
            statement.executeUpdate("UPDATE weekly_schedules SET start_time = '07:00' WHERE id = 701");
            statement.executeUpdate("INSERT INTO weekly_schedules (id, professional_id, day_of_week, start_time, "
                    + "end_time, slot_duration_minutes, enabled) VALUES (702, 601, 'FRIDAY', '10:00', '12:00', 30, true)");
            synchronization.commit();
        }
    }

    // Hace de una sincronización que termina en el medio de la búsqueda: otra conexión, fuera de la transacción del pedido.
    private void confirmAChangeFromAnotherConnection() throws SQLException {
        try (Connection synchronization = dataSource.getConnection();
             Statement statement = synchronization.createStatement()) {
            synchronization.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO professionals (id, category_id, first_name, last_name, enabled) "
                    + "VALUES (605, 1, 'Ana', 'Acosta', true)");
            statement.executeUpdate("UPDATE weekly_schedules SET start_time = '07:00' WHERE id = 701");
            synchronization.commit();
        }
    }
}
