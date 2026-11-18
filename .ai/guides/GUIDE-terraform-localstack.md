# Guide: Terraform + LocalStack (Phase 2)

You write every `.tf` file. This document says what should be produced and what to watch out for.

Goal of Phase 2: `terraform apply` in `../../terraform/environments/localstack` creates an S3
bucket in LocalStack (and later an SQS queue), and `payment-service` stops creating the
bucket itself (`APP_S3_AUTO_CREATE_BUCKET=false`) and takes its name from `terraform output`.

---

## 1. Structure you need to recreate

```
terraform/
├── modules/
│   ├── s3-bucket/          <- write this one first (sets the pattern for your module style)
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── versions.tf
│   └── sqs-queue/          <- second, following the same pattern
└── environments/
    └── localstack/
        ├── providers.tf    <- "aws" provider config for LocalStack + version requirements + backend
        ├── main.tf         <- module calls
        ├── variables.tf
        ├── outputs.tf
        └── terraform.tfvars
```

Rule: **a module does not configure the provider.** The provider (keys, endpoints) is set by
the environment that calls the module. The same `s3-bucket` module should later work against
real AWS with no changes.

---

## 2. `environments/localstack/providers.tf` — the heart of the "LocalStack vs AWS" difference

The `provider "aws"` block must:

- **Region** — anything (e.g. `eu-central-1`); arbitrary for LocalStack.
- **Credentials** — explicit, just non-empty (`access_key = "test"`, `secret_key = "test"`).
  LocalStack Community doesn't verify them.
- **Disable the checks that reach out to real AWS:**
  - `skip_credentials_validation = true`
  - `skip_metadata_api_check     = true`
  - `skip_requesting_account_id  = true`
  - `skip_region_validation      = true`
- **`s3_use_path_style = true`** — LocalStack doesn't support virtual-hosted addressing
  (`bucket.s3.amazonaws.com`); it requires path-style (`localhost:4566/bucket`).
- **An `endpoints { ... }` block** — redirect every service you use to `http://localhost:4566`
  (to start: `s3`, `sqs`; add `iam`, `sts` once you need them).

Also in `providers.tf` (or a separate `versions.tf`):
- `terraform { required_version = ">= 1.6" }`
- `required_providers { aws = { source = "hashicorp/aws", version = ">= 5.0" } }`
- `backend "local" {}` to start (state in a file). Later in Phase 2 you'll switch to
  `backend "s3"` pointing at a bucket inside LocalStack itself — good practice for state bootstrapping.

**A contrast you must be able to explain:** `environments/aws-prod/providers.tf` has NONE of
the above — no `endpoints`, no `skip_*`, no `s3_use_path_style`, no explicit keys
(credentials come from a profile/role).

---

## 3. Module `modules/s3-bucket/`

**`main.tf`** — resources (the AWS 5.x provider splits bucket config into separate resources):
- `aws_s3_bucket` — the bucket itself, `bucket = var.bucket_name`, `tags` = merge of defaults + `var.tags`.
- `aws_s3_bucket_versioning` — driven by `var.versioning_enabled` (bool → `"Enabled"`/`"Disabled"`).
- `aws_s3_bucket_server_side_encryption_configuration` — `AES256` (a no-op on LocalStack, but
  we keep one codepath for both environments).
- `aws_s3_bucket_public_access_block` — all 4 flags `true`.

**`variables.tf`:**
- `bucket_name` (string, required) — add a `validation` for S3 name format (3-63 chars, `[a-z0-9.-]`).
- `versioning_enabled` (bool, default `false`).
- `tags` (map(string), default `{}`).

**`outputs.tf`:** `bucket_id`, `bucket_arn` (useful for IAM policies / Task Role in Phase 3),
`bucket_domain_name`.

**`versions.tf`:** just `required_version` + `required_providers` (no `provider` block).

---

## 4. Module `modules/sqs-queue/` (second, on your own)

Model it on `s3-bucket`. It should support:
- the main `aws_sqs_queue` (`var.queue_name`, `visibility_timeout_seconds`, `message_retention_seconds`).
- An optional **DLQ** (dead-letter queue) driven by `var.enable_dlq` (bool) — a second queue +
  a `redrive_policy` on the main queue with `maxReceiveCount = var.max_receive_count`.
- Outputs: `queue_url`, `queue_arn` (+ DLQ ones, when enabled).

---

## 5. `environments/localstack/main.tf`

- Call `module "order_documents_bucket"` with `source = "../../modules/s3-bucket"`,
  `bucket_name = "${var.project}-order-documents-${var.environment}"`, `versioning_enabled = true`.
  **Important:** the name must match `APP_S3_BUCKET` in `../../docker-compose.yml`
  (`aws-infra-order-documents-local`), otherwise payment-service won't find the bucket.
- Then: call `module "payment_events_queue"` from `modules/sqs-queue`.
- Exercise: `for_each = toset(["ordering","payment","warehouse"])` — three buckets from one declaration.

`variables.tf`: `project` (default `aws-infra`), `environment` (default `local`),
`aws_region`, `localstack_endpoint` (default `http://localhost:4566`).

`outputs.tf`: re-export `bucket_id` and `queue_url` from the modules.

`terraform.tfvars`: concrete values for this environment.

---

## 6. Work loop and tests

```bash
cd terraform/environments/localstack

terraform init            # downloads the provider, initializes the backend
terraform fmt -recursive  # formatting — do this before every commit
terraform validate        # syntax + type consistency
terraform plan            # READ this before you apply
terraform apply

# verification outside Terraform (AWS CLI -> LocalStack):
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=eu-central-1
aws --endpoint-url=http://localhost:4566 s3 ls
aws --endpoint-url=http://localhost:4566 s3api get-bucket-versioning --bucket aws-infra-order-documents-local
aws --endpoint-url=http://localhost:4566 sqs list-queues

terraform output                    # see the exported values
terraform destroy                   # clean up; then apply again — understand idempotency
```

After a successful `apply`: in `../../docker-compose.yml` set `APP_S3_AUTO_CREATE_BUCKET: "false"` for
`payment-service`, restart it, and confirm invoices still land in S3 (since Terraform now owns the bucket).

---

## 7. Checklist

- [ ] `terraform init` succeeds; `.terraform.lock.hcl` exists (in `../../.gitignore`).
- [ ] `terraform validate` = `Success`.
- [ ] `provider "aws"` has `endpoints`, `s3_use_path_style`, all `skip_*`, explicit `access_key/secret_key`.
- [ ] The `s3-bucket` module does NOT contain a `provider` block.
- [ ] `terraform apply` creates the bucket; `aws --endpoint-url=... s3 ls` shows it.
- [ ] The bucket name == `APP_S3_BUCKET` from compose.
- [ ] `terraform destroy` + `apply` again = the same result, zero errors (idempotency).
- [ ] You can explain: `resource` vs `data source`, module inputs/outputs, why `terraform.tfstate` is sensitive, the difference between `plan` and `apply`.

---

## 8. Common mistakes

| Symptom | Cause |
|---|---|
| `InvalidAccessKeyId` / `no valid credential sources` | missing `skip_credentials_validation` / missing explicit keys |
| `dial tcp: ... amazonaws.com` (Terraform is hitting real AWS) | missing / wrong entry in `endpoints` |
| `BucketRegionError` or a weird host in the bucket URL | missing `s3_use_path_style = true` |
| `A provider configuration block is required` inside a module | calling the module from an environment with no provider configured |
| payment-service: `NoSuchBucket` after `apply` | the bucket name in the module ≠ `APP_S3_BUCKET` in compose |
| `Error acquiring the state lock` | a previous `apply` was interrupted; `terraform force-unlock <id>` (carefully) |
