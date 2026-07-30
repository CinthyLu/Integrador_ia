# Análisis de Cumplimiento (Proyecto Final) y Guía de Corrección

Este documento evalúa el estado del proyecto ubicado en la ruta `C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api` frente a la rúbrica y la guía de desarrollo (pasos 1 al 12), e indica los pasos y el código necesario para corregir cada observación detectada.

---

## 📊 Tabla Resumen de Cumplimiento (Pasos 1 al 12)

| Paso | Requerimiento / Funcionalidad | Estado | Puntos Rúbrica | Observación / Pendiente |
| :--- | :--- | :---: | :---: | :--- |
| **Paso 1** | Base de Datos Local y Docker Compose | ⚠️ **Incompleto** | — | Los contenedores postgres y redis están definidos, pero falta integrar el contenedor de la API en el compose local (solicitado en Paso 12.2). |
| **Paso 2** | Arquitectura y Mapeo JPA (Relaciones y Soft-Delete) | ⚠️ **Incompleto** | **6 Pts** | La relación `roles` en `UserEntity` es `EAGER` (debe ser `LAZY`). No existe campo `deleted` en `BaseEntity`, ni `@SQLDelete` / `@Where` en `EventEntity`. Falta Flyway. |
| **Paso 3** | Infraestructura Base, DTOs y Global Exception Handler | **Completo** | **5 Pts** | El controlador de excepciones captura todos los errores correctamente y retorna respuestas uniformes. |
| **Paso 4** | Autenticación, Spring Security y Cifrado BCrypt | ⚠️ **Incompleto** | **10 Pts** | Los endpoints de Swagger no están protegidos mediante Basic Auth para producción. |
| **Paso 5** | JWT, Refresh Tokens y Logout (Con Redis Blacklist) | ❌ **Error Crítico** | **10 Pts** | **El registro está roto.** Al crear un usuario en `/register` no se le asigna ningún rol, impidiendo su inicio de sesión posterior. Falta `RoleRepository`. |
| **Paso 6** | Módulo de Eventos, Categorías y Sesiones (CRUD & Ownership) | **Completo** | **10 Pts** | El CRUD de categorías restringe a ADMIN. La propiedad de eventos (ownership) por ORGANIZER se valida correctamente. |
| **Paso 7** | Inscripciones y Control Transaccional de Cupos | ⚠️ **Incompleto** | **10 Pts** | El endpoint `/api/registrations/**` está expuesto en `SecurityConfig` como `permitAll()`, evadiendo la cadena de filtros de seguridad. |
| **Paso 8** | Sistema de Auditoría y Trazabilidad (Audit Logs) | **Completo** | — | Implementación correcta con AOP interceptando `@Auditable` y registrando IP, navegador y usuario. |
| **Paso 9** | Redis: Rate Limiting y Bloqueo de Cuentas | ❌ **Ausente** | **7 Pts** | **No existe rate limiting ni bloqueo temporal** en Redis. Solo se declaró la excepción pero no hay filtros ni interceptores aplicándolo. |
| **Paso 10** | Módulo de Reportes Descargables (Excel y PDF) | **Completo** | **14 Pts** | Los servicios generan PDFs y Excels directamente en memoria y con conversión horaria a `America/Guayaquil`. |
| **Paso 11** | Pruebas Unitarias y de Integración (JUnit 5 + Mockito) | ⚠️ **Incompleto** | **3 Pts** | Faltan pruebas unitarias para validar la concurrencia y sobreventa de cupos en inscripciones. |
| **Paso 12** | Dockerización y Despliegue en la Nube | ❌ **Ausente** | **3 Pts** | Falta la orquestación local del servicio `app` en `docker-compose.yml` y el archivo `render.yaml` para despliegue automatizado. |

---

## 🛠️ Guía Detallada de Correcciones (Código y Pasos)

A continuación se detalla cómo corregir cada uno de los puntos observados para garantizar la nota máxima.

---

### 1. Corrección del Registro de Usuarios y Rol por Defecto (Paso 5)

#### Paso 1.1: Crear la interfaz `RoleRepository`
Crear el archivo `RoleRepository.java` en el paquete `ec.edu.ups.icc.events.users.repositories`:
```java
package ec.edu.ups.icc.events.users.repositories;

import ec.edu.ups.icc.events.users.entities.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);
}
```

#### Paso 1.2: Modificar `AuthController` para asignar el rol `PARTICIPANT`
Inyectar `RoleRepository` y modificar el endpoint de registro en `AuthController.java`:
```java
// ... Otros imports ...
import ec.edu.ups.icc.events.users.entities.RoleEntity;
import ec.edu.ups.icc.events.users.repositories.RoleRepository;
import java.util.Set;

// Dentro del controlador:
private final RoleRepository roleRepository;

public AuthController(
        JwtService jwtService,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        StringRedisTemplate redisTemplate,
        UserRepository userRepository,
        RoleRepository roleRepository // Inyectar aquí
) {
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
    this.passwordEncoder = passwordEncoder;
    this.redisTemplate = redisTemplate;
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
}

@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    String normalizedEmail = request.getUsername().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmail(normalizedEmail)) {
        return ResponseEntity.badRequest().body("El correo ya está registrado");
    }

    // Buscar el rol de participante en BD
    RoleEntity participantRole = roleRepository.findByName("ROLE_PARTICIPANT")
            .orElseThrow(() -> new IllegalStateException("Rol ROLE_PARTICIPANT no inicializado en base de datos"));

    UserEntity user = new UserEntity();
    user.setName(normalizedEmail);
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setRoles(Set.of(participantRole)); // Asignación del rol

    userRepository.save(user);

    return ResponseEntity.ok("Participante registrado con éxito");
}
```

---

### 2. Implementar Rate Limiting Distribuido y Bloqueo de Cuentas (Paso 9)

#### Paso 2.1: Crear el Filtro de Rate Limiting
Crear el archivo `RateLimitingFilter.java` en `ec.edu.ups.icc.events.core.security`:
```java
package ec.edu.ups.icc.events.core.security;

import ec.edu.ups.icc.events.core.exceptions.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    public RateLimitingFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = resolveIpAddress(request);
        String key;
        long limit;
        Duration duration;

        // Determinar límites según la ruta
        if (path.startsWith("/api/auth/login")) {
            String email = request.getParameter("username"); // Si viene por form
            key = "rate:login:" + ip + ":" + (email != null ? email : "");
            limit = 5;
            duration = Duration.ofMinutes(1);
        } else if (path.startsWith("/api/auth/register")) {
            key = "rate:register:" + ip;
            limit = 3;
            duration = Duration.ofHours(1);
        } else if (path.startsWith("/api/reports/")) {
            String user = getAuthenticatedUser();
            key = "rate:reports:" + user;
            limit = 5;
            duration = Duration.ofMinutes(1);
        } else if (isPublicEndpoint(path)) {
            key = "rate:public:" + ip;
            limit = 60;
            duration = Duration.ofMinutes(1);
        } else {
            String user = getAuthenticatedUser();
            key = "rate:auth:" + user;
            limit = 120;
            duration = Duration.ofMinutes(1);
        }

        Long currentRequests = redisTemplate.opsForValue().increment(key);
        if (currentRequests != null && currentRequests == 1) {
            redisTemplate.expire(key, duration);
        }

        if (currentRequests != null && currentRequests > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(duration.toSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Demasiadas solicitudes. Reintente en unos momentos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                ? auth.getName()
                : "anonymous";
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
```

#### Paso 2.2: Implementar Bloqueo Temporal por Intentos Fallidos en el Login
En `AuthController.java`, modificar el método `login`:
```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    String email = request.getUsername().trim().toLowerCase(Locale.ROOT);
    String blockKey = "blocked-user:" + email;

    // Verificar si el usuario está bloqueado temporalmente
    if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
        return ResponseEntity.status(423)
                .body("Tu cuenta está bloqueada temporalmente por 15 minutos debido a reiterados fallos.");
    }

    try {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Limpiar contador de fallos tras inicio exitoso
        redisTemplate.delete("attempts:" + email);

    } catch (Exception ex) {
        // Incrementar intentos fallidos
        String attemptsKey = "attempts:" + email;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(15));
        }

        if (attempts != null && attempts >= 5) {
            // Bloquear cuenta por 15 minutos
            redisTemplate.opsForValue().set(blockKey, "blocked", 15, TimeUnit.MINUTES);
            redisTemplate.delete(attemptsKey);
            return ResponseEntity.status(423)
                    .body("Has superado el número de intentos permitidos. Cuenta bloqueada por 15 minutos.");
        }

        throw ex; // Relanzar excepción para ser manejada por el GlobalExceptionHandler
    }

    String accessToken = jwtService.generateAccessToken(request.getUsername());
    String refreshToken = jwtService.generateRefreshToken(request.getUsername());

    return ResponseEntity.ok(Map.of("accessToken", accessToken, "refreshToken", refreshToken));
}
```

---

### 3. Habilitar la Configuración de CORS en Spring Security (Paso 8 - Rúbrica)

Modificar `SecurityConfig.java` para asociar la configuración CORS y restringir las rutas adecuadamente:
```java
// ... imports ...
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

// Dentro de SecurityConfig:
private final RateLimitingFilter rateLimitingFilter;

public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        RateLimitingFilter rateLimitingFilter // Inyectar el nuevo filtro
) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.rateLimitingFilter = rateLimitingFilter;
}

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Agregar CORS
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(
                            "/api/auth/**",
                            "/actuator/health",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/v3/api-docs/**"
                    ).permitAll() // ¡Quitar /api/registrations/** de aquí!
                    .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class) // Agregar Rate Limiter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // Leer orígenes desde variable de entorno o fallback seguro
    configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080")); 
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control"));
    configuration.setExposedHeaders(List.of("Content-Disposition"));
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

---

### 4. Configurar Flyway Migrations (Paso 2.3)

#### Paso 4.1: Agregar dependencias en `build.gradle.kts`
Añadir dentro del bloque `dependencies`:
```kotlin
implementation("org.flywaydb:flyway-core")
runtimeOnly("org.flywaydb:flyway-database-postgresql")
```

#### Paso 4.2: Crear el directorio y estructurar el script
1. Crear el directorio `src/main/resources/db/migration/`.
2. Mover o copiar el contenido del archivo `01_schema_and_data.sql` y guardarlo con el nombre `V1__initial_schema_and_data.sql` en esa ruta.

---

### 5. Configurar el Soft-Delete y relacionar la propiedad `deleted` (Paso 2.3)

#### Paso 5.1: Actualizar `BaseEntity.java`
Agregar el atributo `deleted` y sus getters/setters:
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    // ... id, createdAt, updatedAt ...

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
```

#### Paso 5.2: Anotar la entidad `EventEntity.java`
Importar y colocar las anotaciones `@SQLDelete` y `@Where`:
```java
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "events")
@SQLDelete(sql = "UPDATE events SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
public class EventEntity extends BaseEntity {
    // ...
}
```

#### Paso 5.3: Corregir `EventService.java`
El borrado físico (`eventRepository.save(event)` tras cambiar a cancelado) debe transformarse en una invocación de eliminación normal, ya que JPA ejecutará automáticamente la sentencia SQL configurada en `@SQLDelete`:
```java
public void deleteEvent(Long id) {
    EventEntity event = findEventById(id);
    verifyOwnership(event);
    
    // Si tiene participantes inscritos, impedir eliminación física o validar reglas de negocio.
    // Al ejecutar delete, se realiza un soft-delete gracias a las anotaciones.
    eventRepository.delete(event);
}
```

---

### 6. Proteger Swagger en Producción con Basic Auth (Paso 4.3)

Crear un filtro de seguridad adicional específico para Swagger que actúe en producción, o utilizar seguridad a nivel de configuración en el mismo `SecurityFilterChain` usando perfiles activos.
Modificar `SecurityConfig.java` para proteger Swagger en producción:
```java
// Dentro de SecurityConfig:
@Value("${spring.profiles.active:dev}")
private String activeProfile;

@Value("${swagger.username:admin}")
private String swaggerUsername;

@Value("${swagger.password:adminpassword}")
private String swaggerPassword;

// Modificar las reglas en securityFilterChain:
authorize.requestMatchers("/actuator/health").permitAll();

if ("prod".equalsIgnoreCase(activeProfile)) {
    // En producción, exigir basic auth para las rutas de documentación
    authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").authenticated();
} else {
    // En desarrollo, permitir acceso libre
    authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
}
```

---

### 7. Cambiar Relación Eager por Lazy (Paso 2.3)

En `UserEntity.java`, modificar la colección de roles:
```java
// Cambiar EAGER por LAZY
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
private Set<RoleEntity> roles = new HashSet<>();
```

---

### 8. Integrar el Contenedor de la API y Healthchecks en `docker-compose.yml` (Paso 12.2)

Modificar el archivo `docker-compose.yml` en la raíz del proyecto para que orqueste la aplicación localmente:
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
      SWAGGER_USERNAME: admin
      SWAGGER_PASSWORD: adminpassword
      SPRING_PROFILES_ACTIVE: prod

volumes:
  postgres_data:
  redis_data:
```

---

### 9. Crear archivo `render.yaml` para Despliegue Automatizado en Render (Paso 12.3)

Crear el archivo `render.yaml` en la raíz del proyecto para simplificar la creación de la infraestructura en la nube:
```yaml
services:
  # Base de datos relacional PostgreSQL
  - type: database
    name: academic-events-db
    databaseName: academic_events_db
    user: postgres
    plan: free

  # Caché distribuida Redis
  - type: redis
    name: academic-events-redis
    plan: free
    ipAllowList: [] # Solo accesible internamente por otros servicios de Render

  # Servicio Web de la API Spring Boot (Docker)
  - type: web
    name: academic-events-api
    env: docker
    plan: free
    healthCheckPath: /api/actuator/health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DB_HOST
        fromDatabase:
          name: academic-events-db
          property: host
      - key: DB_PORT
        value: 5432
      - key: DB_NAME
        value: academic_events_db
      - key: DB_USER
        fromDatabase:
          name: academic-events-db
          property: user
      - key: DB_PASSWORD
        fromDatabase:
          name: academic-events-db
          property: password
      - key: REDIS_HOST
        fromStack:
          type: redis
          name: academic-events-redis
          property: host
      - key: REDIS_PORT
        fromStack:
          type: redis
          name: academic-events-redis
          property: port
      - key: JWT_SECRET
        generateValue: true # Genera automáticamente una firma segura
      - key: ALLOWED_ORIGINS
        value: "*"
      - key: SWAGGER_USERNAME
        value: evaluador
      - key: SWAGGER_PASSWORD
        value: evaluador123
      - key: JAVA_TOOL_OPTIONS
        value: "-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil"
```
