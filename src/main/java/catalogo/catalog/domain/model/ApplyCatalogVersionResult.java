package catalogo.catalog.domain.model;

import java.util.List;

// Resultado de aplicar una versión incremental.
public sealed interface ApplyCatalogVersionResult {

    record Applied(long version) implements ApplyCatalogVersionResult {
    }

    // Una referencia no está ni en la copia ni en la versión.
    record NotVerifiable(List<String> problems) implements ApplyCatalogVersionResult {

        public NotVerifiable {
            problems = List.copyOf(problems);
        }
    }
}
