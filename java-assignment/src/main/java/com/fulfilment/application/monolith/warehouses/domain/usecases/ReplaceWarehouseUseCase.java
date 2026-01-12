package com.fulfilment.application.monolith.warehouses.domain.usecases;


import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

    private final WarehouseRepository warehouseStore;
    private final LocationResolver locationResolver;

    @Inject
    public ReplaceWarehouseUseCase(WarehouseRepository warehouseStore, LocationResolver locationResolver) {
        this.warehouseStore = warehouseStore;
        this.locationResolver = locationResolver;
    }

    @Override
    public void replace(Warehouse newWarehouse) {
        validateReplacePayload(newWarehouse);

        Warehouse current = findActiveByBusinessUnitCode(newWarehouse.businessUnitCode)
                .orElseThrow(() -> notFound("Active warehouse with businessUnitCode '" +
                        newWarehouse.businessUnitCode + "' not found."));

        // Stock must match
        if (safeInt(newWarehouse.stock) != safeInt(current.stock)) {
            throw unprocessable("Replacement rejected: new stock (" + newWarehouse.stock +
                    ") must match current stock (" + current.stock + ").");
        }

        // New capacity must accommodate current stock
        if (safeInt(newWarehouse.capacity) < safeInt(current.stock)) {
            throw unprocessable("Replacement rejected: capacity (" + newWarehouse.capacity +
                    ") cannot be lower than current stock (" + current.stock + ").");
        }

        // Validate target location
        Location targetLocation = resolveOrFail(newWarehouse.location);

        // Feasibility at target location, excluding current (since it will be archived)
        ensureLocationHasSlot(targetLocation, current);
        ensureLocationCapacityAllows(targetLocation, safeInt(newWarehouse.capacity), current);

        // Archive current
        current.archivedAt = LocalDateTime.now();
        warehouseStore.update(current);

        // Create replacement (same businessUnitCode takes the place)
        Warehouse replacement = new Warehouse();
        replacement.businessUnitCode = current.businessUnitCode;
        replacement.location = newWarehouse.location;
        replacement.capacity = newWarehouse.capacity;
        replacement.stock = newWarehouse.stock; // already validated to match
        replacement.createdAt = LocalDateTime.now();
        replacement.archivedAt = null;

        warehouseStore.create(replacement);
    }

    // --------- helpers ---------
    private void validateReplacePayload(Warehouse data) {
        if (data == null) throw badRequest("Request body is required.");
        if (isBlank(data.businessUnitCode)) throw unprocessable("businessUnitCode is required.");
        if (isBlank(data.location)) throw unprocessable("location is required.");
        if (data.capacity == null) throw unprocessable("capacity is required.");
        if (data.stock == null) throw unprocessable("stock is required.");
        if (safeInt(data.capacity) <= 0) throw unprocessable("Capacity must be a positive integer.");
        if (safeInt(data.stock) < 0) throw unprocessable("Stock must be zero or a positive integer.");
    }

    private Optional<Warehouse> findActiveByBusinessUnitCode(String code) {
        return warehouseStore.getAll().stream()
                .filter(w -> code.equalsIgnoreCase(w.businessUnitCode))
                .filter(w -> w.archivedAt == null)
                .findFirst();
    }

    private Location resolveOrFail(String id) {
        Location loc = locationResolver.resolveByIdentifier(id);
        if (loc == null) throw unprocessable("Invalid location '" + id + "'.");
        return loc;
    }

    private void ensureLocationHasSlot(Location location, Warehouse excluding) {
        long activeCount = warehouseStore.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> eq(w.location, location.identification))
                .filter(w -> excluding == null || !eq(w.businessUnitCode, excluding.businessUnitCode))
                .count();
        if (activeCount >= location.maxNumberOfWarehouses) {
            throw unprocessable("Location '" + location.identification + "' already has the maximum number of warehouses (" +
                    location.maxNumberOfWarehouses + ").");
        }
    }

    private void ensureLocationCapacityAllows(Location location, int newCap, Warehouse excluding) {
        int currentSum = warehouseStore.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> eq(w.location, location.identification))
                .filter(w -> excluding == null || !eq(w.businessUnitCode, excluding.businessUnitCode))
                .mapToInt(w -> safeInt(w.capacity))
                .sum();
        int projected = currentSum + newCap;
        if (projected > location.maxCapacity) {
            throw unprocessable("Location '" + location.identification + "' cannot accommodate capacity " + newCap +
                    ". Current used capacity=" + currentSum + ", maxCapacity=" + location.maxCapacity + ".");
        }
    }

    private boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private int safeInt(Integer v) { return v == null ? 0 : v; }

    private WebApplicationException badRequest(String msg) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(msg).build());
    }
    private WebApplicationException notFound(String msg) {
        return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(msg).build());
    }
    private WebApplicationException unprocessable(String msg) {
        return new WebApplicationException(Response.status(422).entity(msg).build());
    }
}