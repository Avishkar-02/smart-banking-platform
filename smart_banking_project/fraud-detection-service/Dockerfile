# ================================================================
# fraud-detection-service — Fraud rules engine (MySQL + Redis + Kafka)
# ================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

RUN mvn -f pom.xml -pl common-lib,fraud-detection-service -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/fraud-detection-service/target/*.jar app.jar

EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD wget -q -O- http://localhost:8084/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
