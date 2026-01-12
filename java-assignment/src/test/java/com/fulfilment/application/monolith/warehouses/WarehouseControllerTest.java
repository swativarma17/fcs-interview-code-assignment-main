package com.fulfilment.application.monolith.warehouses;

import com.fulfilment.application.monolith.warehouses.adapters.restapi.WarehouseController;
import com.fulfilment.application.monolith.warehouses.adapters.restapi.WarehouseResourceImpl;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    WarehouseResourceImpl warehouseResource;

    @InjectMocks
    WarehouseController controller;

    // -------- listAll() --------
    @Test
    void listAll_shouldDelegateToResource_andReturnList() {
        Warehouse w1 = mock(Warehouse.class);
        Warehouse w2 = mock(Warehouse.class);
        List<Warehouse> expected = List.of(w1, w2);

        when(warehouseResource.listAllWarehousesUnits()).thenReturn(expected);

        List<Warehouse> result = controller.listAll();

        assertSame(expected, result);
        verify(warehouseResource, times(1)).listAllWarehousesUnits();
        verifyNoMoreInteractions(warehouseResource);
    }

    // -------- create(data) --------
    @Test
    void create_shouldPassPayload_andReturnCreatedWarehouse() {
        Warehouse input = mock(Warehouse.class);
        Warehouse created = mock(Warehouse.class);

        when(warehouseResource.createANewWarehouseUnit(input)).thenReturn(created);

        Warehouse result = controller.create(input);

        assertSame(created, result);
        verify(warehouseResource, times(1)).createANewWarehouseUnit(input);
        verifyNoMoreInteractions(warehouseResource);
    }

    // -------- get(id) --------
    @Test
    void get_whenFound_shouldReturnWarehouse() {
        Warehouse found = mock(Warehouse.class);
        when(warehouseResource.getAWarehouseUnitByID("MWH.001")).thenReturn(found);

        Warehouse result = controller.get("MWH.001");

        assertSame(found, result);
        verify(warehouseResource, times(1)).getAWarehouseUnitByID("MWH.001");
        verifyNoMoreInteractions(warehouseResource);
    }

    @Test
    void get_whenNotFound_shouldReturnNull() {
        when(warehouseResource.getAWarehouseUnitByID("NOPE")).thenReturn(null);

        Warehouse result = controller.get("NOPE");

        assertNull(result);
        verify(warehouseResource, times(1)).getAWarehouseUnitByID("NOPE");
        verifyNoMoreInteractions(warehouseResource);
    }

    // -------- archive(id) --------
    @Test
    void archive_shouldDelegate_andReturnVoid() {
        // no exception → success
        doNothing().when(warehouseResource).archiveAWarehouseUnitByID("MWH.001");

        assertDoesNotThrow(() -> controller.archive("MWH.001"));

        verify(warehouseResource, times(1)).archiveAWarehouseUnitByID("MWH.001");
        verifyNoMoreInteractions(warehouseResource);
    }

    @Test
    void archive_whenResourceThrows_shouldPropagateException() {
        doThrow(new jakarta.ws.rs.NotFoundException("not found"))
                .when(warehouseResource).archiveAWarehouseUnitByID("MWH.404");

        jakarta.ws.rs.NotFoundException ex = assertThrows(
                jakarta.ws.rs.NotFoundException.class,
                () -> controller.archive("MWH.404")
        );

        assertTrue(ex.getMessage().contains("not found"));
        verify(warehouseResource, times(1)).archiveAWarehouseUnitByID("MWH.404");
        verifyNoMoreInteractions(warehouseResource);
    }

    // -------- replace(code, data) --------
    @Test
    void replace_shouldDelegateWithCode_andReturnReplacedWarehouse() {
        Warehouse payload = mock(Warehouse.class);
        Warehouse replaced = mock(Warehouse.class);

        when(warehouseResource.replaceTheCurrentActiveWarehouse("MWH.001", payload))
                .thenReturn(replaced);

        Warehouse result = controller.replace("MWH.001", payload);

        assertSame(replaced, result);
        verify(warehouseResource, times(1))
                .replaceTheCurrentActiveWarehouse("MWH.001", payload);
        verifyNoMoreInteractions(warehouseResource);
    }

    @Test
    void replace_whenResourceThrows_shouldPropagateException() {
        Warehouse payload = mock(Warehouse.class);
        when(warehouseResource.replaceTheCurrentActiveWarehouse("MWH.999", payload))
                .thenThrow(new IllegalStateException("Cannot replace"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> controller.replace("MWH.999", payload)
        );

        assertTrue(ex.getMessage().contains("Cannot replace"));
        verify(warehouseResource, times(1))
                .replaceTheCurrentActiveWarehouse("MWH.999", payload);
        verifyNoMoreInteractions(warehouseResource);
    }
}
