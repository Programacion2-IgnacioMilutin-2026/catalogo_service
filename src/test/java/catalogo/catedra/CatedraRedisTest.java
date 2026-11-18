package catalogo.catedra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Test para verificar que CatedraRedis se comporta como la catedra y funciona.
class CatedraRedisTest {

    private static CatedraRedis redis;

    @BeforeAll
    static void startRedis() {
        redis = CatedraRedis.start();
        redis.putMetadata(59, 50);
        redis.putChanges(59, List.of(), List.of(601L), List.of());
        redis.putSnapshotEntities();
    }

    @AfterAll
    static void stopRedis() {
        redis.close();
    }

    @Test
    void theCatedraUserCanReadTheCatalogKeys() {
        assertThat(redis.cliAsCatedraUser("HGET", CatedraRedis.METADATA, "currentVersion")).isEqualTo("59");
        assertThat(redis.cliAsCatedraUser("GET", CatedraRedis.changesKey(59)))
                .isEqualTo("{\"version\":59,\"changes\":{\"professionalCategories\":[],\"professionals\":[601],"
                        + "\"weeklySchedules\":[]}}");
        assertThat(redis.cliAsCatedraUser("HLEN", CatedraRedis.PROFESSIONALS)).isEqualTo("3");
    }

    @Test
    void theCatedraUserCannotPingWriteOrReadOtherKeys() {
        redis.admin().set("other:key", "x");

        assertThat(redis.cliAsCatedraUser("PING")).contains("NOPERM");
        assertThat(redis.cliAsCatedraUser("SET", CatedraRedis.METADATA, "x")).contains("NOPERM");
        assertThat(redis.cliAsCatedraUser("GET", "other:key")).contains("NOPERM");
        assertThat(redis.cliAsCatedraUser("CLIENT", "SETINFO", "lib-name", "test")).contains("NOPERM");
    }
}
