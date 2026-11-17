package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.TechnicalSession;

public interface TechnicalSessionRepository {

    TechnicalSession get();

    void save(TechnicalSession technicalSession);
}
