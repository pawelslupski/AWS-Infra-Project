package com.example.warehouse;

import java.math.BigDecimal;
import java.time.Instant;

/** Shape of the event received from Kafka (topic payments.completed). */
public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        String product,
        int quantity,
        BigDecimal amount,
        String invoiceKey,
        Instant completedAt) {
}
