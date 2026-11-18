package catalogo.catalog.infrastructure.persistence.mapper;

import catalogo.catalog.CatalogSamples;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.infrastructure.persistence.entity.CatalogSyncStateEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Ida y vuelta de los mappers de persistencia de la copia local.
class CatalogPersistenceMappersTest {

    private final ProfessionalCategoryMapper categoryMapper = new ProfessionalCategoryMapper();
    private final ProfessionalMapper professionalMapper = new ProfessionalMapper(categoryMapper);
    private final WeeklyScheduleMapper weeklyScheduleMapper = new WeeklyScheduleMapper(professionalMapper);
    private final CatalogSyncStateMapper syncStateMapper = new CatalogSyncStateMapper();

    @Test
    void aCategoryKeepsEveryField() {
        ProfessionalCategory original = CatalogSamples.category(1);
        assertThat(original).hasNoNullFieldsOrProperties();

        ProfessionalCategory result = categoryMapper.toDomainModel(categoryMapper.toEntity(original));

        assertThat(result).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void aProfessionalKeepsEveryFieldAndItsCategory() {
        Professional original = CatalogSamples.professional(15, CatalogSamples.category(1));
        assertThat(original).hasNoNullFieldsOrProperties();
        assertThat(original.getCategory()).hasNoNullFieldsOrProperties();

        Professional result = professionalMapper.toDomainModel(professionalMapper.toEntity(original));

        assertThat(result).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void aWeeklyScheduleKeepsEveryFieldItsProfessionalAndItsCategory() {
        WeeklySchedule original = CatalogSamples.weeklySchedule(21,
                CatalogSamples.professional(15, CatalogSamples.category(1)));
        assertThat(original).hasNoNullFieldsOrProperties();

        WeeklySchedule result = weeklyScheduleMapper.toDomainModel(weeklyScheduleMapper.toEntity(original));

        assertThat(result).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void theSyncStateKeepsEveryFieldAndAlwaysUsesTheSingleRow() {
        CatalogSyncState original = CatalogSamples.failedState();
        assertThat(original).hasNoNullFieldsOrProperties();

        CatalogSyncStateEntity entity = syncStateMapper.toEntity(original);
        CatalogSyncState result = syncStateMapper.toDomainModel(entity);

        assertThat(entity.getId()).isEqualTo(CatalogSyncStateEntity.SINGLE_ROW_ID);
        assertThat(result).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void nullMapsToNull() {
        assertThat(categoryMapper.toEntity(null)).isNull();
        assertThat(categoryMapper.toDomainModel(null)).isNull();
        assertThat(professionalMapper.toEntity(null)).isNull();
        assertThat(professionalMapper.toDomainModel(null)).isNull();
        assertThat(weeklyScheduleMapper.toEntity(null)).isNull();
        assertThat(weeklyScheduleMapper.toDomainModel(null)).isNull();
        assertThat(syncStateMapper.toEntity(null)).isNull();
        assertThat(syncStateMapper.toDomainModel(null)).isNull();
    }
}
