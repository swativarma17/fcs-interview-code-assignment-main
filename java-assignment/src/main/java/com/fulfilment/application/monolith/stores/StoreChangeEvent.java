
package com.fulfilment.application.monolith.stores;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class StoreChangeEvent{
    public final StoreChangeType type;
    public final Store store;

    public StoreChangeEvent(StoreChangeType type, Store store) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public StoreChangeType getType() {
        return type;
    }

    public Store getStore() {
        return store;
    }


    public StoreChangeType type() {
        return type;
    }

    public Store store() {
        return store;
    }
}
