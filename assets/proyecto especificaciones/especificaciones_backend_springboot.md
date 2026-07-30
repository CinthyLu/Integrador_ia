# Guía de Especificaciones y Buenas Prácticas para Backend con Spring Boot

Esta guía consolida los estándares y las mejores prácticas de arquitectura, diseño, seguridad y despliegue para el desarrollo de proyectos backend con Spring Boot, basados en las directrices de este repositorio.

---

## Índice

1. [Estructura del Proyecto y Organización Modular](#1-estructura-del-proyecto-y-organización-modular)
2. [Conectividad y Configuración (YAML y Perfiles)](#2-conectividad-y-configuración-yaml-y-perfiles)
3. [Entidades JPA y Superclase de Auditoría (BaseEntity)](#3-entidades-jpa-y-superclase-de-auditoría-baseentity)
4. [Modelos, DTOs y Validación de Entrada](#4-modelos-dtos-y-validación-de-entrada)
5. [Manejo Global de Errores y Excepciones](#5-manejo-global-de-errores-y-excepciones)
6. [Relaciones entre Entidades y Optimización de Consultas](#6-relaciones-entre-entidades-y-optimización-de-consultas)
7. [Paginación y Ordenamiento Uniforme](#7-paginación-y-ordenamiento-uniforme)
8. [Seguridad Completa (JWT, Roles y Ownership)](#8-seguridad-completa-jwt-roles-y-ownership)
9. [Documentación Interactiva con OpenAPI / Swagger](#9-documentación-interactiva-con-openapi--swagger)
10. [Entornos de Despliegue y Contenedores (Docker + Nginx)](#10-entornos-de-despliegue-y-contenedores-docker--nginx)

---

## 1. Estructura del Proyecto y Organización Modular

Spring Boot utiliza `@ComponentScan` a partir del paquete raíz (donde se ubica la clase principal `@SpringBootApplication`). Es fundamental estructurar los paquetes a partir de este nivel para asegurar la detección automática de componentes.

### Estructura Modular por Dominios
Para aplicaciones empresariales y escalables, se descarta la organización por capas horizontales (todos los controladores juntos, todos los servicios juntos) y se adopta una **organización basada en dominios o recursos**. Las utilidades comunes y transversales se agrupan en un paquete especial `core/`.

#### Representación de Estructura de Paquetes
```txt
ec.edu.ups.icc.fundamentos01/
│
├── Fundamentos01Application.java   # Clase de inicio y activador de ComponentScan
│
├── core/                           # Capas y componentes transversales
│   ├── config/                     # Configuraciones globales (CORS, OpenAPI, etc.)
│   ├── entities/                   # Superclases persistentes (BaseEntity)
│   ├── exceptions/                 # Jerarquía global de excepciones y handler
│   └── utils/                      # Clases de utilería reutilizables
│
├── auth/                           # Módulo específico para autenticación JWT
│   ├── controllers/
│   ├── dtos/
│   └── services/
│
├── security/                       # Configuración y filtros de Spring Security
│   ├── config/
│   ├── filters/
│   ├── services/
│   └── utils/
│
├── users/                          # Dominio de Usuarios
│   ├── controllers/
│   ├── dtos/
│   ├── entities/
│   ├── mappers/
│   ├── models/
│   └── repositories/
│
└── products/                       # Dominio de Productos
    ├── controllers/
    ├── dtos/
    ├── entities/
    ├── mappers/
    ├── models/
    └── repositories/
```

Cada dominio contiene sus subpaquetes especializados:
* `controllers/`: Punto de entrada HTTP REST.
* `services/`: Interfaces e implementaciones con la lógica de negocio y transaccionalidad.
* `entities/`: Clases anotadas con JPA que representan tablas físicas en PostgreSQL.
* `models/`: Clases de dominio de negocio puras (sin acoplamiento a librerías de persistencia ni API).
* `dtos/`: Clases de transporte de datos con validaciones Jakarta.
* `mappers/`: Conversión estática bidireccional entre DTOs, Modelos y Entidades.

---

## 2. Conectividad y Configuración (YAML y Perfiles)

Se prefiere el formato YAML (`application.yml`) sobre `.properties` por su claridad visual y jerárquica. La base de datos estándar del curso es **PostgreSQL**.

### Configuración Base (`src/main/resources/application.yml`)
```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  profiles:
    active: dev # Perfil activo por defecto
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:devdb}
    username: ${DB_USER:ups}
    password: ${DB_PASSWORD:ups123}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false # Cambiar a true para depurar queries SQL en consola

jwt:
  secret: ${JWT_SECRET:mi-clave-super-secreta-y-larga-para-firmar-los-tokens-jwt-12345}
  expiration: 86400000 # 24 horas en milisegundos
```

> [!TIP]
> El uso de `${VALOR_VAR:default}` permite que la aplicación arranque localmente con valores por defecto pero sea configurable mediante variables de entorno en entornos Docker o de producción sin modificar el código.

---

## 3. Entidades JPA y Superclase de Auditoría (BaseEntity)

Para evitar duplicidad de código en campos redundantes como IDs y datos de auditoría temporal, se implementa una clase base utilizando la anotación `@MappedSuperclass`.

### Entidad Base Transversal (`core/entities/BaseEntity.java`)
```java
package ec.edu.ups.icc.fundamentos01.core.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    @PrePersist
    protected void onCreate() {
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters, Setters y Constructores
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
```

### Entidad de Negocio (`users/entities/UserEntity.java`)
```java
package ec.edu.ups.icc.fundamentos01.users.entities;

import ec.edu.ups.icc.fundamentos01.core.entities.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Getters y Setters de atributos específicos...
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
```

> [!IMPORTANT]
> **Estrategia de Eliminación Lógica**: No se deben utilizar eliminaciones físicas (`DELETE FROM ...`) para datos de negocio. Se debe actualizar el estado `deleted = true` y realizar las consultas filtrando únicamente los registros activos (`deleted = false`).

---

## 4. Modelos, DTOs y Validación de Entrada

El acoplamiento directo entre la base de datos y la interfaz del cliente es una mala práctica. Se debe mantener el aislamiento de responsabilidades en 3 niveles de objetos de datos:
1. **Entidad JPA (`Entity`)**: Representación de persistencia.
2. **Modelo de Dominio (`Model`)**: Representación conceptual para lógica de negocio (sin anotaciones de persistencia ni validación REST).
3. **DTO (`Data Transfer Object`)**: Objetos específicos para recibir (`CreateDto`, `UpdateDto`) o enviar datos (`ResponseDto`).

```
Entidad JPA (DB) <== [Mapper] ==> Modelo de Dominio <== [Mapper] ==> DTO (HTTP Client)
```

### Ejemplo de DTO de Entrada con Jakarta Validation
```java
package ec.edu.ups.icc.fundamentos01.users.dtos;

import jakarta.validation.constraints.*;

public class CreateUserDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Debe ingresar un formato de correo válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // Getters y Setters
}
```

### Activación de validación en controladores REST
En el controlador, la anotación `@Valid` es obligatoria en los cuerpos de las peticiones para interceptar y validar los DTOs antes de delegar la ejecución a los servicios:
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public UserResponseDto create(@Valid @RequestBody CreateUserDto dto) {
    return userService.create(dto);
}
```

---

## 5. Manejo Global de Errores y Excepciones

Un backend robusto no expone trazas de error de sistema (`StackTraces`) ni maneja capturas en cada controlador con bloques `try/catch`. Se utiliza un manejador centralizado con `@RestControllerAdvice` y una jerarquía de excepciones semánticas basadas en el negocio.

### Estructura de Excepciones en `core/exceptions/`
```
core/exceptions/
├── base/
│   └── ApplicationException.java       # Excepción raíz abstracta
├── domain/
│   ├── NotFoundException.java          # Retorna 404
│   ├── ConflictException.java          # Retorna 409
│   └── BadRequestException.java        # Retorna 400
├── handler/
│   └── GlobalExceptionHandler.java     # Controlador global de excepciones
└── response/
    └── ErrorResponse.java              # Estructura JSON estándar de respuesta de error
```

### Excepción Base (`core/exceptions/base/ApplicationException.java`)
```java
package ec.edu.ups.icc.fundamentos01.core.exceptions.base;

import org.springframework.http.HttpStatus;

public abstract class ApplicationException extends RuntimeException {
    private final HttpStatus status;

    protected ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
```

### Excepción de Dominio Frecuente (`core/exceptions/domain/NotFoundException.java`)
```java
package ec.edu.ups.icc.fundamentos01.core.exceptions.domain;

import ec.edu.ups.icc.fundamentos01.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class NotFoundException extends ApplicationException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
```

### Estructura de Respuesta de Error Unificada (`core/exceptions/response/ErrorResponse.java`)
```java
package ec.edu.ups.icc.fundamentos01.core.exceptions.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Map<String, String> details; // Para errores de campos (ej: DTO)

    public ErrorResponse(HttpStatus status, String message, String path, Map<String, String> details) {
        this.timestamp = LocalDateTime.now();
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = message;
        this.path = path;
        this.details = details;
    }

    public ErrorResponse(HttpStatus status, String message, String path) {
        this(status, message, path, null);
    }

    // Getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Map<String, String> getDetails() { return details; }
}
```

### Handler Centralizado (`core/exceptions/handler/GlobalExceptionHandler.java`)
```java
package ec.edu.ups.icc.fundamentos01.core.exceptions.handler;

import ec.edu.ups.icc.fundamentos01.core.exceptions.base.ApplicationException;
import ec.edu.ups.icc.fundamentos01.core.exceptions.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Manejo de excepciones de negocio (NotFound, Conflict, etc.)
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            ApplicationException ex, HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(ex.getStatus(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    // 2. Manejo de fallos en validaciones de DTOs (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request.getRequestURI(), errors);
        return ResponseEntity.badRequest().body(response);
    }

    // 3. Manejo genérico para excepciones inesperadas
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        // En producción registrar el stacktrace internamente (ej: logger.error(ex))
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

---

## 6. Relaciones entre Entidades y Optimización de Consultas

El modelado relacional con JPA requiere un cuidado especial en la carga de datos para evitar degradar el rendimiento del servidor mediante consultas redundantes.

### Buenas Prácticas en Relaciones
* **Carga Perezosa (FetchType.LAZY)**: Es obligatorio configurar `FetchType.LAZY` en relaciones `@ManyToOne`, `@ManyToMany` y `@OneToMany`. Por defecto, `@ManyToOne` utiliza la estrategia ansiosa (`EAGER`), lo que causa la ejecución automática de JOINS e impacta fuertemente el rendimiento.
* **Evitar el problema de Consultas N+1**: Al listar colecciones, si la relación es LAZY, se disparará una consulta SQL por cada entidad relacionada consultada. Esto se soluciona usando sentencias `JOIN FETCH` personalizadas en los métodos del repositorio.
* **Uso de Set en lugar de List**: Para colecciones `@ManyToMany` u `@OneToMany`, se prefiere utilizar `Set<T>` en lugar de `List<T>`. Los conjuntos evitan la inserción de registros duplicados en las tablas intermedias de mapeo y son eficientes en memoria.

### Ejemplo de Configuración N:N en Entidades
```java
// Entidad Propietaria (ProductEntity)
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "product_categories",
    joinColumns = @JoinColumn(name = "product_id"),
    inverseJoinColumns = @JoinColumn(name = "category_id")
)
private Set<CategoryEntity> categories = new HashSet<>();

// Entidad Inversa (CategoryEntity)
@ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
private Set<ProductEntity> products = new HashSet<>();
```

---

## 7. Paginación y Ordenamiento Uniforme

Las respuestas REST que devuelven listas ilimitadas de registros deben ser evitadas en APIs de producción. En su lugar, se exponen endpoints paginados utilizando los mecanismos incorporados en Spring Data JPA (`Page`, `Slice` y `Pageable`).

### Normalización de parámetros en la petición
Para evitar inyección de sentencias SQL erróneas u ordenamientos por campos inexistentes (ej: ordenar por un atributo no preparado en BD), la lógica del servicio debe realizar una validación de campos permitidos en base a una lista blanca (whitelist).

```java
private Pageable createPageable(PaginationDto pagination) {
    String sortBy = normalizeSortBy(pagination.getSortBy());
    Sort.Direction direction = normalizeDirection(pagination.getDirection());
    return PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by(direction, sortBy));
}

private String normalizeSortBy(String sortBy) {
    if (sortBy == null || sortBy.isBlank()) {
        return "id";
    }
    Set<String> allowedFields = Set.of("id", "name", "price", "createdAt");
    if (!allowedFields.contains(sortBy)) {
        throw new BadRequestException("Campo de ordenamiento no permitido: " + sortBy);
    }
    return sortBy;
}
```

---

## 8. Seguridad Completa (JWT, Roles y Ownership)

El flujo de seguridad de la aplicación consta de tres niveles fundamentales evaluados de manera secuencial:

```
Petición HTTP ──> [1. Autenticación JWT] ──> [2. Autorización por Roles] ──> [3. Validación de Propiedad]
                      ¿Quién eres?              ¿Qué rol tienes?                ¿Este recurso es tuyo?
```

### 1. Autenticación con JWT (`security/jwt/JwtAuthenticationFilter.java`)
Se implementa extendiendo `OncePerRequestFilter`. Valida la cabecera `Authorization: Bearer <token>`, extrae los datos del usuario utilizando un servicio que implementa `UserDetailsService`, y almacena el contexto de autenticación de Spring (`SecurityContextHolder`).

### 2. Autorización por Roles (`@PreAuthorize`)
Permite restringir accesos a nivel de controladores mediante Spring Security SpEL.
```java
// Habilitación en la clase de configuración de seguridad
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // <--- OBLIGATORIO
public class SecurityConfig { ... }

// Uso en controladores
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')") // Solo usuarios con ROLE_ADMIN pueden acceder
public void delete(@PathVariable Long id) { ... }
```

### 3. Validación de Propiedad de Recursos (Ownership)
> [!IMPORTANT]
> **Lugar Correcto de Validación**: La validación de propiedad (ownership) **no** debe realizarse en las anotaciones `@PreAuthorize` del controlador con expresiones complejas. Esto genera acoplamiento y consultas duplicadas a la base de datos.
> La validación se realiza en la **capa de servicio**, después de cargar el recurso de la base de datos por primera vez.

#### Implementación del método de validación en el Servicio
```java
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProductResponseDto update(Long id, UpdateProductDto dto, UserDetailsImpl currentUser) {
        // 1. Obtener recurso o lanzar 404
        ProductEntity entity = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // 2. Validar propiedad
        validateOwnership(entity, currentUser);

        // 3. Ejecutar actualización lógica
        entity.setName(dto.getName());
        entity.setPrice(dto.getPrice());
        // ...
        return ProductMapper.toResponse(productRepository.save(entity));
    }

    private void validateOwnership(ProductEntity product, UserDetailsImpl currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        
        // Bypassear validación si el usuario es Administrador
        if (hasRole(currentUser, "ROLE_ADMIN")) {
            return;
        }

        if (product.getOwner() == null || !product.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No posees permisos para modificar o eliminar este recurso");
        }
    }

    private boolean hasRole(UserDetailsImpl user, String role) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(role));
    }
}
```

---

## 9. Documentación Interactiva con OpenAPI / Swagger

La documentación automática y su visualización web interactiva se implementa mediante la integración de la dependencia `springdoc-openapi-starter-webmvc-ui`.

### Dependencia de Gradle (`build.gradle.kts`)
```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
```

### Clase de Configuración de Swagger (`security/config/OpenApiConfig.java`)
```java
package ec.edu.ups.icc.fundamentos01.security.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        Info info = new Info()
                .title("API REST de Backend - Programación y Plataformas Web")
                .version("1.0.0")
                .description("Documentación interactiva de endpoints y esquemas de seguridad.");

        Server localServer = new Server()
                .url("/api")
                .description("Servidor Local (Context Path)");

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Ingrese el JWT generado al hacer login.");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme));
    }
}
```

> [!WARNING]
> Recuerde configurar en el archivo `SecurityConfig.java` el acceso público para los endpoints de Swagger, impidiendo que el filtro JWT los intercepte y devuelva respuestas `401 Unauthorized`:
> ```java
> .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
> ```

---

## 10. Entornos de Despliegue y Contenedores (Docker + Nginx)

Para asegurar la portabilidad y robustez del proyecto en producción, se configuran archivos de despliegue mediante contenedores que separan la API del motor de base de datos PostgreSQL.

### Archivo de Dockerización de la App (`Dockerfile`)
```dockerfile
# Multi-stage build para optimizar peso del contenedor
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Entornos de Red Integrados (`docker-compose.yml`)
```yaml
version: '3.8'

services:
  postgres-db:
    image: postgres:15-alpine
    container_name: postgres_db_prod
    environment:
      POSTGRES_DB: proddb
      POSTGRES_USER: ups
      POSTGRES_PASSWORD: secure_ups_pwd
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  backend-api:
    build: .
    container_name: spring_backend_api
    depends_on:
      - postgres-db
    environment:
      DB_HOST: postgres-db
      DB_PORT: 5432
      DB_NAME: proddb
      DB_USER: ups
      DB_PASSWORD: secure_ups_pwd
      JWT_SECRET: clave-produccion-extremadamente-segura-y-larga
    ports:
      - "8080:8080"

volumes:
  pgdata:
```

### Configuración del Servidor Web Reverso Nginx
Para despliegues reales, la API no se expone directamente a internet. Se utiliza Nginx como proxy inverso y terminador SSL:
```nginx
server {
    listen 80;
    server_name api.mi-dominio.com;

    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
