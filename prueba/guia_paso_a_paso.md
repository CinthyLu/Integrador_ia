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

### 2.2 Crear `BaseEntity` para Auditoría JPA
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
}
```

### 2.3 Mapear Entidades JPA
Mapear cada entidad respetando los nombres exactos del esquema SQL entregado:
* `User` (`users`) - Mapear relaciones `@ManyToMany` con `Role`.
* `Role` (`roles`) - Enums o strings para `ADMIN`, `ORGANIZER`, `PARTICIPANT`.
* `Category` (`categories`).
* `Event` (`events`) - Relación `@ManyToOne` con `User` (organizer) y `Category`.
* `Session` (`sessions`) - Relación `@ManyToOne` con `Event`.
* `Registration` (`registrations`) - Relación `@ManyToOne` con `User` y `Event`.
* `AuditLog` (`audit_logs`).

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
* Exponer endpoints públicos: `/api/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`.

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
* Método para generar lista de participantes inscritos por evento en formato `.xlsx`.
* Aplicar estilos a cabeceras, bordes y formatos de fecha.

### 10.3 Generador PDF (`PdfReportService`)
* Método para generar comprobante/certificado de inscripción en `.pdf`.
* Incluir logo, datos del evento, usuario, fecha de emisión y código de verificación.

### 10.4 Endpoints de Reportes
* Exponer endpoints en `ReportController` retornando `ResponseEntity<byte[]>` con la cabecera `HttpHeaders.CONTENT_DISPOSITION` configurada para descarga.

---

## 🧪 Paso 11: Pruebas Unitarias y de Integración (JUnit 5 + Mockito)

### 11.1 Pruebas Unitarias (`src/test/java/...`)
* `EventServiceTest`: Mockear repositorios y probar validación de cupos y ownership.
* `AuthServiceTest`: Probar login exitoso, credenciales inválidas y bloqueo de cuenta.
* `RegistrationServiceTest`: Probar concurrencia y prevención de doble inscripción.

### 11.2 Pruebas de Integración (`@SpringBootTest` + `@AutoConfigureMockMvc`)
* `AuthControllerIntegrationTest`: Probar flujos completados de obtención de JWT y peticiones protegidas.

---

## 🐳 Paso 12: Dockerización y Despliegue en la Nube (Render / Railway)

### 12.1 Escribir `Dockerfile` Multi-Stage
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 12.2 Configuración para Render / Railway
* Configurar variables de entorno en Render: `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`.
* Desplegar instancia de PostgreSQL y Redis en la plataforma.
* Verificar conexión de la API REST desplegada a través de Swagger UI (`/swagger-ui/index.html`).

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
