FROM docker.io/amazoncorretto:24-alpine3.22-jdk
LABEL authors="dhani"
COPY ./target/network-tunneler-1.0.0-SNAPSHOT-fat.jar ./app.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
