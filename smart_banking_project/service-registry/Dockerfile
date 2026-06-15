# ================================================================
# service-registry — Eureka Server
# Multi-stage build: Stage 1 compiles with Maven, Stage 2 runs the JAR
# ================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

# Build common-lib first and install it to the local Maven repo,
# then build this service. -pl + -am builds this module and its
# required upstream modules (common-lib) in one go.
RUN mvn -f pom.xml -pl common-lib,service-registry -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy only the built jar from the builder stage (keeps final image small)
COPY --from=builder /build/service-registry/target/*.jar app.jar

# Eureka server port
EXPOSE 8761

# Healthcheck hits Eureka's actuator health endpoint.
# start-period gives Eureka time to boot before checks start counting as failures.
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -q -O- http://localhost:8761/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
