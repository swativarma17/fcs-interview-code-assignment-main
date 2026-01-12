package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.stores.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LegacyStoreSyncObserver.
 * Verifies dispatch to LegacyStoreManagerGateway per StoreChangeType.
 */
@ExtendWith(MockitoExtension.class)
class LegacyStoreSyncObserverTest {

    @Mock
    private LegacyStoreManagerGateway legacyStoreManagerGateway;

    @InjectMocks
    private LegacyStoreSyncObserver observer;

    // ---- CREATE ----
    @Test
    void onStoreChange_create_shouldInvokeCreateOnGateway() {
        Store store = new Store();
        StoreChangeEvent event = mockEvent(StoreChangeType.CREATE, store);

        observer.onStoreChange(event);

        verify(legacyStoreManagerGateway, times(1)).createStoreOnLegacySystem(store);
        verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
        verify(legacyStoreManagerGateway, never()).deleteStoreOnLegacySystem(any());
    }

    // ---- UPDATE ----
    @Test
    void onStoreChange_update_shouldInvokeUpdateOnGateway() {
        Store store = new Store();
        StoreChangeEvent event = mockEvent(StoreChangeType.UPDATE, store);

        observer.onStoreChange(event);

        verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(store);
        verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(any());
        verify(legacyStoreManagerGateway, never()).deleteStoreOnLegacySystem(any());
    }

    // ---- PATCH ----
    @Test
    void onStoreChange_patch_shouldInvokeUpdateOnGateway() {
        Store store = new Store();
        StoreChangeEvent event = mockEvent(StoreChangeType.PATCH, store);

        observer.onStoreChange(event);

        verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(store);
        verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(any());
        verify(legacyStoreManagerGateway, never()).deleteStoreOnLegacySystem(any());
    }

    // ---- DELETE ----
    @Test
    void onStoreChange_delete_shouldInvokeDeleteOnGateway() {
        Store store = new Store();
        StoreChangeEvent event = mockEvent(StoreChangeType.DELETE, store);

        observer.onStoreChange(event);

        verify(legacyStoreManagerGateway, times(1)).deleteStoreOnLegacySystem(store);
        verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(any());
        verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
    }

    // ---- Null store payload (should not throw; gateway receives null) ----
    @Test
    void onStoreChange_create_withNullStore_shouldNotThrow_andPassNullToGateway() {
        StoreChangeEvent event = mockEvent(StoreChangeType.CREATE, null);

        assertDoesNotThrow(() -> observer.onStoreChange(event));
        verify(legacyStoreManagerGateway, times(1)).createStoreOnLegacySystem(isNull());
        verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
        verify(legacyStoreManagerGateway, never()).deleteStoreOnLegacySystem(any());
    }

    // ---- helper to mock event with desired type/store ----
    private static StoreChangeEvent mockEvent(StoreChangeType type, Store store) {
        StoreChangeEvent event = mock(StoreChangeEvent.class);
        when(event.getType()).thenReturn(type);
        when(event.getStore()).thenReturn(store);
        return event;
    }
}
