
# Build Spring Boot application
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Run Spring Boot application
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=10000

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
