package catalogo.catedra.domain.model;

import java.time.Instant;

// Aviso de la cátedra de que publicó una versión nueva del catálogo
public record CatalogUpdateNotice(
        String eventId,
        long newVersion,
        Instant occurredAt,
        String source) {
}
