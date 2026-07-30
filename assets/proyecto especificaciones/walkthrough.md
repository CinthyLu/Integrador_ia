# Walkthrough: Reorganización Estructural e Implementación de Buenas Prácticas

Hemos completado con éxito la reorganización estructural y la implementación de las prácticas recomendadas de Spring Boot y seguridad en tu proyecto de trabajo `c:\Users\MSI\Desktop\PPW\Backend\SPRINGBOOT\academic-events-api`.

---

## 🛠️ Cambios Realizados y Mejoras de Arquitectura

### 1. Reorganización Estructural Modular
* **Interfaces y Servicios:** Separamos todos los servicios de negocio (`EventService`, `CategoryService`, `SessionService`, `RegistrationService`, `AuditLogService`, y servicios de reportes) en contratos a través de **Interfaces** (ej. `EventService.java`) y movimos las implementaciones concretas a clases en subpaquetes `impl` (ej. `EventServiceImpl.java`).
* **Mapeadores Aislados (`mappers`):** Creamos mappers dedicados para todos los dominios (ej. `EventMapper.java`, `CategoryMapper.java`, `SessionMapper.java`, `RegistrationMapper.java`, `AuthMapper.java`). Esto elimina el código de mapeo manual de los servicios y desacopla la transferencia de datos.
* **DTOs de Acción Específicos:** Reemplazamos los DTOs únicos y genéricos por DTOs específicos según la acción requerida:
  * **Creación:** `CreateEventDto`, `CreateCategoryDto`, `CreateSessionDto`, `RegisterRequestDto`, `LoginRequestDto` con validaciones de entrada (`@NotBlank`, `@Size`, `@Email`, `@Min`).
  * **Actualización:** `UpdateEventDto`.
  * **Respuestas:** `EventResponseDto`, `CategoryResponseDto`, `SessionResponseDto`, `RegistrationResponseDto`, `AuthResponseDto` para entregar campos seguros al cliente.
* **Controladores con Validación:** Agregamos la anotación `@Valid` en los cuerpos de las peticiones (`@RequestBody`) de todos los controladores para interceptar de forma automática campos inválidos y responder con código `400 Bad Request`.

### 2. Jerarquía de Excepciones
* **Clase Base Abstracta:** Implementamos `ApplicationException` que asocia un estado `HttpStatus` con cada tipo de error.
* **Excepciones de Dominio:** Modificamos las excepciones de negocio (`ResourceNotFoundException`, `BadRequestException`, etc.) para heredar de la base abstracta.
* **Handler Simplificado:** Limpiamos `GlobalExceptionHandler.java` para capturar `ApplicationException` de manera genérica y mapear dinámicamente los códigos de error HTTP del negocio.

### 3. Seguridad, Rate Limiting y CORS
* **Rate Limiting Distribuido con Redis:** Diseñamos un servlet filter `RateLimitingFilter` que utiliza contadores atómicos en Redis para limitar peticiones según la IP o el usuario autenticado:
  * Login: Máximo 5 peticiones por minuto.
  * Registro: Máximo 3 peticiones por hora.
  * Generación de reportes: Máximo 5 peticiones por minuto.
  * Endpoints públicos: Máximo 60 peticiones por minuto.
  * Endpoints autenticados: Máximo 120 peticiones por minuto.
* **Bloqueo Temporal de Cuenta y Baneo en Redis:** Al alcanzar 5 intentos de inicio de sesión fallidos, se bloquea el acceso temporalmente mediante una clave `blocked-user:` en Redis con TTL de 15 minutos, y adicionalmente se bloquea el registro del usuario en base de datos (`accountLocked = true`).
* **Protección de Swagger en Producción:** Agregamos soporte para Basic Authentication (usuario/contraseña por variables de entorno) en el `SecurityConfig.java` únicamente cuando el perfil activo es `prod`, protegiendo `/swagger-ui/**` y `/v3/api-docs/**`.
* **Configuración CORS:** Implementamos `CorsConfigurationSource` dinámico a través de propiedades o variables de entorno para evitar el uso de comodines `*` en producción.
* **Asignación del Rol por Defecto:** Corregimos el bug de registro en `AuthServiceImpl.java`, consultando el `RoleRepository` para asignar el rol `ROLE_PARTICIPANT` por defecto al nuevo usuario.

### 4. Control de Concurrencia y Soft-Delete en Base de Datos
* **Bloqueo Pesimista en Inscripciones:** Agregamos una consulta con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `EventRepository.java` para la reserva de cupos, protegiendo de sobreventa y garantizando que múltiples hilos concurrentes no dejen los cupos disponibles en negativo.
* **Borrado Lógico (Soft-Delete):** Agregamos la columna lógica `deleted` a `BaseEntity.java` y anotamos `EventEntity.java` con `@SQLDelete` and `@Where(clause = "deleted = false")`. La llamada a `delete` de JPA se traduce automáticamente a una actualización del estado y se excluyen los eventos eliminados de consultas generales.
* **Migraciones con Flyway:** Configuramos Flyway en `build.gradle.kts` y movimos el esquema inicial con los datos semilla y las columnas de borrado lógico a `src/main/resources/db/migration/V1__initial_schema_and_data.sql`.

### 5. Configuración DevOps y Despliegue en la Nube
* **Docker Compose:** Añadimos el servicio `app` que compila el `Dockerfile` local y dependencias ordenadas del healthcheck de la base de datos PostgreSQL y Redis.
* **Render Blueprint:** Creamos `render.yaml` con la definición de la base de datos, caché de Redis y el backend utilizando variables de entorno de producción.

---

## 🧪 Validación y Pruebas Exitosas
* **Compilación:** Ejecutamos `./gradlew clean build -x test` y compiló de forma exitosa sin errores de dependencias o rutas.
* **Pruebas Unitarias:** Ejecutamos `./gradlew test` y **las 22 pruebas unitarias pasaron con éxito**, incluyendo las modificaciones al nuevo flujo de DTOs y mapeadores en `RegistrationServiceTest.java`.
