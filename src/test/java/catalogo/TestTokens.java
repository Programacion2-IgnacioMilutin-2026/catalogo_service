package catalogo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// Tokens de usuario de prueba, firmados con una clave RSA que se genera en cada corrida.
public final class TestTokens {

    private static final KeyPair KEYS = generateKeys();

    private TestTokens() {
    }

    public static String valid() {
        return sign("42", Instant.now().plus(Duration.ofHours(1)));
    }

    public static String expired() {
        return sign("42", Instant.now().minus(Duration.ofHours(1)));
    }

    // La firma de un token con los datos de otro: la firma ya no coincide.
    public static String altered() {
        String[] original = valid().split("\\.");
        String[] other = sign("1", Instant.now().plus(Duration.ofHours(1))).split("\\.");
        return original[0] + "." + other[1] + "." + original[2];
    }

    private static String sign(String subject, Instant expiresAt) {
        Instant issuedAt = expiresAt.minus(Duration.ofHours(2));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("auth", "ROLE_USER")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the test token", e);
        }
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }
}
