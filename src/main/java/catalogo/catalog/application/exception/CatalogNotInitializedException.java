package catalogo.catalog.application.exception;

// La copia local todavía no tiene una versión aplicada.
public class CatalogNotInitializedException extends CatalogException {

    public CatalogNotInitializedException() {
        super("The local catalog has not been initialized yet");
    }
}
