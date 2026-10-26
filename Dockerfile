FROM amazoncorretto:25-alpine@sha256:b611ab31a8cff0ab60ff5ee26de46d06922bfff0c54f5029ac588d5337bc533b

# Apply security updates
RUN apk update && \
    apk add --no-cache --upgrade openssl busybox zlib && \
    rm -rf /var/cache/apk/*

# Create a group and user to run the application
RUN addgroup --gid 10000 appgroup && \
    adduser --uid 10000 --ingroup appgroup --disabled-password --gecos "" appuser

# Define a volume to safely store temporary files across restarts
VOLUME /tmp
WORKDIR /app

# Copy the built jar from the build output
COPY build/libs/*.jar /app/application.jar

# Set the user to run the application
RUN chown -R appuser:appgroup /app
USER 10000

# Expose the application port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "application.jar"]
