package com.example.warehouse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** View of stock levels - for verifying that reservations from Kafka actually work. */
@RestController
@RequestMapping("/stock")
class StockController {

    private final StockRepository repository;

    StockController(StockRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    List<StockItem> all() {
        return repository.findAll();
    }

    @GetMapping("/{product}")
    ResponseEntity<StockItem> byProduct(@PathVariable String product) {
        return repository.findByProduct(product)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
