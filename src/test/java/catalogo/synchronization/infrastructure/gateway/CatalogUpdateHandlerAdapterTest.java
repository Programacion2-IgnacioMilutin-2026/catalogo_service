package catalogo.synchronization.infrastructure.gateway;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.IncrementalSyncResult;
import catalogo.synchronization.domain.model.VerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// El adaptador que recibe los avisos de catedra. Los pasa a la sincronización incremental con sus valores.
class CatalogUpdateHandlerAdapterTest {

    private record Call(String eventId, long newVersion) {
    }

    @Test
    void aNoticeIsProcessedWithItsEventIdAndNewVersion() {
        List<Call> calls = new ArrayList<>();
        SynchronizationService service = new SynchronizationService(
                () -> new FullSyncResult.Failed("not used"),
                VerificationResult.Skipped::new,
                (eventId, newVersion) -> {
                    calls.add(new Call(eventId, newVersion));
                    return new IncrementalSyncResult.UpToDate(newVersion);
                });

        new CatalogUpdateHandlerAdapter(service).handleCatalogUpdate(new CatalogUpdateNotice(
                "81545656-d410-4dd1-8a06-18142a406205", 368, Instant.parse("2026-11-19T15:18:49Z"), "SCHEDULED"));

        assertThat(calls).containsExactly(new Call("81545656-d410-4dd1-8a06-18142a406205", 368));
    }
}
