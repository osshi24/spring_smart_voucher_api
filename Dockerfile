# Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy pom.xml and download dependencies (layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src src
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
