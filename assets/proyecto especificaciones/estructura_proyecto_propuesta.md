# Propuesta de Estructura de Directorios y Archivos del Proyecto

Esta es la estructura modular recomendada para la API de Gestión de Eventos Académicos, alineada estrictamente con las buenas prácticas del curso (interfaces de servicio, DTOs específicos por responsabilidad, mappers independientes y jerarquía de excepciones).

---

## 📁 Árbol de Directorios y Archivos Propuesto

```txt
academic-events-api/
├── 00_create_database.sql
├── 01_schema_and_data.sql
├── Dockerfile
├── docker-compose.yml
├── render.yaml
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── java/
    │   │   └── ec/edu/ups/icc/events/
    │   │       ├── AcademicEventsApiApplication.java
    │   │       │
    │   │       ├── core/                      # Infraestructura y Configuración Transversal
    │   │       │   ├── config/
    │   │       │   │   └── SwaggerConfig.java
    │   │       │   ├── security/
    │   │       │   │   ├── SecurityConfig.java
    │   │       │   │   ├── SecurityBeansConfig.java
    │   │       │   │   ├── JwtService.java
    │   │       │   │   ├── JwtAuthenticationFilter.java
    │   │       │   │   └── RateLimitingFilter.java
    │   │       │   ├── exceptions/
    │   │       │   │   ├── base/
    │   │       │   │   │   └── ApplicationException.java
    │   │       │   │   ├── domain/
    │   │       │   │   │   ├── ResourceNotFoundException.java
    │   │       │   │   │   ├── BadRequestException.java
    │   │       │   │   │   ├── UnauthorizedException.java
    │   │       │   │   │   ├── ForbiddenException.java
    │   │       │   │   │   ├── BusinessRuleException.java
    │   │       │   │   │   └── RateLimitExceededException.java
    │   │       │   │   ├── handler/
    │   │       │   │   │   └── GlobalExceptionHandler.java
    │   │       │   │   └── response/
    │   │       │   │       └── ApiErrorResponse.java
    │   │       │   └── entities/
    │   │       │       └── BaseEntity.java
    │   │       │
    │   │       ├── auth/                      # Módulo de Autenticación
    │   │       │   ├── controllers/
    │   │       │   │   └── AuthController.java
    │   │       │   ├── services/
    │   │       │   │   ├── AuthService.java   # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── AuthServiceImpl.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── RegisterRequestDto.java
    │   │       │   │   ├── LoginRequestDto.java
    │   │       │   │   ├── AuthResponseDto.java
    │   │       │   │   └── TokenRefreshRequestDto.java
    │   │       │   └── mappers/
    │   │       │       └── AuthMapper.java
    │   │       │
    │   │       ├── users/                     # Módulo de Usuarios
    │   │       │   ├── entities/
    │   │       │   │   ├── UserEntity.java
    │   │       │   │   └── RoleEntity.java
    │   │       │   ├── repositories/
    │   │       │   │   ├── UserRepository.java
    │   │       │   │   └── RoleRepository.java
    │   │       │   ├── services/
    │   │       │   │   ├── UserService.java   # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── UserServiceImpl.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── CreateUserDto.java
    │   │       │   │   ├── UpdateUserDto.java
    │   │       │   │   └── UserResponseDto.java
    │   │       │   └── mappers/
    │   │       │       └── UserMapper.java
    │   │       │
    │   │       ├── categories/                # Módulo de Categorías
    │   │       │   ├── controllers/
    │   │       │   │   └── CategoryController.java
    │   │       │   ├── services/
    │   │       │   │   ├── CategoryService.java # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── CategoryServiceImpl.java
    │   │       │   ├── repositories/
    │   │       │   │   └── CategoryRepository.java
    │   │       │   ├── entities/
    │   │       │   │   └── CategoryEntity.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── CreateCategoryDto.java
    │   │       │   │   ├── UpdateCategoryDto.java
    │   │       │   │   └── CategoryResponseDto.java
    │   │       │   └── mappers/
    │   │       │       └── CategoryMapper.java
    │   │       │
    │   │       ├── events/                    # Módulo de Eventos
    │   │       │   ├── controllers/
    │   │       │   │   └── EventController.java
    │   │       │   ├── services/
    │   │       │   │   ├── EventService.java # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── EventServiceImpl.java
    │   │       │   ├── repositories/
    │   │       │   │   └── EventRepository.java
    │   │       │   ├── entities/
    │   │       │   │   ├── EventEntity.java
    │   │       │   │   ├── EventModality.java
    │   │       │   │   └── EventStatus.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── CreateEventDto.java
    │   │       │   │   ├── UpdateEventDto.java
    │   │       │   │   └── EventResponseDto.java
    │   │       │   └── mappers/
    │   │       │       └── EventMapper.java
    │   │       │
    │   │       ├── sessions/                  # Módulo de Sesiones
    │   │       │   ├── controllers/
    │   │       │   │   └── SessionController.java
    │   │       │   ├── services/
    │   │       │   │   ├── SessionService.java # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── SessionServiceImpl.java
    │   │       │   ├── repositories/
    │   │       │   │   └── SessionRepository.java
    │   │       │   ├── entities/
    │   │       │   │   └── SessionEntity.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── CreateSessionDto.java
    │   │       │   │   ├── UpdateSessionDto.java
    │   │       │   │   └── SessionResponseDto.java
    │   │       │   └── mappers/
    │   │       │       └── SessionMapper.java
    │   │       │
    │   │       ├── registrations/             # Módulo de Inscripciones
    │   │       │   ├── controllers/
    │   │       │   │   └── RegistrationController.java
    │   │       │   ├── services/
    │   │       │   │   ├── RegistrationService.java # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── RegistrationServiceImpl.java
    │   │       │   ├── repositories/
    │   │       │   │   └── RegistrationRepository.java
    │   │       │   ├── entities/
    │   │       │   │   ├── RegistrationEntity.java
    │   │       │   │   └── RegistrationStatus.java
    │   │       │   ├── dtos/
    │   │       │   │   ├── CreateRegistrationDto.java
    │   │       │   │   ├── UpdateRegistrationDto.java
    │   │       │   │   └── RegistrationResponseDto.java
    │   │       │   └── mappers/
    │   │       │       └── RegistrationMapper.java
    │   │       │
    │   │       ├── audit/                     # Módulo de Auditoría y Logs
    │   │       │   ├── annotations/
    │   │       │   │   └── Auditable.java
    │   │       │   ├── aspects/
    │   │       │   │   └── AuditAspect.java
    │   │       │   ├── services/
    │   │       │   │   ├── AuditLogService.java # Interfaz
    │   │       │   │   └── impl/
    │   │       │   │       └── AuditLogServiceImpl.java
    │   │       │   ├── repositories/
    │   │       │   │   └── AuditLogRepository.java
    │   │       │   └── entities/
    │   │       │       └── AuditLogEntity.java
    │   │       │
    │   │       └── reports/                   # Módulo de Reportes Descargables
    │   │           ├── controllers/
    │   │           │   └── ReportController.java
    │   │           ├── services/
    │   │           │   ├── ExcelReportService.java # Interfaz
    │   │           │   ├── PdfReportService.java   # Interfaz
    │   │           │   ├── ReportAccessService.java # Interfaz
    │   │           │   └── impl/
    │   │           │       ├── ExcelReportServiceImpl.java
    │   │           │       ├── PdfReportServiceImpl.java
    │   │           │       └── ReportAccessServiceImpl.java
    │   │           └── utils/
    │   │               └── ReportDateTimeUtils.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── db/
    │           └── migration/
    │               └── V1__initial_schema_and_data.sql # Flyway inicial
    │
    └── test/
        └── java/
            └── ec/edu/ups/icc/events/
                ├── AcademicEventsApiApplicationTests.java
                ├── core/
                │   └── exceptions/
                │       └── GlobalExceptionHandlerTest.java
                ├── security/
                │   └── config/
                │       └── SecurityBeansConfigTest.java
                └── registrations/
                    └── services/
                        └── RegistrationServiceTest.java # Pruebas de concurrencia e inscripción
```

---

## 📝 Descripción y Contenido de cada Carpeta

### 1. `core/` (Estructura Transversal de la Aplicación)
* **`config/`**: Contiene configuraciones genéricas, como la del Swagger/OpenAPI (`SwaggerConfig.java`) para habilitar el esquema Bearer JWT.
* **`security/`**: Concentra la configuración de Spring Security (`SecurityConfig.java`), los filtros JWT y de Rate Limiting, y los beans relacionados como el PasswordEncoder.
* **`exceptions/`**:
  * **`base/`**: Contiene la excepción raíz de la aplicación (`ApplicationException.java`) de la cual heredan todas las demás.
  * **`domain/`**: Excepciones de negocio que expresan el tipo de error (NotFound, BadRequest, etc.) sin manejar respuestas HTTP directamente.
  * **`handler/`**: El `@RestControllerAdvice` (`GlobalExceptionHandler.java`) para capturar las excepciones y construir respuestas uniformes.
  * **`response/`**: Modelo estándar de error (`ApiErrorResponse.java`).
* **`entities/`**: Contiene `BaseEntity.java` con las propiedades comunes de auditoría JPA (`createdAt`, `updatedAt`, `deleted`).

### 2. Módulos por Dominio (`auth/`, `users/`, `categories/`, `events/`, `sessions/`, `registrations/`, `audit/`, `reports/`)
Cada módulo se encarga de un recurso o funcionalidad específica de negocio y separa estrictamente sus responsabilidades:
* **`controllers/`**: Recibe las peticiones HTTP, valida la entrada mediante `@Valid` y devuelve DTOs de salida envueltos en `ResponseEntity`.
* **`services/`**: 
  * Contiene la **interfaz** del servicio, la cual expone los métodos que forman el contrato de negocio.
  * Contiene el subpaquete `impl/` con la implementación concreta de dicha interfaz, la cual se encarga de la lógica y la comunicación con repositorios y mappers.
* **`repositories/`**: Interfaces que extienden de `JpaRepository` para la persistencia.
* **`entities/`**: Las entidades JPA mapeadas directamente a las tablas de la base de datos PostgreSQL.
* **`dtos/`**: DTOs especializados para cada acción. Por ejemplo, `CreateEventDto` para el JSON de entrada y `EventResponseDto` para la respuesta de la API.
* **`mappers/`**: Clases con métodos estáticos de mapeo (ej. `EventMapper.java`) para convertir entre DTOs y Entidades, manteniendo la lógica limpia en controladores y servicios.
