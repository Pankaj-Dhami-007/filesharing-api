# --------- Stage 1: Build the application ---------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory inside build container
WORKDIR /app

# Copy pom.xml and download dependencies (cache boost)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the entire source code
COPY src ./src

# Build the jar (skip tests for faster builds)
RUN mvn clean package -DskipTests

# --------- Stage 2: Run the application ---------
FROM eclipse-temurin:21-jdk-jammy

# Set working directory inside runtime container
WORKDIR /app

# Copy built jar from previous stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the port your Spring Boot app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]