package e2e;

import catalogo.CatalogoApplication;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.CatalogSnapshotResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetCatalogSnapshotUseCase;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.FullSyncResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.function.ToLongFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La sincronización completa contra la cátedra real, con las credenciales del .env local y una base descartable de Testcontainers.
@Tag("e2e")
@EnabledIf(value = "e2e.E2ECredentials#available", disabledReason = "No catedra credentials in the local .env: E2E tests skipped")
@SpringBootTest(classes = CatalogoApplication.class)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class FullSynchronizationE2ETest {

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private GetCatalogSnapshotUseCase getCatalogSnapshotUseCase;

    @Autowired
    private SynchronizationService synchronizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anEmptyCatalogIsInitializedFromTheRealSnapshot(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(90))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());
        String token = technicalSessionService.getTechnicalSession().map(TechnicalSession::token).orElseThrow();
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        assertThat(tables.syncState()).containsEntry("local_version", null);

        CatalogSnapshotResult read = getCatalogSnapshotUseCase.getCatalogSnapshot();
        assertThat(read).as("snapshot read by the test").isInstanceOf(CatalogSnapshotResult.Obtained.class);
        CatalogSnapshot expected = ((CatalogSnapshotResult.Obtained) read).snapshot();

        long start = System.nanoTime();
        FullSyncResult result = synchronizationService.synchronizeFullCatalog();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result).as("full synchronization result").isInstanceOf(FullSyncResult.Applied.class);
        long applied = ((FullSyncResult.Applied) result).snapshotVersion();
        System.out.println("E2E: snapshot " + expected.snapshotVersion() + " read by the test with "
                + expected.categories().size() + " categories, " + expected.professionals().size()
                + " professionals and " + expected.weeklySchedules().size() + " weekly schedules; version "
                + applied + " applied in " + elapsedMillis + " ms");
        assertThat(tables.syncState())
                .containsEntry("local_version", applied)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS");
        if (applied == expected.snapshotVersion()) {
            assertThat(tables.ids("professional_categories"))
                    .containsExactlyElementsOf(sortedIds(expected.categories(), c -> c.id()));
            assertThat(tables.ids("professionals"))
                    .containsExactlyElementsOf(sortedIds(expected.professionals(), p -> p.id()));
            assertThat(tables.ids("weekly_schedules"))
                    .containsExactlyElementsOf(sortedIds(expected.weeklySchedules(), w -> w.id()));
        } else {
            System.out.println("E2E: the catalog changed between the two reads; only completeness is checked");
            assertThat(tables.ids("professional_categories")).isNotEmpty();
        }
        assertThat(output).doesNotContain(token).doesNotContain(E2ECredentials.password());
    }

    private static <T> List<Long> sortedIds(List<T> items, ToLongFunction<T> id) {
        return items.stream().mapToLong(id).sorted().boxed().toList();
    }
}
