FROM eclipse-temurin:17-jre

WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod

COPY back/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
