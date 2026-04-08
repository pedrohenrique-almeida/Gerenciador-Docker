# syntax=docker/dockerfile:1.6

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache de dependencias: copia apenas o pom primeiro
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Copia o codigo e empacota
COPY src ./src
RUN mvn -B -q clean package -DskipTests \
 && cp target/*.jar /workspace/app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario nao-root
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
