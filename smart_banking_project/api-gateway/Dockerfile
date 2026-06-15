# ================================================================
# api-gateway — Spring Cloud Gateway (WebFlux/reactive)
# ================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

RUN mvn -f pom.xml -pl common-lib,api-gateway -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/api-gateway/target/*.jar app.jar

EXPOSE 8080

# Gateway exposes health,info,gateway endpoints (management.endpoints.web.exposure.include)
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 \
  CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
