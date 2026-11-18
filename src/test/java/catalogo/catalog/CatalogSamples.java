package catalogo.catalog;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncResult;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.model.WeeklySchedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

// Modelos de ejemplo para los tests.
public final class CatalogSamples {

    private CatalogSamples() {
    }

    public static ProfessionalCategory category(long id) {
        return ProfessionalCategory.builder()
                .id(id)
                .name("Categoría " + id)
                .description("Descripción de la categoría " + id)
                .enabled(true)
                .build();
    }

    public static Professional professional(long id, ProfessionalCategory category) {
        return Professional.builder()
                .id(id)
                .firstName("Nombre " + id)
                .lastName("Apellido " + id)
                .enabled(true)
                .category(category)
                .build();
    }

    public static WeeklySchedule weeklySchedule(long id, Professional professional) {
        return WeeklySchedule.builder()
                .id(id)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30)
                .enabled(true)
                .professional(professional)
                .build();
    }

    public static CatalogSyncState failedState() {
        return CatalogSyncState.builder()
                .localVersion(7L)
                .lastSyncAt(Instant.parse("2026-11-17T12:30:00Z"))
                .lastSuccessfulSyncAt(Instant.parse("2026-11-17T12:00:00Z"))
                .lastSyncType(SyncType.FULL)
                .lastSyncResult(SyncResult.ERROR)
                .lastErrorType(SyncErrorType.SOURCE_UNAVAILABLE)
                .build();
    }
}
