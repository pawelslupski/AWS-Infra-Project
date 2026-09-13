package com.example.payment;

import java.math.BigDecimal;
import java.time.Instant;

/** Shape of the event received from Kafka (topic orders.created). */
public record OrderCreatedEvent(
        Long orderId,
        String product,
        int quantity,
        BigDecimal amount,
        Instant createdAt) {
}
