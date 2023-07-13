#FROM openjdk:11.0.16-jdk-slim
FROM openjdk:11.0.16-jdk

WORKDIR /app

#ARG JAR_FILE_NAME
#ARG JAR_FILE=target/libs/${JAR_FILE_NAME}.jar
#ARG JAR_FILE=target/operator1-1.0-SNAPSHOT.jar

#COPY ${JAR_FILE} app.jar
COPY target/operator1-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080