FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY common common
COPY services services
RUN --mount=type=cache,target=/root/.m2 MAVEN_OPTS="-Xmx256m" ./mvnw -B -ntp -pl services/payments-service -am package -DskipTests
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system payflow && useradd --system --gid payflow payflow
WORKDIR /app
COPY --from=build /workspace/services/payments-service/target/payments-service-0.1.0-SNAPSHOT.jar app.jar
USER payflow
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=160m"
ENTRYPOINT ["java","-jar","app.jar"]
