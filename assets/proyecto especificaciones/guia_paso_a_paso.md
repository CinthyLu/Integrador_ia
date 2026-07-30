# Guía Paso a Paso: Desarrollo de la API REST Segura para Gestión de Eventos Académicos

Esta guía detalla el paso a paso secuencial, práctico y técnico para construir el proyecto integrador backend en **Spring Boot**, **PostgreSQL** y **Redis**, cumpliendo con los estándares de arquitectura modular, seguridad JWT, rate limiting y reportes requeridos.

---

## 📋 Índice de Pasos

1. [Paso 1: Entorno de Desarrollo, Base de Datos Local y Proyecto Base](#paso-1-entorno-de-desarrollo-base-de-datos-local-y-proyecto-base)
2. [Paso 2: Arquitectura Modular y Mapeo de Entidades JPA](#paso-2-arquitectura-modular-y-mapeo-de-entidades-jpa)
3. [Paso 3: Infraestructura Base, DTOs y Manejo Global de Excepciones](#paso-3-infraestructura-base-dtos-y-manejo-global-de-excepciones)
4. [Paso 4: Autenticación, Spring Security y Cifrado BCrypt](#paso-4-autenticación-spring-security-y-cifrado-bcrypt)
5. [Paso 5: Implementación de JWT, Refresh Tokens y Logout](#paso-5-implementación-de-jwt-refresh-tokens-y-logout)
6. [Paso 6: Módulo de Eventos, Categorías y Sesiones (CRUD + Paginación + Ownership)](#paso-6-módulo-de-eventos-categorías-y-sesiones-crud--paginación--ownership)
7. [Paso 7: Módulo de Inscripciones y Control Transaccional de Cupos](#paso-7-módulo-de-inscripciones-y-control-transaccional-de-cupos)
8. [Paso 8: Sistema de Auditoría y Trazabilidad (Audit Logs)](#paso-8-sistema-de-auditoría-y-trazabilidad-audit-logs)
9. [Paso 9: Integración de Redis: Rate Limiting Distribuido y Bloqueos Temporales](#paso-9-integración-de-redis-rate-limiting-distribuido-y-bloqueos-temporales)
10. [Paso 10: Módulo de Reportes Descargables (Excel con Apache POI y PDF con OpenPDF)](#paso-10-módulo-de-reportes-descargables-excel-con-apache-poi-y-pdf-con-openpdf)
11. [Paso 11: Pruebas Unitarias y de Integración (JUnit 5 + Mockito)](#paso-11-pruebas-unitarias-y-de-integración-junit-5--mockito)
12. [Paso 12: Dockerización y Despliegue en la Nube (Render / Railway)](#paso-12-dockerización-y-despliegue-en-la-nube-render--railway)

---

## 🛠️ Paso 1: Entorno de Desarrollo, Base de Datos Local y Proyecto Base

### 1.1 Generar el proyecto Spring Boot
Utilizar [Spring Initializr](https://start.spring.io/) o CLI con la siguiente configuración:
* **Project:** Gradle - Groovy o Maven (Java 17 o 21)
* **Group:** `ec.edu.ups.icc`
* **Artifact:** `academic-events-api`
* **Dependencies:**
  * `Spring Web`
  * `Spring Data JPA`
  * `Spring Security`
  * `Spring Data Redis`
  * `Validation` (Bean Validation)
  * `PostgreSQL Driver`
  * `Lombok`
  * `Spring Boot Actuator`
  * `Springdoc OpenAPI` (Swagger)

### 1.2 Configurar el Docker Compose Local
Crear un archivo `docker-compose.yml` en la raíz para PostgreSQL y Redis:

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

  redis:
    image: redis:7-alpine
    container_name: academic_events_redis_container
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

Ejecutar contenedores:
```bash
docker compose up -d
```

### 1.3 Cargar los Scripts SQL Docente
Ejecutar en PostgreSQL en orden:
1. `00_create_database.sql`
2. `01_schema_and_data.sql`

Verificar que las tablas existan: `users`, `roles`, `user_roles`, `categories`, `events`, `sessions`, `registrations`, `audit_logs`.

---

## 🏗️ Paso 2: Arquitectura Modular y Mapeo de Entidades JPA

### 2.1 Crear Estructura de Paquetes Modular por Dominio
```txt
src/main/java/ec/edu/ups/icc/events/
├── core/
│   ├── config/
│   ├── entities/
│   ├── exceptions/
│   └── utils/
├── auth/
├── security/
├── users/
├── categories/
├── events/
├── sessions/
├── registrations/
├── audit/
└── reports/
```

### 2.2 Crear `BaseEntity` para Auditoría y Soft-Delete JPA
En `core/entities/BaseEntity.java`:
```java
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false; // Campo para borrado lógico (Soft-Delete)
}
```

### 2.3 Mapear Entidades JPA y Configurar Flyway (Opcional pero recomendado)
Mapear cada entidad respetando los nombres exactos del esquema SQL entregado y considerando las siguientes optimizaciones de rendimiento:
* **Lazy Loading:** Configurar explícitamente `FetchType.LAZY` en todas las relaciones `@ManyToOne`, `@ManyToMany` y `@OneToMany` para evitar consultas N+1.
* **Uso de Set:** Usar `Set<T>` en lugar de `List<T>` en las relaciones de colección para evitar duplicación de datos en tablas intermedias.
* **Soft-Delete:** En las entidades correspondientes (especialmente `Event`), utilizar las anotaciones `@SQLDelete(sql = "UPDATE events SET deleted = true WHERE id = ?")` y `@Where(clause = "deleted = false")` para evitar la eliminación física si ya hay inscritos.

Entidades a mapear:
* `User` (`users`) - Mapear relaciones `@ManyToMany` con `Role`.
* `Role` (`roles`) - Enums o strings para `ADMIN`, `ORGANIZER`, `PARTICIPANT`.
* `Category` (`categories`).
* `Event` (`events`) - Relación `@ManyToOne` con `User` (organizer) y `Category`.
* `Session` (`sessions`) - Relación `@ManyToOne` con `Event`.
* `Registration` (`registrations`) - Relación `@ManyToOne` con `User` y `Event`.
* `AuditLog` (`audit_logs`).

*(Opcional)* Si deciden utilizar **Flyway** para cumplir con el entregable de migraciones:
1. Agregar la dependencia en `build.gradle.kts`:
   `implementation("org.flywaydb:flyway-core")` y `runtimeOnly("org.flywaydb:flyway-database-postgresql")`.
2. Renombrar el script `01_schema_and_data.sql` a `V1__initial_schema_and_data.sql` y colocarlo dentro de `src/main/resources/db/migration/`.

### 2.4 Configurar `application.yml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/academic_events_db
    username: postgres
    password: postgrespassword
  jpa:
    hibernate:
      ddl-auto: validate # ¡OBLIGATORIO: Validar sin alterar esquema!
    show-sql: true
    properties:
      hibernate.format_sql: true
```

Verificación: Iniciar la aplicación y comprobar que Hibernate valide correctamente el esquema sin arrojar errores.

---

## ⚠️ Paso 3: Infraestructura Base, DTOs y Manejo Global de Excepciones

### 3.1 Definir DTOs Estandarizados de Respuesta
En `core/dtos/ApiResponse.java`:
```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
```

### 3.2 Crear Excepciones Personalizadas del Dominio
* `ResourceNotFoundException`
* `BadRequestException`
* `UnauthorizedException`
* `ForbiddenException`
* `BusinessRuleException`

### 3.3 Implementar Controlador Global de Excepciones (`GlobalExceptionHandler`)
En `core/exceptions/GlobalExceptionHandler.java`:
* Anotar con `@RestControllerAdvice`.
* Capturar `MethodArgumentNotValidException` (Bean Validation).
* Capturar excepciones propias del dominio.
* Retornar respuestas estructuradas en JSON con código HTTP apropiado (`400`, `401`, `403`, `404`, `409`, `429`, `500`).

---

## 🔒 Paso 4: Autenticación, Spring Security y Cifrado BCrypt

### 4.1 Beans de Seguridad
En `security/config/SecurityBeansConfig.java`:
```java
@Configuration
public class SecurityBeansConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 4.2 Cargar Usuario (`CustomUserDetailsService`)
Implementar `UserDetailsService` consultando el `UserRepository` por email y mapeando los roles a `GrantedAuthority`.

### 4.3 Configuración del Filtro de Seguridad (`SecurityConfig`)
* Deshabilitar `csrf` (para API stateless).
* Definir política de sesión `SessionCreationPolicy.STATELESS`.
* Configurar reglas de autorización con `@EnableMethodSecurity`.
* Exponer endpoints públicos generales: `/api/auth/**`.
* **Proteger Swagger en producción con Basic Auth:** 
  * En entorno local, permitir acceso libre a `/swagger-ui/**` y `/v3/api-docs/**`.
  * En producción (`prod`), requerir autenticación básica (`httpBasic()`) con un usuario/contraseña configurado a través de variables de entorno (`SWAGGER_USERNAME` / `SWAGGER_PASSWORD`).

---

## 🔑 Paso 5: Implementación de JWT, Refresh Tokens y Logout

### 5.1 Servicio JWT (`JwtService`)
* Generación de **Access Token** (expiración corta: 15 min).
* Generación de **Refresh Token** (expiración larga: 7 días).
* Firma con algoritmo HMAC-SHA256 (`Keys.hmacShaKeyFor`).
* Métodos de extracción de `username` y validación de firma/expiración.

### 5.2 Filtro JWT (`JwtAuthenticationFilter`)
* Interceptar peticiones HTTP.
* Extraer el token de la cabecera `Authorization: Bearer <token>`.
* Validar el token y cargar el `UsernamePasswordAuthenticationToken` en el `SecurityContextHolder`.

### 5.3 Endpoints de Autenticación (`AuthController`)
* `POST /api/auth/register`: Registrar participante codificando contraseña con `BCrypt`.
* `POST /api/auth/login`: Autenticar credenciales y retornar `accessToken` + `refreshToken`.
* `POST /api/auth/refresh`: Validar `refreshToken` y emitir nuevo `accessToken`.
* `POST /api/auth/logout`: Revocar token (añadir a lista negra en Redis).

---

## 📅 Paso 6: Módulo de Eventos, Categorías y Sesiones (CRUD + Paginación + Ownership)

### 6.1 CRUD de Categorías
* Repository: `CategoryRepository`.
* Service: `CategoryService`.
* Controller: `CategoryController` (`GET` público, `POST`/`PUT`/`DELETE` restringido a `ADMIN`).

### 6.2 Búsqueda, Filtrado y Paginación de Eventos
* Utilizar `Pageable` y `Page<EventDTO>` en `GET /api/events`.
* Permitir filtros combinados: búsqueda por texto en título/descripción, filtro por categoría, modalidad y rango de fechas.

### 6.3 Verificación de Propiedad (Ownership)
* Anotar métodos en `EventService` con `@PreAuthorize("hasRole('ADMIN') or (hasRole('ORGANIZER') and #event.organizer.id == authentication.principal.id)")`.
* Garantizar que un organizador NO pueda modificar o eliminar eventos pertenecientes a otro organizador.

---

## 📝 Paso 7: Módulo de Inscripciones y Control Transaccional de Cupos

### 7.1 Lógica de Inscripción (`RegistrationService`)
Implementar método `@Transactional`:
1. Verificar que el evento exista y esté activo.
2. Validar que la fecha del evento no haya pasado.
3. Verificar que el usuario no esté previamente inscrito (`existsByUserIdAndEventId`).
4. **Verificar cupos disponibles:** Validar que `event.getAvailableSeats() > 0`.
5. Reducir en 1 los cupos disponibles (`event.setAvailableSeats(...)`).
6. Guardar la inscripción en `registrations`.

### 7.2 Cancelación de Inscripción
* Permitir al participante cancelar su propia inscripción.
* Restablecer el cupo disponible en la transacción (`+1`).

---

## 📜 Paso 8: Sistema de Auditoría y Trazabilidad (Audit Logs)

### 8.1 Eventos de Auditoría
Crear una anotación personalizada o un filtro AOP (`@Auditable`) o interceptor HTTP.

### 8.2 Servicio de Auditoría (`AuditLogService`)
* Registrar en `audit_logs`:
  * `user_id` (o `null` si no autenticado).
  * `action` (e.g., `LOGIN_SUCCESS`, `LOGIN_FAILED`, `CREATE_EVENT`, `REGISTER_EVENT`).
  * `resource_name` / `resource_id`.
  * `ip_address` y `user_agent`.
  * `timestamp`.

---

## ⚡ Paso 9: Integración de Redis: Rate Limiting Distribuido y Bloqueos Temporales

### 9.1 Configuración de Redis (`RedisConfig`)
Configurar `RedisTemplate<String, Object>` con serializadores JSON.

### 9.2 Filtro de Rate Limiting (`RateLimitingFilter`)
Implementar filtro servlet que valide los límites definidos en la especificación:
* **Login (`POST /api/auth/login`):** Máx 5 req/min (IP + email).
* **Registro (`POST /api/auth/register`):** Máx 3 req/hora (IP).
* **Públicos:** Máx 60 req/min (IP).
* **Autenticados:** Máx 120 req/min (User ID).
* **Reportes:** Máx 5 req/min (User ID).

Si se excede: Responder `HTTP 429 Too Many Requests` e incluir cabecera `Retry-After`.

### 9.3 Bloqueo Temporal de Cuentas por Fallo Repetido
* Al fallar 5 intentos de inicio de sesión consecutivos, guardar clave en Redis: `blocked-user:{email}` con TTL de 15-30 minutos.
* Rechazar autenticaciones subsiguientes mientras la clave persista en Redis.

---

## 📊 Paso 10: Módulo de Reportes Descargables (Excel con Apache POI y PDF con OpenPDF)

### 10.1 Añadir Dependencias
```xml
<!-- Apache POI para Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
<!-- OpenPDF para PDF -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>1.3.39</version>
</dependency>
```

### 10.2 Generador Excel (`ExcelReportService`)
* Método para generar la lista de participantes inscritos por evento en formato `.xlsx`.
* Aplicar estilos a cabeceras, bordes y formatos de fecha en la zona horaria de negocio (`America/Guayaquil`).

### 10.3 Generador PDF (`PdfReportService`)
* **Reporte de inscritos por evento:** Método para generar la lista de inscritos en `.pdf` (para ADMIN u ORGANIZER propietario del evento).
* **Certificado/Comprobante de inscripción:** Método para generar comprobante individual en `.pdf` (para PARTICIPANT).
* Diseñar la estructura visual: logo de la UPS, título del reporte, datos generales del evento/usuario, fecha de emisión en la zona horaria `America/Guayaquil` y formato estético.

### 10.4 Conversión de Zona Horaria en Reportes
* Asegurar que todas las fechas y horas almacenadas en UTC en la base de datos sean convertidas a la zona horaria `America/Guayaquil` utilizando clases como `ZonedDateTime` o `ZoneId` al momento de renderizar los PDF o escribir las celdas en el Excel.

### 10.5 Endpoints de Reportes
* Exponer endpoints en `ReportController` (`/api/reports/events/{eventId}/registrations.pdf`, `/api/reports/events/{eventId}/registrations.xlsx`, `/api/registrations/{id}/certificate.pdf`).
* Retornar `ResponseEntity<byte[]>` con el flujo de datos (stream de bytes) en memoria (sin guardarlos localmente en el servidor) y establecer la cabecera `HttpHeaders.CONTENT_DISPOSITION` como `attachment; filename="..."` con el `Content-Type` correcto.

---

## 🧪 Paso 11: Pruebas Unitarias y de Integración (JUnit 5 + Mockito)

### 11.1 Pruebas Unitarias (`src/test/java/...`)
* `EventServiceTest`: Mockear repositorios y probar validación de cupos y ownership.
* `AuthServiceTest`: Probar login exitoso, credenciales inválidas y bloqueo de cuenta.
* `RegistrationServiceTest`: Probar concurrencia y prevención de doble inscripción.

### 11.2 Pruebas de Integración (`@SpringBootTest` + `@AutoConfigureMockMvc`)
* `AuthControllerIntegrationTest`: Probar flujos completados de obtención de JWT y peticiones protegidas.

---

## 🐳 Paso 12: Dockerización, Orquestación Local y Despliegue en la Nube

Este paso cubre la dockerización de la API, la orquestación local de todos los servicios mediante Docker Compose y el despliegue final en la nube (ej. Render o Railway).

### 12.1 Escribir el `Dockerfile` de Producción (Multi-Stage)
Crear un archivo llamado `Dockerfile` (sin extensión) en la raíz del proyecto para generar una imagen ligera y segura:
```dockerfile
# --- Stage 1: Compilación de la aplicación ---
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle
COPY gradle/ gradle/
COPY gradlew gradlew
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts

# Descargar dependencias para aprovechar la caché de capas de Docker
RUN ./gradlew dependencies --no-daemon || true

# Copiar el código fuente y compilar
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# --- Stage 2: Servidor de ejecución ligero ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el ejecutable generado en el stage anterior
COPY --from=builder /app/build/libs/*.jar app.jar

# Exponer el puerto
EXPOSE 8080

# Comando de entrada
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 12.2 Configurar el `docker-compose.yml` para Orquestación Local
Crear o actualizar el archivo `docker-compose.yml` en la raíz del proyecto para orquestar la API (`app`), la base de datos PostgreSQL (`postgres`) y la caché Redis (`redis`) con healthchecks:
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
      SPRING_PROFILES_ACTIVE: prod

volumes:
  postgres_data:
  redis_data:
```

### 12.3 Despliegue en la Nube (Render)

#### 12.3.1 Desplegar Base de Datos PostgreSQL
1. En Render, crea un servicio **PostgreSQL** (`New +` -> `PostgreSQL`).
2. Asígnale un nombre (ej. `academic-events-db`) y la base de datos `academic_events_db`.
3. Una vez creada la base de datos, conéctate externamente con tu cliente SQL (ej. DBeaver o pgAdmin) usando la URI externa provista por Render y carga los scripts SQL en orden:
   * `00_create_database.sql`
   * `01_schema_and_data.sql`

#### 12.3.2 Desplegar Caché Redis
1. En Render, crea un servicio **Redis** (`New +` -> `Redis`).
2. Guarda el host y puerto interno, así como la contraseña si aplica.

#### 12.3.3 Desplegar la API Spring Boot (Web Service)
1. En Render, crea un **Web Service** (`New +` -> `Web Service`) apuntando al repositorio de tu API.
2. Configura el **Runtime** como **Docker**.
3. Añade las siguientes **Variables de Entorno** obligatorias:
   * `SPRING_PROFILES_ACTIVE=prod`
   * `DB_HOST`: Host interno de PostgreSQL provisto por Render.
   * `DB_PORT=5432`
   * `DB_NAME=academic_events_db`
   * `DB_USER=postgres_user` (o el usuario de tu BD).
   * `DB_PASSWORD`: Contraseña de tu BD.
   * `REDIS_HOST`: Host interno de tu Redis.
   * `REDIS_PORT=6379`
   * `REDIS_PASSWORD`: Contraseña de Redis (si aplica).
   * `JWT_SECRET`: Llave secreta en hexadecimal para producción (mínimo 64 caracteres).
   * `ALLOWED_ORIGINS`: URL del frontend permitido o localhost.
   * `SWAGGER_USER`: Usuario para el Basic Auth del Swagger (ej. `evaluador`).
   * `SWAGGER_PASSWORD`: Contraseña para el Basic Auth del Swagger (ej. `evaluador123`).
   * `JAVA_TOOL_OPTIONS`: `-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil` (Limita el uso de memoria RAM en el tier gratuito al 75% y establece la zona horaria a la de Ecuador).

---

## 📋 Lista de Verificación Final (Checklist)

- [ ] Esquema cargado y `spring.jpa.hibernate.ddl-auto=validate` activo.
- [ ] Registro y Login con BCrypt y JWT funcionando.
- [ ] Ownership en Eventos y Roles (`ADMIN`, `ORGANIZER`, `PARTICIPANT`) respetados.
- [ ] Transaccionalidad de cupos en inscripciones probada.
- [ ] Rate Limiting y Bloqueos en Redis activos (retorna `429`).
- [ ] Excepciones uniformes con `@RestControllerAdvice`.
- [ ] Reportes Excel y PDF descargables correctamente.
- [ ] Cobertura de pruebas unitarias relevante.
- [ ] Swagger UI disponible y documentado.
- [ ] Contenedores Docker y despliegue público sin errores.
