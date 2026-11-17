package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.domain.ports.out.TechnicalLoginClient;
import catalogo.catedra.infrastructure.client.dto.TechnicalLoginRequest;
import catalogo.catedra.infrastructure.client.dto.TechnicalLoginResponse;
import catalogo.catedra.infrastructure.client.mapper.TechnicalIntegrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Hace un intento de login y traduce la respuesta HTTP a un resultado del dominio.
@Slf4j
@Component
public class CatedraLoginClientAdapter implements TechnicalLoginClient {

    private static final String LOGIN_PATH = "/api/authenticate";
    private static final String BASE_URL_PROPERTY = "catalogo.catedra.base-url";
    private static final String USERNAME_PROPERTY = "catalogo.catedra.username";
    private static final String PASSWORD_PROPERTY = "catalogo.catedra.password";

    private final CatedraProperties catedraProperties;
    private final TechnicalIntegrationMapper technicalIntegrationMapper;
    private final String configurationProblem;
    private final RestClient restClient;

    public CatedraLoginClientAdapter(CatedraProperties catedraProperties, TechnicalIntegrationMapper technicalIntegrationMapper) {
        this.catedraProperties = catedraProperties;
        this.technicalIntegrationMapper = technicalIntegrationMapper;
        this.configurationProblem = configurationProblemOf(catedraProperties);
        if (!configurationProblem.isEmpty()) {
            log.warn("Technical login disabled: {}", configurationProblem);
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(catedraProperties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(catedraProperties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public TechnicalLoginResult login() {
        if (!configurationProblem.isEmpty()) {
            return new TechnicalLoginResult.ConfigurationError(configurationProblem);
        }
        try {
            TechnicalLoginResponse response = restClient.post()
                    .uri(withoutTrailingSlash(catedraProperties.baseUrl()) + LOGIN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TechnicalLoginRequest.builder()
                            .username(catedraProperties.username())
                            .password(catedraProperties.password())
                            .rememberMe(true)
                            .build())
                    .retrieve()
                    .body(TechnicalLoginResponse.class);
            if (response == null) {
                return new TechnicalLoginResult.Unavailable("Central API answered an empty login response");
            }
            if (isBlank(response.getIdToken())) {
                return new TechnicalLoginResult.Unavailable("Central API answered an incomplete login response: missing id_token");
            }
            if (response.getIntegration() == null) {
                return new TechnicalLoginResult.Unavailable("Central API answered an incomplete login response: missing integration");
            }
            return new TechnicalLoginResult.Success(response.getIdToken(), technicalIntegrationMapper.toDomain(response.getIntegration()));
        } catch (HttpClientErrorException e) {
            return new TechnicalLoginResult.ConfigurationError(
                    "Central API rejected the technical login with HTTP " + e.getStatusCode().value() + codeOf(e));
        } catch (HttpServerErrorException e) {
            return new TechnicalLoginResult.Unavailable("Central API answered HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            return new TechnicalLoginResult.Unavailable("Central API is not reachable or did not answer in time");
        } catch (RestClientException e) {
            return new TechnicalLoginResult.Unavailable("Central API answered an unreadable login response");
        } catch (RuntimeException e) {
            return new TechnicalLoginResult.Unavailable("Unexpected error during the technical login: " + e.getClass().getSimpleName());
        }
    }

    private String codeOf(HttpClientErrorException e) {
        try {
            Map<?, ?> problem = e.getResponseBodyAs(Map.class);
            return problem != null && problem.get("code") != null ? " (" + problem.get("code") + ")" : "";
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static String configurationProblemOf(CatedraProperties properties) {
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.baseUrl())) {
            missing.add(BASE_URL_PROPERTY);
        }
        if (isBlank(properties.username())) {
            missing.add(USERNAME_PROPERTY);
        }
        if (isBlank(properties.password())) {
            missing.add(PASSWORD_PROPERTY);
        }
        if (!missing.isEmpty()) {
            return "missing configuration properties " + missing;
        }
        if (!isHttpUrl(properties.baseUrl())) {
            return BASE_URL_PROPERTY + " is not a valid http or https URL"
                    + (startsWithQuote(properties.baseUrl()) ? " (remove the quotes from the .env value)" : "");
        }
        return "";
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            return httpScheme && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean startsWithQuote(String value) {
        return value.startsWith("\"") || value.startsWith("'");
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
