# Plan de Desarrollo - Paso 12: Dockerización, Orquestación Local y Despliegue en la Nube (Render) con Seguridad

Este documento detalla cada una de las acciones necesarias para implementar el **Paso 12** en el proyecto **academic-events-api**. Contiene las modificaciones de archivos, los nuevos archivos a crear, las variables de entorno requeridas y los comandos de verificación para obtener la nota máxima de acuerdo con la rúbrica de evaluación.

---

## 📂 1. Resumen de Archivos

### 🆕 Archivos Nuevos
Para complementar el módulo de seguridad y despliegue, se sugiere crear el siguiente archivo:

| # | Archivo | Ruta | Propósito |
|---|---------|------|-----------|
| 1 | [desarrollo_paso_12.md](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/desarrollo_paso_12.md) | `.` (Raíz) | Esta misma guía de desarrollo detallada para el Paso 12. |
| 2 | [WebConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/config/WebConfig.java) | `src/main/java/ec/edu/ups/icc/events/core/config/` | Configuración global y dinámica de CORS basada en la propiedad de `application.yml`. |

### 🛠️ Archivos Existentes a Modificar

| # | Archivo | Ruta | Modificación Requerida |
|---|---------|------|------------------------|
| 1 | [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/security/SecurityConfig.java) | `src/main/java/ec/edu/ups/icc/events/core/security/` | Implementar protección de Swagger con HTTP Basic Auth exclusiva en producción, usando `@Order(1)`. Configurar CORS en la cadena de seguridad REST. |
| 2 | [application.yml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/resources/application.yml) | `src/main/resources/` | Agregar propiedades de credenciales para Swagger y verificar que los perfiles y conexiones dinámicas estén listos. |
| 3 | [docker-compose.yml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/docker-compose.yml) | `.` (Raíz) | Agregar el servicio `app` para orquestar la API junto a `postgres` y `redis` localmente con Healthchecks. |

---

## 📋 2. Detalle de Acciones a Realizar

### Acción 1: Configurar CORS Dinámico
Para cumplir con la rúbrica sobre control de CORS dinámico cargado desde `cors.allowed-origins` en `application.yml`:
* **Archivo:** [WebConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/config/WebConfig.java) [NUEVO]
* **Detalle:** Implementar un Bean de CORS en Spring Boot:
```java
package ec.edu.ups.icc.events.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        
        // Cargar orígenes dinámicamente desde variables de entorno
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

### Acción 2: Agregar Propiedades de Swagger en `application.yml`
* **Archivo:** [application.yml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/resources/application.yml) [MODIFICAR]
* **Detalle:** Configurar los valores por defecto para el Basic Auth del Swagger al final del archivo:
```yaml
swagger:
  username: ${SWAGGER_USER:evaluador}
  password: ${SWAGGER_PASSWORD:evaluador123}
```

### Acción 3: Proteger Swagger en Producción con Basic Auth (`SecurityConfig`)
* **Archivo:** [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/security/SecurityConfig.java) [MODIFICAR]
* **Detalle:** Configurar dos cadenas de seguridad (`SecurityFilterChain`):
  1. La primera con `@Order(1)` aplicada únicamente a las rutas de Swagger, que solicita Basic Auth usando en memoria al usuario evaluador configurado.
  2. La segunda con `@Order(2)` para la API REST principal que utiliza JWT y habilita CORS.
```java
package ec.edu.ups.icc.events.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${swagger.username}")
    private String swaggerUsername;

    @Value("${swagger.password}")
    private String swaggerPassword;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // 1. Cadena de seguridad exclusiva para proteger Swagger en producción con Basic Auth
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder
    ) throws Exception {

        // Creamos un proveedor de autenticación en memoria exclusivo para el evaluador del Swagger
        UserDetails evaluator = User.builder()
                .username(swaggerUsername)
                .password(passwordEncoder.encode(swaggerPassword))
                .roles("EVALUATOR")
                .build();

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(new InMemoryUserDetailsManager(evaluator));
        authProvider.setPasswordEncoder(passwordEncoder);
        ProviderManager authManager = new ProviderManager(authProvider);

        http
                .securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .authenticationManager(authManager)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("EVALUATOR")
                )
                .httpBasic(withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    // 2. Cadena de seguridad principal para la API REST (Autenticación JWT)
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/api/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
```

### Acción 4: Completar `docker-compose.yml` con el servicio de la API (app)
* **Archivo:** [docker-compose.yml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/docker-compose.yml) [MODIFICAR]
* **Detalle:** Añadir el servicio `app` para que dependa de los contenedores saludables (`postgres` y `redis`):
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: academic_events_db_container
    environment:
      POSTGRES_DB: academic_events_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgrespassword
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d academic_events_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: academic_events_redis_container
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    build: .
    container_name: academic_events_api_app
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      PORT: 8080
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: academic_events_db
      DB_USER: postgres
      DB_PASSWORD: postgrespassword
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
      ALLOWED_ORIGINS: "http://localhost:3000,http://localhost:8080"
      SWAGGER_USER: admin
      SWAGGER_PASSWORD: adminpassword

volumes:
  postgres_data:
  redis_data:
```

### Acción 5: Entender y Verificar el `Dockerfile` de Multi-Stage
* **Archivo:** [Dockerfile](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/Dockerfile) [VERIFICAR / NO CAMBIAR]
* **Detalle:** El Dockerfile del proyecto utiliza un enfoque de compilación multi-etapa (multi-stage build):
  - **Fase de Compilación (`builder`):** Utiliza la imagen `eclipse-temurin:17-jdk-alpine` para copiar la configuración y dependencias de Gradle y compilar el JAR con `./gradlew bootJar -x test`.
  - **Fase de Ejecución:** Utiliza la imagen ligera `eclipse-temurin:17-jre-alpine` que reduce el tamaño final de la imagen a menos de 200MB, e inicia el contenedor ejecutando `java -jar app.jar`.

---

## ☁️ 3. Configuración y Despliegue en Render (Nube)

### 3.1 Base de Datos PostgreSQL
1. Crea un servicio **PostgreSQL** en Render.
2. Nombra la base de datos como `academic_events_db`.
3. Una vez aprovisionada, conéctate externamente (ej: DBeaver, pgAdmin) y ejecuta el contenido del script de la base de datos `01_schema_and_data.sql`.

### 3.2 Caché Redis
1. Crea un servicio **Redis** en Render (en la misma región que tu base de datos).
2. Guarda el **Host interno** (ej: `red-xxxxxxxxxx`) provisto para configurar la comunicación privada.

### 3.3 Web Service (API de Spring Boot)
1. Crea un **Web Service** conectado a tu repositorio en GitHub.
2. Define el **Runtime** como **Docker**.
3. Asegura seleccionar la **misma región** que la base de datos y Redis.
4. En la pestaña **Advanced**, inyecta las siguientes variables de entorno (Environment Variables):

| Variable de Entorno | Valor Recomendado | Propósito |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activa configuraciones de producción. |
| `DB_HOST` | *(Host interno de PostgreSQL en Render)* | Conexión privada a la base de datos. |
| `DB_PORT` | `5432` | Puerto estándar. |
| `DB_NAME` | `academic_events_db_xxxx` (según Render) | Nombre real de la BD en Render. |
| `DB_USER` | *(Usuario en Render)* | Usuario administrador de PostgreSQL. |
| `DB_PASSWORD` | *(Contraseña en Render)* | Contraseña de base de datos. |
| `REDIS_HOST` | *(Host interno de Redis en Render)* | Conexión interna y gratuita a Redis. |
| `REDIS_PORT` | `6379` | Puerto estándar de Redis. |
| `JWT_SECRET` | *(Clave hexadecimal de 64 caracteres)* | Llave criptográfica para firmas JWT. |
| `ALLOWED_ORIGINS` | `https://tu-frontend.onrender.com` | Orígenes dinámicos autorizados por CORS. |
| `SWAGGER_USER` | `evaluador` | Usuario para acceder al Swagger. |
| `SWAGGER_PASSWORD` | `SeguraClaveDocente2026` | Contraseña para acceder al Swagger. |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil` | Restringe consumo de RAM en tier free y fija la zona horaria del servidor. |

---

## 🧪 4. Plan de Pruebas y Verificación

### Pruebas Locales (Docker Compose)
1. Construir e iniciar contenedores locales:
   ```bash
   docker compose up -d --build
   ```
2. Ejecutar los scripts SQL de datos de prueba dentro del contenedor PostgreSQL local.
3. Abrir `http://localhost:8080/api/swagger-ui.html`. Verificar que solicite credenciales y loguearse con `admin` / `adminpassword`.
4. Ejecutar peticiones desde Swagger y validar que el token JWT sea enviado correctamente en las cabeceras.

### Pruebas en Producción (Render)
1. Navegar al Swagger público de tu despliegue: `https://tu-api.onrender.com/api/swagger-ui.html`.
2. Verificar que se presente el modal del navegador exigiendo credenciales (HTTP Basic Auth).
3. Iniciar sesión usando `evaluador` y `SeguraClaveDocente2026`.
4. Comprobar que todas las fechas devueltas sigan el estándar ISO 8601 (UTC) en los endpoints, pero que al descargar reportes se aplique la conversión local a la zona horaria de Ecuador (`America/Guayaquil`).
