# terraform/environments/aws-prod — TO BE RECREATED YOURSELF (Phase 3)

Same structure as `../localstack`, but:

- `providers.tf` — **no** `endpoints` block, no `skip_*`, no `s3_use_path_style`.
  Credentials from an AWS profile (`AWS_PROFILE`) or a role. `s3` backend (a real bucket) + state locking.
- `main.tf` — module calls: `networking` → `ecr` → `ecs-service` (+ `s3-bucket`, `sqs-queue`).
- `variables.tf` / `terraform.tfvars` — region, CIDRs, task sizes, `desired_count`, passwords from SSM.

Task checklist: see `PROJECT_PLAN.md` → PHASE 3.

> ⚠️ `terraform destroy` after every learning session — cost control (NAT GW, ALB, RDS are billed hourly).
