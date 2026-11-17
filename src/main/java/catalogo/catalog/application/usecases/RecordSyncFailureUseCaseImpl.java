package catalogo.catalog.application.usecases;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// Registra una sincronización fallida como último intento, en su propia transacción.
@Component
@RequiredArgsConstructor
public class RecordSyncFailureUseCaseImpl implements RecordSyncFailureUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void recordSyncFailure(SyncType syncType, SyncErrorType errorType) {
        CatalogSyncState state = catalogSyncStateRepository.get();
        state.recordFailure(syncType, errorType, clock.instant());
        catalogSyncStateRepository.save(state);
    }
}
