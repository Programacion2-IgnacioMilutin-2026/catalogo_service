package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatedraCategory;
import catalogo.catedra.domain.model.CatedraProfessional;
import catalogo.catedra.domain.model.CatedraWeeklySchedule;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.infrastructure.client.dto.CatalogVersionChangesResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraCategoryResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraProfessionalResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraWeeklyScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

// Revisa lo que la cátedra publica en Redis y lo convierte al modelo.
@Component
@RequiredArgsConstructor
public class CatalogVersionChangesMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern VERSION = Pattern.compile("\\d{1,18}");

    private final CatedraEntityMapper catedraEntityMapper;

    public RedisReadResult<CatalogMetadata> metadataOf(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return new RedisReadResult.Invalid<>("the catalog metadata does not exist");
        }
        List<String> problems = new ArrayList<>();
        Long current = versionField(fields, "currentVersion", problems);
        Long oldest = versionField(fields, "oldestAvailableVersion", problems);
        if (current != null && oldest != null && current < oldest) {
            problems.add("currentVersion " + current + " is lower than oldestAvailableVersion " + oldest);
        }
        if (!problems.isEmpty()) {
            return new RedisReadResult.Invalid<>("invalid catalog metadata: " + problems);
        }
        return new RedisReadResult.Received<>(new CatalogMetadata(current, oldest));
    }

    public RedisReadResult<CatalogVersionChangesResponse> changesOf(long version, String json) {
        if (json == null) {
            return new RedisReadResult.Invalid<>("the changes of version " + version + " do not exist");
        }
        CatalogVersionChangesResponse response;
        try {
            response = JSON.readValue(json, CatalogVersionChangesResponse.class);
        } catch (JacksonException e) {
            return new RedisReadResult.Invalid<>("the changes of version " + version + " cannot be read");
        }
        List<String> problems = new ArrayList<>();
        if (response == null) {
            problems.add("empty");
        } else {
            if (response.getVersion() == null) {
                problems.add("missing version");
            } else if (response.getVersion() != version) {
                problems.add("version " + response.getVersion() + " does not match");
            }
            CatalogVersionChangesResponse.Changes changes = response.getChanges();
            if (changes == null) {
                problems.add("missing changes");
            } else {
                checkIds(changes.getProfessionalCategories(), "professionalCategories", problems);
                checkIds(changes.getProfessionals(), "professionals", problems);
                checkIds(changes.getWeeklySchedules(), "weeklySchedules", problems);
            }
        }
        if (!problems.isEmpty()) {
            return new RedisReadResult.Invalid<>("invalid changes of version " + version + ": " + problems);
        }
        return new RedisReadResult.Received<>(response);
    }

    public static Set<Long> distinctIds(List<Long> ids) {
        return new LinkedHashSet<>(ids);
    }
    
    public RedisReadResult<CatalogVersionChanges> versionChangesOf(long version, CatalogVersionChangesResponse changes,
                                                                   Map<Long, String> categoryJsons,
                                                                   Map<Long, String> professionalJsons,
                                                                   Map<Long, String> weeklyScheduleJsons) {
        List<String> problems = new ArrayList<>();
        List<CatedraCategory> categories = entities("professionalCategories",
                distinctIds(changes.getChanges().getProfessionalCategories()), categoryJsons,
                CatedraCategoryResponse.class, catedraEntityMapper::problemsOf, catedraEntityMapper::toDomain,
                CatedraCategoryResponse::getId, problems);
        List<CatedraProfessional> professionals = entities("professionals",
                distinctIds(changes.getChanges().getProfessionals()), professionalJsons,
                CatedraProfessionalResponse.class, catedraEntityMapper::problemsOf, catedraEntityMapper::toDomain,
                CatedraProfessionalResponse::getId, problems);
        List<CatedraWeeklySchedule> weeklySchedules = entities("weeklySchedules",
                distinctIds(changes.getChanges().getWeeklySchedules()), weeklyScheduleJsons,
                CatedraWeeklyScheduleResponse.class, catedraEntityMapper::problemsOf, catedraEntityMapper::toDomain,
                CatedraWeeklyScheduleResponse::getId, problems);
        if (!problems.isEmpty()) {
            return new RedisReadResult.Invalid<>("invalid entities of version " + version + ": " + problems);
        }
        return new RedisReadResult.Received<>(
                new CatalogVersionChanges(version, categories, professionals, weeklySchedules));
    }

    private static <D, M> List<M> entities(String collection, Set<Long> ids, Map<Long, String> jsons, Class<D> type,
                                           BiFunction<D, String, List<String>> problemsOf, Function<D, M> toDomain,
                                           Function<D, Long> idOf, List<String> problems) {
        List<M> entities = new ArrayList<>();
        for (Long id : ids) {
            String where = collection + " " + id;
            String json = jsons.get(id);
            if (json == null) {
                problems.add(where + ": not in its hash");
                continue;
            }
            D entity;
            try {
                entity = JSON.readValue(json, type);
            } catch (JacksonException e) {
                problems.add(where + ": unreadable");
                continue;
            }
            List<String> entityProblems = problemsOf.apply(entity, where);
            if (!entityProblems.isEmpty()) {
                problems.addAll(entityProblems);
            } else if (!Objects.equals(id, idOf.apply(entity))) {
                problems.add(where + ": id does not match its field");
            } else {
                entities.add(toDomain.apply(entity));
            }
        }
        return entities;
    }

    private static Long versionField(Map<String, String> fields, String name, List<String> problems) {
        String value = fields.get(name);
        if (value == null) {
            problems.add("missing " + name);
            return null;
        }
        if (!VERSION.matcher(value).matches()) {
            problems.add("invalid " + name);
            return null;
        }
        return Long.parseLong(value);
    }

    private static void checkIds(List<Long> ids, String collection, List<String> problems) {
        if (ids == null) {
            problems.add("missing " + collection);
        } else if (ids.contains(null)) {
            problems.add(collection + " has an empty id");
        }
    }
}
