# terraform/modules

**You write every module yourself.** Guide + specs: [`../../.ai/guides`](../../.ai/guides/GUIDE-terraform-localstack.md).

| Module | Phase | Target contents |
|---|---|---|
| `s3-bucket/` | 2 | bucket + versioning + SSE + public access block (write this one first — it sets your module style) |
| `sqs-queue/` | 2 | queue + optional DLQ + `redrive_policy` |
| `networking/` | 3 | VPC, public/private/data subnets, IGW, NAT, route tables |
| `ecr/` | 3 | image repository + lifecycle policy |
| `ecs-service/` | 3 | cluster / task def / Fargate service + target group + listener rule |

Every module: `main.tf` + `variables.tf` + `outputs.tf` + `versions.tf`.
You configure the provider in `environments/*`, **NOT** in the module.
