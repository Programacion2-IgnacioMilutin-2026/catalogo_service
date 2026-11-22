package catalogo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

// Tokens de usuario de prueba, firmados con una clave RSA que se genera en cada corrida, y archivos PEM de prueba.
public final class TestTokens {

    private static final String USER_ID = "42";
    private static final String USER_ROLE = "ROLE_USER";
    private static final KeyPair KEYS = generateKeys("RSA", 2048);
    private static final KeyPair OTHER_KEYS = generateKeys("RSA", 2048);
    private static final String PUBLIC_KEY_LOCATION = writePem("PUBLIC KEY", KEYS.getPublic().getEncoded());

    private TestTokens() {
    }

    public static String publicKeyLocation() {
        return PUBLIC_KEY_LOCATION;
    }

    public static String otherPublicKeyLocation() {
        return writePem("PUBLIC KEY", OTHER_KEYS.getPublic().getEncoded());
    }

    public static String shortPublicKeyLocation() {
        return writePem("PUBLIC KEY", generateKeys("RSA", 1024).getPublic().getEncoded());
    }

    public static String ellipticCurvePublicKeyLocation() {
        return writePem("PUBLIC KEY", generateKeys("EC", 256).getPublic().getEncoded());
    }

    public static String privateKeyLocation() {
        return writePem("PRIVATE KEY", KEYS.getPrivate().getEncoded());
    }

    public static String notAKeyLocation() {
        return writeTempFile("esto no es una clave\n");
    }

    public static String valid() {
        return sign(claims(USER_ID, USER_ROLE, oneHourFromNow()), KEYS.getPrivate());
    }

    public static String expired() {
        return expiringAt(Instant.now().minus(Duration.ofHours(1)));
    }

    // La firma de un token con los datos de otro: la firma ya no coincide.
    public static String altered() {
        String[] original = valid().split("\\.");
        String[] other = withSubject("1").split("\\.");
        return original[0] + "." + other[1] + "." + original[2];
    }

    public static String expiringAt(Instant expiresAt) {
        return sign(claims(USER_ID, USER_ROLE, expiresAt), KEYS.getPrivate());
    }

    public static String withoutExpiry() {
        return sign(claims(USER_ID, USER_ROLE, null), KEYS.getPrivate());
    }

    public static String withSubject(String subject) {
        return sign(claims(subject, USER_ROLE, oneHourFromNow()), KEYS.getPrivate());
    }

    public static String withoutSubject() {
        return withSubject(null);
    }

    public static String withAuth(String auth) {
        return sign(claims(USER_ID, auth, oneHourFromNow()), KEYS.getPrivate());
    }

    public static String signedWithOtherKey() {
        return sign(claims(USER_ID, USER_ROLE, oneHourFromNow()), OTHER_KEYS.getPrivate());
    }

    public static String signedWithHmac() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(USER_ID, USER_ROLE, oneHourFromNow()));
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the test token", e);
        }
        return jwt.serialize();
    }

    public static String unsigned() {
        return new PlainJWT(claims(USER_ID, USER_ROLE, oneHourFromNow())).serialize();
    }

    private static Instant oneHourFromNow() {
        return Instant.now().plus(Duration.ofHours(1));
    }

    private static JWTClaimsSet claims(String subject, String auth, Instant expiresAt) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("auth", auth);
        if (expiresAt == null) {
            return builder.issueTime(new Date()).build();
        }
        return builder
                .issueTime(Date.from(expiresAt.minus(Duration.ofHours(2))))
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String sign(JWTClaimsSet claims, PrivateKey key) {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the test token", e);
        }
        return jwt.serialize();
    }

    private static String writePem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return writeTempFile("-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }

    private static String writeTempFile(String content) {
        try {
            Path file = Files.createTempFile("catalogo-test-key-", ".pem");
            file.toFile().deleteOnExit();
            Files.writeString(file, content, StandardCharsets.US_ASCII);
            return file.toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the test key file", e);
        }
    }

    private static KeyPair generateKeys(String algorithm, int size) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
            generator.initialize(size);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }
}
