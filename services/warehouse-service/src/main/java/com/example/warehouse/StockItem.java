package com.example.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Stock level of a single product in the Warehouse service's PostgreSQL database. */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String product;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    /** Optimistic lock - protects against a race between two reservations of the same product. */
    @Version
    private long version;

    protected StockItem() {
        // required by JPA
    }

    public StockItem(String product, int available, int reserved) {
        this.product = product;
        this.available = available;
        this.reserved = reserved;
    }

    /**
     * Reserves up to {@code quantity} units (as many as the available stock allows).
     *
     * @return the number of units actually reserved
     */
    public int reserve(int quantity) {
        int toReserve = Math.min(quantity, available);
        available -= toReserve;
        reserved += toReserve;
        return toReserve;
    }

    public Long getId() {
        return id;
    }

    public String getProduct() {
        return product;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}
