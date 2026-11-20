FROM eclipse-temurin:17-jre

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup

COPY --chown=appuser:appgroup target/account-service-0.0.1-SNAPSHOT.jar app.jar
COPY --chown=appuser:appgroup src/main/resources/rds/rds-truststore.jks /app/rds/rds-truststore.jks

EXPOSE 8083

USER appuser

ENTRYPOINT ["sh", "-c", "exec java -Djavax.net.ssl.trustStore=/app/rds/rds-truststore.jks -Djavax.net.ssl.trustStorePassword=$RDS_TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=PKCS12 -jar app.jar"]
