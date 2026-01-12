
package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.stores.StoreChangeEvent;
import com.fulfilment.application.monolith.stores.StoreChangeType;
import com.fulfilment.application.monolith.stores.StoreResource;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreResourceTest {

    @Mock
    Event<StoreChangeEvent> storeChangeEvent; // ✅ mock CDI Event

    @InjectMocks
    StoreResource resource;                   // inject event into resource

    // -------------------- GET /store --------------------
//    @Test
//    void get_shouldReturnListSortedByName() {
//        Store s1 = store(1L, "Alpha", 10);
//        Store s2 = store(2L, "Beta", 5);
//
//        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
//            // Flexible stubbing to avoid strict Sort instance mismatch
//            mocked.when(() -> Store.listAll(any(Sort.class)))
//                    .thenReturn(List.of(s1, s2));
//
//            List<Store> result = resource.get();
//
//            assertEquals(2, result.size());
//            assertEquals("Alpha", result.get(0).name);
//            assertEquals("Beta", result.get(1).name);
//
//            // Verify it was called once with some Sort (content verified at runtime, optional)
//            mocked.verify(() -> Store.listAll(any(Sort.class)), times(1));
//        }
//    }

    // -------------------- GET /store/{id} --------------------
   /* @Test
    void getSingle_whenFound_shouldReturnEntity() {
        Store existing = store(10L, "Zwolle", 40);

        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(10L)).thenReturn(existing);

            Store result = resource.getSingle(10L);

            assertNotNull(result);
            assertEquals("Zwolle", result.name);
            mocked.verify(() -> Store.findById(10L), times(1));
        }
    }*/

    /*@Test
    void getSingle_whenMissing_shouldThrow404() {
        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(99L)).thenReturn(null);

            WebApplicationException ex = assertThrows(WebApplicationException.class,
                    () -> resource.getSingle(99L));

            assertEquals(404, ex.getResponse().getStatus());
            assertTrue(ex.getMessage().contains("99"));
            mocked.verify(() -> Store.findById(99L), times(1));
        }
    }*/

    // -------------------- POST /store --------------------
    @Test
    void create_whenIdPreset_shouldThrow422_andNotPersistOrFireEvent() {
        Store withId = store(1L, "Preset", 50);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.create(withId));

        assertEquals(422, ex.getResponse().getStatus());
        verify(storeChangeEvent, never()).fire(any());
        // persist() would be on the instance; since error occurs first, no need to verify
    }

    @Test
    void create_whenValid_shouldPersist_andFireCreateEvent_andReturn201() {
        Store newStore = spy(store(null, "New Store", 25));
        doNothing().when(newStore).persist(); // avoid DB side effects

        Response resp = resource.create(newStore);

        assertEquals(201, resp.getStatus());
        assertSame(newStore, resp.getEntity());
        verify(newStore, times(1)).persist();

        ArgumentCaptor<StoreChangeEvent> evtCaptor = ArgumentCaptor.forClass(StoreChangeEvent.class);
        verify(storeChangeEvent, times(1)).fire(evtCaptor.capture());
        StoreChangeEvent evt = evtCaptor.getValue();
        assertEquals(StoreChangeType.CREATE, evt.type());
        assertSame(newStore, evt.store());
    }

    // -------------------- PUT /store/{id} --------------------
    @Test
    void update_whenNameMissing_shouldThrow422_andNotFireEvent() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.update(5L, store(null, null, 10)));
        assertEquals(422, ex.getResponse().getStatus());
        verify(storeChangeEvent, never()).fire(any());
    }

    /*@Test
    void update_whenTargetMissing_shouldThrow404_andNotFireEvent() {
        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(7L)).thenReturn(null);

            WebApplicationException ex = assertThrows(WebApplicationException.class,
                    () -> resource.update(7L, store(null, "Updated", 20)));

            assertEquals(404, ex.getResponse().getStatus());
            mocked.verify(() -> Store.findById(7L), times(1));
            verify(storeChangeEvent, never()).fire(any());
        }
    }*/




    // -------------------- PATCH /store/{id} --------------------
    @Test
    void patch_whenPayloadNull_shouldThrow422_andNotFireEvent() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.patch(11L, null));
        assertEquals(422, ex.getResponse().getStatus());
        verify(storeChangeEvent, never()).fire(any());
    }

   /* @Test
    void patch_whenTargetMissing_shouldThrow404_andNotFireEvent() {
        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(11L)).thenReturn(null);

            WebApplicationException ex = assertThrows(WebApplicationException.class,
                    () -> resource.patch(11L, store(null, "Partial", 5)));

            assertEquals(404, ex.getResponse().getStatus());
            mocked.verify(() -> Store.findById(11L), times(1));
            verify(storeChangeEvent, never()).fire(any());
        }
    }
*/
    /*@Test
    void patch_whenValid_shouldUpdateOnlyProvidedFields_andFirePatchEvent() {
        Store existing = store(20L, "Original", 10);
        Store payload = store(null, "NewName", 50); // both fields present

        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(20L)).thenReturn(existing);

            Store result = resource.patch(20L, payload);

            assertSame(existing, result);
            assertEquals("NewName", existing.name);
            assertEquals(50, existing.quantityProductsInStock);

            ArgumentCaptor<StoreChangeEvent> evtCaptor = ArgumentCaptor.forClass(StoreChangeEvent.class);
            verify(storeChangeEvent, times(1)).fire(evtCaptor.capture());
            StoreChangeEvent evt = evtCaptor.getValue();
            assertEquals(StoreChangeType.PATCH, evt.type());
            assertSame(existing, evt.store());

            mocked.verify(() -> Store.findById(20L), times(1));
        }
    }*/

   /* @Test
    void patch_blankNameOrNonPositiveQuantity_shouldNotChangeThoseFields_butStillFireEvent() {
        Store existing = store(21L, "KeepName", 15);
        Store payload = store(null, "   ", 0); // blank name & non-positive qty

        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(21L)).thenReturn(existing);

            Store result = resource.patch(21L, payload);

            assertSame(existing, result);
            // unchanged
            assertEquals("KeepName", existing.name);
            assertEquals(15, existing.quantityProductsInStock);

            // event fired even if nothing changed (per implementation)
            verify(storeChangeEvent, times(1))
                    .fire(argThat(evt ->
                            evt.type() == StoreChangeType.PATCH &&
                                    evt.store() == existing
                    ));

            mocked.verify(() -> Store.findById(21L), times(1));
        }
    }*/

    // -------------------- DELETE /store/{id} --------------------
   /* @Test
    void delete_whenMissing_shouldThrow404_andNotFireEvent() {
        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(77L)).thenReturn(null);

            WebApplicationException ex = assertThrows(WebApplicationException.class,
                    () -> resource.delete(77L));

            assertEquals(404, ex.getResponse().getStatus());
            mocked.verify(() -> Store.findById(77L), times(1));
            verify(storeChangeEvent, never()).fire(any());
        }
    }*/

   /* @Test
    void delete_whenFound_shouldCallDelete_andFireDeleteEvent_andReturn204() {
        Store existing = spy(store(12L, "ToDelete", 3));
        doNothing().when(existing).delete(); // avoid DB effects

        try (MockedStatic<Store> mocked = mockStatic(Store.class)) {
            mocked.when(() -> Store.findById(12L)).thenReturn(existing);

            Response resp = resource.delete(12L);

            assertEquals(204, resp.getStatus());
            // 204 No Content ⇒ no media type
            assertNull(resp.getMediaType());
            verify(existing, times(1)).delete();

            ArgumentCaptor<StoreChangeEvent> evtCaptor = ArgumentCaptor.forClass(StoreChangeEvent.class);
            verify(storeChangeEvent, times(1)).fire(evtCaptor.capture());
            StoreChangeEvent evt = evtCaptor.getValue();
            assertEquals(StoreChangeType.DELETE, evt.type());
            assertSame(existing, evt.store());

            mocked.verify(() -> Store.findById(12L), times(1));
        }
    }*/

    // -------------------- helpers --------------------
    private static Store store(Long id, String name, int qty) {
        Store s = new Store();
        s.id = id;
        s.name = name;
        s.quantityProductsInStock = qty;
        return s;
    }
}
