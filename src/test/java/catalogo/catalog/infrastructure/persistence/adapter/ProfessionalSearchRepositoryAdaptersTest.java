package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// La búsqueda de profesionales contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProfessionalSearchRepositoryAdaptersTest {

    private static final ProfessionalSearchCriteria NO_FILTERS = new ProfessionalSearchCriteria();

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void catalogWithOneCategory() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Kinesiología", null, true);
    }

    @Test
    void professionalsComeInSpanishAlphabeticalOrderByLastNameFirstNameAndId() {
        tables.insertProfessional(1, 1, "Ana", "Zárate", true);
        tables.insertProfessional(2, 1, "Ana", "Muñoz", true);
        tables.insertProfessional(3, 1, "Bruno", "Gómez", false);
        tables.insertProfessional(4, 1, "Ana", "Munro", true);
        tables.insertProfessional(5, 1, "Ana", "Gómez", true);
        tables.insertProfessional(6, 1, "ana", "gomez", true);
        tables.insertProfessional(7, 1, "Ana", "de la Fuente", false);
        tables.insertProfessional(8, 1, "Ana", "Álvarez", true);
        tables.insertProfessional(9, 1, "Lucía", "Núñez", true);

        List<Professional> found = professionalRepository.findMatching(NO_FILTERS, 0, 100);

        assertThat(found).extracting(Professional::getId).containsExactly(8L, 7L, 5L, 6L, 3L, 4L, 2L, 9L, 1L);
        assertThat(found.getFirst())
                .returns("Ana", Professional::getFirstName)
                .returns("Álvarez", Professional::getLastName)
                .returns(true, Professional::isEnabled);
        assertThat(found.getFirst().getCategory().getName()).isEqualTo("Kinesiología");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("nameSearches")
    void everyWordOfTheNameIsInTheFirstOrLastNameIgnoringCaseAndAccents(String name, List<Long> expectedIds) {
        tables.insertProfessional(1, 1, "Tomás", "Ferreyra", true);
        tables.insertProfessional(2, 1, "Valentina", "Ibáñez", true);
        tables.insertProfessional(3, 1, "Lucía", "Núñez", true);
        tables.insertProfessional(4, 1, "Mariana", "Paz", true);
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.builder().name(name).build();

        List<Professional> found = professionalRepository.findMatching(criteria, 0, 100);

        assertThat(found).extracting(Professional::getId).containsExactlyElementsOf(expectedIds);
        assertThat(professionalRepository.countMatching(criteria)).isEqualTo(expectedIds.size());
    }

    static Stream<Arguments> nameSearches() {
        return Stream.of(
                arguments("tomás ferreyra", List.of(1L)),
                arguments("ferreyra tomás", List.of(1L)),
                arguments("TOM ferr", List.of(1L)),
                arguments("  tomas  ", List.of(1L)),
                arguments("tomás gómez", List.of()),
                arguments("nunez", List.of(3L)),
                arguments("nuñez", List.of(3L)),
                arguments("NÚÑEZ", List.of(3L)),
                arguments("ibanez", List.of(2L)),
                arguments("ana", List.of(4L)),
                arguments("%", List.of()),
                arguments("_", List.of()),
                arguments("\\", List.of()));
    }

    @Test
    void withoutFiltersEveryProfessionalIsCounted() {
        tables.insertProfessional(1, 1, "Tomás", "Ferreyra", true);
        tables.insertProfessional(2, 1, "Valentina", "Ibáñez", false);

        assertThat(professionalRepository.countMatching(NO_FILTERS)).isEqualTo(2);
    }
}
