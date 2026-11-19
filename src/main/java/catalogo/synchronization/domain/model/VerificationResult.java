package catalogo.synchronization.domain.model;

// Resultado de una verificación de la versión del catálogo.
public sealed interface VerificationResult {

    // Había otra sincronización en curso.
    record Skipped() implements VerificationResult {
    }

    record UpToDate(long localVersion) implements VerificationResult {
    }

    // La copia quedó en la versión vigente.
    record Updated(long localVersion) implements VerificationResult {
    }

    record Failed(String reason) implements VerificationResult {
    }
}
