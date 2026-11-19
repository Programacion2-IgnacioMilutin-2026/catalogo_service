package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.infrastructure.client.dto.CatalogSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

// Revisa que la respuesta del snapshot tenga la versión y las tres colecciones, y la convierte al modelo de dominio.
@Component
@RequiredArgsConstructor
public class CatalogSnapshotMapper {

    private final CatedraEntityMapper catedraEntityMapper;

    public List<String> problemsOf(CatalogSnapshotResponse response) {
        List<String> problems = new ArrayList<>();
        if (response == null) {
            return List.of("empty response");
        }
        if (response.getSnapshotVersion() == null) {
            problems.add("missing snapshotVersion");
        }
        checkEach(response.getProfessionalCategories(), "professionalCategories", catedraEntityMapper::problemsOf,
                problems);
        checkEach(response.getProfessionals(), "professionals", catedraEntityMapper::problemsOf, problems);
        checkEach(response.getWeeklySchedules(), "weeklySchedules", catedraEntityMapper::problemsOf, problems);
        return List.copyOf(problems);
    }

    public CatalogSnapshot toDomain(CatalogSnapshotResponse response) {
        return new CatalogSnapshot(
                response.getSnapshotVersion(),
                response.getProfessionalCategories().stream().map(catedraEntityMapper::toDomain).toList(),
                response.getProfessionals().stream().map(catedraEntityMapper::toDomain).toList(),
                response.getWeeklySchedules().stream().map(catedraEntityMapper::toDomain).toList());
    }

    private static <T> void checkEach(List<T> items, String collection,
                                      BiFunction<T, String, List<String>> problemsOfItem, List<String> problems) {
        if (items == null) {
            problems.add("missing " + collection);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            problems.addAll(problemsOfItem.apply(items.get(i), collection + "[" + i + "]"));
        }
    }
}
