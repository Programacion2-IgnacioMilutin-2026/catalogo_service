package catalogo.catalog.infrastructure.web.controller;

import catalogo.TestTokens;
import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.application.service.ProfessionalCategoryService;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalCategoryDtoMapper;
import catalogo.config.TestSecurityConfiguration;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La entrada web del listado de categorías, con la seguridad y el manejador de errores reales.
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(ProfessionalCategoryController.class)
@Import({TestSecurityConfiguration.class, ProfessionalCategoryDtoMapper.class})
class ProfessionalCategoryControllerTest {

    private static final String CATEGORIES = "/api/professional-categories";
    private static final String TWO_CATEGORIES_JSON = """
            [
              {"id": 2, "name": "Kinesiología", "description": "Rehabilitación física", "enabled": false},
              {"id": 1, "name": "Pediatría", "description": null, "enabled": true}
            ]""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalCategoryService professionalCategoryService;

    @Test
    void theListHasTheFourFieldsOfEachCategoryAndNothingElse() throws Exception {
        givenTwoCategories();

        mockMvc.perform(get(CATEGORIES))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(TWO_CATEGORIES_JSON, JsonCompareMode.STRICT));
    }

    @Test
    void aCategoryWithoutDescriptionKeepsTheFieldWithNull() throws Exception {
        givenTwoCategories();

        mockMvc.perform(get(CATEGORIES))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"description\":null")));
    }

    @Test
    void queryParametersDoNotChangeTheResponse() throws Exception {
        givenTwoCategories();

        mockMvc.perform(get(CATEGORIES).queryParam("page", "3").queryParam("size", "1").queryParam("sort", "id"))
                .andExpect(status().isOk())
                .andExpect(content().json(TWO_CATEGORIES_JSON, JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationHeaders")
    void anyAuthorizationHeaderGetsTheSameResponseAsNoHeader(String authorization) throws Exception {
        givenTwoCategories();

        mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(content().json(TWO_CATEGORIES_JSON, JsonCompareMode.STRICT));
    }

    static Stream<Named<String>> authorizationHeaders() {
        return Stream.of(
                Named.of("a validly signed token", "Bearer " + TestTokens.valid()),
                Named.of("an expired token", "Bearer " + TestTokens.expired()),
                Named.of("an altered token", "Bearer " + TestTokens.altered()),
                Named.of("a text that is not a token", "Bearer not-a-token"),
                Named.of("basic credentials", "Basic dXN1YXJpbzpjbGF2ZQ=="));
    }

    @Test
    void aCatalogNotInitializedAnswers503AsAProblem() throws Exception {
        given(professionalCategoryService.getAllProfessionalCategories())
                .willThrow(new CatalogNotInitializedException());

        mockMvc.perform(get(CATEGORIES))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.code").value("CATALOG_NOT_INITIALIZED"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void aDatabaseFailureAnswers500WithoutInternalDetailsAndIsLogged(CapturedOutput output) throws Exception {
        given(professionalCategoryService.getAllProfessionalCategories())
                .willThrow(new DataAccessResourceFailureException(
                        "Connection to catalog-db:5432 refused reading professional_categories"));

        String body = mockMvc.perform(get(CATEGORIES))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("catalog-db", "professional_categories", "DataAccess", "Exception", "at ");
        assertThat(output).contains("Unexpected error").contains("DataAccessResourceFailureException");
    }

    private void givenTwoCategories() {
        given(professionalCategoryService.getAllProfessionalCategories()).willReturn(List.of(
                category(2, "Kinesiología", "Rehabilitación física", false),
                category(1, "Pediatría", null, true)));
    }

    private static ProfessionalCategory category(long id, String name, String description, boolean enabled) {
        return ProfessionalCategory.builder().id(id).name(name).description(description).enabled(enabled).build();
    }
}
