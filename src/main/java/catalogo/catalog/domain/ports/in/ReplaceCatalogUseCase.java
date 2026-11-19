package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;

import java.util.List;

// Puerto de entrada que catalog ofrece para reemplazar los datos del catalogo. Siempre queda igual a la recibida (Transaccional).
public interface ReplaceCatalogUseCase {
    void replaceCatalog(long snapshotVersion, List<ProfessionalCategory> categories,
                        List<Professional> professionals, List<WeeklySchedule> weeklySchedules);
}