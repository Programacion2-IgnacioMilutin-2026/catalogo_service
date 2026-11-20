package catalogo.synchronization.domain.model;

// Resultado de poner la copia local al día con la versión vigente de la cátedra.
public sealed interface CatchUpResult {

    record UpToDate(long localVersion) implements CatchUpResult {
    }

    // La copia quedó en la versión vigente, con versiones aplicadas o una sincronización completa.
    record Updated(long localVersion) implements CatchUpResult {
    }

    record Failed(String reason) implements CatchUpResult {
    }
}
