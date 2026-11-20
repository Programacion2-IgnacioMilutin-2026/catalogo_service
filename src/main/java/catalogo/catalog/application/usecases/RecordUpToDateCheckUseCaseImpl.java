package catalogo.catalog.application.usecases;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.ports.in.RecordUpToDateCheckUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// Registra una verificación con el catálogo al día (que este igual a la catedra, no al dia de tiempo), solo si corrige un fracaso anterior, en su propia transacción.
@Component
@RequiredArgsConstructor
public class RecordUpToDateCheckUseCaseImpl implements RecordUpToDateCheckUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void recordUpToDateCheck() {
        CatalogSyncState state = catalogSyncStateRepository.get();
        if (state.recordUpToDateCheck(clock.instant())) {
            catalogSyncStateRepository.save(state);
        }
    }
}
