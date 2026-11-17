package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.SnapshotFetchResult;

// Pedido del snapshot una vez.
public interface CatalogSnapshotClient {

    SnapshotFetchResult fetchSnapshot(String technicalToken);
}
