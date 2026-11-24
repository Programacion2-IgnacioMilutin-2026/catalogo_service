package catalogo.catalog.infrastructure.web.mapper;

import catalogo.catalog.CatalogSamples;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.infrastructure.web.dto.ProfessionalCategoryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// La conversión de una categoría a la respuesta de la app.
class ProfessionalCategoryDtoMapperTest {

    private final ProfessionalCategoryDtoMapper mapper = new ProfessionalCategoryDtoMapper();

    @Test
    void domainToResponseKeepsEveryExposedField() {
        ProfessionalCategory domain = CatalogSamples.category(1);
        domain.setEnabled(false);
        assertThat(domain).hasNoNullFieldsOrProperties();

        assertThat(mapper.toResponse(domain)).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void aCategoryWithoutDescriptionKeepsItEmpty() {
        ProfessionalCategory domain = CatalogSamples.category(2);
        domain.setDescription(null);

        ProfessionalCategoryResponse response = mapper.toResponse(domain);

        assertThat(response.getDescription()).isNull();
        assertThat(response).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void nullMapsToNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
