FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
COPY eureka-server/src eureka-server/src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -pl eureka-server -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/eureka-server/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
