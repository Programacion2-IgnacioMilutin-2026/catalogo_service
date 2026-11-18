package catalogo.catalog.application.exception;

import java.util.List;

// Una versión que no se puede aplicar porque referencia entidades que no están ni en la copia ni en la versión.
public class CatalogVersionNotVerifiableException extends CatalogException {

    public CatalogVersionNotVerifiableException(long version, List<String> problems) {
        super("Catalog version " + version + " cannot be verified: " + problems);
    }
}
