package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Estado único de la sincronización.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSyncState {
    private Long localVersion;
    private Instant lastSyncAt;
    private Instant lastSuccessfulSyncAt;
    private SyncType lastSyncType;
    private SyncResult lastSyncResult;
    private SyncErrorType lastErrorType;

    public boolean isInitialized() {
        return localVersion != null;
    }

    public void recordSuccess(SyncType syncType, long appliedVersion, Instant at) {
        localVersion = appliedVersion;
        lastSyncAt = at;
        lastSuccessfulSyncAt = at;
        lastSyncType = syncType;
        lastSyncResult = SyncResult.SUCCESS;
        lastErrorType = null;
    }

    public void recordFailure(SyncErrorType errorType, Instant at) {
        lastSyncAt = at;
        lastSyncResult = SyncResult.ERROR;
        lastErrorType = errorType;
    }

    public boolean recordUpToDateCheck(Instant at) {
        if (lastSyncResult != SyncResult.ERROR) {
            return false;
        }
        lastSyncAt = at;
        lastSyncResult = SyncResult.SUCCESS;
        lastErrorType = null;
        return true;
    }
}
