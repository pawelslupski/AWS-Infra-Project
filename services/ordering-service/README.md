# ordering-service

Entry point into the system: REST `POST /orders` → write to PostgreSQL → `OrderCreatedEvent` on Kafka.
**Java code is ready** (5 classes) — deliberately trivial from a business standpoint. **You write the `Dockerfile` yourself.**

## Classes

| Class | Role |
|---|---|
| `OrderingServiceApplication` | bootstrap |
| `Order` | JPA entity (table `orders`) |
| `OrderRepository` | Spring Data JPA |
| `OrderCreatedEvent` | JSON shape of the Kafka event (topic `orders.created`) |
| `OrderController` | REST `POST /orders` + `GET /orders/{id}`, save + `kafka.send`, validation, nested DTO |

## Endpoints

```bash
curl -X POST http://localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"product":"WIDGET","quantity":3}'
curl http://localhost:8081/orders/1
curl http://localhost:8081/actuator/health
```

The unit price is hardcoded (`9.99`) — a stub, this is about the flow, not pricing.

## Dockerfile — write it yourself

Guide + checklist + common mistakes: [`../../.ai/guides`](../../.ai/guides/GUIDE-dockerfile.md).

Parameters for this service: port **8081**, `LABEL ...title="ordering-service"`.
Start with this service — payment and warehouse will be nearly identical.

```bash
docker build -t ordering-service:local .
```

Once built: uncomment the `ordering-service` block in the top-level `docker-compose.yml`.
