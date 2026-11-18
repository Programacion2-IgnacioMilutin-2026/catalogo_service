package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.SnapshotFetchResult;
import catalogo.catedra.domain.ports.out.CatalogSnapshotClient;
import catalogo.catedra.infrastructure.client.dto.CatalogSnapshotResponse;
import catalogo.catedra.infrastructure.client.mapper.CatalogSnapshotMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

// Hace un solo pedido del snapshot y traduce la respuesta HTTP a un resultado del dominio.
@Component
public class CatedraSnapshotClientAdapter implements CatalogSnapshotClient {

    private static final String SNAPSHOT_PATH = "/api/synchronization/snapshot";

    private final CatedraProperties catedraProperties;
    private final CatalogSnapshotMapper catalogSnapshotMapper;
    private final RestClient restClient;

    public CatedraSnapshotClientAdapter(CatedraProperties catedraProperties, CatalogSnapshotMapper catalogSnapshotMapper) {
        this.catedraProperties = catedraProperties;
        this.catalogSnapshotMapper = catalogSnapshotMapper;
        CatedraProperties.Snapshot snapshot = catedraProperties.snapshot();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(snapshot.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(snapshot.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public SnapshotFetchResult fetchSnapshot(String technicalToken) {
        try {
            CatalogSnapshotResponse response = restClient.get()
                    .uri(withoutTrailingSlash(catedraProperties.baseUrl()) + SNAPSHOT_PATH)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(technicalToken))
                    .retrieve()
                    .body(CatalogSnapshotResponse.class);
            if (response == null) {
                return new SnapshotFetchResult.Invalid("Central API answered an incomplete snapshot: [empty response]");
            }
            List<String> problems = catalogSnapshotMapper.problemsOf(response);
            if (!problems.isEmpty()) {
                return new SnapshotFetchResult.Invalid("Central API answered an incomplete snapshot: " + problems);
            }
            return new SnapshotFetchResult.Received(catalogSnapshotMapper.toDomain(response));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                return new SnapshotFetchResult.TokenRejected();
            }
            return new SnapshotFetchResult.Rejected(
                    "Central API rejected the snapshot request with HTTP " + e.getStatusCode().value() + codeOf(e));
        } catch (HttpServerErrorException e) {
            return new SnapshotFetchResult.Unavailable("Central API answered HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            return new SnapshotFetchResult.Unavailable("Central API is not reachable or did not answer in time");
        } catch (RestClientException e) {
            return new SnapshotFetchResult.Invalid("Central API answered an unreadable snapshot");
        } catch (RuntimeException e) {
            return new SnapshotFetchResult.Unavailable(
                    "Unexpected error during the snapshot request: " + e.getClass().getSimpleName());
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

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
