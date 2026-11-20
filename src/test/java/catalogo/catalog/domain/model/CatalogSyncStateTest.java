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

        state.recordFailure(SyncErrorType.INVALID_DATA, NOW);

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

        state.recordFailure(SyncErrorType.SOURCE_UNAVAILABLE, NOW);

        assertThat(state.isInitialized()).isFalse();
        assertThat(state.getLastSuccessfulSyncAt()).isNull();
        assertThat(state.getLastSyncType()).isNull();
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.ERROR);
    }

    @Test
    void aFailureKeepsTheTypeOfTheLastSyncThatAppliedChanges() {
        CatalogSyncState state = new CatalogSyncState();
        state.recordSuccess(SyncType.INCREMENTAL, 304, Instant.parse("2026-11-17T12:00:00Z"));

        state.recordFailure(SyncErrorType.SOURCE_UNAVAILABLE, NOW);

        assertThat(state.getLastSyncType()).isEqualTo(SyncType.INCREMENTAL);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.ERROR);
    }

    @Test
    void anUpToDateCheckAfterASuccessChangesNothing() {
        CatalogSyncState state = new CatalogSyncState();
        Instant success = Instant.parse("2026-11-17T12:00:00Z");
        state.recordSuccess(SyncType.FULL, 303, success);

        boolean changed = state.recordUpToDateCheck(NOW);

        assertThat(changed).isFalse();
        assertThat(state.getLastSyncAt()).isEqualTo(success);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
    }

    @Test
    void anUpToDateCheckAfterAFailureRecordsASuccessfulAttemptOnly() {
        CatalogSyncState state = CatalogSamples.failedState();

        boolean changed = state.recordUpToDateCheck(NOW);

        assertThat(changed).isTrue();
        assertThat(state.getLastSyncAt()).isEqualTo(NOW);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.SUCCESS);
        assertThat(state.getLastErrorType()).isNull();
        assertThat(state.getLocalVersion()).isEqualTo(7L);
        assertThat(state.getLastSuccessfulSyncAt()).isEqualTo(Instant.parse("2026-11-17T12:00:00Z"));
        assertThat(state.getLastSyncType()).isEqualTo(SyncType.FULL);
    }

    @Test
    void anUpToDateCheckBeforeAnySyncChangesNothing() {
        CatalogSyncState state = new CatalogSyncState();

        assertThat(state.recordUpToDateCheck(NOW)).isFalse();
        assertThat(state.getLastSyncAt()).isNull();
        assertThat(state.getLastSyncResult()).isNull();
    }
}
