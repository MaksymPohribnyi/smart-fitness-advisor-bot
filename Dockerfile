FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies (layer caching)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN ./gradlew build --no-daemon -x test

# Run application
EXPOSE 8080
CMD ["java", "-jar", "build/libs/smart-fitness-advisor-bot-0.0.1-SNAPSHOT.jar"]