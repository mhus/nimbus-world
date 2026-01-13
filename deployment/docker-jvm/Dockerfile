# Build stage - compiles everything once
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# Copy root pom.xml
COPY server/pom.xml .

# Copy project directories (excluding exports, data, etc.)
COPY server/plugins/ plugins/
COPY server/generated/ generated/
COPY server/shared/ shared/
COPY server/universe/ universe/
COPY server/world-shared/ world-shared/
COPY server/world-player/ world-player/
COPY server/world-control/ world-control/
COPY server/world-life/ world-life/
COPY server/world-generator/ world-generator/
COPY server/tools/ tools/

# Copy client shared sources to client_shared_src (mimics the symlink structure)
COPY client/packages/shared/src/ client_shared_src/

# Build everything with Maven
RUN mvn clean install -DskipTests

# Runtime stage for world-player
FROM eclipse-temurin:25-jre AS world-player

WORKDIR /app

# Install wget for healthchecks
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built world-player artifact from build stage
COPY --from=build /build/world-player/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 9042

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9042/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Runtime stage for world-control
FROM eclipse-temurin:25-jre AS world-control

WORKDIR /app

# Install wget for healthchecks
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built world-control artifact from build stage
COPY --from=build /build/world-control/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 9043

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9043/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Runtime stage for world-life
FROM eclipse-temurin:25-jre AS world-life

WORKDIR /app

# Install wget for healthchecks
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built world-life artifact from build stage
COPY --from=build /build/world-life/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 9044

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9044/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Runtime stage for world-generator
FROM eclipse-temurin:25-jre AS world-generator

WORKDIR /app

# Install wget for healthchecks
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built world-generator artifact from build stage
COPY --from=build /build/world-generator/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 9045

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9045/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
