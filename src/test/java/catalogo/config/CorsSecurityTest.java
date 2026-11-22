package catalogo.config;

import catalogo.catalog.application.service.ProfessionalCategoryService;
import catalogo.catalog.application.service.ProfessionalService;
import catalogo.catalog.infrastructure.web.controller.ProfessionalCategoryController;
import catalogo.catalog.infrastructure.web.controller.ProfessionalController;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalCategoryDtoMapper;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalDtoMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Qué navegadores pueden usar la API, sin orígenes configurados y con uno.
@WebMvcTest({ProfessionalCategoryController.class, ProfessionalController.class})
@Import({TestSecurityConfiguration.class, ProfessionalCategoryDtoMapper.class, ProfessionalDtoMapper.class})
class CorsSecurityTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String CATEGORIES = "/api/professional-categories";
    private static final String PROFESSIONALS = "/api/professionals";
    private static final String PROFESSIONAL = "/api/professionals/15";

    @Nested
    class WithoutAllowedOrigins {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProfessionalCategoryService professionalCategoryService;

        @MockitoBean
        private ProfessionalService professionalService;

        @ParameterizedTest
        @ValueSource(strings = {CATEGORIES, PROFESSIONALS, PROFESSIONAL})
        void aPreflightFromAnyOriginIsRejected(String path) throws Exception {
            mockMvc.perform(options(path)
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @ParameterizedTest
        @ValueSource(strings = {CATEGORIES, PROFESSIONALS, PROFESSIONAL})
        void aRequestFromAnyOriginIsRejected(String path) throws Exception {
            mockMvc.perform(get(path).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

            verifyNoInteractions(professionalCategoryService, professionalService);
        }

        @Test
        void aRequestWithoutOriginIsNotAffected() throws Exception {
            expectRequestsWithoutOriginAsAlways(mockMvc, professionalCategoryService);
        }
    }

    @Nested
    @TestPropertySource(properties = "catalogo.cors.allowed-origins=" + ALLOWED_ORIGIN)
    class WithAnAllowedOrigin {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProfessionalCategoryService professionalCategoryService;

        @MockitoBean
        private ProfessionalService professionalService;

        @Test
        void aPreflightToAProtectedRouteIsAcceptedWithoutAToken() throws Exception {
            mockMvc.perform(options(PROFESSIONAL)
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            containsStringIgnoringCase(HttpHeaders.AUTHORIZATION)))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void aRequestWithoutATokenGetsA401ThatTheBrowserCanRead() throws Exception {
            mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
        }

        @Test
        void aPublicRouteAnswersAsAlwaysWithTheCorsHeader() throws Exception {
            given(professionalCategoryService.getAllProfessionalCategories()).willReturn(List.of());

            mockMvc.perform(get(CATEGORIES).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(content().json("[]", JsonCompareMode.STRICT));
        }

        @Test
        void aPreflightThatAsksForPostIsRejected() throws Exception {
            mockMvc.perform(options(PROFESSIONALS)
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void aPreflightFromAnotherOriginIsRejected() throws Exception {
            mockMvc.perform(options(PROFESSIONAL)
                            .header(HttpHeaders.ORIGIN, "http://other.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void aRequestWithoutOriginIsNotAffected() throws Exception {
            expectRequestsWithoutOriginAsAlways(mockMvc, professionalCategoryService);
        }
    }

    // Sin Origin, CORS no interviene: la app, turnos y Postman no lo mandan.
    private static void expectRequestsWithoutOriginAsAlways(MockMvc mockMvc, ProfessionalCategoryService service)
            throws Exception {
        given(service.getAllProfessionalCategories()).willReturn(List.of());

        mockMvc.perform(get(CATEGORIES))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
        mockMvc.perform(get(PROFESSIONAL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
