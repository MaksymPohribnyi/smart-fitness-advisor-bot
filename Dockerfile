FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files
COPY gradlew build.gradle settings.gradle gradle.properties ./
COPY gradle gradle


# Download dependencies (layer caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Build application
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Run application
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]