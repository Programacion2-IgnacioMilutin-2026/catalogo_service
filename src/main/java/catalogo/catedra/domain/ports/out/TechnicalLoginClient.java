package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.TechnicalLoginResult;

public interface TechnicalLoginClient {

    TechnicalLoginResult login();
}
