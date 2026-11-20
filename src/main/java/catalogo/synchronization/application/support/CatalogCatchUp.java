package catalogo.synchronization.application.support;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ApplyCatalogVersionUseCase;
import catalogo.catalog.domain.ports.in.GetCatalogSyncStateUseCase;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.RecordUpToDateCheckUseCase;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.catedra.domain.ports.in.GetCatalogMetadataUseCase;
import catalogo.catedra.domain.ports.in.GetCatalogVersionChangesUseCase;
import catalogo.synchronization.domain.model.CatchUpResult;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.VerificationPolicy;
import catalogo.synchronization.domain.ports.in.SynchronizeFullCatalogUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// Pone la copia local al día con la versión vigente de Redis. La usan la verificación y el aviso de Kafka, con el candado ya tomado. Fachada, utiliza puertos de otros slices.
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogCatchUp {

    private final GetCatalogSyncStateUseCase getCatalogSyncStateUseCase;
    private final SynchronizeFullCatalogUseCase synchronizeFullCatalogUseCase;
    private final GetCatalogMetadataUseCase getCatalogMetadataUseCase;
    private final GetCatalogVersionChangesUseCase getCatalogVersionChangesUseCase;
    private final ApplyCatalogVersionUseCase applyCatalogVersionUseCase;
    private final RecordSyncFailureUseCase recordSyncFailureUseCase;
    private final RecordUpToDateCheckUseCase recordUpToDateCheckUseCase;
    private final VerificationPolicy verificationPolicy;

    public CatchUpResult catchUp(String name) {
        return new Run(name).start();
    }

    private final class Run {

        private final String name;

        private Run(String name) {
            this.name = name;
        }

        private CatchUpResult start() {
            if (!getCatalogSyncStateUseCase.getCatalogSyncState().isInitialized()) {
                log.info("{}: no local version, requesting the full synchronization", name);
                return afterFullSynchronization();
            }
            return pass(false);
        }

        // Una pasada. Lee la metadata, compara y aplica lo que falta.
        private CatchUpResult pass(boolean afterFull) {
            long localVersion = getCatalogSyncStateUseCase.getCatalogSyncState().getLocalVersion();
            return switch (getCatalogMetadataUseCase.getCatalogMetadata()) {
                case CatalogMetadataResult.Unavailable unavailable ->
                        fail(SyncErrorType.SOURCE_UNAVAILABLE, unavailable.reason());
                case CatalogMetadataResult.Rejected rejected ->
                        fail(SyncErrorType.INTEGRATION_REJECTED, rejected.reason());
                case CatalogMetadataResult.Invalid invalid -> discontinuity(afterFull, invalid.reason());
                case CatalogMetadataResult.Obtained obtained -> compare(localVersion, obtained.metadata(), afterFull);
            };
        }

        private CatchUpResult compare(long localVersion, CatalogMetadata metadata, boolean afterFull) {
            if (localVersion == metadata.currentVersion()) {
                return upToDate(localVersion);
            }
            if (localVersion < metadata.oldestAvailableVersion() || localVersion > metadata.currentVersion()) {
                return discontinuity(afterFull, "local version " + localVersion + " is outside the available history "
                        + metadata.oldestAvailableVersion() + " to " + metadata.currentVersion());
            }
            log.info("{}: local version {} is behind the current version {}", name, localVersion,
                    metadata.currentVersion());
            return applyPendingVersions(localVersion, metadata.currentVersion(), afterFull);
        }

        private CatchUpResult applyPendingVersions(long localVersion, long currentVersion, boolean afterFull) {
            for (long version = localVersion + 1; version <= currentVersion; version++) {
                CatchUpResult failure = switch (getCatalogVersionChangesUseCase.getCatalogVersionChanges(version)) {
                    case CatalogVersionChangesResult.Unavailable unavailable ->
                            fail(SyncErrorType.SOURCE_UNAVAILABLE, unavailable.reason());
                    case CatalogVersionChangesResult.Rejected rejected ->
                            fail(SyncErrorType.INTEGRATION_REJECTED, rejected.reason());
                    case CatalogVersionChangesResult.Invalid invalid -> discontinuity(afterFull, invalid.reason());
                    case CatalogVersionChangesResult.Obtained obtained -> apply(obtained.changes(), afterFull);
                };
                if (failure != null) {
                    return failure;
                }
            }
            log.info("{} applied versions {} to {}: local version {}", name, localVersion + 1, currentVersion,
                    currentVersion);
            return new CatchUpResult.Updated(currentVersion);
        }

        private CatchUpResult apply(CatalogVersionChanges changes, boolean afterFull) {
            int maxAttempts = verificationPolicy.persistenceMaxAttempts();
            String lastFailure = "";
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (!waitBefore(attempt)) {
                    return fail(SyncErrorType.PERSISTENCE_FAILURE, "Interrupted while waiting to apply version "
                            + changes.version());
                }
                ApplyCatalogVersionResult result;
                try {
                    result = applyCatalogVersionUseCase.applyCatalogVersion(changes.version(), categoriesOf(changes),
                            professionalsOf(changes), weeklySchedulesOf(changes));
                } catch (RuntimeException e) {
                    lastFailure = e.getClass().getSimpleName();
                    log.warn("Catalog version {} attempt {}/{} could not be applied: {}", changes.version(), attempt,
                            maxAttempts, lastFailure);
                    continue;
                }
                return switch (result) {
                    case ApplyCatalogVersionResult.Applied applied -> {
                        log.info("Catalog version {} applied: {} categories, {} professionals, {} weekly schedules",
                                applied.version(), changes.categories().size(), changes.professionals().size(),
                                changes.weeklySchedules().size());
                        yield null;
                    }
                    case ApplyCatalogVersionResult.NotVerifiable notVerifiable -> discontinuity(afterFull,
                            "Catalog version " + changes.version() + " cannot be verified: " + notVerifiable.problems());
                };
            }
            return fail(SyncErrorType.PERSISTENCE_FAILURE, "Catalog version " + changes.version()
                    + " not applied after " + maxAttempts + " attempts: " + lastFailure);
        }

        private CatchUpResult discontinuity(boolean afterFull, String reason) {
            if (afterFull) {
                return fail(SyncErrorType.INVALID_DATA, "The discontinuity persists after the full synchronization: "
                        + reason);
            }
            log.warn("{} found a discontinuity, requesting the full synchronization: {}", name, reason);
            return afterFullSynchronization();
        }

        private CatchUpResult afterFullSynchronization() {
            return switch (synchronizeFullCatalogUseCase.synchronizeFullCatalog()) {
                case FullSyncResult.Applied applied -> {
                    log.info("{}: full synchronization applied version {}, checking Redis once more", name,
                            applied.snapshotVersion());
                    CatchUpResult secondPass = pass(true);
                    yield secondPass instanceof CatchUpResult.UpToDate upToDate
                            ? new CatchUpResult.Updated(upToDate.localVersion())
                            : secondPass;
                }
                case FullSyncResult.Failed failed -> new CatchUpResult.Failed(failed.reason());
                case FullSyncResult.Discarded discarded -> {
                    log.error("{}: the full synchronization was discarded although the lock was held", name);
                    yield new CatchUpResult.Failed("The full synchronization was discarded");
                }
            };
        }

        private CatchUpResult upToDate(long localVersion) {
            log.info("{}: up to date at version {}", name, localVersion);
            try {
                recordUpToDateCheckUseCase.recordUpToDateCheck();
            } catch (RuntimeException e) {
                log.error("{}: the up-to-date check could not be recorded: {}", name, e.getClass().getSimpleName());
            }
            return new CatchUpResult.UpToDate(localVersion);
        }

        private CatchUpResult fail(SyncErrorType errorType, String reason) {
            log.warn("{} failed with {}: {}", name, errorType, reason);
            try {
                recordSyncFailureUseCase.recordSyncFailure(errorType);
            } catch (RuntimeException e) {
                log.error("{}: the failure could not be recorded: {}", name, e.getClass().getSimpleName());
            }
            return new CatchUpResult.Failed(errorType + ": " + reason);
        }

        private boolean waitBefore(int attempt) {
            Duration delay = verificationPolicy.delayBeforeAttempt(attempt);
            if (delay.isZero()) {
                return true;
            }
            try {
                Thread.sleep(delay);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    // Traduce la versión a los modelos de la copia local.

    private static List<ProfessionalCategory> categoriesOf(CatalogVersionChanges changes) {
        return changes.categories().stream()
                .map(category -> ProfessionalCategory.builder()
                        .id(category.id())
                        .name(category.name())
                        .description(category.description())
                        .enabled(category.enabled())
                        .build())
                .toList();
    }

    private static List<Professional> professionalsOf(CatalogVersionChanges changes) {
        return changes.professionals().stream()
                .map(professional -> Professional.builder()
                        .id(professional.id())
                        .firstName(professional.firstName())
                        .lastName(professional.lastName())
                        .enabled(professional.enabled())
                        .category(ProfessionalCategory.builder().id(professional.categoryId()).build())
                        .build())
                .toList();
    }

    private static List<WeeklySchedule> weeklySchedulesOf(CatalogVersionChanges changes) {
        return changes.weeklySchedules().stream()
                .map(schedule -> WeeklySchedule.builder()
                        .id(schedule.id())
                        .dayOfWeek(schedule.dayOfWeek())
                        .startTime(schedule.startTime())
                        .endTime(schedule.endTime())
                        .slotDurationMinutes(schedule.slotDurationMinutes())
                        .enabled(schedule.enabled())
                        .professional(Professional.builder().id(schedule.professionalId()).build())
                        .build())
                .toList();
    }
}
