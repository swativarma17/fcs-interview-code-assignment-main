package com.fulfilment.application.monolith.warehouses.domain.usecases;


import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;

import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

    private final WarehouseRepository warehouseStore;
    private final LocationResolver locationResolver;

    @Inject
    public CreateWarehouseUseCase(WarehouseRepository warehouseStore, LocationResolver locationResolver) {
        this.warehouseStore = warehouseStore;
        this.locationResolver = locationResolver;
    }

    @Override
    public void create(Warehouse warehouse) {
        validateCreatePayload(warehouse);

        // Unique business unit code among active warehouses
        boolean existsActive = warehouseStore.getAll().stream()
                .anyMatch(w -> eq(w.businessUnitCode, warehouse.businessUnitCode) && w.archivedAt == null);
        if (existsActive) {
            throw conflict("Warehouse with businessUnitCode '" + warehouse.businessUnitCode + "' already exists.");
        }

        // Validate location
        Location location = resolveOrFail(warehouse.location);

        // Location feasibility: slot available + capacity envelope
        ensureLocationHasSlot(location);
        ensureLocationCapacityAllows(location, safeInt(warehouse.capacity));

        // Capacity vs stock
        ensureCapacityHandlesStock(safeInt(warehouse.capacity), safeInt(warehouse.stock));

        warehouse.createdAt = LocalDateTime.now();
        warehouse.archivedAt = null;

        warehouseStore.create(warehouse);
    }

    // --------- helpers ---------
    private void validateCreatePayload(Warehouse data) {
        if (data == null) throw badRequest("Request body is required.");
        if (isBlank(data.businessUnitCode)) throw unprocessable("businessUnitCode is required.");
        if (isBlank(data.location)) throw unprocessable("location is required.");
        if (data.capacity == null) throw unprocessable("capacity is required.");
        if (data.stock == null) throw unprocessable("stock is required.");
    }

    private Location resolveOrFail(String locationId) {
        Location location = locationResolver.resolveByIdentifier(locationId);
        if (location == null) {
            throw unprocessable("Invalid location '" + locationId + "'.");
        }
        return location;
    }

    private void ensureLocationHasSlot(Location location) {
        long activeCount = warehouseStore.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> eq(w.location, location.identification))
                .count();
        if (activeCount >= location.maxNumberOfWarehouses) {
            throw unprocessable("Location '" + location.identification + "' already has the maximum number of warehouses (" +
                    location.maxNumberOfWarehouses + ").");
        }
    }

    private void ensureLocationCapacityAllows(Location location, int newCap) {
        int currentSum = warehouseStore.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> eq(w.location, location.identification))
                .mapToInt(w -> safeInt(w.capacity))
                .sum();
        int projected = currentSum + newCap;
        if (projected > location.maxCapacity) {
            throw unprocessable("Location '" + location.identification + "' cannot accommodate capacity " + newCap +
                    ". Current used capacity=" + currentSum + ", maxCapacity=" + location.maxCapacity + ".");
        }
    }

    private void ensureCapacityHandlesStock(int cap, int stock) {
        if (cap <= 0) throw unprocessable("Capacity must be a positive integer.");
        if (stock < 0) throw unprocessable("Stock must be zero or a positive integer.");
        if (cap < stock) throw unprocessable("Capacity (" + cap + ") cannot be lower than stock (" + stock + ").");
    }

    private boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private int safeInt(Integer v) { return v == null ? 0 : v; }

    private WebApplicationException badRequest(String msg) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(msg).build());
    }
    private WebApplicationException conflict(String msg) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(msg).build());
    }
    private WebApplicationException unprocessable(String msg) {
        // 422 without relying on Status.UNPROCESSABLE_ENTITY (for wider API compatibility)
        return new WebApplicationException(Response.status(422).entity(msg).build());
    }
}

