FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY authorization-service/pom.xml authorization-service/
COPY authorization-service/src authorization-service/src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -pl authorization-service -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/authorization-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
