package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "warehouses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouses_bucode", columnNames = "businessUnitCode")
})
public class DbWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String businessUnitCode;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime archivedAt;

    // ----- getters/setters -----
    public Long getId() { return id; }
    public String getBusinessUnitCode() { return businessUnitCode; }
    public void setBusinessUnitCode(String businessUnitCode) { this.businessUnitCode = businessUnitCode; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }

    // ----- mapping helpers -----
    public Warehouse toWarehouse() {
        Warehouse w = new Warehouse();
        w.businessUnitCode = this.businessUnitCode;
        w.location = this.location;
        w.capacity = this.capacity;
        w.stock = this.stock;
        w.createdAt = this.createdAt;
        w.archivedAt = this.archivedAt;
        return w;
    }

    public static DbWarehouse fromWarehouse(Warehouse w) {
        DbWarehouse db = new DbWarehouse();
        db.setBusinessUnitCode(w.businessUnitCode);
        db.setLocation(w.location);
        db.setCapacity(w.capacity);
        db.setStock(w.stock);
        db.setCreatedAt(w.createdAt != null ? w.createdAt : LocalDateTime.now());
        db.setArchivedAt(w.archivedAt);
        return db;
    }
}
