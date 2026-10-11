FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/wallet-ledger-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
