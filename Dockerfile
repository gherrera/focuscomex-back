# Dockerfile
# Build stage: use Temurin JDK 21 and install Maven (configurable via ARG)
FROM eclipse-temurin:21-jdk AS build

# Install Maven (image is Debian-based)
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

# Build the project (skip tests by default)
RUN mvn -B -DskipTests clean package

# Runtime stage: Temurin JRE 21
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the runnable JAR from the build stage. Adjust path if your artifact changes.
COPY --from=build /workspace/target/focuscomex-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]