package catalogo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

// Decodificador de los tokens de los usuarios con la clave pública de turnos.
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    private static final int MIN_KEY_BITS = 2048;
    private static final int MAX_USER_ID_DIGITS = 19;

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, ResourceLoader resourceLoader, Clock clock) {
        return readPublicKey(properties.publicKeyLocation(), resourceLoader)
                .map(key -> validatingDecoder(key, clock))
                .orElseGet(JwtDecoderConfig::rejectingDecoder);
    }

    private static Optional<RSAPublicKey> readPublicKey(String location, ResourceLoader resourceLoader) {
        if (location == null || location.isBlank()) {
            warnWithoutKey("is not configured (catalogo.jwt.public-key-location)");
            return Optional.empty();
        }
        RSAPublicKey key;
        try (InputStream content = resourceLoader.getResource(location).getInputStream()) {
            key = RsaKeyConverters.x509().convert(content);
        } catch (IOException | UncheckedIOException e) {
            warnWithoutKey("at " + location + " could not be read");
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            warnWithoutKey("at " + location + " is not an RSA public key in PEM format");
            return Optional.empty();
        }
        if (key.getModulus().bitLength() < MIN_KEY_BITS) {
            warnWithoutKey("at " + location + " is shorter than " + MIN_KEY_BITS + " bits");
            return Optional.empty();
        }
        return Optional.of(key);
    }

    private static void warnWithoutKey(String problem) {
        log.warn("The JWT public key {}: every request to a protected route will be rejected", problem);
    }

    private static JwtDecoder validatingDecoder(RSAPublicKey key, Clock clock) {
        JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ZERO);
        expiry.setClock(clock);
        expiry.setAllowEmptyExpiryClaim(false);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                List.<OAuth2TokenValidator<Jwt>>of(expiry, userIdValidator())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> userIdValidator() {
        OAuth2Error invalid = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token does not identify a user", null);
        return jwt -> isUserId(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalid);
    }

    private static boolean isUserId(String subject) {
        if (subject == null || subject.isEmpty() || subject.length() > MAX_USER_ID_DIGITS
                || !subject.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        try {
            Long.parseLong(subject);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static JwtDecoder rejectingDecoder() {
        return token -> {
            throw new BadJwtException("There is no public key to validate the token");
        };
    }
}
