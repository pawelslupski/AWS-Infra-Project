# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# first just pom.xml as it changes very rarely
COPY pom.xml .
# dowloads only dependencies (without any compilation as the /src does not exist yet)
RUN mvn -B dependency:go-offline -U
# if the code changes which is more frequent, the cache invalidation starts from here
COPY src ./src
# dependencies are already downloaded to the local .m2 repo
RUN mvn -B package -DskipTests
# .jar layered for the cache optimization from least to most frequently changed
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ---- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl for HEALTHCHECK needs, packet lists cleaned within the same layer
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# unprivileged user with explicit UID/GID
RUN groupadd -g 1001 spring \
    && useradd -u 1001 -g spring -s /usr/sbin/nologin -M spring

COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# assumes existence of the actuator dependencies in mvn with the reasonable spring startup time
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# it keeps java process still as PID 1 in order to retrieve SIGTERM correctly
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]