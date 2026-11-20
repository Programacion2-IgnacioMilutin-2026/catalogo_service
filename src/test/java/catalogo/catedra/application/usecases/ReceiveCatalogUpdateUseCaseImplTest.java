package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Recibir un aviso.
class ReceiveCatalogUpdateUseCaseImplTest {

    @Test
    void aValidNoticeIsHandedToTheHandler() {
        List<CatalogUpdateNotice> handled = new ArrayList<>();
        CatalogUpdateNotice notice = new CatalogUpdateNotice("81545656-d410-4dd1-8a06-18142a406205", 368,
                Instant.parse("2026-11-19T15:18:49.018998134Z"), "SCHEDULED");

        new ReceiveCatalogUpdateUseCaseImpl(handled::add).receiveCatalogUpdate(notice);

        assertThat(handled).containsExactly(notice);
    }
}
