# Backend image for the Test deploy (CI-002). Built only in GitHub Actions, for linux/arm64.
# The base is the official Temurin 25 JRE, pinned to its multi-arch index digest.
# The jar is packaged by the workflow before this step.
FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce

WORKDIR /app
COPY target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
