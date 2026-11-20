package catalogo.synchronization.domain.ports.in;

import catalogo.synchronization.domain.model.VerificationResult;

// Puerto de entrada para verificar la versión del catálogo contra la que publica la cátedra.
public interface VerifyCatalogVersionUseCase {
    VerificationResult verifyCatalogVersion();
}
