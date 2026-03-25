FROM eclipse-temurin:21-jdk-jammy
VOLUME /tmp
COPY build/libs/apigateway-service-1.1.1.jar GatewayServer.jar
ENTRYPOINT ["java", "-jar", "GatewayServer.jar"]