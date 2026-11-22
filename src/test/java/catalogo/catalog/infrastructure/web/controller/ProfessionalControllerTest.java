package catalogo.catalog.infrastructure.web.controller;

import catalogo.TestTokens;
import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.application.exception.ProfessionalNotFoundException;
import catalogo.catalog.application.service.ProfessionalService;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalDtoMapper;
import catalogo.config.TestSecurityConfiguration;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La entrada web de la búsqueda de profesionales, con la seguridad y el manejador de errores reales.
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(ProfessionalController.class)
@Import({TestSecurityConfiguration.class, ProfessionalDtoMapper.class})
class ProfessionalControllerTest {

    private static final String PROFESSIONALS = "/api/professionals";
    private static final String PROFESSIONAL = PROFESSIONALS + "/15";
    private static final String TOKEN = TestTokens.valid();
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String REQUEST_LINE = "requested by user";
    private static final String VALENTINA_JSON = """
            {
              "id": 15,
              "firstName": "Valentina",
              "lastName": "Ibáñez",
              "enabled": true,
              "category": {"id": 1, "name": "Kinesiología", "enabled": false},
              "weeklySchedules": [
                {"id": 21, "dayOfWeek": "MONDAY", "startTime": "08:00:00", "endTime": "12:30:00",
                 "slotDurationMinutes": 30, "enabled": true},
                {"id": 22, "dayOfWeek": "THURSDAY", "startTime": "14:00:00", "endTime": "18:00:00",
                 "slotDurationMinutes": 20, "enabled": false}
              ]
            }""";
    private static final String ONE_PROFESSIONAL_JSON = """
            {
              "items": [
                {
                  "id": 15,
                  "firstName": "Valentina",
                  "lastName": "Ibáñez",
                  "enabled": true,
                  "category": {"id": 1, "name": "Kinesiología", "enabled": false},
                  "weeklySchedules": [
                    {"id": 21, "dayOfWeek": "MONDAY", "startTime": "08:00:00", "endTime": "12:30:00",
                     "slotDurationMinutes": 30, "enabled": true},
                    {"id": 22, "dayOfWeek": "THURSDAY", "startTime": "14:00:00", "endTime": "18:00:00",
                     "slotDurationMinutes": 20, "enabled": false}
                  ]
                },
                {
                  "id": 16,
                  "firstName": "Tomás",
                  "lastName": "Ferreyra",
                  "enabled": false,
                  "category": {"id": 1, "name": "Kinesiología", "enabled": false},
                  "weeklySchedules": []
                }
              ],
              "page": 0,
              "size": 10,
              "totalItems": 2
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalService professionalService;

    @Test
    void thePageHasTheFieldsOfTheContractWithTimesAsHoursMinutesAndSeconds() throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ONE_PROFESSIONAL_JSON, JsonCompareMode.STRICT));
    }

    @Test
    void withoutParametersTheServiceGetsNoFiltersAndTheFirstPageOfTen() throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS)).andExpect(status().isOk());

        verify(professionalService).searchProfessionals(any(ProfessionalSearchCriteria.class), eq(0), eq(10));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationHeaders")
    void anyAuthorizationHeaderGetsTheSameResponseAsNoHeader(String authorization) throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(content().json(ONE_PROFESSIONAL_JSON, JsonCompareMode.STRICT));
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
        given(professionalService.searchProfessionals(any(), anyInt(), anyInt()))
                .willThrow(new CatalogNotInitializedException());

        mockMvc.perform(get(PROFESSIONALS))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.code").value("CATALOG_NOT_INITIALIZED"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void aDatabaseFailureAnswers500WithoutInternalDetailsAndIsLogged(CapturedOutput output) throws Exception {
        given(professionalService.searchProfessionals(any(), anyInt(), anyInt()))
                .willThrow(new DataAccessResourceFailureException(
                        "Connection to catalog-db:5432 refused reading weekly_schedules"));

        String body = mockMvc.perform(get(PROFESSIONALS))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("catalog-db", "weekly_schedules", "DataAccess", "Exception", "at ");
        assertThat(output).contains("Unexpected error").contains("DataAccessResourceFailureException");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "dayOfWeek=FUNDAY | dayOfWeek",
            "dayOfWeek=lunes | dayOfWeek",
            "from=25:00 | from",
            "to=9:00 | to",
            "from=9:00 | from",
            "from=09:00:00.5 | from",
            "from=12:00&to=09:00 | from",
            "from=10:00&to=10:00 | from",
            "categoryId=abc | categoryId",
            "categoryId=12345678901234567890 | categoryId",
            "enabled=TRUE | enabled",
            "enabled=yes | enabled",
            "enabled=1 | enabled",
            "enabled=quizas | enabled",
            "page=-1 | page",
            "page=abc | page",
            "page=12345678901234567890 | page",
            "size=0 | size",
            "size=101 | size",
            "size=diez | size"})
    void anInvalidParameterAnswers400WithItsFieldAndNothingIsSearched(String query, String field) throws Exception {
        String body = mockMvc.perform(get(PROFESSIONALS + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].message").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("java.", "Failed to convert", "Exception");
        verifyNoInteractions(professionalService);
    }

    @Test
    void everyInvalidParameterIsReportedOnceInTheSameResponse() throws Exception {
        mockMvc.perform(get(PROFESSIONALS + "?size=0&dayOfWeek=FUNDAY&dayOfWeek=SUNNYDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("size", "dayOfWeek")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'size')].message",
                        contains("must be a whole number from 1 to 100")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'dayOfWeek')].message",
                        contains("must be a day from MONDAY to SUNDAY")));
    }

    @Test
    void fromNotBeforeToIsASingleErrorOnFrom() throws Exception {
        mockMvc.perform(get(PROFESSIONALS + "?from=14:00&to=10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be before to"));
    }

    @Test
    void aMalformedTimeIsReportedByItsFormatAndNotAsARange() throws Exception {
        mockMvc.perform(get(PROFESSIONALS + "?from=99:00&to=10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be a time as HH:mm or HH:mm:ss"));
    }

    @Test
    void emptyAndUnknownParametersGetTheSameSearchAsNoParameters() throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS + "?name=&categoryId=&enabled=&dayOfWeek=&from=&to=&page=&size="
                        + "&sort=lastName&foo=bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(ONE_PROFESSIONAL_JSON, JsonCompareMode.STRICT));
        mockMvc.perform(get(PROFESSIONALS).queryParam("name", "   "))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfessionalSearchCriteria> criteria = ArgumentCaptor.forClass(ProfessionalSearchCriteria.class);
        verify(professionalService, times(2)).searchProfessionals(criteria.capture(), eq(0), eq(10));
        ProfessionalSearchCriteria noFilters = ProfessionalSearchCriteria.builder()
                .daysOfWeek(EnumSet.noneOf(DayOfWeek.class))
                .build();
        assertThat(criteria.getAllValues())
                .allSatisfy(received -> assertThat(received).usingRecursiveComparison().isEqualTo(noFilters));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "dayOfWeek=monday&dayOfWeek=FRIDAY",
            "dayOfWeek=Monday,friday",
            "dayOfWeek=MONDAY&dayOfWeek=&dayOfWeek=FRIDAY"})
    void daysInAnyCaseRepeatedOrWithCommasGetTheSameDays(String query) throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS + "?" + query)).andExpect(status().isOk());

        ArgumentCaptor<ProfessionalSearchCriteria> criteria = ArgumentCaptor.forClass(ProfessionalSearchCriteria.class);
        verify(professionalService).searchProfessionals(criteria.capture(), eq(0), eq(10));
        assertThat(criteria.getValue().getDaysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void validValuesAtTheEdgesAreAccepted() throws Exception {
        givenTwoProfessionals();

        mockMvc.perform(get(PROFESSIONALS + "?page=2000000000&size=100&categoryId=-5&enabled=true"
                        + "&from=09:00:30&to=23:59"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfessionalSearchCriteria> criteria = ArgumentCaptor.forClass(ProfessionalSearchCriteria.class);
        verify(professionalService).searchProfessionals(criteria.capture(), eq(2000000000), eq(100));
        assertThat(criteria.getValue().getCategoryId()).isEqualTo(-5L);
        assertThat(criteria.getValue().getEnabled()).isTrue();
        assertThat(criteria.getValue().getFrom()).isEqualTo(LocalTime.of(9, 0, 30));
        assertThat(criteria.getValue().getTo()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void oneProfessionalHasTheSameFieldsAsEachItemOfTheSearch() throws Exception {
        given(professionalService.getProfessional(15L)).willReturn(valentina());

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(VALENTINA_JSON, JsonCompareMode.STRICT));
    }

    @Test
    void queryParametersDoNotChangeTheProfessionalAsked() throws Exception {
        given(professionalService.getProfessional(15L)).willReturn(valentina());

        mockMvc.perform(get(PROFESSIONAL + "?x=1&professionalId=99").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(content().json(VALENTINA_JSON, JsonCompareMode.STRICT));

        verify(professionalService).getProfessional(15L);
    }

    @Test
    void aProfessionalThatIsNotInTheCatalogAnswers404AsAProblem() throws Exception {
        given(professionalService.getProfessional(999L)).willThrow(new ProfessionalNotFoundException(999L));

        mockMvc.perform(get(PROFESSIONALS + "/999").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.firstName").doesNotExist());
    }

    @Test
    void aCatalogNotInitializedAnswers503AsAProblemAlsoForOneProfessional() throws Exception {
        given(professionalService.getProfessional(15L)).willThrow(new CatalogNotInitializedException());

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("CATALOG_NOT_INITIALIZED"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "1.0", "1e3", "%2B15", "0xF", "%23F", "%2015", "1%205", "15%20", "%0915",
            "%0115", "%D9%A4%D9%A2", "9223372036854775808", "-9223372036854775809"})
    void anInvalidProfessionalIdAnswers400WithItsFieldAndNothingIsAsked(String id) throws Exception {
        mockMvc.perform(get(URI.create(PROFESSIONALS + "/" + id)).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {"title": "Bad Request", "status": 400, "detail": "One or more parameters are invalid",
                         "code": "VALIDATION_ERROR",
                         "fieldErrors": [{"field": "professionalId", "message": "must be a whole number"}]}
                        """, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.instance").value(PROFESSIONALS + "/" + id));

        verifyNoInteractions(professionalService);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"015, 15", "0, 0", "-1, -1", "-0, 0", "9223372036854775807, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808"})
    void wholeNumbersReachTheServiceAsTheirValue(String id, long expected) throws Exception {
        given(professionalService.getProfessional(anyLong())).willReturn(valentina());

        mockMvc.perform(get(PROFESSIONALS + "/" + id).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());

        verify(professionalService).getProfessional(expected);
    }

    @Test
    void aUrlTheSecurityRejectsNeverReachesTheService() throws Exception {
        mockMvc.perform(get(URI.create(PROFESSIONALS + "/%0A15")).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verifyNoInteractions(professionalService);
    }

    @Test
    void aProfessionalFoundLeavesOneLineWithTheUserAndHowManySchedules(CapturedOutput output) throws Exception {
        given(professionalService.getProfessional(15L)).willReturn(valentina());

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isOk());

        assertOneRequestLine(output, "Professional 15 requested by user 42: found with 2 weekly schedules");
    }

    @Test
    void aProfessionalNotFoundLeavesOneLineWithTheUser(CapturedOutput output) throws Exception {
        given(professionalService.getProfessional(999L)).willThrow(new ProfessionalNotFoundException(999L));

        mockMvc.perform(get(PROFESSIONALS + "/999").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());

        assertOneRequestLine(output, "Professional 999 requested by user 42: not found");
    }

    @Test
    void aCatalogNotInitializedLeavesOneLineWithTheUser(CapturedOutput output) throws Exception {
        given(professionalService.getProfessional(15L)).willThrow(new CatalogNotInitializedException());

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isServiceUnavailable());

        assertOneRequestLine(output, "Professional 15 requested by user 42: catalog not initialized");
    }

    @Test
    void aFailureLeavesTheLineWithTheUserJustBeforeTheError(CapturedOutput output) throws Exception {
        given(professionalService.getProfessional(15L))
                .willThrow(new DataAccessResourceFailureException("Connection to catalog-db:5432 refused"));

        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isInternalServerError());

        assertOneRequestLine(output, "Professional 15 requested by user 42: failed");
        assertThat(output.getAll().indexOf("requested by user 42: failed"))
                .isNotNegative()
                .isLessThan(output.getAll().indexOf("Unexpected error"));
    }

    @Test
    void anInvalidIdLeavesTheLineOfTheHandlerWithTheUserAndWithoutTheValue(CapturedOutput output) throws Exception {
        mockMvc.perform(get(URI.create(PROFESSIONALS + "/%0115")).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest());

        assertOneRequestLine(output,
                "Invalid parameters for GET /api/professionals/{professionalId} requested by user 42: professionalId");
        assertThat(output.getAll()).doesNotContain("\u0001");
    }

    @Test
    void requestsTheSecurityRejectsLeaveNoLine(CapturedOutput output) throws Exception {
        String altered = TestTokens.altered();

        mockMvc.perform(get(PROFESSIONAL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + altered))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URI.create(PROFESSIONALS + "/%0A15")).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(REQUEST_LINE).doesNotContain(altered).doesNotContain(TOKEN);
        verifyNoInteractions(professionalService);
    }

    private static void assertOneRequestLine(CapturedOutput output, String line) {
        assertThat(output.getAll()).contains(line).doesNotContain(TOKEN);
        assertThat(StringUtils.countOccurrencesOf(output.getAll(), REQUEST_LINE)).isEqualTo(1);
    }

    private void givenTwoProfessionals() {
        Professional tomas = Professional.builder()
                .id(16L).firstName("Tomás").lastName("Ferreyra").enabled(false).category(kinesiology()).build();
        ProfessionalPage page = ProfessionalPage.builder()
                .professionals(List.of(
                        valentina(),
                        ProfessionalWithSchedules.builder()
                                .professional(tomas)
                                .weeklySchedules(List.of())
                                .build()))
                .totalItems(2)
                .build();
        given(professionalService.searchProfessionals(any(), anyInt(), anyInt())).willReturn(page);
    }

    private static ProfessionalCategory kinesiology() {
        return ProfessionalCategory.builder()
                .id(1L).name("Kinesiología").description("Rehabilitación física").enabled(false).build();
    }

    private static ProfessionalWithSchedules valentina() {
        Professional valentina = Professional.builder()
                .id(15L).firstName("Valentina").lastName("Ibáñez").enabled(true).category(kinesiology()).build();
        return ProfessionalWithSchedules.builder()
                .professional(valentina)
                .weeklySchedules(List.of(
                        schedule(21, valentina, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 30), 30, true),
                        schedule(22, valentina, DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(18, 0), 20, false)))
                .build();
    }

    private static WeeklySchedule schedule(long id, Professional professional, DayOfWeek day, LocalTime start,
                                           LocalTime end, int slotMinutes, boolean enabled) {
        return WeeklySchedule.builder()
                .id(id)
                .professional(professional)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .slotDurationMinutes(slotMinutes)
                .enabled(enabled)
                .build();
    }
}
