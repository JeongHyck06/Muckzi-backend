FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
COPY --from=build /src/build/libs/*.jar /app.jar
CMD ["java", "-jar", "/app.jar"]
