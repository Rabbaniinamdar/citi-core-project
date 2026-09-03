FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/account-service-0.0.1-SNAPSHOT.jar app.jar

COPY src/main/resources/rds/rds-truststore.jks /app/rds/rds-truststore.jks

EXPOSE 8083

ENTRYPOINT ["sh", "-c", "exec java -Djavax.net.ssl.trustStore=/app/rds/rds-truststore.jks -Djavax.net.ssl.trustStorePassword=$RDS_TRUSTSTORE_PASSWORD -Djavax.net.ssl.trustStoreType=PKCS12 -jar app.jar"]