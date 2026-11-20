package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.TechnicalIntegration;

// Quien necesita enterarse de que la integración habilitada.
public interface TechnicalIntegrationListener {

    void integrationEnabled(TechnicalIntegration integration);
}
