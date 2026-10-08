FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 lab && useradd --uid 10001 --gid lab --create-home lab
WORKDIR /app
COPY --from=build --chown=lab:lab /workspace/target/ibm-mq-native-lab-0.1.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
CMD ["--lab.command=help"]
