# Stage 1: Build stage using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom file and download dependencies (caching optimizations)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Expose default backend port (mapped dynamically by container orchestrators like Render)
EXPOSE 8080

# Copy final standalone JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Start the spring boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
