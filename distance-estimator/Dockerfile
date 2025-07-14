# Build stage
FROM maven:3.8-openjdk-18 AS build
COPY /src /home/shared/src
COPY /pom.xml /home/shared/pom.xml
COPY /src /home/app/src
COPY /pom.xml /home/app/pom.xml
COPY /src/main/resources/application.properties /home/app/src/main/resources/application.properties
RUN mvn -f /home/shared/pom.xml clean install
RUN mvn -f /home/app/pom.xml clean package spring-boot:repackage

# Package stage
FROM openjdk:18-slim
COPY --from=build /home/app/target/application-0.1-SNAPSHOT.jar /usr/local/lib/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=80", "-jar","/usr/local/lib/application.jar"]