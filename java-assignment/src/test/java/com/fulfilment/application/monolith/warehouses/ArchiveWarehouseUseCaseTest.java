package com.fulfilment.application.monolith.warehouses;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.usecases.ArchiveWarehouseUseCase;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveWarehouseUseCaseTest {

    @Mock
    WarehouseRepository warehouseRepository;

    @InjectMocks
    ArchiveWarehouseUseCase useCase;

    // -------- BAD REQUEST: null warehouse --------
    @Test
    void archive_nullWarehouse_throws400() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> useCase.archive(null));

        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(String.valueOf(ex.getResponse().getEntity()).contains("businessUnitCode is required"));
        verify(warehouseRepository, never()).getAll();
        verify(warehouseRepository, never()).update(any());
    }

    // -------- BAD REQUEST: blank businessUnitCode --------
    @Test
    void archive_blankCode_throws400() {
        Warehouse w = warehouse(null);         // code = null
        WebApplicationException ex1 = assertThrows(WebApplicationException.class,
            () -> useCase.archive(w));
        assertEquals(400, ex1.getResponse().getStatus());

        Warehouse w2 = warehouse("   ");       // blank code
        WebApplicationException ex2 = assertThrows(WebApplicationException.class,
            () -> useCase.archive(w2));
        assertEquals(400, ex2.getResponse().getStatus());

        verify(warehouseRepository, never()).getAll();
        verify(warehouseRepository, never()).update(any());
    }

    // -------- NOT FOUND: repository has no matching warehouse --------
    @Test
    void archive_notFound_throws404() {
        when(warehouseRepository.getAll()).thenReturn(List.of(
            existingWarehouse("OTHER.001", null),
            existingWarehouse("MWH.002", null)
        ));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> useCase.archive(warehouse("MWH.001")));

        assertEquals(404, ex.getResponse().getStatus());
        assertTrue(String.valueOf(ex.getResponse().getEntity()).contains("Warehouse 'MWH.001' not found"));

        verify(warehouseRepository, times(1)).getAll();
        verify(warehouseRepository, never()).update(any());
    }

    // -------- CONFLICT: already archived --------
    @Test
    void archive_alreadyArchived_throws409() {
        LocalDateTime archived = LocalDateTime.now().minusDays(1);
        when(warehouseRepository.getAll()).thenReturn(List.of(
            existingWarehouse("MWH.001", archived)
        ));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> useCase.archive(warehouse("MWH.001")));

        assertEquals(409, ex.getResponse().getStatus());
        assertTrue(String.valueOf(ex.getResponse().getEntity()).contains("already archived"));

        verify(warehouseRepository, times(1)).getAll();
        verify(warehouseRepository, never()).update(any());
    }

    // -------- HAPPY PATH: archives and updates --------
    @Test
    void archive_success_setsArchivedAt_andCallsUpdate() {
        Warehouse existing = existingWarehouse("MWH.001", null);
        when(warehouseRepository.getAll()).thenReturn(List.of(existing));

        // capture argument passed to repository.update(...)
        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);

        useCase.archive(warehouse("MWH.001"));

        // update invoked
        verify(warehouseRepository, times(1)).getAll();
        verify(warehouseRepository, times(1)).update(captor.capture());

        Warehouse updated = captor.getValue();
        assertSame(existing, updated, "Use case should update the found existing entity");
        assertNotNull(updated.archivedAt, "archivedAt must be set");
        // Optional: sanity check of time window
        assertTrue(updated.archivedAt.isAfter(LocalDateTime.now().minusMinutes(1)),
                "archivedAt should be recent");
    }

    // -------- Case-insensitive match --------
    @Test
    void archive_caseInsensitiveCode_success() {
        Warehouse existing = existingWarehouse("MWH.001", null); // stored uppercase
        when(warehouseRepository.getAll()).thenReturn(List.of(existing));

        useCase.archive(warehouse("mwh.001")); // lower-case input

        verify(warehouseRepository, times(1)).update(existing);
        assertNotNull(existing.archivedAt);
    }

    // ---------- helper factory methods ----------
    private static Warehouse warehouse(String code) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        // other fields can be set if needed by your model
        return w;
    }

    private static Warehouse existingWarehouse(String code, LocalDateTime archivedAt) {
        Warehouse w = new Warehouse();
        w.businessUnitCode = code;
        w.archivedAt = archivedAt;
        return w;
    }
}
