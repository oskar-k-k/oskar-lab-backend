FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon test bootJar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER 10001:10001
ENV SERVER_PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0"
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=6 CMD curl -fsS -X POST -H 'Content-Type: application/json' -d '{"page":0,"pageSize":1}' http://127.0.0.1:8080/apps > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
