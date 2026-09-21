# ---- Stage 1: build the jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Added BuildKit cache mount to speed up downloads across builds
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -q

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -q

# ---- Stage 2: small runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user and group for security
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser:appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]