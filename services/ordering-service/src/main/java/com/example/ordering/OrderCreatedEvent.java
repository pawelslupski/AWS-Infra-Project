package com.example.ordering;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to Kafka after an order is created.
 * payment-service consumes the same JSON shape (its own copy of the record).
 */
public record OrderCreatedEvent(
        Long orderId,
        String product,
        int quantity,
        BigDecimal amount,
        Instant createdAt) {
}
