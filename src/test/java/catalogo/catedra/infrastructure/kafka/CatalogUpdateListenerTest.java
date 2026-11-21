package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.application.service.CatalogUpdateService;
import catalogo.catedra.domain.model.CatalogUpdateNotice;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

// El oyente del topic del catálogo de kafka.
@ExtendWith(OutputCaptureExtension.class)
class CatalogUpdateListenerTest {

    private static final String VALID = "{\"eventId\":\"81545656-d410-4dd1-8a06-18142a406205\","
            + "\"eventType\":\"CatalogUpdated\",\"newVersion\":368,"
            + "\"occurredAt\":\"2026-11-19T15:18:49.018998134Z\",\"source\":\"SCHEDULED\",\"schemaVersion\":1}";

    private final List<CatalogUpdateNotice> received = new ArrayList<>();
    private final CatalogUpdateListener listener = new CatalogUpdateListener(new CatalogUpdateReader(),
            new CatalogUpdateService(received::add));

    @Test
    void aValidNoticeIsHandedToCatedra() {
        listener.onMessage(record(VALID));

        assertThat(received).extracting(CatalogUpdateNotice::eventId)
                .containsExactly("81545656-d410-4dd1-8a06-18142a406205");
    }

    @Test
    void anInvalidNoticeIsDiscardedWithItsPositionEventIdAndReason(CapturedOutput output) {
        listener.onMessage(record(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));

        assertThat(received).isEmpty();
        assertThat(output).contains("Catalog notice at partition 0 offset 7 discarded "
                + "(eventId=81545656-d410-4dd1-8a06-18142a406205): unsupported schemaVersion 2");
    }

    @Test
    void anErrorWhileProcessingIsLoggedAndDoesNotEscape(CapturedOutput output) {
        CatalogUpdateListener failing = new CatalogUpdateListener(new CatalogUpdateReader(),
                new CatalogUpdateService(notice -> {
                    throw new IllegalStateException("unexpected");
                }));

        assertThatNoException().isThrownBy(() -> failing.onMessage(record(VALID)));

        assertThat(output).contains("Catalog notice at partition 0 offset 7 could not be processed: "
                + "IllegalStateException");
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("catedra.catalog.proyecto-de-prueba", 0, 7L, "368", value);
    }
}
