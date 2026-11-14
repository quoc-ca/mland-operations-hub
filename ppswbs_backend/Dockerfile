# Stage 1: Build application JAR
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source and build package
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime environment
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
COPY firebase-service-account.json* ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
