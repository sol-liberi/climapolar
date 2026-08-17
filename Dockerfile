FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system --gid 10001 climapolar \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin climapolar \
    && rm -f /usr/bin/pebble \
    && mkdir -p /app/data/media \
    && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /app/target/frio-polar-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/ || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
