
package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Observes StoreChangeEvent AFTER_SUCCESS (i.e., post-commit),
 * guaranteeing that legacy sync happens only if DB commit succeeds.
 */
@ApplicationScoped
public class LegacyStoreSyncObserver {

    @Inject
    LegacyStoreManagerGateway legacyStoreManagerGateway;

    public void onStoreChange(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreChangeEvent event) {
        Store store = event.getStore();
        switch (event.getType()) {
            case CREATE -> legacyStoreManagerGateway.createStoreOnLegacySystem(store);
            case UPDATE, PATCH -> legacyStoreManagerGateway.updateStoreOnLegacySystem(store);
            case DELETE -> legacyStoreManagerGateway.deleteStoreOnLegacySystem(store);
            default -> { /* no-op */ }
        }
    }
}
