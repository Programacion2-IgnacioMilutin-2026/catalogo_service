package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.domain.model.CatalogUpdateNotice;

// Lo que resulta de leer un mensaje del topic del catálogo.
public sealed interface CatalogUpdateReading {

    record Valid(CatalogUpdateNotice notice) implements CatalogUpdateReading {
    }

    record Invalid(String eventId, String reason) implements CatalogUpdateReading {
    }
}
