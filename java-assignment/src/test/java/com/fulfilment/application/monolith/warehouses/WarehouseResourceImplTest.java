package com.fulfilment.application.monolith.warehouses;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.adapters.restapi.WarehouseResourceImpl;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseResourceImplTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    LocationResolver locationResolver;

    @InjectMocks
    WarehouseResourceImpl resource;

    // ---------------------- listAll ----------------------
    @Test
    void listAll_returnsMappedCopy_ofAllWarehouses() {
        Warehouse w1 = wh("MWH.001", "LOC-001", 100, 10, LocalDateTime.now(), null);
        Warehouse w2 = wh("MWH.002", "LOC-002", 200, 20, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(w1, w2));

        List<Warehouse> out = resource.listAllWarehousesUnits();

        assertEquals(2, out.size());
        // returned objects are copies (not the same instances)
        assertNotSame(w1, out.get(0));
        assertEquals("MWH.001", out.get(0).businessUnitCode);
        assertEquals("LOC-001", out.get(0).location);
        assertEquals(100, out.get(0).capacity);
        assertEquals(10, out.get(0).stock);

        assertNotSame(w2, out.get(1));
        assertEquals("MWH.002", out.get(1).businessUnitCode);

        verify(warehouseRepository, times(1)).getAll();
    }

    // ---------------------- create ----------------------
    @Test
    void create_whenPayloadNull_throws400() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(null));
        assertEquals(400, ex.getResponse().getStatus());
        verifyNoInteractions(warehouseRepository, locationResolver);
    }

    @Test
    void create_whenMissingRequiredFields_throws400_forEach() {
        // missing businessUnitCode
        Warehouse w1 = wh(null, "LOC", 10, 0, null, null);
        WebApplicationException ex1 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w1));
        assertEquals(400, ex1.getResponse().getStatus());

        // missing location
        Warehouse w2 = wh("CODE", null, 10, 0, null, null);
        WebApplicationException ex2 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w2));
        assertEquals(400, ex2.getResponse().getStatus());

        // missing capacity
        Warehouse w3 = new Warehouse();
        w3.businessUnitCode = "CODE";
        w3.location = "LOC";
        w3.capacity = null;
        w3.stock = 0;
        WebApplicationException ex3 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w3));
        assertEquals(400, ex3.getResponse().getStatus());

        // missing stock
        Warehouse w4 = new Warehouse();
        w4.businessUnitCode = "CODE";
        w4.location = "LOC";
        w4.capacity = 10;
        w4.stock = null;
        WebApplicationException ex4 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w4));
        assertEquals(400, ex4.getResponse().getStatus());
    }

    @Test
    void create_whenActiveWarehouseWithSameCodeExists_throws409() {
        Warehouse existing = wh("MWH.001", "LOC-001", 50, 5, LocalDateTime.now(), null); // active
        when(warehouseRepository.getAll()).thenReturn(List.of(existing));

        Warehouse input = wh("MWH.001", "LOC-002", 100, 10, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(input));

        assertEquals(409, ex.getResponse().getStatus()); // conflict
        verify(warehouseRepository, times(1)).getAll();
        verify(locationResolver, never()).resolveByIdentifier(anyString());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenLocationInvalid_throws400() {
        when(warehouseRepository.getAll()).thenReturn(List.of()); // no conflict
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(null);

        Warehouse input = wh("MWH.001", "ZWOLLE-001", 100, 10, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(input));

        assertEquals(400, ex.getResponse().getStatus());
        verify(locationResolver, times(1)).resolveByIdentifier("ZWOLLE-001");
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenLocationHasNoSlot_throws400() {
        Warehouse existing1 = wh("X.001", "ZWOLLE-001", 10, 0, LocalDateTime.now(), null);
        Warehouse existing2 = wh("X.002", "ZWOLLE-001", 15, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(existing1, existing2));
        Location location = loc("ZWOLLE-001", /*maxWarehouses*/ 2, /*maxCapacity*/ 100);
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(location);

        // adding third active warehouse on same location exceeds maxNumberOfWarehouses
        Warehouse input = wh("MWH.001", "ZWOLLE-001", 10, 0, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(input));

        assertEquals(400, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenLocationCapacityEnvelopeExceeded_throws400() {
        Warehouse existing1 = wh("X.001", "ZWOLLE-001", 60, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(existing1));
        Location location = loc("ZWOLLE-001", /*maxWarehouses*/ 10, /*maxCapacity*/ 100);
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(location);

        // projected = 60(existing) + 50(new) = 110 > 100 => BAD_REQUEST
        Warehouse input = wh("MWH.001", "ZWOLLE-001", 50, 0, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(input));

        assertEquals(400, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_whenCapacityOrStockInvalid_throws400() {
        when(warehouseRepository.getAll()).thenReturn(List.of());
        when(locationResolver.resolveByIdentifier(anyString())).thenReturn(loc("LOC", 5, 100));

        // capacity <= 0
        Warehouse w1 = wh("MWH.001", "LOC", 0, 0, null, null);
        WebApplicationException ex1 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w1));
        assertEquals(400, ex1.getResponse().getStatus());

        // stock < 0
        Warehouse w2 = wh("MWH.001", "LOC", 10, -1, null, null);
        WebApplicationException ex2 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w2));
        assertEquals(400, ex2.getResponse().getStatus());

        // capacity < stock
        Warehouse w3 = wh("MWH.001", "LOC", 5, 10, null, null);
        WebApplicationException ex3 = assertThrows(WebApplicationException.class,
                () -> resource.createANewWarehouseUnit(w3));
        assertEquals(400, ex3.getResponse().getStatus());

        verify(warehouseRepository, never()).create(any());
    }

    @Test
    void create_success_persists_andReturnsCopy() {
        when(warehouseRepository.getAll()).thenReturn(List.of()); // no conflict
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(loc("ZWOLLE-001", 10, 100));

        Warehouse input = wh("MWH.001", "ZWOLLE-001", 50, 10, null, null);

        // capture entity passed to create
        ArgumentCaptor<Warehouse> toCreate = ArgumentCaptor.forClass(Warehouse.class);

        Warehouse out = resource.createANewWarehouseUnit(input);

        verify(warehouseRepository, times(1)).create(toCreate.capture());
        Warehouse created = toCreate.getValue();
        assertEquals("MWH.001", created.businessUnitCode);
        assertEquals("ZWOLLE-001", created.location);
        assertEquals(50, created.capacity);
        assertEquals(10, created.stock);
        assertNotNull(created.createdAt);
        assertNull(created.archivedAt);

        // response is a shallow copy of created
        assertNotSame(created, out);
        assertEquals(created.businessUnitCode, out.businessUnitCode);
        assertEquals(created.location, out.location);
        assertEquals(created.capacity, out.capacity);
        assertEquals(created.stock, out.stock);
    }

    // ---------------------- get by id ----------------------
    @Test
    void getById_whenIdBlankOrNull_throws400() {
        WebApplicationException ex1 = assertThrows(WebApplicationException.class,
                () -> resource.getAWarehouseUnitByID(null));
        assertEquals(400, ex1.getResponse().getStatus());

        WebApplicationException ex2 = assertThrows(WebApplicationException.class,
                () -> resource.getAWarehouseUnitByID("  "));
        assertEquals(400, ex2.getResponse().getStatus());
    }

    @Test
    void getById_whenNotFound_throws404() {
        when(warehouseRepository.getAll()).thenReturn(List.of(wh("MWH.002", "LOC", 10, 0, null, null)));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.getAWarehouseUnitByID("MWH.001"));

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void getById_success_returnsCopy() {
        Warehouse existing = wh("MWH.001", "LOC", 100, 10, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(existing));

        Warehouse out = resource.getAWarehouseUnitByID("mwh.001"); // case-insensitive

        assertNotSame(existing, out);
        assertEquals("MWH.001", out.businessUnitCode);
        assertEquals("LOC", out.location);
    }

    // ---------------------- archive ----------------------
    @Test
    void archive_whenNotFound_throws404() {
        when(warehouseRepository.getAll()).thenReturn(List.of(wh("MWH.002", "LOC", 10, 0, null, null)));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.archiveAWarehouseUnitByID("MWH.001"));

        assertEquals(404, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).update(any());
    }

    @Test
    void archive_whenAlreadyArchived_throws409() {
        Warehouse archived = wh("MWH.001", "LOC", 10, 0, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        when(warehouseRepository.getAll()).thenReturn(List.of(archived));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.archiveAWarehouseUnitByID("MWH.001"));

        assertEquals(409, ex.getResponse().getStatus());
        verify(warehouseRepository, never()).update(any());
    }

    @Test
    void archive_success_setsArchivedAt_andCallsUpdate() {
        Warehouse active = wh("MWH.001", "LOC", 10, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(active));

        resource.archiveAWarehouseUnitByID("mwh.001");

        assertNotNull(active.archivedAt);
        verify(warehouseRepository, times(1)).update(active);
    }

    // ---------------------- replace ----------------------
    @Test
    void replace_whenInvalidPathOrBody_throws400_forEach() {
        // invalid path
        WebApplicationException ex1 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse(" ", wh("MWH.001", "LOC", 10, 0, null, null)));
        assertEquals(400, ex1.getResponse().getStatus());

        // null body
        WebApplicationException ex2 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", null));
        assertEquals(400, ex2.getResponse().getStatus());

        // missing location
        Warehouse wLoc = new Warehouse();
        wLoc.businessUnitCode = "MWH.001"; wLoc.capacity = 10; wLoc.stock = 0; wLoc.location = null;
        WebApplicationException ex3 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", wLoc));
        assertEquals(400, ex3.getResponse().getStatus());

        // missing capacity
        Warehouse wCap = new Warehouse();
        wCap.businessUnitCode = "MWH.001"; wCap.location = "LOC"; wCap.stock = 0; wCap.capacity = null;
        WebApplicationException ex4 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", wCap));
        assertEquals(400, ex4.getResponse().getStatus());

        // missing stock
        Warehouse wStock = new Warehouse();
        wStock.businessUnitCode = "MWH.001"; wStock.location = "LOC"; wStock.capacity = 10; wStock.stock = null;
        WebApplicationException ex5 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", wStock));
        assertEquals(400, ex5.getResponse().getStatus());

        // body businessUnitCode mismatches path
        Warehouse wMismatch = wh("MWH.999", "LOC", 10, 0, null, null);
        WebApplicationException ex6 = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", wMismatch));
        assertEquals(400, ex6.getResponse().getStatus());
    }

    @Test
    void replace_whenActiveCurrentNotFound_throws404() {
        Warehouse other = wh("OTHER", "LOC", 10, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(other));

        Warehouse input = wh("MWH.001", "LOC2", 10, 0, null, null);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenStockMismatch_throws400() {
        Warehouse current = wh("MWH.001", "LOC", 10, 5, LocalDateTime.now(), null); // stock=5
        when(warehouseRepository.getAll()).thenReturn(List.of(current));

        Warehouse input = wh("MWH.001", "LOC2", 10, 6, null, null); // stock=6

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityLowerThanCurrentStock_throws400() {
        Warehouse current = wh("MWH.001", "LOC", 10, 7, LocalDateTime.now(), null); // stock=7
        when(warehouseRepository.getAll()).thenReturn(List.of(current));

        Warehouse input = wh("MWH.001", "LOC2", 6, 7, null, null); // capacity < stock
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenNewLocationInvalid_throws400() {
        Warehouse current = wh("MWH.001", "LOC", 10, 5, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current));
        when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(null);

        Warehouse input = wh("MWH.001", "ZWOLLE-001", 20, 5, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenNewLocationHasNoSlot_excludingCurrent_throws400() {
        Warehouse current = wh("MWH.001", "ZWOLLE-001", 50, 5, LocalDateTime.now(), null);
        Warehouse otherActive = wh("OTHER", "ZWOLLE-001", 10, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current, otherActive));

        // max warehouses = 1 → only 'current' allowed; excluding current, activeCount==1, equals max → reject
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", /*maxWarehouses*/ 1, /*maxCapacity*/ 100));

        Warehouse input = wh("MWH.001", "ZWOLLE-001", 60, 5, null, null); // same location

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void replace_whenCapacityEnvelopeExceeded_excludingCurrent_throws400() {
        Warehouse current = wh("MWH.001", "ZWOLLE-001", 50, 5, LocalDateTime.now(), null);
        Warehouse otherActive = wh("OTHER", "ZWOLLE-001", 60, 0, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current, otherActive));

        // maxCapacity = 100; excluding current, currentSum=60; projected=60 + newCapacity(50)=110 -> reject
        when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
                .thenReturn(loc("ZWOLLE-001", /*maxWarehouses*/ 10, /*maxCapacity*/ 100));

        Warehouse input = wh("MWH.001", "ZWOLLE-001", 50, 5, null, null);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.replaceTheCurrentActiveWarehouse("MWH.001", input));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void replace_success_archivesCurrent_createsReplacement_returnsCopy() {
        Warehouse current = wh("MWH.001", "OLD-LOC", 50, 5, LocalDateTime.now(), null);
        when(warehouseRepository.getAll()).thenReturn(List.of(current));
        when(locationResolver.resolveByIdentifier("NEW-LOC")).thenReturn(loc("NEW-LOC", 10, 100));

        Warehouse input = wh("MWH.001", "NEW-LOC", 60, 5, null, null);

        ArgumentCaptor<Warehouse> updatedCurrent = ArgumentCaptor.forClass(Warehouse.class);
        ArgumentCaptor<Warehouse> createdReplacement = ArgumentCaptor.forClass(Warehouse.class);

        Warehouse out = resource.replaceTheCurrentActiveWarehouse("MWH.001", input);

        // current archived and updated
        verify(warehouseRepository, times(1)).update(updatedCurrent.capture());
        Warehouse curUpdated = updatedCurrent.getValue();
        assertSame(current, curUpdated);
        assertNotNull(current.archivedAt);

        // replacement created
        verify(warehouseRepository, times(1)).create(createdReplacement.capture());
        Warehouse created = createdReplacement.getValue();
        assertEquals("MWH.001", created.businessUnitCode); // enforced same code
        assertEquals("NEW-LOC", created.location);
        assertEquals(60, created.capacity);
        assertEquals(5, created.stock);
        assertNotNull(created.createdAt);
        assertNull(created.archivedAt);

        // response is copy of created
        assertNotSame(created, out);
        assertEquals(created.businessUnitCode, out.businessUnitCode);
        assertEquals(created.location, out.location);
        assertEquals(created.capacity, out.capacity);
        assertEquals(created.stock, out.stock);
    }

    // ---------------------- helpers ----------------------
    private static Warehouse wh(String code, String loc, Integer cap, Integer stock,
                                LocalDateTime created, LocalDateTime archived) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        w.location = loc;
        w.capacity = cap;
        w.stock = stock;
        w.createdAt = created;
        w.archivedAt = archived;
        return w;
    }

    private static Location loc(String id, int maxWarehouses, int maxCapacity) {
        Location l = new Location(id, maxWarehouses, maxCapacity);
        return l;
    }
}
