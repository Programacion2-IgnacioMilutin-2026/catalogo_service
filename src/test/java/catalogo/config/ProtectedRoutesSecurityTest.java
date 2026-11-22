package catalogo.config;

import catalogo.TestTokens;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.application.service.ProfessionalCategoryService;
import catalogo.catalog.application.service.ProfessionalService;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.infrastructure.web.controller.ProfessionalCategoryController;
import catalogo.catalog.infrastructure.web.controller.ProfessionalController;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalCategoryDtoMapper;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalDtoMapper;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Las rutas que no son públicas, con la seguridad y el manejador de errores reales.
@WebMvcTest({ProfessionalController.class, ProfessionalCategoryController.class})
@Import({TestSecurityConfiguration.class, ProfessionalDtoMapper.class, ProfessionalCategoryDtoMapper.class})
class ProtectedRoutesSecurityTest {

    private static final String PROFESSIONAL = "/api/professionals/15";
    private static final String MISSING_ROUTE = "/api/no-such-route";
    private static final String UNAUTHORIZED_BODY = """
            {"title": "Unauthorized", "status": 401, "detail": "A valid bearer token is required",
             "instance": "/api/professionals/15"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalService professionalService;

    @MockitoBean
    private ProfessionalCategoryService professionalCategoryService;

    @ParameterizedTest
    @ValueSource(strings = {PROFESSIONAL, MISSING_ROUTE})
    void withoutATokenAProtectedRouteIsRejectedWith401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void the401DoesNotSayWhyAndAsksForABearerToken() throws Exception {
        mockMvc.perform(get(PROFESSIONAL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void everyInvalidAuthorizationGetsTheSame401(String authorization) throws Exception {
        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT));
    }

    static Stream<Named<String>> everyInvalidAuthorizationGetsTheSame401() {
        return Stream.of(
                Named.of("another scheme", "Basic abc"),
                Named.of("Bearer without a token", "Bearer"),
                Named.of("Bearer and a space", "Bearer "),
                Named.of("a text that is not a token", "Bearer xxx"),
                Named.of("signed with another key", "Bearer " + TestTokens.signedWithOtherKey()),
                Named.of("signed with HS256", "Bearer " + TestTokens.signedWithHmac()),
                Named.of("without a signature", "Bearer " + TestTokens.unsigned()),
                Named.of("changed after being signed", "Bearer " + TestTokens.altered()),
                Named.of("expired", "Bearer " + TestTokens.expired()),
                Named.of("without expiry", "Bearer " + TestTokens.withoutExpiry()),
                Named.of("with a subject that is not a user id", "Bearer " + TestTokens.withSubject("abc")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    void aValidTokenGetsTheProfessionalWhateverItsRoles(String auth) throws Exception {
        Professional professional = CatalogSamples.professional(15, CatalogSamples.category(1));
        given(professionalService.getProfessional(15L)).willReturn(ProfessionalWithSchedules.builder()
                .professional(professional)
                .weeklySchedules(List.of())
                .build());

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.withAuth(auth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(15));
    }

    static Stream<Named<String>> roles() {
        return Stream.of(
                Named.of("without auth", null),
                Named.of("empty auth", ""),
                Named.of("ROLE_USER", "ROLE_USER"),
                Named.of("ROLE_ADMIN", "ROLE_ADMIN"));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource
    void aValidTokenReachesTheRouteWhateverItsRoles(String auth, String path) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.withAuth(auth)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    static Stream<Arguments> aValidTokenReachesTheRouteWhateverItsRoles() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String path : new String[]{"/api/synchronization/status", MISSING_ROUTE}) {
            cases.add(arguments(Named.of("without auth", null), path));
            cases.add(arguments(Named.of("empty auth", ""), path));
            cases.add(arguments(Named.of("ROLE_USER", "ROLE_USER"), path));
            cases.add(arguments(Named.of("ROLE_ADMIN", "ROLE_ADMIN"), path));
        }
        return cases.build();
    }

    @Test
    void aNonNumericProfessionalIdWithoutATokenIsRejectedWith401BeforeItsValidation() throws Exception {
        mockMvc.perform(get("/api/professionals/abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aNonNumericProfessionalIdWithAValidTokenReachesItsValidation() throws Exception {
        mockMvc.perform(get("/api/professionals/abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("professionalId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals", "/api/professional-categories"})
    void aPostWithoutATokenIsRejectedWith403WithoutData(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {"title": "Forbidden", "status": 403, "detail": "Access is denied", "instance": "%s"}
                        """.formatted(path), JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals", "/api/professional-categories"})
    void aPostWithAnInvalidTokenIsRejectedWith401(String path) throws Exception {
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer xxx"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals", "/api/professional-categories"})
    void aPostWithAValidTokenIsNotAllowedOnARouteThatOnlyReads(String path) throws Exception {
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid()))
                .andExpect(status().isMethodNotAllowed());
    }
}
