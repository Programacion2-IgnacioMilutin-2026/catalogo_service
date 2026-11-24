package catalogo.config;

import catalogo.TestTokens;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(OutputCaptureExtension.class)
class JwtDecoderConfigTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final String WITHOUT_KEY = "every request to a protected route will be rejected";

    private final JwtDecoderConfig config = new JwtDecoderConfig();

    @Test
    void acceptsATokenSignedWithTheConfiguredKey(CapturedOutput output) {
        assertThat(decoder().decode(TestTokens.valid()).getSubject()).isEqualTo("42");
        assertThat(output).doesNotContain(WITHOUT_KEY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rejectsATokenNotSignedWithTheConfiguredKeyInRs256(String token) {
        JwtDecoder decoder = decoder();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    static Stream<Named<String>> rejectsATokenNotSignedWithTheConfiguredKeyInRs256() {
        return Stream.of(
                Named.of("signed with another RSA key", TestTokens.signedWithOtherKey()),
                Named.of("signed with HS256", TestTokens.signedWithHmac()),
                Named.of("without a signature", TestTokens.unsigned()),
                Named.of("changed after being signed", TestTokens.altered()));
    }

    @Test
    void rejectsATokenThatExpiredOneSecondAgo() {
        JwtDecoder decoder = decoderAt(NOW);
        String token = TestTokens.expiringAt(NOW.minusSeconds(1));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void acceptsATokenThatExpiresInOneSecond() {
        assertThat(decoderAt(NOW).decode(TestTokens.expiringAt(NOW.plusSeconds(1))).getSubject()).isEqualTo("42");
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        JwtDecoder decoder = decoder();
        String token = TestTokens.withoutExpiry();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutSubject() {
        JwtDecoder decoder = decoder();
        String token = TestTokens.withoutSubject();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-1", "+42", "4 2", " 42", "٤٢", "9223372036854775808", "00000000000000000042"})
    void rejectsATokenWhoseSubjectIsNotAUserId(String subject) {
        JwtDecoder decoder = decoder();
        String token = TestTokens.withSubject(subject);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "0", "9223372036854775807"})
    void acceptsAUserIdUpToTheLargestLong(String subject) {
        assertThat(decoder().decode(TestTokens.withSubject(subject)).getSubject()).isEqualTo(subject);
    }

    @Test
    void aDecoderBuiltAgainFromTheSameKeyKeepsAcceptingTheToken() {
        String token = TestTokens.valid();

        assertThat(decoder().decode(token).getSubject()).isEqualTo("42");
        assertThat(decoder().decode(token).getSubject()).isEqualTo("42");
    }

    @Test
    void aDecoderWithTheKeyOfAnotherPairRejectsTheToken() {
        JwtDecoder decoder = decoderWith(TestTokens.otherPublicKeyLocation());
        String token = TestTokens.valid();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void withoutAUsableKeyEveryTokenIsRejectedAndAWarningSaysWhy(String location, String problem, CapturedOutput output)
            throws IOException {
        JwtDecoder decoder = decoderWith(location);
        String token = TestTokens.valid();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
        assertThat(output).contains("The JWT public key", problem, WITHOUT_KEY).doesNotContain("BEGIN");
        for (String line : contentLines(location)) {
            assertThat(output).doesNotContain(line);
        }
    }

    static Stream<Arguments> withoutAUsableKeyEveryTokenIsRejectedAndAWarningSaysWhy() {
        String notRsa = "is not an RSA public key in PEM format";
        return Stream.of(
                arguments(Named.of("without the property", null), "is not configured"),
                arguments(Named.of("empty", ""), "is not configured"),
                arguments(Named.of("blank", "   "), "is not configured"),
                arguments(Named.of("a file that does not exist", "file:/nonexistent/catalogo/jwt-public.pem"),
                        "could not be read"),
                arguments(Named.of("a file that is not a key", TestTokens.notAKeyLocation()), notRsa),
                arguments(Named.of("a private key", TestTokens.privateKeyLocation()), notRsa),
                arguments(Named.of("an elliptic curve public key", TestTokens.ellipticCurvePublicKeyLocation()), notRsa),
                arguments(Named.of("an RSA key of 1024 bits", TestTokens.shortPublicKeyLocation()),
                        "is shorter than 2048 bits"));
    }

    private JwtDecoder decoder() {
        return decoderWith(TestTokens.publicKeyLocation());
    }

    private JwtDecoder decoderWith(String location) {
        return config.jwtDecoder(new JwtProperties(location), new DefaultResourceLoader(), Clock.systemUTC());
    }

    private JwtDecoder decoderAt(Instant now) {
        return config.jwtDecoder(new JwtProperties(TestTokens.publicKeyLocation()), new DefaultResourceLoader(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    // Las líneas del archivo que no son el encabezado ni el pie, para comprobar que no llegan al log.
    private static List<String> contentLines(String location) throws IOException {
        if (location == null || !location.startsWith("file:")) {
            return List.of();
        }
        Path file = Path.of(URI.create(location));
        if (!Files.exists(file)) {
            return List.of();
        }
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("-----"))
                .toList();
    }
}
