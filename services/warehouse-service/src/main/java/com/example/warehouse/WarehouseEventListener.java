package com.example.warehouse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer. On receiving PaymentCompletedEvent, reserves the
 * product's stock level in the PostgreSQL database.
 */
@Component
class WarehouseEventListener {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEventListener.class);
    private static final int DEFAULT_STOCK = 100;

    private final StockRepository repository;

    WarehouseEventListener(StockRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.kafka.topic.payments-completed}")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: order={}, product={}, quantity={}",
                event.orderId(), event.product(), event.quantity());

        StockItem item = repository.findByProduct(event.product())
                .orElseGet(() -> repository.save(new StockItem(event.product(), DEFAULT_STOCK, 0)));

        int reserved = item.reserve(event.quantity());
        repository.save(item);

        if (reserved < event.quantity()) {
            log.warn("Partial reservation for product {}: requested {}, reserved {} (insufficient stock)",
                    event.product(), event.quantity(), reserved);
        }
        log.info("Stock for product {} after reservation: available={}, reserved={}",
                item.getProduct(), item.getAvailable(), item.getReserved());
    }
}
