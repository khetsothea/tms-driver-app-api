FROM maven:3.9.5-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY tms-backend-shared/pom.xml tms-backend-shared/pom.xml
COPY tms-core-api/pom.xml tms-core-api/pom.xml
COPY tms-driver-app-api/pom.xml tms-driver-app-api/pom.xml
COPY tms-auth-api/pom.xml tms-auth-api/pom.xml
COPY tms-telematics-api/pom.xml tms-telematics-api/pom.xml
COPY tms-safety-api/pom.xml tms-safety-api/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY tms-message-api/pom.xml tms-message-api/pom.xml
COPY device-gateway/pom.xml device-gateway/pom.xml
COPY tms-backend-shared/src tms-backend-shared/src
COPY tms-core-api/src tms-core-api/src
COPY tms-driver-app-api/src tms-driver-app-api/src

RUN mvn -pl tms-driver-app-api -am clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/tms-driver-app-api/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
