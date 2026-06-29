# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/app

# Copy gradle wrapper and necessary build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# Make gradle wrapper executable and build the application
# We use --no-daemon to avoid spawning gradle daemons in the container
# We skip tests since this is just building the image
RUN chmod +x ./gradlew
RUN ./gradlew build -x test --no-daemon

# Stage 2: Create a lightweight runtime image
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
