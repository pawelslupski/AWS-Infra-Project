variable "region" {
  type = string
  default = "eu-central-1"
}

variable "s3_endpoint_url" {
  type = string
  default = "http://localhost:4566"
}

variable "sqs_endpoint_url" {
  type = string
  default = "http://localhost:4566"
}