# --- Stage 1: Compilación de la aplicación ---
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle
COPY gradle/ gradle/
COPY gradlew gradlew
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts

# Descargar dependencias para aprovechar la caché de Docker
RUN ./gradlew dependencies --no-daemon || true

# Copiar el código fuente y compilar
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# --- Stage 2: Servidor de ejecución ligero ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el ejecutable generado en el stage anterior
COPY --from=builder /app/build/libs/*.jar app.jar

# Exponer el puerto configurado por la variable de entorno PORT
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
