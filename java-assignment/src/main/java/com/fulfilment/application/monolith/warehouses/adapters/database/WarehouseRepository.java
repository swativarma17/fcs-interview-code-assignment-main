package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {


    @Override
    public List<Warehouse> getAll() {
        return this.listAll().stream().map(DbWarehouse::toWarehouse).toList();
    }

    @Override
    @Transactional
    public void create(Warehouse warehouse) {
        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse must not be null.");
        }
        // Ensure createdAt for new entries
        if (warehouse.createdAt == null) {
            warehouse.createdAt = LocalDateTime.now();
        }
        warehouse.archivedAt = null;

        DbWarehouse entity = DbWarehouse.fromWarehouse(warehouse);
        this.persist(entity);

    }

    @Override
    @Transactional
    public void update(Warehouse warehouse) {
        if (warehouse == null || warehouse.businessUnitCode == null || warehouse.businessUnitCode.isBlank()) {
            throw new IllegalArgumentException("businessUnitCode is required for update.");
        }

        DbWarehouse existing = findDbByBusinessUnitCode(warehouse.businessUnitCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse with businessUnitCode '" + warehouse.businessUnitCode + "' not found."));

        // Update mutable fields
        existing.setLocation(warehouse.location);
        existing.setCapacity(safeInt(warehouse.capacity));
        existing.setStock(safeInt(warehouse.stock));
        existing.setCreatedAt(warehouse.createdAt);   // keep/create as needed
        existing.setArchivedAt(warehouse.archivedAt); // archive timestamp when applicable

        // JPA dirty checking will persist changes at TX end; flush if you want immediate SQL execution:
        // this.getEntityManager().flush();
    }

    @Override
    @Transactional
    public void remove(Warehouse warehouse) {
        if (warehouse == null || warehouse.businessUnitCode == null || warehouse.businessUnitCode.isBlank()) {
            throw new IllegalArgumentException("businessUnitCode is required for remove.");
        }

        Optional<DbWarehouse> existing = findDbByBusinessUnitCode(warehouse.businessUnitCode);
        existing.ifPresent(this::delete);
    }

    @Override
    public Warehouse findByBusinessUnitCode(String buCode) {
        return findDbByBusinessUnitCode(buCode)
                .map(DbWarehouse::toWarehouse)
                .orElse(null);
    }


    private Optional<DbWarehouse> findDbByBusinessUnitCode(String buCode) {
        if (buCode == null || buCode.isBlank()) return Optional.empty();
        return find("businessUnitCode = ?1", buCode).firstResultOptional();
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
