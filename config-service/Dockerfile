FROM eclipse-temurin:17-jre

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup

COPY --chown=appuser:appgroup target/config-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8888

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
