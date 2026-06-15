# ================================================================
# config-server — Spring Cloud Config Server (native profile)
# Serves configuration from src/main/resources/configurations/*.properties
# ================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY . .

# config-server has no dependency on common-lib, but building with -am
# is harmless and keeps the command consistent across all Dockerfiles.
RUN mvn -f pom.xml -pl config-server -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/config-server/target/*.jar app.jar

EXPOSE 8888

# config-server must be UP before any other service can start
# (they all use spring.config.import=configserver:...)
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q -O- http://localhost:8888/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
