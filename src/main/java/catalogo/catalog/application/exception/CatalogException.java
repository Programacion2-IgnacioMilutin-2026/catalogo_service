package catalogo.catalog.application.exception;

// Excepción base del slice catalog.
public class CatalogException extends RuntimeException {

    protected CatalogException(String message) {
        super(message);
    }
}
