FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/inventory-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
