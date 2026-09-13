Hi Claude! You're acting as a Principal Cloud Architect and Senior DevOps Engineer.

We're building an evolvable project for 2-3 months of learning. My goal is to master
INFRASTRUCTURE, TERRAFORM, DOCKER (writing Dockerfiles), AWS ECS/FARGATE, and LOCALSTACK
FROM SCRATCH, WITH MY OWN HANDS.

Your task is NOT to write the whole project for me, but to provide:
1. A personalized roadmap (`PROJECT_PLAN.md`).
2. A model file structure (a directory tree to recreate on disk).
3. ONE example pattern for each component (1x Dockerfile, 1x docker-compose file, 1x Terraform module), based on which I will write the remaining parts myself from scratch.

---

### 1. ARCHITECTURAL ASSUMPTIONS:
- System: 3 microservices (Ordering, Payment, Warehouse) in Spring Boot 3 / Java 21 (with a minimal API skeleton).
- Databases: Real, separate PostgreSQL instances for each service (in Docker Compose).
- Message broker: Apache Kafka (KRaft).
- Local cloud: LocalStack Community (image: localstack/localstack) on port 4566, emulating AWS S3 and SQS.
- IaC: Terraform for both LocalStack and production AWS.

---

### 2. REQUIREMENTS TO GENERATE:

#### PART A: `PROJECT_PLAN.md` file
- Architecture diagram in Mermaid format.
- The learning plan split into 3 infrastructure-focused Phases (with `[ ]` checklists).
- A cheat sheet of key networking and cloud concepts (VPC, CIDR, Subnets, Security Groups, Task Roles).

#### PART B: Templates and Structure (to be recreated yourself)
- A directory structure tree for the project (infra and terraform).
- A model `docker-compose.yml` (Kafka, 3x Postgres, LocalStack).
- A model `Dockerfile` for the first service (multi-stage build for ECS Fargate) with an explanation of every line, so I can write the rest myself.
- A model `main.tf` file in Terraform to connect to LocalStack and create a test S3 bucket.
