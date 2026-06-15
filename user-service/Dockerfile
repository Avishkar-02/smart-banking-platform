# ================================================================
# user-service — Auth/User management (MySQL + Redis + Kafka)
# ================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

RUN mvn -f pom.xml -pl common-lib,user-service -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/user-service/target/*.jar app.jar

EXPOSE 8081

# start-period is generous because this service needs config-server,
# Eureka, MySQL, Redis, and Kafka all reachable before it reports UP.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD wget -q -O- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
