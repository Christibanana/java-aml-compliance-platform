FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 aml
WORKDIR /app
COPY --from=build /workspace/target/aml-platform-*.jar app.jar
RUN mkdir -p /app/data/evidence && chown -R aml:aml /app/data
USER aml
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
