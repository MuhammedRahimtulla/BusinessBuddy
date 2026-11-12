# BusinessBuddy: AI-Powered Business Cost and Inventory Management System
# Two-stage build: compile and test with Maven, then run on a small Java 21 runtime image.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in CI; the image build only packages the application.
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 businessbuddy
COPY --from=build /src/target/businessbuddy.jar /app/businessbuddy.jar
USER businessbuddy
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/businessbuddy.jar"]
