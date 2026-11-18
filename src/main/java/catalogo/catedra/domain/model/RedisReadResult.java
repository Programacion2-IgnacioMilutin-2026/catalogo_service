package catalogo.catedra.domain.model;

// Resultado de una sola lectura de Redis.
public sealed interface RedisReadResult<T> {

    record Received<T>(T value) implements RedisReadResult<T> {
    }

    // Redis rechazo las credenciales o el permiso (WRONGPASS, NOAUTH o NOPERM).
    record CredentialsRejected<T>(String reason) implements RedisReadResult<T> {
    }

    // Sin conexion, timeout, corte o un error pasajero de Redis.
    record Unavailable<T>(String reason) implements RedisReadResult<T> {
    }

    // Redis respondio, pero lo publicado no se puede interpretar o le faltan datos.
    record Invalid<T>(String reason) implements RedisReadResult<T> {
    }
}
