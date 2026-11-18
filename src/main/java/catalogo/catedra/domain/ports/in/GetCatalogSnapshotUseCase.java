package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.CatalogSnapshotResult;

// Puerto de entrada para obtener el snapshot.
public interface GetCatalogSnapshotUseCase {
    CatalogSnapshotResult getCatalogSnapshot();
}
