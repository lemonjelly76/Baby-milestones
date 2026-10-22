FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY core core
COPY server server
# Only the JVM modules: the Android app isn't needed (or buildable) here.
RUN sed -i 's/include(":core", ":server", ":app")/include(":core", ":server")/' settings.gradle.kts \
    && sed -i '/android/d' build.gradle.kts \
    && ./gradlew --no-daemon -q :server:bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /src/server/build/libs/server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
