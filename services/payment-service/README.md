# payment-service

Kafka consumer + S3 integration. **Java code is ready** (8 classes) — deliberately trivial from a business standpoint.
**You write the `Dockerfile` yourself** (see the section at the bottom).

## Flow

1. `@KafkaListener` on `orders.created` receives `OrderCreatedEvent`.
2. `PaymentEventListener` — idempotency (`existsByOrderId`), saves `Payment` to PostgreSQL.
3. `InvoiceStorage` generates a TXT invoice and does `putObject` to S3 (`invoices/order-<id>.txt`).
4. The invoice key is saved to `payments.invoice_key`; `PaymentCompletedEvent` is published on `payments.completed`.

## Classes

| Class | Role |
|---|---|
| `PaymentServiceApplication` | bootstrap |
| `Payment` | JPA entity (table `payments`, `order_id` UNIQUE = idempotency) |
| `PaymentRepository` | Spring Data + `existsByOrderId` |
| `OrderCreatedEvent` / `PaymentCompletedEvent` | JSON shape of the Kafka events |
| `AwsS3Config` | `S3Client` bean (AWS SDK v2) — LocalStack endpoint + `forcePathStyle` |
| `InvoiceStorage` | `@PostConstruct` checks/creates the bucket, `uploadInvoice` = `putObject` |
| `PaymentEventListener` | `@KafkaListener` + orchestration of steps 2-4 |

## S3 configuration (`app.s3.*` in `application.yml`)

| Key | Default | Note |
|---|---|---|
| `endpoint` | `http://localhost:4566` | empty ⇒ real AWS (the SDK derives the address from the region) |
| `bucket` | `aws-infra-order-documents-local` | matches the Terraform output (`environments/localstack`) |
| `auto-create-bucket` | `true` | **Phase 2:** set to `false` — Terraform manages the bucket |
| `access-key` / `secret-key` | `test` / `test` | ignored on real AWS — use a Task Role |

## To do in Phase 2/3

- `APP_S3_AUTO_CREATE_BUCKET=false`, bucket from `terraform output`.
- Add an `SqsClient` (model it on `AwsS3Config`) and send the payment event to SQS.
- On Fargate: remove `access-key`/`secret-key`, credentials from the Task Role; `endpoint` empty.

---

## Dockerfile — write it yourself

Guide + checklist + common mistakes: [`../../.ai/guides`](../../.ai/guides/GUIDE-dockerfile.md).

Parameters for this service: port **8082**, `LABEL ...title="payment-service"`.
The `Dockerfile` is identical to `ordering-service`'s — the AWS SDK (S3) is just a Maven
dependency, it doesn't change how the image is built. Write it from memory, then `diff` it
against your ordering one.

```bash
docker build -t payment-service:local .
```

Once built: uncomment the `payment-service` block in the top-level `docker-compose.yml`
(the S3/DB/Kafka variables are already filled in there).
