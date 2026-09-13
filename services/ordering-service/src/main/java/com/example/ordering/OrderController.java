package com.example.ordering;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * REST + orchestration: saves the order to PostgreSQL and publishes
 * {@link OrderCreatedEvent} to a real Kafka.
 */
@RestController
@RequestMapping("/orders")
class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private static final BigDecimal UNIT_PRICE = new BigDecimal("9.99");

    private final OrderRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    OrderController(OrderRepository repository,
                    KafkaTemplate<String, Object> kafka,
                    @Value("${app.kafka.topic.orders-created}") String topic) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
    }

    @PostMapping
    @Transactional
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        BigDecimal amount = UNIT_PRICE
                .multiply(BigDecimal.valueOf(request.quantity()))
                .setScale(2, RoundingMode.HALF_UP);

        Order order = repository.save(new Order(request.product(), request.quantity(), amount));

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), order.getProduct(), order.getQuantity(), order.getAmount(), Instant.now());
        kafka.send(topic, String.valueOf(order.getId()), event);

        log.info("Saved order {} ({} x{}) and sent OrderCreatedEvent -> {}",
                order.getId(), order.getProduct(), order.getQuantity(), topic);

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record CreateOrderRequest(@NotBlank String product, @Min(1) int quantity) {
    }

    record OrderResponse(Long id, String product, int quantity, BigDecimal amount,
                         String status, Instant createdAt) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getProduct(), o.getQuantity(), o.getAmount(),
                    o.getStatus().name(), o.getCreatedAt());
        }
    }
}
