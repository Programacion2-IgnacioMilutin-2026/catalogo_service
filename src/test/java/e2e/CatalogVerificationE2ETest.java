package e2e;

import catalogo.CatalogoApplication;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetCatalogMetadataUseCase;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.VerificationResult;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La verificación contra la cátedra real de la verificacion del catalogo.
@Tag("e2e")
@EnabledIf(value = "e2e.E2ECredentials#available", disabledReason = "No catedra credentials in the local .env: E2E tests skipped")
@SpringBootTest(classes = CatalogoApplication.class, properties = {
        "catalogo.synchronization.verification.enabled=true",
        "catalogo.synchronization.verification.interval=1h"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CatalogVerificationE2ETest {

    private static final String VERIFICATION_ENDED =
            "(?s).*(Catalog verification: up to date at version|Catalog verification applied versions"
                    + "|Catalog verification failed).*";

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private GetCatalogMetadataUseCase getCatalogMetadataUseCase;

    @Autowired
    private SynchronizationService synchronizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theCatalogIsLoadedUpdatedIncrementallyAndRebuiltAgainstTheRealCatedra(CapturedOutput output) {
        CatalogTables tables = new CatalogTables(jdbcTemplate);

        // 1. Base vacía: la verificación del arranque espera el login, carga el snapshot y consulta Redis.
        await().atMost(Duration.ofSeconds(120)).until(() -> output.getOut().matches(VERIFICATION_ENDED));
        TechnicalSession session = technicalSessionService.getTechnicalSession().orElseThrow();
        CatalogMetadata atStartup = metadata();
        Map<String, Object> loaded = tables.syncState();
        System.out.println("E2E 1: startup verification left local version " + loaded.get("local_version")
                + " with " + loaded.get("last_sync_type") + "; Redis currentVersion " + atStartup.currentVersion()
                + ", oldestAvailableVersion " + atStartup.oldestAvailableVersion());
        assertThat(output.getOut()).contains("Catalog verification: no local version, requesting the full synchronization");
        assertThat(loaded).containsEntry("last_sync_result", "SUCCESS");
        assertThat((Long) loaded.get("local_version")).isNotNull().isLessThanOrEqualTo(atStartup.currentVersion());
        int categories = tables.ids("professional_categories").size();
        int professionals = tables.ids("professionals").size();
        int schedules = tables.ids("weekly_schedules").size();

        // 2. Versión local tres por detrás, dentro de la ventana: se aplica de forma incremental.
        CatalogMetadata before = metadata();
        long behind = before.currentVersion() - 3;
        assertThat(behind).as("three versions behind is inside the history").isGreaterThanOrEqualTo(before.oldestAvailableVersion());
        lowerLocalVersionTo(behind);
        long start = System.nanoTime();
        VerificationResult incremental = synchronizationService.verifyCatalogVersion();
        long incrementalMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        CatalogMetadata after = metadata();
        System.out.println("E2E 2: from version " + behind + " the verification answered " + incremental + " in "
                + incrementalMillis + " ms; Redis currentVersion " + after.currentVersion());
        assertThat(incremental).isInstanceOf(VerificationResult.Updated.class);
        assertThat(((VerificationResult.Updated) incremental).localVersion())
                .isBetween(before.currentVersion(), after.currentVersion());
        assertThat(tables.syncState())
                .containsEntry("last_sync_type", "INCREMENTAL")
                .containsEntry("last_sync_result", "SUCCESS");
        assertThat(output.getOut()).contains("Catalog verification applied versions " + (behind + 1) + " to");
        assertThat(tables.ids("professional_categories")).hasSize(categories);
        assertThat(tables.ids("professionals")).hasSize(professionals);
        assertThat(tables.ids("weekly_schedules")).hasSize(schedules);

        // 3. Versión local anterior a la ventana: discontinuidad, reconstrucción con el snapshot.
        CatalogMetadata beforeRebuild = metadata();
        long tooOld = beforeRebuild.oldestAvailableVersion() - 1;
        lowerLocalVersionTo(tooOld);
        start = System.nanoTime();
        VerificationResult rebuilt = synchronizationService.verifyCatalogVersion();
        long rebuildMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        CatalogMetadata afterRebuild = metadata();
        System.out.println("E2E 3: from version " + tooOld + " the verification answered " + rebuilt + " in "
                + rebuildMillis + " ms; Redis currentVersion " + afterRebuild.currentVersion());
        assertThat(rebuilt).isInstanceOf(VerificationResult.Updated.class);
        assertThat(((VerificationResult.Updated) rebuilt).localVersion())
                .isBetween(beforeRebuild.currentVersion(), afterRebuild.currentVersion());
        assertThat(output.getOut()).contains("Catalog verification found a discontinuity, requesting the full synchronization");
        if (beforeRebuild.currentVersion() == afterRebuild.currentVersion()) {
            assertThat(tables.syncState()).containsEntry("last_sync_type", "FULL");
        } else {
            System.out.println("E2E 3: the catedra published a version during the rebuild; the type is not checked");
        }

        assertThat(output)
                .doesNotContain(session.token())
                .doesNotContain(session.integration().redisPassword())
                .doesNotContain(E2ECredentials.password());
    }

    private CatalogMetadata metadata() {
        CatalogMetadataResult result = getCatalogMetadataUseCase.getCatalogMetadata();
        assertThat(result).as("metadata read by the test").isInstanceOf(CatalogMetadataResult.Obtained.class);
        return ((CatalogMetadataResult.Obtained) result).metadata();
    }

    // Solo en la base descartable del test: la cátedra no se toca.
    private void lowerLocalVersionTo(long version) {
        jdbcTemplate.update("UPDATE catalog_sync_state SET local_version = ?", version);
    }
}
