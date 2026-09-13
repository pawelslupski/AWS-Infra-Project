# environment: localstack — TO BE WRITTEN (Phase 2)

Full spec: [`../../../docs/GUIDE-terraform-localstack.md`](../../../docs/GUIDE-terraform-localstack.md) — sections 2, 5, 6.

Files to create in this directory:

| file | contents |
|---|---|
| `providers.tf` | `terraform{}` (required_version, required_providers, `backend "local" {}`) + `provider "aws"` for LocalStack: `endpoints{}`, `s3_use_path_style=true`, all `skip_*`, explicit `access_key/secret_key="test"` |
| `main.tf` | `module "..."` calls into `../../modules/s3-bucket` (and later `sqs-queue`) |
| `variables.tf` | `project`, `environment`, `aws_region`, `localstack_endpoint` |
| `outputs.tf` | re-export `bucket_id`, `queue_url` from the modules |
| `terraform.tfvars` | concrete values for this environment |

**Critical:** the bucket name must come out equal to `APP_S3_BUCKET` from `docker-compose.yml`
(`aws-infra-order-documents-local`), otherwise `payment-service` will get `NoSuchBucket`.

First time: `terraform init && terraform apply`, then in compose set
`APP_S3_AUTO_CREATE_BUCKET: "false"` and restart payment-service.
