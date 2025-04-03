# Build
FROM gradle:8.5-jdk17 AS builder
COPY --chown=gradle:gradle . /home/gradle/app
WORKDIR /home/gradle/app
RUN gradle shadowJar --no-daemon

# Run
FROM eclipse-temurin:17-jdk
COPY --from=builder /home/gradle/app/build/libs/*-all.jar /app/app.jar
WORKDIR /app
CMD ["java", "-jar", "app.jar"]