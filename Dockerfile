FROM eclipse-temurin:21-jdk-jammy
VOLUME /tmp
COPY build/libs/apigateway-service-1.0.jar GatewayServer.jar
ENTRYPOINT ["java", "-jar", "GatewayServer.jar"]