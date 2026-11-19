package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;

import java.util.List;

// Puerto de entrada que catalog ofrece para aplicar una version incremental.
public interface ApplyCatalogVersionUseCase {
    ApplyCatalogVersionResult applyCatalogVersion(long version, List<ProfessionalCategory> categories,
                                                  List<Professional> professionals,
                                                  List<WeeklySchedule> weeklySchedules);
}
