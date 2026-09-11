# syntax=docker/dockerfile:1

###############################################################################
# Stage 1 — Build the Angular admin UI / playground
#
# angular.json writes its output to ../src/main/resources/META-INF/resources,
# so the bundle lands inside the Maven source tree and is packaged into the JAR.
# Set BUILD_UI=false to produce an API-only image (skips the bundle copy below).
###############################################################################
FROM node:22-alpine AS ui
WORKDIR /build/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

###############################################################################
# Stage 2 — Build the Quarkus backend (fast-jar)
###############################################################################
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build

COPY pom.xml ./
COPY src ./src

# The UI is built in stage 1, so the frontend-maven-plugin profile stays off
# here (it would install a second Node toolchain). ui.required in
# application.properties still controls whether the app serves the bundle.
ARG BUILD_UI=true
COPY --from=ui /build/src/main/resources/META-INF/resources ./ui-bundle
RUN if [ "$BUILD_UI" = "true" ]; then \
        mkdir -p src/main/resources/META-INF/resources && \
        cp -r ui-bundle/. src/main/resources/META-INF/resources/; \
    fi; \
    rm -rf ui-bundle

# The BuildKit cache mount keeps ~/.m2 warm between builds without baking it
# into a layer, which is far cheaper than a dependency:go-offline warm-up step.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

###############################################################################
# Stage 3 — Runtime
###############################################################################
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 aibridge \
    && useradd --system --uid 1001 --gid aibridge --create-home aibridge

WORKDIR /app

# Copy the fast-jar layers in order of change frequency for better layer reuse.
COPY --from=backend --chown=aibridge:aibridge /build/target/quarkus-app/lib/ ./lib/
COPY --from=backend --chown=aibridge:aibridge /build/target/quarkus-app/*.jar ./
COPY --from=backend --chown=aibridge:aibridge /build/target/quarkus-app/app/ ./app/
COPY --from=backend --chown=aibridge:aibridge /build/target/quarkus-app/quarkus/ ./quarkus/

USER aibridge

ENV QUARKUS_PROFILE=docker \
    QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
