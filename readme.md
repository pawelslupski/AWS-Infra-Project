# AWS Infra Project

Learning project (2-3 months): **Infrastructure · Docker · Terraform · AWS ECS/Fargate · LocalStack**.

**Start with [`.ai/project_plan.md`](.ai/project_plan.md).**

## Division of work

**You get ready-made** (so you can focus on infra, not Java):

| Element | What it is |
|---|---|
| `.ai/project_plan.md` | roadmap, 2 diagrams, a networking/cloud concepts cheat sheet |
| `docker-compose.yml` | **local infrastructure**: Kafka (KRaft) + kafka-ui + 3× PostgreSQL + LocalStack. The application service blocks are in the file, but **commented out** |
| `services/*/src/` + `pom.xml` | minimal, REAL logic for 3 microservices (Spring Boot 3 / Java 21 / Kafka / S3), 5-8 classes per service |
| `services/*/.dockerignore` | build-context hygiene (not the learning topic) |
| `guides` | guides: what to write and why, checklists, common mistakes |

**You write it yourself — the ENTIRE infrastructure**, with me (Claude) as the teacher: spec + checklist + review:

| Artifact | Phase | Guide |
|---|---|---|
| `services/*/Dockerfile` (×3) | 1 | [`guides`](.ai/guides/GUIDE-dockerfile.md) |
| `terraform/modules/s3-bucket/` + `environments/localstack/` | 2 | [`guides`](.ai/guides/GUIDE-terraform-localstack.md) |
| `terraform/modules/sqs-queue/` | 2 | same |
| `terraform/modules/{networking,ecr,ecs-service}/` + `environments/aws-prod/` | 3 | (guide to follow in Phase 3) |
| wiring the services into `docker-compose.yml`, deploying to Fargate | 1 / 3 | — |

The Java code is deliberately trivial from a business standpoint — it only serves as a "living
organism" for checking whether your infrastructure correctly wires together the database, Kafka,
and the cloud.

## Event flow (what the ready-made code does)

```
POST /orders ──▶ ordering ──(orders.created)──▶ payment ──▶ PostgreSQL(payments)
                    │                              │
              PostgreSQL(orders)             invoice ▶ S3 (LocalStack)
                                                   │
                                       (payments.completed)
                                                   │
                                                   ▼
                                     warehouse ──▶ PostgreSQL(stock_items)
```

## Directory structure

```
.
├── PROJECT_PLAN.md
├── docker-compose.yml          infrastructure READY; service blocks commented out
├── .env.example
├── docs/
│   ├── GUIDE-dockerfile.md
│   └── GUIDE-terraform-localstack.md
├── services/
│   ├── ordering-service/       src/ + pom.xml + README + .dockerignore   (Dockerfile: you)
│   ├── payment-service/        src/ + pom.xml + README + .dockerignore   (Dockerfile: you)
│   └── warehouse-service/      src/ + pom.xml + README + .dockerignore   (Dockerfile: you)
├── infra/
│   ├── kafka/
│   └── localstack/init/        LocalStack startup scripts (leave empty — that's what Terraform is for)
└── terraform/                  You write ALL of this per docs/GUIDE-terraform-localstack.md
    ├── modules/{s3-bucket,sqs-queue,networking,ecr,ecs-service}/   (README = spec)
    └── environments/{localstack,aws-prod}/                         (README = spec)
```

## Ports

| Service | Host | On the Compose network |
|---|---|---|
| ordering-service | http://localhost:8081 | `ordering-service:8081` |
| payment-service | http://localhost:8082 | `payment-service:8082` |
| warehouse-service | http://localhost:8083 | `warehouse-service:8083` |
| kafka-ui | http://localhost:8080 | — |
| Kafka | `localhost:9092` | `kafka:19092` |
| PostgreSQL (ordering / payment / warehouse) | `5432` / `5433` / `5434` | `postgres-*:5432` |
| LocalStack | http://localhost:4566 | `localstack:4566` |

## Quick start

```bash
# Infrastructure only (without your Dockerfiles) — for learning and working on the services from an IDE:
docker compose up -d
docker compose ps

# After writing services/<name>/Dockerfile: uncomment its block in docker-compose.yml, then:
docker compose up -d --build
```

## Running the services without Docker (before you write a Dockerfile)

Infra in containers, services from your IDE / `mvn spring-boot:run` (they'll use `localhost` and the ports from the table above):

```bash
docker compose up -d          # kafka + 3x postgres + localstack + kafka-ui
cd services/ordering-service && JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo /usr/lib/jvm/java-21-openjdk) mvn spring-boot:run
```

## Runbook — end-to-end test (proof that the integration works)

Requires all 3 services running (containers built from your Dockerfiles **or** `mvn spring-boot:run` ×3).

```bash
# 1. Place an order (ordering -> PostgreSQL -> Kafka)
curl -s -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -d '{"product":"WIDGET","quantity":3}'
#   -> {"id":1,"product":"WIDGET","quantity":3,"amount":29.97,"status":"CREATED",...}

# 2. payment consumed the event, saved the payment, and uploaded an invoice to S3
docker compose logs payment-service 2>/dev/null | grep -E "OrderCreated|Invoice|PaymentCompleted"

# 3. The invoice is really sitting in S3 (LocalStack)
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=eu-central-1
aws --endpoint-url=http://localhost:4566 s3 ls s3://aws-infra-order-documents-local/invoices/
aws --endpoint-url=http://localhost:4566 s3 cp \
  s3://aws-infra-order-documents-local/invoices/order-1.txt -

# 4. warehouse reserved the stock
curl -s http://localhost:8083/stock

# 5. Look inside the payments database
docker compose exec postgres-payment psql -U payment -d payment -c \
  "select id, order_id, amount, status, invoice_key from payments;"
```

> **Note on the bucket:** until you write the Terraform (Phase 2), `payment-service` creates the
> `aws-infra-order-documents-local` bucket itself (`APP_S3_AUTO_CREATE_BUCKET=true`). After
> `terraform apply`, switch it to `false` in `docker-compose.yml`.

## Requirements

Docker + Docker Compose · Terraform ≥ 1.6 · AWS CLI v2 · JDK 21 · Maven (`mvn` or `mvnw`)
