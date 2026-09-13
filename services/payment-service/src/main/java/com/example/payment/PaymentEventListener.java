package com.example.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Kafka consumer. On receiving OrderCreatedEvent:
 *  1) saves a payment record to PostgreSQL,
 *  2) generates an invoice and uploads it to S3,
 *  3) publishes PaymentCompletedEvent to Kafka.
 */
@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentRepository repository;
    private final InvoiceStorage invoiceStorage;
    private final KafkaTemplate<String, Object> kafka;
    private final String paymentsCompletedTopic;

    PaymentEventListener(PaymentRepository repository,
                         InvoiceStorage invoiceStorage,
                         KafkaTemplate<String, Object> kafka,
                         @Value("${app.kafka.topic.payments-completed}") String paymentsCompletedTopic) {
        this.repository = repository;
        this.invoiceStorage = invoiceStorage;
        this.kafka = kafka;
        this.paymentsCompletedTopic = paymentsCompletedTopic;
    }

    @KafkaListener(topics = "${app.kafka.topic.orders-created}")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: order={}, amount={}", event.orderId(), event.amount());

        if (repository.existsByOrderId(event.orderId())) {
            log.info("Payment for order {} already processed - skipping (idempotency)", event.orderId());
            return;
        }

        Payment payment = new Payment(event.orderId(), event.amount());
        payment.markCompleted();
        payment = repository.save(payment);

        String invoiceKey = invoiceStorage.uploadInvoice(payment, event.product(), event.quantity());
        payment.setInvoiceKey(invoiceKey);
        repository.save(payment);

        PaymentCompletedEvent out = new PaymentCompletedEvent(
                payment.getId(), event.orderId(), event.product(), event.quantity(),
                event.amount(), invoiceKey, Instant.now());
        kafka.send(paymentsCompletedTopic, String.valueOf(event.orderId()), out);

        log.info("Payment {} completed, invoice={}, sent PaymentCompletedEvent -> {}",
                payment.getId(), invoiceKey, paymentsCompletedTopic);
    }
}
