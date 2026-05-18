FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/target/nfce-scraper-0.1.0-SNAPSHOT.jar app.jar

# Lean JVM for a low-traffic POC on Railway's metered credit:
# small serial-GC heap, capped metaspace, crash-on-OOM so the restart
# policy recycles instead of running degraded.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx300m", "-XX:MaxMetaspaceSize=128m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
