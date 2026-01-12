
package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements creation, retrieval, replacement and archiving with all business rules.
 */
@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

    @Inject
    private WarehouseRepository warehouseRepository;

    @Inject
    private LocationResolver locationResolver; // Your LocationGateway implementing LocationResolver

    @Override
    public List<Warehouse> listAllWarehousesUnits() {
        return warehouseRepository.getAll().stream()
                .map(this::toWarehouseResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
        validateBasicPayloadForCreate(data);

        // 1) Ensure business unit code not already present among ACTIVE warehouses
        Optional<Warehouse> existingActive =
                findActiveByBusinessUnitCode(data.businessUnitCode);
        if (existingActive.isPresent()) {
            throw new WebApplicationException(
                    "Warehouse with businessUnitCode '" + data.businessUnitCode + "' already exists.",
                    Response.Status.CONFLICT); // 409
        }

        // 2) Validate location existence
        Location location = resolveOrFail(data.location);

        // 3) Feasibility at location: slot available, capacity envelope
        ensureLocationHasSlot(location, /*excluding*/ null);
        ensureLocationCapacityAllows(location, data.capacity, /*excluding*/ null);

        // 4) Capacity vs stock
        ensureCapacityHandlesStock(data.capacity, data.stock);

        // 5) Persist
        Warehouse toCreate = new Warehouse();
        toCreate.businessUnitCode = data.businessUnitCode;
        toCreate.location = data.location;
        toCreate.capacity = data.capacity;
        toCreate.stock = data.stock;
        toCreate.createdAt = LocalDateTime.now();
        toCreate.archivedAt = null;

        warehouseRepository.create(toCreate);

        return toWarehouseResponse(toCreate);
    }

    @Override
    public Warehouse getAWarehouseUnitByID(String id) {
        if (id == null || id.isBlank()) {
            throw new WebApplicationException("Invalid id.", Response.Status.BAD_REQUEST);
        }
        return warehouseRepository.getAll().stream()
                .filter(w -> id.equalsIgnoreCase(w.businessUnitCode))
                .findFirst()
                .map(this::toWarehouseResponse)
                .orElseThrow(() -> new WebApplicationException(
                        "Warehouse with id '" + id + "' not found.", Response.Status.NOT_FOUND));
    }

    @Override
    public void archiveAWarehouseUnitByID(String id) {
        Warehouse entity = warehouseRepository.getAll().stream()
                .filter(w -> id != null && id.equalsIgnoreCase(w.businessUnitCode))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(
                        "Warehouse with id '" + id + "' not found.", Response.Status.NOT_FOUND));

        if (entity.archivedAt != null) {
            throw new WebApplicationException(
                    "Warehouse '" + id + "' is already archived.", Response.Status.CONFLICT);
        }

        entity.archivedAt = LocalDateTime.now();
        warehouseRepository.update(entity);
    }

    @Override
    public Warehouse replaceTheCurrentActiveWarehouse(
            String businessUnitCode, @NotNull Warehouse data) {

        validateBasicPayloadForReplace(businessUnitCode, data);

        // Find current ACTIVE warehouse to replace
        Warehouse current = findActiveByBusinessUnitCode(businessUnitCode)
                .orElseThrow(() -> new WebApplicationException(
                        "Active warehouse with businessUnitCode '" + businessUnitCode + "' not found.",
                        Response.Status.NOT_FOUND));

        // Ensure stock matching
        if (!Objects.equals(safeInt(data.stock), safeInt(current.stock))) {
            throw new WebApplicationException(
                    "Replacement rejected: new stock (" + data.stock + ") must match current stock ("
                            + current.stock + ").",
                    Response.Status.BAD_REQUEST);
        }

        // Ensure new capacity accommodates current stock
        if (safeInt(data.capacity) < safeInt(current.stock)) {
            throw new WebApplicationException(
                    "Replacement rejected: capacity (" + data.capacity + ") cannot be lower than current stock ("
                            + current.stock + ").",
                    Response.Status.BAD_REQUEST);
        }

        // Validate target location
        Location newLocation = resolveOrFail(data.location);

        // Feasibility at target location:
        // slot availability and capacity envelope, factoring that 'current' will be archived.
        ensureLocationHasSlot(newLocation, /*excluding*/ current);
        ensureLocationCapacityAllows(newLocation, data.capacity, /*excluding*/ current);

        // Archive current
        current.archivedAt = LocalDateTime.now();
        warehouseRepository.update(current);

        // Create new warehouse (same businessUnitCode takes the place)
        Warehouse replacement = new Warehouse();
        replacement.businessUnitCode = businessUnitCode; // enforce same code
        replacement.location = data.location;
        replacement.capacity = data.capacity;
        replacement.stock = data.stock; // equals current.stock (validated)
        replacement.createdAt = LocalDateTime.now();
        replacement.archivedAt = null;

        warehouseRepository.create(replacement);

        return toWarehouseResponse(replacement);
    }

    // --------------------- Helpers & validation methods ---------------------

    private Warehouse toWarehouseResponse(Warehouse warehouse) {
        // shallow copy (keeps API surface consistent)
        Warehouse response = new Warehouse();
        response.setBusinessUnitCode(warehouse.businessUnitCode);
        response.setLocation(warehouse.location);
        response.setCapacity(warehouse.capacity);
        response.setStock(warehouse.stock);
        response.setCreatedAt(warehouse.createdAt);
        response.setArchivedAt(warehouse.archivedAt);
        return response;
    }

    private Optional<Warehouse> findActiveByBusinessUnitCode(String businessUnitCode) {
        if (businessUnitCode == null) return Optional.empty();
        return warehouseRepository.getAll().stream()
                .filter(w -> businessUnitCode.equalsIgnoreCase(w.businessUnitCode))
                .filter(w -> w.archivedAt == null)
                .findFirst();
    }

    private Location resolveOrFail(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            throw new WebApplicationException("Location must be informed.", Response.Status.BAD_REQUEST);
        }
        Location location = locationResolver.resolveByIdentifier(locationId);
        if (location == null) {
            throw new WebApplicationException(
                    "Invalid location '" + locationId + "'.", Response.Status.BAD_REQUEST);
        }
        return location;
    }

    private void ensureLocationHasSlot(Location location, Warehouse excluding) {
        long activeCount = warehouseRepository.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> w.location != null && w.location.equalsIgnoreCase(location.identification))
                .filter(w -> excluding == null || !w.businessUnitCode.equalsIgnoreCase(excluding.businessUnitCode))
                .count();

        if (activeCount >= location.maxNumberOfWarehouses) {
            throw new WebApplicationException(
                    "Location '" + location.identification + "' already has the maximum number of warehouses (" +
                            location.maxNumberOfWarehouses + ").",
                    Response.Status.BAD_REQUEST);
        }
    }

    private void ensureLocationCapacityAllows(Location location, int newWarehouseCapacity, Warehouse excluding) {
        int currentSum = warehouseRepository.getAll().stream()
                .filter(w -> w.archivedAt == null)
                .filter(w -> w.location != null && w.location.equalsIgnoreCase(location.identification))
                .filter(w -> excluding == null || !w.businessUnitCode.equalsIgnoreCase(excluding.businessUnitCode))
                .mapToInt(w -> safeInt(w.capacity))
                .sum();

        int projected = currentSum + safeInt(newWarehouseCapacity);

        if (projected > location.maxCapacity) {
            throw new WebApplicationException(
                    "Location '" + location.identification + "' cannot accommodate capacity " + newWarehouseCapacity +
                            ". Current used capacity=" + currentSum + ", maxCapacity=" + location.maxCapacity + ".",
                    Response.Status.BAD_REQUEST);
        }
    }

    private void ensureCapacityHandlesStock(Integer capacity, Integer stock) {
        int c = safeInt(capacity);
        int s = safeInt(stock);
        if (c <= 0) {
            throw new WebApplicationException("Capacity must be a positive integer.", Response.Status.BAD_REQUEST);
        }
        if (s < 0) {
            throw new WebApplicationException("Stock must be zero or a positive integer.", Response.Status.BAD_REQUEST);
        }
        if (c < s) {
            throw new WebApplicationException(
                    "Capacity (" + c + ") cannot be lower than stock (" + s + ").",
                    Response.Status.BAD_REQUEST);
        }
    }

    private void validateBasicPayloadForCreate(Warehouse data) {
        if (data == null) {
            throw new WebApplicationException("Request body is required.", Response.Status.BAD_REQUEST);
        }
        if (data.businessUnitCode == null || data.businessUnitCode.isBlank()) {
            throw new WebApplicationException("businessUnitCode is required.", Response.Status.BAD_REQUEST);
        }
        if (data.location == null || data.location.isBlank()) {
            throw new WebApplicationException("location is required.", Response.Status.BAD_REQUEST);
        }
        if (data.capacity == null) {
            throw new WebApplicationException("capacity is required.", Response.Status.BAD_REQUEST);
        }
        if (data.stock == null) {
            throw new WebApplicationException("stock is required.", Response.Status.BAD_REQUEST);
        }
    }

    private void validateBasicPayloadForReplace(String businessUnitCode, Warehouse data) {
        if (businessUnitCode == null || businessUnitCode.isBlank()) {
            throw new WebApplicationException("businessUnitCode path parameter is required.", Response.Status.BAD_REQUEST);
        }
        if (data == null) {
            throw new WebApplicationException("Request body is required.", Response.Status.BAD_REQUEST);
        }
        if (data.location == null || data.location.isBlank()) {
            throw new WebApplicationException("location is required.", Response.Status.BAD_REQUEST);
        }
        if (data.capacity == null) {
            throw new WebApplicationException("capacity is required.", Response.Status.BAD_REQUEST);
        }
        if (data.stock == null) {
            throw new WebApplicationException("stock is required.", Response.Status.BAD_REQUEST);
        }
        // Enforce that incoming businessUnitCode (if sent) matches the path (optional, but strict):
        if (data.businessUnitCode != null &&
                !businessUnitCode.equalsIgnoreCase(data.businessUnitCode)) {
            throw new WebApplicationException(
                    "businessUnitCode in body must match path parameter.", Response.Status.BAD_REQUEST);
        }
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
