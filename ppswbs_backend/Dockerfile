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

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
