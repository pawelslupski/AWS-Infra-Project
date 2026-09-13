# Guide: writing a `Dockerfile` for a Spring Boot microservice, for ECS Fargate

You write the file. This document says **what** it should do and **why** — it's not a ready-made answer.
Once you've written it, come back and go through the checklist at the end.

Applies to: `services/ordering-service/`, `payment-service/`, `warehouse-service/`.
The Java code is ready; all that's missing is the `Dockerfile` (and an entry in `docker-compose.yml`).

---

## 1. Goal

Build a **small, secure** OCI image that:
- is built from source (needs no local Maven/JDK on the CI side or on Fargate),
- has only the JRE + the app (no build tools),
- runs as a non-root user,
- respects the container's RAM limit (Fargate enforces a hard limit),
- correctly reacts to `SIGTERM` (graceful shutdown on an ECS deploy),
- exposes a health check at `/actuator/health`.

Target image size: **~200-350 MB** (JRE ~180 MB + Spring dependencies + the app).
If you end up with 700 MB+, you've left Maven/JDK in the image. Go back to multi-stage.

---

## 2. Required structure: multi-stage build

### `build` stage
- Base: an image with Maven **and JDK 21** (e.g. `maven:3.9-eclipse-temurin-21`).
- `WORKDIR`, e.g. `/workspace`.
- **First** copy just `pom.xml`, **then** download dependencies, **only then** `COPY` the code.
  Reason: Docker layers are cached up to the first change. `pom.xml` changes rarely,
  the code changes constantly. On an ordinary code change you don't want to re-download the
  whole internet. To fetch dependencies, use a Maven goal that does this without compiling
  (`dependency:go-offline`).
- Build the artifact: `mvn -B clean package -DskipTests`
  (running tests inside the image is an antipattern — that's what the CI pipeline is for).
- **Split the fat jar into layers.** Spring Boot 3 can do this itself:
  `java -Djarmode=layertools -jar target/*.jar extract --destination <directory>`
  This produces 4 subdirectories ordered by volatility:
  `dependencies` (rarely changes) → `spring-boot-loader` → `snapshot-dependencies` → `application` (changes often).

### `runtime` stage
- Base: **JRE 21 only** (e.g. `eclipse-temurin:21-jre-jammy`). Not the JDK.
- Install `curl` (needed for `HEALTHCHECK`); clean the package manager cache afterwards,
  so the layer doesn't bloat.
- Create a **non-root system user** (`groupadd` + `useradd`, a fixed UID/GID, e.g. 1001).
- `WORKDIR /app`.
- Copy the **4 layers separately**, `COPY --from=build ...`, from least to most frequently changing.
  Set ownership right away (`--chown`), rather than a separate `RUN chown` (that would add a layer).
- `USER <your-user>` — from this point on the container is not root.
- `EXPOSE <port>` — **documentation only**. Actually opening the port is done via `-p` /
  `portMappings` in the task definition. But fill it in truthfully anyway.
- `ENV` with JVM options. Key one: **`-XX:MaxRAMPercentage`** (e.g. `75.0`) — this makes the
  JVM look at the container's limit, not the whole host's RAM. Without it, the OOM killer
  will kill the task on Fargate.
- `HEALTHCHECK` — `curl` against `http://localhost:<port>/actuator/health`, with a sensible
  `--start-period` (Spring takes a dozen or so seconds to come up).
- `ENTRYPOINT` in **exec form** (a JSON array), launching `JarLauncher` from the extracted layers.
  Use `exec java ...` so the JVM process gets **PID 1** and catches `SIGTERM`
  (the launcher class in Boot 3.3 is `org.springframework.boot.loader.launch.JarLauncher`).

---

## 3. Per-service parameters

| Service | port | `LABEL ...title` | notes |
|---|---|---|---|
| ordering-service | 8081 | `ordering-service` | REST + Kafka producer |
| payment-service | 8082 | `payment-service` | adds the AWS SDK (S3) — but that's a Maven dependency, the `Dockerfile` doesn't change |
| warehouse-service | 8083 | `warehouse-service` | same as ordering |

The `Dockerfile` for all three is **nearly identical** — it differs only in port and label.
Write ordering first, then do payment and warehouse from memory; `diff` them to see where they differ.

---

## 4. `.dockerignore`

Already present in every service directory (ready-made — it's not the learning topic, just
build-context hygiene). Take a look and understand why `target/`, `.git/`, `.idea/` are there.

---

## 5. Test

```bash
# build
docker build -t ordering-service:local ./services/ordering-service

# size (target: < 350 MB)
docker images ordering-service:local

# layers — check that dependencies are separate from application
docker history ordering-service:local

# quick run (needs a DB/Kafka — see section 6)
docker run --rm -p 8081:8081 ordering-service:local
```

## 6. Wiring into `docker-compose.yml`

In `docker-compose.yml` the `ordering-service` / `payment-service` / `warehouse-service` blocks
are **commented out**. Once you've built the image, uncomment the right block and:

```bash
docker compose up -d --build ordering-service
docker compose logs -f ordering-service
curl http://localhost:8081/actuator/health
```

The environment variables (DB host, `kafka:19092`, S3 endpoint) are already in those blocks —
that's part of the "ready-made docker-compose". Your job is the `Dockerfile` + uncommenting.

---

## 7. Checklist — check your `Dockerfile`

- [ ] There are **two** `FROM`s (`... AS build` and runtime), runtime on **JRE**, not JDK.
- [ ] `COPY pom.xml` + fetching dependencies **before** `COPY src`.
- [ ] `-DskipTests` on `package`.
- [ ] `layertools extract` in the build stage; 4 separate `COPY --from=build` in runtime.
- [ ] `curl` installed, package cache cleaned in the same `RUN` layer.
- [ ] `useradd` + `USER` — `docker run ... whoami` does NOT return `root`.
- [ ] `-XX:MaxRAMPercentage` in the JVM options.
- [ ] `ENTRYPOINT` in exec form (`["...","..."]`), with `exec java`.
- [ ] `EXPOSE` = the service's actual port.
- [ ] `HEALTHCHECK` with `--start-period` ≥ 30s.
- [ ] `docker images` shows < 350 MB.
- [ ] `docker run` + `curl /actuator/health` = `{"status":"UP"}` (with a running DB/Kafka).

## 8. Common mistakes

| Symptom | Cause |
|---|---|
| Image is 700 MB+ | runtime based on a JDK/Maven image, or no multi-stage |
| Every build re-downloads dependencies | `COPY src` before fetching dependencies |
| `Container killed OOMKilled` on Fargate | missing `MaxRAMPercentage`, the JVM sees the host's RAM |
| ECS deploy hangs until timeout, then `SIGKILL` | `ENTRYPOINT` in shell form → java isn't PID 1, doesn't catch `SIGTERM` |
| `HEALTHCHECK` always `unhealthy` | `curl` missing from the image, or `--start-period` too short |
| `permission denied` on startup | `USER` set before `COPY --chown`, files are owned by root |
