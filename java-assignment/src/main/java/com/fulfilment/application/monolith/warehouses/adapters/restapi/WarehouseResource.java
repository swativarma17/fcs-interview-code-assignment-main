package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface WarehouseResource {

    public List<Warehouse> listAllWarehousesUnits();

    public Warehouse createANewWarehouseUnit(Warehouse data);

    public Warehouse getAWarehouseUnitByID(String id);

    public void archiveAWarehouseUnitByID(String id);

    public Warehouse replaceTheCurrentActiveWarehouse(
            String businessUnitCode,  Warehouse data);
}
