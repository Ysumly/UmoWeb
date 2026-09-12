FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY ["Server Side/UmoWebBackend/pom.xml", "./pom.xml"]
COPY ["docker/backend/settings.xml", "./settings.xml"]
COPY ["Server Side/UmoWebBackend/src", "./src"]
RUN mvn -B -s ./settings.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system --gid 10001 umo \
    && useradd --system --uid 10001 --gid umo --home-dir /app --shell /usr/sbin/nologin umo \
    && mkdir -p /app/data \
    && chown -R umo:umo /app

COPY --from=build --chown=umo:umo /workspace/target/UmoWebBackend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=umo:umo ["docker/demo-data/", "/app/data/"]

USER umo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
