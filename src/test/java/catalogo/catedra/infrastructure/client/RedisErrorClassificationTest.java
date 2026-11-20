package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.RedisReadResult;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Cómo se clasifica cada error de Redis.
class RedisErrorClassificationTest {

    @ParameterizedTest
    @ValueSource(strings = {"WRONGPASS invalid username-password pair or user is disabled.",
            "NOAUTH Authentication required.", "NOPERM User grp_secret has no permissions to access a key"})
    void credentialAndPermissionErrorsAreRejectionsThatOnlyShowTheCode(String message) {
        RedisReadResult<Object> result = CatedraRedisClientAdapter.classify(new RedisCommandExecutionException(message));

        assertThat(result).isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(((RedisReadResult.CredentialsRejected<Object>) result).reason())
                .contains(message.substring(0, message.indexOf(' ')))
                .doesNotContain("grp_secret");
    }

    @Test
    void aRejectionDuringTheConnectionIsARejection() {
        RedisConnectionException connecting = new RedisConnectionException("Unable to connect",
                new RedisCommandExecutionException("WRONGPASS invalid username-password pair"));

        assertThat(CatedraRedisClientAdapter.classify(connecting)).isInstanceOf(RedisReadResult.CredentialsRejected.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOADING Redis is loading the dataset in memory", "BUSY Redis is busy running a script",
            "TRYAGAIN Multiple keys request during rehashing", "MASTERDOWN Link with MASTER is down",
            "ERR something unexpected"})
    void passingAndUnknownErrorsAreUnavailable(String message) {
        assertThat(CatedraRedisClientAdapter.classify(new RedisCommandExecutionException(message)))
                .isInstanceOf(RedisReadResult.Unavailable.class);
    }

    @Test
    void aKeyWithAnotherTypeIsInvalid() {
        assertThat(CatedraRedisClientAdapter.classify(new RedisCommandExecutionException(
                "WRONGTYPE Operation against a key holding the wrong kind of value")))
                .isInstanceOf(RedisReadResult.Invalid.class);
    }

    @Test
    void timeoutsAndLostConnectionsAreUnavailable() {
        assertThat(CatedraRedisClientAdapter.classify(new RedisCommandTimeoutException("Command timed out")))
                .isInstanceOf(RedisReadResult.Unavailable.class);
        assertThat(CatedraRedisClientAdapter.classify(new RedisConnectionException("Connection refused")))
                .isInstanceOf(RedisReadResult.Unavailable.class);
        assertThat(CatedraRedisClientAdapter.classify(new RedisException("Connection closed")))
                .isInstanceOf(RedisReadResult.Unavailable.class);
    }
}
