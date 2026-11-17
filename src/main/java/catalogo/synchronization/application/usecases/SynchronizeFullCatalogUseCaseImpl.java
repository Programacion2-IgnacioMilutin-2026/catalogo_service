package catalogo.synchronization.application.usecases;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.CatalogSnapshotResult;
import catalogo.catedra.domain.ports.in.GetCatalogSnapshotUseCase;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.ports.in.SynchronizeFullCatalogUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Sincronización completa. Pide el snapshot a la cátedra y reemplaza con él la copia local. Fachada, utiliza puertos de Catedra y Catalog.
@Slf4j
@Component
@RequiredArgsConstructor
public class SynchronizeFullCatalogUseCaseImpl implements SynchronizeFullCatalogUseCase {

    private final SynchronizationLock synchronizationLock;
    private final GetCatalogSnapshotUseCase getCatalogSnapshotUseCase;
    private final ReplaceCatalogUseCase replaceCatalogUseCase;
    private final RecordSyncFailureUseCase recordSyncFailureUseCase;

    @Override
    public FullSyncResult synchronizeFullCatalog() {
        if (!synchronizationLock.tryAcquire()) {
            log.info("Full catalog synchronization discarded: another synchronization is in progress");
            return new FullSyncResult.Discarded();
        }
        try {
            return switch (getCatalogSnapshotUseCase.getCatalogSnapshot()) {
                case CatalogSnapshotResult.Obtained obtained -> apply(obtained.snapshot());
                case CatalogSnapshotResult.Unavailable unavailable ->
                        fail(SyncErrorType.SOURCE_UNAVAILABLE, unavailable.reason());
                case CatalogSnapshotResult.Rejected rejected ->
                        fail(SyncErrorType.INTEGRATION_REJECTED, rejected.reason());
                case CatalogSnapshotResult.Invalid invalid -> fail(SyncErrorType.INVALID_DATA, invalid.reason());
            };
        } finally {
            synchronizationLock.release();
        }
    }

    private FullSyncResult apply(CatalogSnapshot snapshot) {
        LocalCopy copy = localCopyOf(snapshot);
        try {
            replaceCatalogUseCase.replaceCatalog(snapshot.snapshotVersion(), copy.categories(), copy.professionals(),
                    copy.weeklySchedules());
        } catch (RuntimeException e) {
            return fail(SyncErrorType.PERSISTENCE_FAILURE,
                    "The local copy could not be replaced: " + e.getClass().getSimpleName());
        }
        log.info("Full catalog synchronization applied version {}: {} categories, {} professionals, {} weekly schedules",
                snapshot.snapshotVersion(), copy.categories().size(), copy.professionals().size(),
                copy.weeklySchedules().size());
        return new FullSyncResult.Applied(snapshot.snapshotVersion());
    }

    private FullSyncResult fail(SyncErrorType errorType, String reason) {
        log.warn("Full catalog synchronization failed with {}: {}", errorType, reason);
        try {
            recordSyncFailureUseCase.recordSyncFailure(SyncType.FULL, errorType);
        } catch (RuntimeException e) {
            log.error("The failed full catalog synchronization could not be recorded: {}", e.getClass().getSimpleName());
        }
        return new FullSyncResult.Failed(errorType + ": " + reason);
    }

    // Traduce el snapshot a los modelos de la copia local.
    private static LocalCopy localCopyOf(CatalogSnapshot snapshot) {
        List<ProfessionalCategory> categories = snapshot.categories().stream()
                .map(category -> ProfessionalCategory.builder()
                        .id(category.id())
                        .name(category.name())
                        .description(category.description())
                        .enabled(category.enabled())
                        .build())
                .toList();
        Map<Long, ProfessionalCategory> categoriesById = byId(categories, ProfessionalCategory::getId);
        List<Professional> professionals = snapshot.professionals().stream()
                .map(professional -> Professional.builder()
                        .id(professional.id())
                        .firstName(professional.firstName())
                        .lastName(professional.lastName())
                        .enabled(professional.enabled())
                        .category(categoriesById.get(professional.categoryId()))
                        .build())
                .toList();
        Map<Long, Professional> professionalsById = byId(professionals, Professional::getId);
        List<WeeklySchedule> weeklySchedules = snapshot.weeklySchedules().stream()
                .map(schedule -> WeeklySchedule.builder()
                        .id(schedule.id())
                        .dayOfWeek(schedule.dayOfWeek())
                        .startTime(schedule.startTime())
                        .endTime(schedule.endTime())
                        .slotDurationMinutes(schedule.slotDurationMinutes())
                        .enabled(schedule.enabled())
                        .professional(professionalsById.get(schedule.professionalId()))
                        .build())
                .toList();
        return new LocalCopy(categories, professionals, weeklySchedules);
    }

    private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private record LocalCopy(List<ProfessionalCategory> categories, List<Professional> professionals,
                             List<WeeklySchedule> weeklySchedules) {
    }
}
