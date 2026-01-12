package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.usecases.ReplaceWarehouseUseCase;
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
 * Unit tests for ReplaceWarehouseUseCase:
 * - payload validation (400/422)
 * - active current lookup (404)
 * - stock equality and capacity vs current stock (422)
 * - target location validity and feasibility excluding current (422)
 * - success path: archive current + create replacement
 */
@ExtendWith(MockitoExtension.class)
class ReplaceWarehouseUseCaseTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    LocationResolver locationResolver;

    ReplaceWarehouseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReplaceWarehouseUseCase(warehouseRepository, locationResolver);
    }

    // ---------------------- Validation: payload ----------------------

    @Test
    void replace_whenPayloadNull_shouldThrow400() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(null));
        assertEquals(400, ex.getResponse().getStatus(), "Expected BAD_REQUEST (400)");
        verifyNoInteractions(warehouseRepository, locationResolver);
    }

    @Test
    void replace_whenMissingBusinessUnitCode_shouldThrow422() {
        Warehouse w = wh(null, "LOC", 10, 0);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenMissingLocation_shouldThrow422() {
        Warehouse w = wh("MWH.001", null, 10, 0);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityNull_shouldThrow422() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = "MWH.001";
        w.location = "LOC";
        w.capacity = null;
        w.stock = 0;

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenStockNull_shouldThrow422() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = "MWH.001";
        w.location = "LOC";
        w.capacity = 10;
        w.stock = null;

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityNotPositive_shouldThrow422() {
        Warehouse w = wh("MWH.001", "LOC", 0, 0);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenStockNegative_shouldThrow422() {
        Warehouse w = wh("MWH.001", "LOC", 10, -1);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(w));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ---------------------- Active current lookup ----------------------

    @Test
    void replace_whenActiveCurrentNotFound_shouldThrow404() {
        // Repo has only different code or archived entries
        Warehouse archived = existing("MWH.001", "LOC", 10, 0, LocalDateTime.now().minusDays(1));
        Warehouse otherActive = existing("OTHER", "LOC", 10, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(archived, otherActive));

        Warehouse incoming = wh("MWH.001", "LOC2", 10, 0);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ---------------------- Stock match & capacity vs current stock ----------------------

    @Test
    void replace_whenStockMismatch_shouldThrow422() {
        Warehouse current = existing("MWH.001", "LOC", 10, 5, null); // stock = 5
        when(warehouseRepository.getAll()).thenReturn(List.of(current));

        Warehouse incoming = wh("MWH.001", "LOC2", 10, 6); // stock = 6

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityLowerThanCurrentStock_shouldThrow422() {
        Warehouse current = existing("MWH.001", "LOC", 10, 7, null); // current stock = 7
        when(warehouseRepository.getAll()).thenReturn(List.of(current));

        Warehouse incoming = wh("MWH.001", "LOC2", 6, 7); // new capacity < current stock

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ---------------------- Target location validation & feasibility ----------------------

    @Test
    void replace_whenTargetLocationInvalid_shouldThrow422() {
        Warehouse current = existing("MWH.001", "LOC", 10, 5, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current));
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(null);

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 20, 5);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenNoSlotAtTarget_excludingCurrent_shouldThrow422() {
        // current at ZWOLLE-001; another active at same location
        Warehouse current = existing("MWH.001", "ZWOLLE-001", 50, 5, null);
        Warehouse otherActive = existing("OTHER", "ZWOLLE-001", 10, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current, otherActive));

        // max warehouses = 1 → excluding current, activeCount==1 already ⇒ reject
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", /*maxNumberOfWarehouses*/ 1, /*maxCapacity*/ 100));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 60, 5);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityEnvelopeExceeded_excludingCurrent_shouldThrow422() {
        Warehouse current = existing("MWH.001", "ZWOLLE-001", 50, 5, null);
        Warehouse otherActive = existing("OTHER", "ZWOLLE-001", 60, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current, otherActive));

        // maxCapacity = 100; excluding current, currentSum=60; projected=60 + newCap(50)=110 -> reject
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", /*maxNumberOfWarehouses*/ 10, /*maxCapacity*/ 100));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 50, 5);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> useCase.replace(incoming));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void replace_shouldMatchLocationCaseInsensitively_andAllowWhenWithinLimits() {
        Warehouse current = existing("MWH.001", "zwolle-001", 50, 5, null);
        Warehouse otherActive = existing("OTHER", "ZwOlLe-001", 20, 0, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current, otherActive));

        // excluding current, currentSum=20; newCap=60; projected=80 <= max 100; max warehouses = 3, activeCount=1
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", 3, 100));

        Warehouse incoming = wh("MWH.001", "ZWOLLE-001", 60, 5);

        assertDoesNotThrow(() -> useCase.replace(incoming));
        // Verify archive+update and create invoked
        verify(warehouseRepository, times(1)).update(current);
        verify(warehouseRepository, times(1)).create(any(Warehouse.class));
    }

    // ---------------------- Success path ----------------------

    @Test
    void replace_success_shouldArchiveCurrent_andCreateReplacement_withSameCode() {
        Warehouse current = existing("MWH.001", "OLD-LOC", 50, 5, null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current));
        when(locationResolver.resolveByIdentifier("NEW-LOC")).thenReturn(loc("NEW-LOC", 10, 100));

        Warehouse incoming = wh("MWH.001", "NEW-LOC", 60, 5);

        LocalDateTime before = LocalDateTime.now();

        ArgumentCaptor<Warehouse> updatedCurrent = ArgumentCaptor.forClass(Warehouse.class);
        ArgumentCaptor<Warehouse> createdReplacement = ArgumentCaptor.forClass(Warehouse.class);

        useCase.replace(incoming);

        // current archived and updated
        verify(warehouseRepository, times(1)).update(updatedCurrent.capture());
        Warehouse curUpdated = updatedCurrent.getValue();
        assertSame(current, curUpdated);
        assertNotNull(current.archivedAt, "archivedAt should be set on current");
        assertFalse(current.archivedAt.isBefore(before));
        assertTrue(Duration.between(before, current.archivedAt).toMinutes() < 2);

        // replacement created
        verify(warehouseRepository, times(1)).create(createdReplacement.capture());
        Warehouse created = createdReplacement.getValue();
        assertEquals("MWH.001", created.businessUnitCode, "replacement must keep same BU code");
        assertEquals("NEW-LOC", created.location);
        assertEquals(60, created.capacity);
        assertEquals(5, created.stock);
        assertNotNull(created.createdAt);
        assertNull(created.archivedAt);

        // Ensure locationResolver was used
        verify(locationResolver, times(1)).resolveByIdentifier("NEW-LOC");
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
