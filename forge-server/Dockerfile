FROM eclipse-temurin:21-jdk-alpine AS build

ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

WORKDIR /workspace/forge-server
COPY forge-server/.mvn .mvn
COPY forge-server/mvnw forge-server/pom.xml ./
COPY forge-server/src ./src
COPY packages/forge-contracts /workspace/packages/forge-contracts
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S forge && adduser -S forge -G forge
WORKDIR /app
COPY --from=build --chown=forge:forge /workspace/forge-server/target/forge-server-0.1.0-SNAPSHOT.jar app.jar
USER forge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
