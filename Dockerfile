# Build stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 chama
COPY --from=build /app/target/chama-*.jar app.jar
USER chama
ENV TZ=Africa/Nairobi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
