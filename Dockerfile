# Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN addgroup --system clickkart && adduser --system --ingroup clickkart clickkart
WORKDIR /app
COPY --from=build /workspace/target/clickkart-cart-service.jar app.jar
# logback-spring.xml writes to ./logs - this directory must exist and be writable by the
# non-root user before the JVM starts.
RUN mkdir -p /app/logs && chown -R clickkart:clickkart /app
USER clickkart

ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8091

# Probes /actuator/health/readiness, not /actuator/health - the same endpoint the Kubernetes
# readiness probe uses, so "healthy" means the same thing locally and in a cluster. The readiness
# group (readinessState,db,redis - see clickkart-config-repository) covers exactly the two
# dependencies this service cannot serve a request without.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:${SERVER_PORT:-8091}/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
