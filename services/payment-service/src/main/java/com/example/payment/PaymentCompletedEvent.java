package com.example.payment;

import java.math.BigDecimal;
import java.time.Instant;

/** Event published to Kafka (topic payments.completed) once a payment is settled. */
public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        String product,
        int quantity,
        BigDecimal amount,
        String invoiceKey,
        Instant completedAt) {
}
