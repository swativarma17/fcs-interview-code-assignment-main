package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

    private final WarehouseRepository warehouseStore;

    @Inject
    public ArchiveWarehouseUseCase(WarehouseRepository warehouseStore) {
        this.warehouseStore = warehouseStore;
    }

    @Override
    public void archive(Warehouse warehouse) {
        if (warehouse == null || isBlank(warehouse.businessUnitCode)) {
            throw badRequest("businessUnitCode is required to archive.");
        }

        Warehouse existing = findByBusinessUnitCode(warehouse.businessUnitCode)
                .orElseThrow(() -> notFound("Warehouse '" + warehouse.businessUnitCode + "' not found."));

        if (existing.archivedAt != null) {
            throw conflict("Warehouse '" + warehouse.businessUnitCode + "' is already archived.");
        }

        existing.archivedAt = LocalDateTime.now();
        warehouseStore.update(existing);
    }

    private Optional<Warehouse> findByBusinessUnitCode(String code) {
        return warehouseStore.getAll().stream()
                .filter(w -> code.equalsIgnoreCase(w.businessUnitCode))
                .findFirst();
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private WebApplicationException badRequest(String msg) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity(msg).build());
    }
    private WebApplicationException notFound(String msg) {
        return new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity(msg).build());
    }
    private WebApplicationException conflict(String msg) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity(msg).build());
    }
}

