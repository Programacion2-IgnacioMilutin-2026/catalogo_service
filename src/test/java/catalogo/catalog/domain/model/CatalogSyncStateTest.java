package catalogo.catalog.domain.model;

import catalogo.catalog.CatalogSamples;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Qué cambia en el estado de sincronización con un éxito y con un fracaso.
class CatalogSyncStateTest {

    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");

    @Test
    void anEmptyStateIsNotInitialized() {
        assertThat(new CatalogSyncState().isInitialized()).isFalse();
    }

    @Test
    void aSuccessUpdatesEverythingAndClearsThePreviousError() {
        CatalogSyncState state = CatalogSamples.failedState();

        state.recordSuccess(SyncType.FULL, 303, NOW);

        assertThat(state.isInitialized()).isTrue();
        assertThat(state.getLocalVersion()).isEqualTo(303L);
        assertThat(state.getLastSyncAt()).isEqualTo(NOW);
        assertThat(state.getLastSuccessfulSyncAt()).isEqualTo(NOW);
        assertThat(state.getLastSyncType()).isEqualTo(SyncType.FULL);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
        assertThat(state.getLastErrorType()).isNull();
    }

    @Test
    void aFailureOnlyChangesTheLastAttempt() {
        CatalogSyncState state = new CatalogSyncState();
        state.recordSuccess(SyncType.FULL, 303, Instant.parse("2026-11-17T12:00:00Z"));

        state.recordFailure(SyncType.FULL, SyncErrorType.INVALID_DATA, NOW);

        assertThat(state.getLocalVersion()).isEqualTo(303L);
        assertThat(state.getLastSuccessfulSyncAt()).isEqualTo(Instant.parse("2026-11-17T12:00:00Z"));
        assertThat(state.getLastSyncAt()).isEqualTo(NOW);
        assertThat(state.getLastSyncType()).isEqualTo(SyncType.FULL);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.ERROR);
        assertThat(state.getLastErrorType()).isEqualTo(SyncErrorType.INVALID_DATA);
    }

    @Test
    void aFailureBeforeAnySuccessLeavesTheCatalogNotInitialized() {
        CatalogSyncState state = new CatalogSyncState();

        state.recordFailure(SyncType.FULL, SyncErrorType.SOURCE_UNAVAILABLE, NOW);

        assertThat(state.isInitialized()).isFalse();
        assertThat(state.getLastSuccessfulSyncAt()).isNull();
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.ERROR);
    }
}
