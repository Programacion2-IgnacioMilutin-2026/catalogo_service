package catalogo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Servidor HTTP para simular un sistema externo en los tests.
public final class FakeHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<ReceivedRequest> receivedRequests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Response>> responsesByPath = new ConcurrentHashMap<>();

    public record Response(int status, String body, String contentType, Duration delay) {

        public static Response of(int status, String body) {
            return new Response(status, body, "application/json", Duration.ZERO);
        }

        public static Response empty(int status) {
            return new Response(status, "", null, Duration.ZERO);
        }

        public Response after(Duration delay) {
            return new Response(status, body, contentType, delay);
        }

        public Response withContentType(String contentType) {
            return new Response(status, body, contentType, delay);
        }
    }

    public record ReceivedRequest(String method, String path, Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private FakeHttpServer(HttpServer server) {
        this.server = server;
    }

    public static FakeHttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeHttpServer fake = new FakeHttpServer(server);
            server.createContext("/", fake::handle);
            server.setExecutor(fake.executor);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void respond(String path, Response... responses) {
        responsesByPath.put(path, new ArrayDeque<>(List.of(responses)));
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    public List<ReceivedRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }

    public List<ReceivedRequest> receivedRequests(String path) {
        return receivedRequests.stream().filter(request -> request.path().equals(path)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedRequests.add(new ReceivedRequest(exchange.getRequestMethod(), path, headersOf(exchange), body));

            Response response = nextResponse(path);
            if (!waitFor(response.delay())) {
                return;
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            if (response.contentType() != null) {
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
            }
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(response.status(), -1);
                return;
            }
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private Response nextResponse(String path) {
        Deque<Response> responses = responsesByPath.get(path);
        if (responses == null) {
            return Response.empty(404);
        }
        synchronized (responses) {
            return responses.size() > 1 ? responses.poll() : responses.peek();
        }
    }

    private static Map<String, String> headersOf(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) ->
                headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
        return Map.copyOf(headers);
    }

    private static boolean waitFor(Duration delay) {
        if (delay.isZero()) {
            return true;
        }
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
