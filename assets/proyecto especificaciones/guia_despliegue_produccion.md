# 🚀 Guía de Despliegue Completa y Protección de Swagger (Paso 12)

Esta guía detalla el paso a paso para realizar el despliegue del proyecto integrador, cumpliendo con todas las especificaciones de la rúbrica (incluyendo la protección obligatoria de Swagger en producción).

---

## 📌 ¿Qué incluye el Paso 12 y qué más se requiere?

El Paso 12 original de la guía base abarca la dockerización y el despliegue de PostgreSQL, Redis y la API en la nube. Sin embargo, para obtener la **nota máxima en la rúbrica**, se deben implementar de forma obligatoria las siguientes configuraciones de seguridad e infraestructura:

1. **Dockerfile Optimizado:** Multi-stage build para reducir peso.
2. **Orquestación en Local:** Integrar la API en `docker-compose.yml` junto a BD y Redis.
3. **CORS Dinámico:** Configurado por variable de entorno `ALLOWED_ORIGINS`.
4. **Zonas Horarias:** Configuración de la JVM para correr con la zona horaria correcta.
5. **Protección de Swagger en Producción:** Restringir el acceso a `/swagger-ui/**` y `/v3/api-docs/**` con **Basic Auth** (con credenciales configuradas vía variables de entorno).

---

## 🛠️ Paso 1: Configurar la Protección de Swagger con Basic Auth en Producción

Para cumplir con la rúbrica sin interferir con la autenticación JWT de la base de datos, implementaremos una cadena de filtros de seguridad separada y exclusiva para Swagger.

### 1. Agregar las propiedades en `src/main/resources/application.yml`
Agrega estas variables al final de tu archivo `application.yml` para definir las credenciales por defecto (que luego se sobreescribirán en producción con variables de entorno):

```yaml
swagger:
  username: ${SWAGGER_USER:evaluador}
  password: ${SWAGGER_PASSWORD:evaluador123}
```

### 2. Modificar `SecurityConfig.java`
Edita tu clase de configuración de seguridad para proteger Swagger usando HTTP Basic Auth de manera aislada (inyectando un `AuthenticationManager` exclusivo en memoria para esas rutas):

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
        
        // Creamos un proveedor de autenticación en memoria exclusivo para el evaluador de Swagger
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

*Nota: Al usar `@Order(1)`, Spring Security interceptará primero las peticiones a Swagger y pedirá Basic Auth. Las demás peticiones pasarán a la cadena principal `@Order(2)` que exige JWT.*

---

## 🐳 Paso 2: Crear el `Dockerfile` de Producción

Crea un archivo llamado `Dockerfile` (sin extensión) en la raíz del proyecto para generar una imagen optimizada multi-stage con Java 17:

```dockerfile
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

# Comando para ejecutar la aplicación limitando recursos de RAM en contenedores gratuitos
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 📦 Paso 3: Probar la Dockerización en Local

Actualiza tu archivo `docker-compose.yml` en la raíz del proyecto para agregar el servicio de la API (`app`) y orquestar todo localmente:

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

### Ejecutar localmente con Docker Compose
1. En tu consola corre:
   ```bash
   docker compose up -d --build
   ```
2. Ejecuta los scripts SQL (`00_create_database.sql` y `01_schema_and_data.sql`) en el contenedor PostgreSQL local.
3. Abre en tu navegador `http://localhost:8080/api/swagger-ui.html`. Debería solicitarte usuario (`admin`) y contraseña (`adminpassword`).

---

## ☁️ Paso 4: Despliegue en la Nube (ej. Render)

### 1. Desplegar PostgreSQL en Render
1. Haz clic en **New +** -> **PostgreSQL**.
2. Nombre: `academic-events-db`.
3. Database Name: `academic_events_db`.
4. User: `postgres_user` (o el de tu preferencia).
5. Selecciona la región más cercana (ej. `Oregon (US West)` o `Ohio (US East)`).
6. Haz clic en **Create Database**.
7. Una vez creada, conéctate a ella utilizando DBeaver u otra herramienta usando la **External Connection URI** y ejecuta tus scripts SQL en orden:
   * `00_create_database.sql`
   * `01_schema_and_data.sql`

### 2. Desplegar Redis en Render
1. Haz clic en **New +** -> **Redis**.
2. Nombre: `academic-events-redis`.
3. Haz clic en **Create Redis**.
4. Toma nota de la **Internal Connection URI** (servirá para enlazarlo a la API). Generalmente tiene la forma `redis://red-xxxxxxxxxx:6379` o `rediss://...`.
   * *Si la URL tiene la estructura `redis://default:password@host:port`, extrae el Host, Port y Password para tus variables.*

### 3. Desplegar la API de Spring Boot
1. Haz clic en **New +** -> **Web Service**.
2. Conecta tu repositorio de GitHub.
3. Nombre: `academic-events-api`.
4. Runtime: **Docker**.
5. Plan: **Free**.
6. Agrega las siguientes **Variables de Entorno (Environment Variables)** en la configuración del servicio:

| Variable | Valor de ejemplo / Origen |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | *(Host interno de tu PostgreSQL en Render)* |
| `DB_PORT` | `5432` |
| `DB_NAME` | `academic_events_db` |
| `DB_USER` | `postgres_user` |
| `DB_PASSWORD` | *(Contraseña de tu BD en Render)* |
| `REDIS_HOST` | *(Host de Redis en Render, ej: `red-xxxxxxxxxx`)* |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | *(Si tu Redis tiene contraseña configurada)* |
| `JWT_SECRET` | *(Una clave de 64 caracteres en hexadecimal para producción)* |
| `ALLOWED_ORIGINS` | `http://localhost:3000` *(o la URL de tu frontend si tienes)* |
| `SWAGGER_USER` | `evaluador` *(Usuario de acceso al Swagger)* |
| `SWAGGER_PASSWORD` | `SeguraClaveDocente2026` *(Contraseña de acceso)* |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil` *(Limita uso de RAM al 75% del contenedor free y setea la zona horaria de Ecuador)* |

7. Haz clic en **Create Web Service**.

Una vez compilada y levantada, podrás probar ingresando a:
`https://tu-app-render.onrender.com/api/swagger-ui.html`
Donde el navegador te pedirá la autenticación básica configurada en `SWAGGER_USER` y `SWAGGER_PASSWORD` para poder ver y testear los endpoints.
