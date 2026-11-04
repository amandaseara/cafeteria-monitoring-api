FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

COPY src src

RUN ./mvnw -B clean verify

FROM gcr.io/distroless/java21-debian13:nonroot

WORKDIR /app

COPY --from=build --chown=65532:65532 \
    /app/target/cafeteria-api-0.0.1-SNAPSHOT.jar \
    /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]

