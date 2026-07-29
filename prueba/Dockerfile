# Stage 1: Build de la aplicación Spring Boot con Gradle Multi-stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de Gradle y código fuente
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src

# Otorgar permisos de ejecución a gradlew y construir la aplicación omitiendo tests en el build de producción
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

# Stage 2: Imagen final liviana JRE de ejecución en producción
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear usuario no-root para seguridad del contenedor
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar el ejecutable JAR generado
COPY --from=builder /app/build/libs/*.jar app.jar

# Puerto por defecto expuesto
EXPOSE 8080

# Limitar memoria RAM para entornos gratuitos de nube como Render (-XX:MaxRAMPercentage)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil"

ENTRYPOINT ["java", "-jar", "app.jar"]
