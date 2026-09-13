package com.example.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByProduct(String product);
}
