# module: s3-bucket — TO BE WRITTEN (Phase 2, first module)

Full spec: [`../../../.ai/guides`](../../../.ai/guides/GUIDE-terraform-localstack.md) — section 3.

Files to create in this directory:

| file | contents |
|---|---|
| `main.tf` | `aws_s3_bucket` + `aws_s3_bucket_versioning` + `aws_s3_bucket_server_side_encryption_configuration` + `aws_s3_bucket_public_access_block` |
| `variables.tf` | `bucket_name` (string, with `validation`), `versioning_enabled` (bool, default false), `tags` (map, default {}) |
| `outputs.tf` | `bucket_id`, `bucket_arn`, `bucket_domain_name` |
| `versions.tf` | `required_version` + `required_providers` (aws >= 5.0). NO `provider` block. |

Test: call it from `environments/localstack/main.tf`, `terraform apply`, check
`aws --endpoint-url=http://localhost:4566 s3 ls`.
