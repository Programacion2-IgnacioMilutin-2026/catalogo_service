package catalogo.catalog.application.exception;

// El profesional pedido no está en la copia local.
public class ProfessionalNotFoundException extends CatalogException {

    public ProfessionalNotFoundException(Long professionalId) {
        super("Could not find professional with id: " + professionalId);
    }
}
