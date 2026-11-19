package catalogo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Tests para comprobar que el servidor http utlizado para los tests funciona correctamente.
class FakeHttpServerTest {

    private final FakeHttpServer server = FakeHttpServer.start();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void answersTheConfiguredResponsesInOrderAndRepeatsTheLastOne() throws Exception {
        server.respond("/things", FakeHttpServer.Response.of(503, "{\"n\":1}"), FakeHttpServer.Response.of(200, "{\"n\":2}"));

        assertThat(get("/things").statusCode()).isEqualTo(503);
        assertThat(get("/things").body()).isEqualTo("{\"n\":2}");
        assertThat(get("/things").body()).isEqualTo("{\"n\":2}");
    }

    @Test
    void remembersTheMethodPathHeadersAndBodyOfEachRequest() throws Exception {
        server.respond("/things", FakeHttpServer.Response.of(201, "{}"));

        client.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + "/things"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"x\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(server.receivedRequests("/things")).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/things");
            assertThat(request.header("Content-Type")).isEqualTo("application/json");
            assertThat(request.body()).isEqualTo("{\"name\":\"x\"}");
        });
    }

    @Test
    void aPathWithoutAConfiguredResponseAnswersNotFound() throws Exception {
        assertThat(get("/unknown").statusCode()).isEqualTo(404);
        assertThat(server.receivedRequests()).hasSize(1);
    }

    @Test
    void anEmptyResponseHasNoBodyNorContentType() throws Exception {
        server.respond("/things", FakeHttpServer.Response.empty(401));

        HttpResponse<String> response = get("/things");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Content-Type")).isEmpty();
    }

    @Test
    void aDelayedResponseArrivesAfterItsDelay() throws Exception {
        server.respond("/things", FakeHttpServer.Response.of(200, "{}").after(Duration.ofMillis(300)));

        long start = System.nanoTime();
        get("/things");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
