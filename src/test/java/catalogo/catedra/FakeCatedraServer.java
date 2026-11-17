package catalogo.catedra;

import catalogo.FakeHttpServer;
import catalogo.FakeHttpServer.ReceivedRequest;
import catalogo.FakeHttpServer.Response;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Cátedra simulada para los tests. Creado a partir de las respuestas reales de la catedra. Por defecto responde un login técnico exitoso, con valores ficticios.
public final class FakeCatedraServer implements AutoCloseable {

    public static final String LOGIN_PATH = "/api/authenticate";
    public static final String TEST_USERNAME = "test-login";
    public static final String TEST_PASSWORD = "test-password";
    public static final String TEST_TOKEN = "test-technical-jwt";
    public static final String TEST_REDIS_PASSWORD = TechnicalIntegrations.REDIS_PASSWORD;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LOGIN_RESPONSE = readResource("/catedra/login-200.json");

    private final FakeHttpServer http;

    private FakeCatedraServer(FakeHttpServer http) {
        this.http = http;
    }

    public static FakeCatedraServer start() {
        FakeCatedraServer catedra = new FakeCatedraServer(FakeHttpServer.start());
        catedra.respondToLogin(provisionedLogin());
        return catedra;
    }

    public String baseUrl() {
        return http.baseUrl();
    }

    public void respondToLogin(Response... responses) {
        http.respond(LOGIN_PATH, responses);
    }

    public List<ReceivedRequest> loginRequests() {
        return http.receivedRequests(LOGIN_PATH);
    }

    public List<ReceivedRequest> receivedRequests() {
        return http.receivedRequests();
    }

    @Override
    public void close() {
        http.close();
    }

    public static Response provisionedLogin() {
        return Response.of(200, LOGIN_RESPONSE);
    }

    public static Response loginWithStatus(String provisioningStatus) {
        ObjectNode login = loginTree();
        integrationOf(login).put("provisioningStatus", provisioningStatus);
        return Response.of(200, JSON.writeValueAsString(login));
    }

    public static Response loginWithout(String field) {
        ObjectNode login = loginTree();
        if (login.has(field)) {
            login.remove(field);
        } else {
            integrationOf(login).remove(field);
        }
        return Response.of(200, JSON.writeValueAsString(login));
    }

    public static Response loginWithUnknownFields() {
        ObjectNode login = loginTree();
        login.put("somethingNew", "value");
        integrationOf(login).put("anotherNewField", 7);
        return Response.of(200, JSON.writeValueAsString(login));
    }

    public static Response problem(int status, String code) {
        ObjectNode problem = JSON.createObjectNode();
        problem.put("title", "Error");
        problem.put("status", status);
        problem.put("detail", "Rejected by the test server");
        problem.put("code", code);
        return Response.of(status, JSON.writeValueAsString(problem)).withContentType("application/problem+json");
    }

    private static ObjectNode loginTree() {
        return (ObjectNode) JSON.readTree(LOGIN_RESPONSE);
    }

    private static ObjectNode integrationOf(JsonNode login) {
        return (ObjectNode) login.get("integration");
    }

    private static String readResource(String path) {
        try (InputStream input = FakeCatedraServer.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Test resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
