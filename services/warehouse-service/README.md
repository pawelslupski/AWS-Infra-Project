# warehouse-service

Payment event consumer. **Java code is ready** (6 classes) — deliberately trivial from a business standpoint.
**You write the `Dockerfile` yourself** (see the section at the bottom).

## Flow

1. `@KafkaListener` on `payments.completed` receives `PaymentCompletedEvent`.
2. `WarehouseEventListener` looks up a `StockItem` by product name (or creates a new one with stock 100).
3. `StockItem.reserve(quantity)` — moves units from `available` to `reserved` (`@Version` = optimistic locking).
4. `GET /stock` and `GET /stock/{product}` — a way to check that reservations were actually recorded.

## Classes

| Class | Role |
|---|---|
| `WarehouseServiceApplication` | bootstrap |
| `StockItem` | JPA entity (table `stock_items`, `product` UNIQUE, `@Version`) |
| `StockRepository` | Spring Data + `findByProduct` |
| `PaymentCompletedEvent` | JSON shape of the Kafka event |
| `WarehouseEventListener` | `@KafkaListener` + reservation logic |
| `StockController` | REST for checking the current state |

## To do in Phase 2/3 (optional)

- Receive from SQS instead of/alongside Kafka (`SqsClient`).
- `POST /stock` endpoint to top up stock; a separate `Reservation` entity tied to `orderId`.

---

## Dockerfile — write it yourself

Guide + checklist + common mistakes: [`../../.ai/guides`](../../.ai/guides/GUIDE-dockerfile.md).

Parameters for this service: port **8083**, `LABEL ...title="warehouse-service"`.
This service doesn't use S3 — the `Dockerfile` is identical to `ordering`'s apart from the port and label.
This is the third time — you should be able to write it without looking at your notes.

```bash
docker build -t warehouse-service:local .
```

Once built: uncomment the `warehouse-service` block in the top-level `docker-compose.yml`.
