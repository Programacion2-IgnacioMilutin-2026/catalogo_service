package e2e;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

// Los E2E leen el mismo .env que la aplicación y nunca muestra sus valores.
final class E2ECredentials {

    private static final List<String> CREDENTIALS = List.of(
            "CATALOGO_CATEDRA_BASE_URL", "CATALOGO_CATEDRA_USERNAME", "CATALOGO_CATEDRA_PASSWORD");

    private E2ECredentials() {
    }

    static boolean available() {
        Properties env = read();
        return CREDENTIALS.stream().allMatch(name -> !env.getProperty(name, "").isBlank());
    }

    static String password() {
        return read().getProperty("CATALOGO_CATEDRA_PASSWORD", "");
    }

    private static Properties read() {
        Properties properties = new Properties();
        Path path = Path.of(System.getProperty("catalogo.env-file", ".env"));
        if (!Files.isReadable(path)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException e) {
            return new Properties();
        }
        return properties;
    }
}
