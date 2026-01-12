package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateWarehouseUseCase: covers validation, conflicts, location constraints,
 * capacity/stock rules, and success path with repository interactions.
 */
@ExtendWith(MockitoExtension.class)
class CreateWarehouseUseCaseTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    LocationResolver locationResolver;

    CreateWarehouseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateWarehouseUseCase(warehouseRepository, locationResolver);
    }

    // ---------------------- Validation: payload ----------------------

    @Test
    void create_whenPayloadNull_shouldThrow400() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(null));
        assertEquals(400, ex.getResponse().getStatus(), "Expected BAD_REQUEST (400)");
        verifyNoInteractions(warehouseRepository, locationResolver);
    }

    @Test
    void create_whenMissingBusinessUnitCode_shouldThrow422() {
        Warehouse w = wh(null, "LOC", 10, 0);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus(), "Expected UNPROCESSABLE (422)");
    }

    @Test
    void create_whenMissingLocation_shouldThrow422() {
        Warehouse w = wh("MWH.001", null, 10, 0);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_whenCapacityNull_shouldThrow422() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = "MWH.001";
        w.location = "LOC";
        w.capacity = null;  // missing
        w.stock = 0;
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_whenStockNull_shouldThrow422() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = "MWH.001";
        w.location = "LOC";
        w.capacity = 10;
        w.stock = null;     // missing
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ---------------------- Conflict: active warehouse with same code ----------------------

    @Test
    void create_whenActiveWarehouseExists_shouldThrow409() {
        Warehouse existingActive = existing("mwh.001", "LOC-1", 20, 0, /*archivedAt*/ null);
        when(warehouseRepository.getAll()).thenReturn(List.of(existingActive));

        Warehouse incoming = wh("MWH.001", "LOC-2", 10, 0); // case-insensitive match

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(incoming));
        assertEquals(409, ex.getResponse().getStatus(), "Expected CONFLICT (409)");
        verify(warehouseRepository, times(1)).getAll();
        verify(locationResolver, never()).resolveByIdentifier(anyString());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenOnlyArchivedWithSameCode_shouldNotConflict() {
        Warehouse archived = existing("MWH.001", "LOC-1", 20, 0, LocalDateTime.now().minusDays(1));
        when(warehouseRepository.getAll()).thenReturn(List.of(archived));
        when(locationResolver.resolveByIdentifier("LOC-2")).thenReturn(loc("LOC-2", 10, 100));

        Warehouse incoming = wh("MWH.001", "LOC-2", 10, 0);

        assertDoesNotThrow(() -> useCase.create(incoming));
        verify(warehouseRepository, times(1)).create(incoming);
    }

    // ---------------------- Invalid location ----------------------

    @Test
    void create_whenLocationResolverReturnsNull_shouldThrow422() {
        when(warehouseRepository.getAll()).thenReturn(List.of()); // no conflicts
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(null);

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 10, 0);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(incoming));
        assertEquals(422, ex.getResponse().getStatus());
        verify(locationResolver, times(1)).resolveByIdentifier("ZWOLLE-001");
        verify(warehouseRepository, never()).create(any());
    }

    // ---------------------- Location constraints: slot & capacity envelope ----------------------

    @Test
    void create_whenLocationHasNoSlot_shouldThrow422() {
        // already has 2 active warehouses at the same location, and max is 2
        Warehouse a1 = existing("A1", "ZWOLLE-001", 40, 0, null);
        Warehouse a2 = existing("A2", "ZWOLLE-001", 40, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(a1, a2));
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", /*maxNumberOfWarehouses*/ 2, /*maxCapacity*/ 200));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 10, 0);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(incoming));
        assertEquals(422, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenLocationCapacityEnvelopeExceeded_shouldThrow422() {
        // currentSum(capacity) = 60; newCap = 50; maxCapacity = 100 → 110 > 100
        Warehouse a1 = existing("A1", "ZWOLLE-001", 60, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(a1));
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", 10, 100));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 50, 0);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(incoming));
        assertEquals(422, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenLocationMatchIsCaseInsensitive_shouldRespectSlotAndCapacity() {
        // Location identification and warehouse.location compared case-insensitively
        Warehouse a1 = existing("A1", "zwolle-001", 50, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(a1));
        // maxWarehouses=2, maxCapacity=120 → currentSum=50, newCap=60 → projected=110 <=120 OK
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", 2, 120));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 60, 0);

        assertDoesNotThrow(() -> useCase.create(incoming));
        verify(warehouseRepository, times(1)).create(incoming);
    }

    // ---------------------- Capacity vs stock ----------------------

    @Test
    void create_whenCapacityNotPositive_shouldThrow422() {
        when(warehouseRepository.getAll()).thenReturn(List.of());
        when(locationResolver.resolveByIdentifier("LOC")).thenReturn(loc("LOC", 10, 100));

        Warehouse w = wh("MWH.001", "LOC", 0, 0);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_whenStockNegative_shouldThrow422() {
        when(warehouseRepository.getAll()).thenReturn(List.of());
        when(locationResolver.resolveByIdentifier("LOC")).thenReturn(loc("LOC", 10, 100));

        Warehouse w = wh("MWH.001", "LOC", 10, -1);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_whenCapacityLowerThanStock_shouldThrow422() {
        when(warehouseRepository.getAll()).thenReturn(List.of());
        when(locationResolver.resolveByIdentifier("LOC")).thenReturn(loc("LOC", 10, 100));

        Warehouse w = wh("MWH.001", "LOC", 5, 6);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.create(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ---------------------- Success path ----------------------

    @Test
    void create_success_shouldSetCreatedAt_ClearArchivedAt_andPersist() {
        when(warehouseRepository.getAll()).thenReturn(List.of());                    // no conflicts
        when(locationResolver.resolveByIdentifier("ZW-01")).thenReturn(loc("ZW-01", 10, 100));

        Warehouse incoming = wh("MWH.007", "ZW-01", 60, 5);

        // (Optional) record time before operation for window assertion
        LocalDateTime before = LocalDateTime.now();

        useCase.create(incoming);

        // It persists the incoming instance (use case modifies it in-place)
        verify(warehouseRepository, times(1)).create(incoming);

        // Fields set
        assertNotNull(incoming.createdAt, "createdAt must be set");
        assertNull(incoming.archivedAt, "archivedAt must be cleared to null");
        // Time sanity: createdAt within a reasonable window (e.g. 2 minutes)
        assertFalse(incoming.createdAt.isBefore(before));
        assertTrue(Duration.between(before, incoming.createdAt).toMinutes() < 2);
    }

    // ---------------------- Helpers ----------------------

    private static Warehouse wh(String code, String location, Integer capacity, Integer stock) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        w.location = location;
        w.capacity = capacity;
        w.stock = stock;
        return w;
    }

    private static Warehouse existing(String code, String location, Integer capacity, Integer stock, LocalDateTime archivedAt) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        w.location = location;
        w.capacity = capacity;
        w.stock = stock;
        w.createdAt = LocalDateTime.now().minusDays(1);
        w.archivedAt = archivedAt;
        return w;
    }

    private static Location loc(String id, int maxWarehouses, int maxCapacity) {
        return new Location(id, maxWarehouses, maxCapacity);
    }
}

