# Paso 7: Módulo de Inscripciones y Control Transaccional de Cupos

Este documento detalla técnicamente el diseño, las reglas de negocio, la arquitectura y las pruebas del **Módulo de Inscripciones** implementado en la API REST de Eventos Académicos.

---

## 📋 1. Reglas de Negocio Implementadas

Para el registro de participantes en los eventos académicos publicados, se programaron las siguientes validaciones en la capa de servicios:

1. **Estado del Evento (`PUBLISHED`):** Solo se permiten inscripciones a eventos cuyo estado sea estrictamente `PUBLISHED`. Si está en `DRAFT`, `CANCELLED` o `FINISHED`, se rechaza lanzando un `BusinessRuleException`.
2. **Fecha de Inicio:** No se permiten inscripciones a eventos cuya fecha de inicio (`startDate`) ya haya pasado en el servidor.
3. **Unicidad y Reactivación (Índice Único):** La base de datos tiene una restricción única en la tabla `registrations` para la pareja `(user_id, event_id)`. Para evitar errores cuando un usuario vuelve a intentar inscribirse en un evento que canceló previamente:
   - Si no existe un registro previo: Se inserta uno nuevo con estado `CONFIRMED`.
   - Si el registro existe con estado `CANCELLED`: Se actualiza/reactiva cambiando su estado a `CONFIRMED` y actualizando la fecha de inscripción al momento actual.
   - Si el registro existe con estado `CONFIRMED`: Se arroja una excepción indicando que ya está registrado.
4. **Verificación y Reserva de Cupos:** 
   - Se valida que `available_seats > 0`.
   - Si hay cupo, se disminuye la cantidad de cupos disponibles en `1` (`event.setAvailableSeats(event.getAvailableSeats() - 1)`).
5. **Transaccionalidad Atómica:** Toda la lógica de validación, decremento de asientos y guardado en base de datos se realiza bajo la anotación `@Transactional` de Spring, asegurando que si ocurre algún fallo, se haga un rollback completo.
6. **Cancelación de Inscripciones:**
   - Permite cambiar el estado de la inscripción a `CANCELLED`.
   - Devuelve el cupo al evento sumando `+1` a `available_seats` de forma transaccional.

---

## 🏗️ 2. Estructura de Componentes Creados

```txt
academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/
├── controllers/
│   └── RegistrationController.java    # Controlador REST para endpoints de inscripciones
├── dtos/
│   └── RegistrationDTO.java           # DTO para las respuestas formateadas
├── entities/
│   ├── RegistrationEntity.java        # Entidad JPA para la tabla 'registrations' (existente)
│   └── RegistrationStatus.java        # Enum para 'CONFIRMED' y 'CANCELLED' (existente)
├── repositories/
│   └── RegistrationRepository.java    # Consultas JPA específicas
└── services/
    └── RegistrationService.java       # Lógica transaccional de cupos e inscripciones
```

---

## 💻 3. Detalle del Código Implementado

### 3.1 Consultas Específicas ([RegistrationRepository.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/repositories/RegistrationRepository.java))
```java
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {
    boolean existsByEventId(Long eventId);
    Optional<RegistrationEntity> findByUserIdAndEventId(Long userId, Long eventId);
    Page<RegistrationEntity> findByUserId(Long userId, Pageable pageable);
    Page<RegistrationEntity> findByEventOrganizerId(Long organizerId, Pageable pageable);
}
```

### 3.2 Lógica Crítica de Inscripción ([RegistrationService.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/services/RegistrationService.java))
```java
@Transactional
public RegistrationEntity registerUserToEvent(Long eventId, CustomUserDetails currentUser) {
    EventEntity event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));

    if (event.getStatus() != EventStatus.PUBLISHED) {
        throw new BusinessRuleException("No se permiten inscripciones en eventos que no estén publicados");
    }

    if (LocalDateTime.now().isAfter(event.getStartDate())) {
        throw new BusinessRuleException("No se permiten inscripciones en eventos que ya hayan iniciado o finalizado");
    }

    UserEntity user = userRepository.findById(currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

    Optional<RegistrationEntity> existingOpt = registrationRepository.findByUserIdAndEventId(user.getId(), event.getId());

    if (existingOpt.isPresent()) {
        RegistrationEntity existing = existingOpt.get();
        if (existing.getStatus() == RegistrationStatus.CONFIRMED) {
            throw new BusinessRuleException("El usuario ya se encuentra inscrito de forma activa en este evento");
        } else {
            // Reactivación
            if (event.getAvailableSeats() <= 0) {
                throw new BusinessRuleException("No hay cupos disponibles");
            }
            event.setAvailableSeats(event.getAvailableSeats() - 1);
            eventRepository.save(event);

            existing.setStatus(RegistrationStatus.CONFIRMED);
            existing.setRegistrationDate(LocalDateTime.now());
            return registrationRepository.save(existing);
        }
    }

    if (event.getAvailableSeats() <= 0) {
        throw new BusinessRuleException("No hay cupos disponibles");
    }

    event.setAvailableSeats(event.getAvailableSeats() - 1);
    eventRepository.save(event);

    RegistrationEntity registration = new RegistrationEntity();
    registration.setUser(user);
    registration.setEvent(event);
    registration.setStatus(RegistrationStatus.CONFIRMED);
    registration.setRegistrationDate(LocalDateTime.now());

    return registrationRepository.save(registration);
}
```

### 3.3 Listado y Permisos por Roles ([RegistrationController.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/controllers/RegistrationController.java))
Para listar las inscripciones (`GET /api/registrations`), controlamos la visibilidad desde el servicio utilizando el principal de Spring Security:
* **Administrador (`ROLE_ADMIN`):** Visualiza todas las inscripciones del sistema.
* **Organizador (`ROLE_ORGANIZER`):** Visualiza las inscripciones a los eventos organizados por él (`findByEventOrganizerId`).
* **Participante (`ROLE_PARTICIPANT`):** Visualiza únicamente sus propias inscripciones (`findByUserId`).

---

## 🧪 4. Pruebas Unitarias ([RegistrationServiceTest.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/test/java/ec/edu/ups/icc/events/registrations/services/RegistrationServiceTest.java))

Desarrollamos una suite completa de pruebas usando **JUnit 5** y **Mockito** para asegurar el correcto funcionamiento aislado de la capa de servicios sin necesidad de levantar bases de datos externas:

* `registerUserToEvent_Success`: Verifica que disminuya en `1` los cupos y guarde la inscripción con estado `CONFIRMED`.
* `registerUserToEvent_EventNotFound`: Lanza un `ResourceNotFoundException`.
* `registerUserToEvent_EventNotPublished`: Bloquea inscripciones si el estado es `DRAFT` o `CANCELLED`.
* `registerUserToEvent_EventDatePassed`: Bloquea inscripciones si la fecha actual ya superó el inicio del evento.
* `registerUserToEvent_NoSeats`: Valida la prevención de sobreventas lanzando una excepción si `availableSeats = 0`.
* `registerUserToEvent_AlreadyRegisteredActive`: Evita duplicar inscripciones activas.
* `registerUserToEvent_Reactivation`: Comprueba que una inscripción con estado `CANCELLED` sea reactivada correctamente a `CONFIRMED` en lugar de arrojar un error de índice único en base de datos.
* `cancelRegistration_Success`: Verifica que el estado de la inscripción cambie a `CANCELLED` y se sume `+1` cupo disponible al evento asociado.


Searched for "@RequestMapping"
Viewed AuthController.java:1-132
Viewed CategoryController.java:1-48
Viewed EventController.java:1-64
Viewed RegistrationController.java:1-37
Searched for "class Report"
Viewed CategoryDTO.java:1-9
Viewed EventDTO.java:1-23
Viewed RegistrationDTO.java:1-17
Edited academic-events-api.postman_collection.json

¡Listo! He creado el archivo con todas las pruebas automatizadas.

El archivo ha sido guardado directamente en la raíz de la carpeta de trabajo actual:
👉 **[academic-events-api.postman_collection.json](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/academic-events-api.postman_collection.json)**

---

### 📦 ¿Qué incluye y cómo funciona esta colección?

La colección está estructurada de forma idéntica a tu código del backend y está dividida en 4 carpetas organizadas:
1. **Autenticación (Auth):** Registro de nuevos usuarios, Login, refrescar token y Logout.
2. **Categorías (Categories):** Consultar todas, consultar por ID, crear, actualizar y eliminar.
3. **Eventos (Events):** Buscar y filtrar por texto/modalidad/fechas (paginado), consultar por ID, crear, actualizar y eliminar.
4. **Inscripciones (Registrations):** Listar inscripciones realizadas, inscribirse a un evento (con control de cupo) y cancelar una inscripción.

#### ⚡ Automatización de Tokens (Lo mejor para ahorrar tiempo)
He configurado scripts automáticos en las peticiones clave:
* Al ejecutar con éxito la petición de **`Login`**, un script guarda automáticamente el `accessToken` y `refreshToken` dentro de las variables de la colección de Postman/Bruno.
* Las peticiones que requieren estar autenticado (como crear eventos o inscribirse) ya tienen configurado el encabezado `Authorization: Bearer {{accessToken}}` de forma automática. **No tendrán que copiar y pegar tokens manualmente en cada endpoint.**
* Al ejecutar **`Logout`**, las variables de token se limpian de forma automática.

#### 🌐 Variable de Servidor Dinámica (`baseUrl`)
Por defecto, la variable `{{baseUrl}}` está configurada para apuntar directamente a tu servidor en producción en Render:
`https://academic-events-api-h1kf.onrender.com/api`

Si tú o tus compañeros quieren hacer pruebas en local, simplemente deben cambiar el valor de la variable `baseUrl` en la pestaña de variables de Postman/Bruno por:
`http://localhost:8080/api`

---

### 📥 Cómo importarlo en tus herramientas:

* **En Postman:** Abre Postman, haz clic en el botón **`Import`** (arriba a la izquierda), arrastra o selecciona el archivo `.json` y haz clic en importar.
* **En Bruno:** Abre la aplicación de Bruno, haz clic en el menú de arriba a la izquierda, selecciona **`Import Collection`**, elige la opción **`Postman Collection`**, selecciona este archivo `.json` y ¡listo!
l




.......


│   │   │                   │   ├───controllers
│   │   │                   │   │       CategoryController.class
│   │   │                   │   │
│   │   │                   │   ├───dtos
│   │   │                   │   │       CategoryDTO.class
│   │   │                   │   │       CategoryResponseDto.class
│   │   │                   │   │       CreateCategoryDto.class
│   │   │                   │   │
│   │   │                   │   ├───entities
│   │   │                   │   │       CategoryEntity.class
│   │   │                   │   │
│   │   │                   │   ├───mappers
│   │   │                   │   │       CategoryMapper.class
│   │   │                   │   │
│   │   │                   │   ├───repositories
│   │   │                   │   │       CategoryRepository.class
│   │   │                   │   │
│   │   │                   │   └───services
│   │   │                   │       │   CategoryService.class
│   │   │                   │       │
│   │   │                   │       └───impl
│   │   │                   │               CategoryServiceImpl.class
│   │   │                   │
│   │   │                   ├───core
│   │   │                   │   ├───config
│   │   │                   │   ├───dtos
│   │   │                   │   │       ApiErrorResponse.class
│   │   │                   │   │       ApiResponse.class
│   │   │                   │   │
│   │   │                   │   ├───entities
│   │   │                   │   │       BaseEntity.class
│   │   │                   │   │
│   │   │                   │   ├───exceptions
│   │   │                   │   │   │   BadRequestException.class
│   │   │                   │   │   │   BusinessRuleException.class
│   │   │                   │   │   │   ForbiddenException.class
│   │   │                   │   │   │   GlobalExceptionHandler.class
│   │   │                   │   │   │   RateLimitExceededException.class
│   │   │                   │   │   │   ResourceNotFoundException.class
│   │   │                   │   │   │   UnauthorizedException.class
│   │   │                   │   │   │
│   │   │                   │   │   └───base
│   │   │                   │   │           ApplicationException.class
│   │   │                   │   │
│   │   │                   │   └───utils
│   │   │                   ├───events
│   │   │                   │   ├───controllers
│   │   │                   │   │       EventController.class
│   │   │                   │   │
│   │   │                   │   ├───dtos
│   │   │                   │   │       CreateEventDto.class
│   │   │                   │   │       EventDTO.class
│   │   │                   │   │       EventFilterDTO.class
│   │   │                   │   │       EventResponseDto.class
│   │   │                   │   │       UpdateEventDto.class
│   │   │                   │   │
│   │   │                   │   ├───entities
│   │   │                   │   │       EventEntity.class
│   │   │                   │   │       EventModality.class
│   │   │                   │   │       EventStatus.class
│   │   │                   │   │
│   │   │                   │   ├───mappers
│   │   │                   │   │       EventMapper.class
│   │   │                   │   │
│   │   │                   │   ├───repositories
│   │   │                   │   │       EventRepository.class
│   │   │                   │   │
│   │   │                   │   └───services
│   │   │                   │       │   EventService.class
│   │   │                   │       │
│   │   │                   │       └───impl
│   │   │                   │               EventServiceImpl.class
│   │   │                   │
│   │   │                   ├───registrations
│   │   │                   │   ├───controllers
│   │   │                   │   │       RegistrationController.class
│   │   │                   │   │
│   │   │                   │   ├───dtos
│   │   │                   │   │       CreateRegistrationDto.class
│   │   │                   │   │       RegistrationDTO.class
│   │   │                   │   │       RegistrationResponseDto.class
│   │   │                   │   │
│   │   │                   │   ├───entities
│   │   │                   │   │       RegistrationEntity.class
│   │   │                   │   │       RegistrationStatus.class
│   │   │                   │   │
│   │   │                   │   ├───mappers
│   │   │                   │   │       RegistrationMapper.class
│   │   │                   │   │
│   │   │                   │   ├───repositories
│   │   │                   │   │       RegistrationRepository.class
│   │   │                   │   │
│   │   │                   │   └───services
│   │   │                   │       │   RegistrationService.class
│   │   │                   │       │
│   │   │                   │       └───impl
│   │   │                   │               RegistrationServiceImpl.class
│   │   │                   │
│   │   │                   ├───reports
│   │   │                   │   ├───controllers
│   │   │                   │   │       ReportController.class
│   │   │                   │   │
│   │   │                   │   ├───services
│   │   │                   │   │   │   ExcelReportService.class
│   │   │                   │   │   │   PdfReportService.class
│   │   │                   │   │   │   ReportAccessService.class
│   │   │                   │   │   │
│   │   │                   │   │   └───impl
│   │   │                   │   │           ExcelReportServiceImpl.class
│   │   │                   │   │           PdfReportServiceImpl.class
│   │   │                   │   │           ReportAccessServiceImpl.class
│   │   │                   │   │
│   │   │                   │   └───utils
│   │   │                   │           ReportDateTimeUtils.class
│   │   │                   │
│   │   │                   ├───security
│   │   │                   │   ├───config
│   │   │                   │   │       SecurityBeansConfig.class
│   │   │                   │   │       SecurityConfig.class
│   │   │                   │   │
│   │   │                   │   ├───filters
│   │   │                   │   │       JwtAuthenticationFilter.class
│   │   │                   │   │       RateLimitingFilter.class
│   │   │                   │   │
│   │   │                   │   └───services
│   │   │                   │           JwtService.class
│   │   │                   │
│   │   │                   ├───sessions
│   │   │                   │   ├───controllers
│   │   │                   │   │       SessionController.class
│   │   │                   │   │
│   │   │                   │   ├───dtos
│   │   │                   │   │       CreateSessionDto.class
│   │   │                   │   │       SessionDTO.class
│   │   │                   │   │       SessionResponseDto.class
│   │   │                   │   │
│   │   │                   │   ├───entities
│   │   │                   │   │       SessionEntity.class
│   │   │                   │   │
│   │   │                   │   ├───mappers
│   │   │                   │   │       SessionMapper.class
│   │   │                   │   │
│   │   │                   │   ├───repositories
│   │   │                   │   │       SessionRepository.class
│   │   │                   │   │
│   │   │                   │   └───services
│   │   │                   │       │   SessionService.class
│   │   │                   │       │
│   │   │                   │       └───impl
│   │   │                   │               SessionServiceImpl.class
│   │   │                   │
│   │   │                   └───users
│   │   │                       ├───controllers
│   │   │                       ├───dtos
│   │   │                       ├───entities
│   │   │                       │       RoleEntity.class
│   │   │                       │       UserEntity.class
│   │   │                       │
│   │   │                       ├───repositories
│   │   │                       │       RoleRepository.class
│   │   │                       │       UserRepository.class
│   │   │                       │
│   │   │                       └───services
│   │   │                               CustomUserDetailsService.class
│   │   │
│   │   ├───static
│   │   │   └───images
│   │   │           ups-logo.png
│   │   │
│   │   └───templates
│   └───test
│       └───ec
│           └───edu
│               └───ups
│                   └───icc
│                       └───events
│                           │   AcademicEventsApiApplicationTests.class
│                           │
│                           ├───core
│                           │   └───exceptions
│                           │           GlobalExceptionHandlerTest$TestController.class
│                           │           GlobalExceptionHandlerTest$TestRequest.class
│                           │           GlobalExceptionHandlerTest.class
│                           │
│                           ├───registrations
│                           │   └───services
│                           │           RegistrationServiceTest.class
│                           │
│                           └───security
│                               └───config
│                                       SecurityBeansConfigTest.class
│
├───build
│   │   resolvedMainClassName
│   │
│   ├───classes
│   │   └───java
│   │       ├───main
│   │       │   └───ec
│   │       │       └───edu
│   │       │           └───ups
│   │       │               └───icc
│   │       │                   └───events
│   │       │                       │   AcademicEventsApiApplication.class
│   │       │                       │
│   │       │                       ├───audit
│   │       │                       │   ├───annotations
│   │       │                       │   │       Auditable.class
│   │       │                       │   │
│   │       │                       │   ├───aspects
│   │       │                       │   │       AuditAspect.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       AuditLogEntity.class
│   │       │                       │   │
│   │       │                       │   ├───repositories
│   │       │                       │   │       AuditLogRepository.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   AuditLogService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               AuditLogServiceImpl.class
│   │       │                       │
│   │       │                       ├───auth
│   │       │                       │   ├───controllers
│   │       │                       │   │       AuthController.class
│   │       │                       │   │
│   │       │                       │   ├───dtos
│   │       │                       │   │       AuthResponseDto.class
│   │       │                       │   │       LoginRequestDto.class
│   │       │                       │   │       RegisterRequestDto.class
│   │       │                       │   │
│   │       │                       │   ├───mappers
│   │       │                       │   │       AuthMapper.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   AuthService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               AuthServiceImpl.class
│   │       │                       │
│   │       │                       ├───categories
│   │       │                       │   ├───controllers
│   │       │                       │   │       CategoryController.class
│   │       │                       │   │
│   │       │                       │   ├───dtos
│   │       │                       │   │       CategoryDTO.class
│   │       │                       │   │       CategoryResponseDto.class
│   │       │                       │   │       CreateCategoryDto.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       CategoryEntity.class
│   │       │                       │   │
│   │       │                       │   ├───mappers
│   │       │                       │   │       CategoryMapper.class
│   │       │                       │   │
│   │       │                       │   ├───repositories
│   │       │                       │   │       CategoryRepository.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   CategoryService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               CategoryServiceImpl.class
│   │       │                       │
│   │       │                       ├───core
│   │       │                       │   ├───dtos
│   │       │                       │   │       ApiErrorResponse.class
│   │       │                       │   │       ApiResponse.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       BaseEntity.class
│   │       │                       │   │
│   │       │                       │   └───exceptions
│   │       │                       │       │   BadRequestException.class
│   │       │                       │       │   BusinessRuleException.class
│   │       │                       │       │   ForbiddenException.class
│   │       │                       │       │   GlobalExceptionHandler.class
│   │       │                       │       │   RateLimitExceededException.class
│   │       │                       │       │   ResourceNotFoundException.class
│   │       │                       │       │   UnauthorizedException.class
│   │       │                       │       │
│   │       │                       │       └───base
│   │       │                       │               ApplicationException.class
│   │       │                       │
│   │       │                       ├───events
│   │       │                       │   ├───controllers
│   │       │                       │   │       EventController.class
│   │       │                       │   │
│   │       │                       │   ├───dtos
│   │       │                       │   │       CreateEventDto.class
│   │       │                       │   │       EventDTO.class
│   │       │                       │   │       EventFilterDTO.class
│   │       │                       │   │       EventResponseDto.class
│   │       │                       │   │       UpdateEventDto.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       EventEntity.class
│   │       │                       │   │       EventModality.class
│   │       │                       │   │       EventStatus.class
│   │       │                       │   │
│   │       │                       │   ├───mappers
│   │       │                       │   │       EventMapper.class
│   │       │                       │   │
│   │       │                       │   ├───repositories
│   │       │                       │   │       EventRepository.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   EventService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               EventServiceImpl.class
│   │       │                       │
│   │       │                       ├───registrations
│   │       │                       │   ├───controllers
│   │       │                       │   │       RegistrationController.class
│   │       │                       │   │
│   │       │                       │   ├───dtos
│   │       │                       │   │       CreateRegistrationDto.class
│   │       │                       │   │       RegistrationDTO.class
│   │       │                       │   │       RegistrationResponseDto.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       RegistrationEntity.class
│   │       │                       │   │       RegistrationStatus.class
│   │       │                       │   │
│   │       │                       │   ├───mappers
│   │       │                       │   │       RegistrationMapper.class
│   │       │                       │   │
│   │       │                       │   ├───repositories
│   │       │                       │   │       RegistrationRepository.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   RegistrationService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               RegistrationServiceImpl.class
│   │       │                       │
│   │       │                       ├───reports
│   │       │                       │   ├───controllers
│   │       │                       │   │       ReportController.class
│   │       │                       │   │
│   │       │                       │   ├───services
│   │       │                       │   │   │   ExcelReportService.class
│   │       │                       │   │   │   PdfReportService.class
│   │       │                       │   │   │   ReportAccessService.class
│   │       │                       │   │   │
│   │       │                       │   │   └───impl
│   │       │                       │   │           ExcelReportServiceImpl.class
│   │       │                       │   │           PdfReportServiceImpl.class
│   │       │                       │   │           ReportAccessServiceImpl.class
│   │       │                       │   │
│   │       │                       │   └───utils
│   │       │                       │           ReportDateTimeUtils.class
│   │       │                       │
│   │       │                       ├───security
│   │       │                       │   ├───config
│   │       │                       │   │       SecurityBeansConfig.class
│   │       │                       │   │       SecurityConfig.class
│   │       │                       │   │
│   │       │                       │   ├───filters
│   │       │                       │   │       JwtAuthenticationFilter.class
│   │       │                       │   │       RateLimitingFilter.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │           JwtService.class
│   │       │                       │
│   │       │                       ├───sessions
│   │       │                       │   ├───controllers
│   │       │                       │   │       SessionController.class
│   │       │                       │   │
│   │       │                       │   ├───dtos
│   │       │                       │   │       CreateSessionDto.class
│   │       │                       │   │       SessionDTO.class
│   │       │                       │   │       SessionResponseDto.class
│   │       │                       │   │
│   │       │                       │   ├───entities
│   │       │                       │   │       SessionEntity.class
│   │       │                       │   │
│   │       │                       │   ├───mappers
│   │       │                       │   │       SessionMapper.class
│   │       │                       │   │
│   │       │                       │   ├───repositories
│   │       │                       │   │       SessionRepository.class
│   │       │                       │   │
│   │       │                       │   └───services
│   │       │                       │       │   SessionService.class
│   │       │                       │       │
│   │       │                       │       └───impl
│   │       │                       │               SessionServiceImpl.class
│   │       │                       │
│   │       │                       └───users
│   │       │                           ├───entities
│   │       │                           │       RoleEntity.class
│   │       │                           │       UserEntity.class
│   │       │                           │
│   │       │                           ├───repositories
│   │       │                           │       RoleRepository.class
│   │       │                           │       UserRepository.class
│   │       │                           │
│   │       │                           └───services
│   │       │                                   CustomUserDetailsService.class
│   │       │
│   │       └───test
│   │           └───ec
│   │               └───edu
│   │                   └───ups
│   │                       └───icc
│   │                           └───events
│   │                               │   AcademicEventsApiApplicationTests.class
│   │                               │
│   │                               ├───core
│   │                               │   └───exceptions
│   │                               │           GlobalExceptionHandlerTest$TestController.class        
│   │                               │           GlobalExceptionHandlerTest$TestRequest.class
│   │                               │           GlobalExceptionHandlerTest.class
│   │                               │
│   │                               ├───registrations
│   │                               │   └───services
│   │                               │           RegistrationServiceTest.class
│   │                               │
│   │                               └───security
│   │                                   └───config
│   │                                           SecurityBeansConfigTest.class
│   │
│   ├───generated
│   │   └───sources
│   │       ├───annotationProcessor
│   │       │   └───java
│   │       │       ├───main
│   │       │       └───test
│   │       └───headers
│   │           └───java
│   │               ├───main
│   │               └───test
│   ├───libs
│   │       academic-events-api-0.0.1-SNAPSHOT-plain.jar
│   │       academic-events-api-0.0.1-SNAPSHOT.jar
│   │
│   ├───reports
│   │   ├───problems
│   │   │       problems-report.html
│   │   │
│   │   └───tests
│   │       └───test
│   │           │   index.html
│   │           │
│   │           ├───css
│   │           │       base-style.css
│   │           │       style.css
│   │           │
│   │           ├───FtunHc9yZY4
│   │           │       index.html
│   │           │
│   │           ├───js
│   │           │       report.js
│   │           │
│   │           ├───VeysyaGNGRo
│   │           │       752qwoIYTJY.html
│   │           │       hcGstLmizTI.html
│   │           │       index.html
│   │           │       ITlJ8Grlh_k.html
│   │           │       kWlK02HSu9M.html
│   │           │       Pr16CIfn30o.html
│   │           │       qiZQCnBBA-0.html
│   │           │       S1Rz_tL2H8c.html
│   │           │       V2cwc1X9JYc.html
│   │           │
│   │           ├───XknOnDbBRkA
│   │           │       index.html
│   │           │
│   │           └───ZHxyUG_6N0Y
│   │                   index.html
│   │
│   ├───resources
│   │   └───main
│   │       │   application.yml
│   │       │
│   │       ├───db
│   │       │   └───migration
│   │       │           V1__initial_schema_and_data.sql
│   │       │
│   │       ├───static
│   │       │   └───images
│   │       │           ups-logo.png
│   │       │
│   │       └───templates
│   ├───test-results
│   │   └───test
│   │       │   TEST-ec.edu.ups.icc.events.AcademicEventsApiApplicationTests.xml
│   │       │   TEST-ec.edu.ups.icc.events.core.exceptions.GlobalExceptionHandlerTest.xml
│   │       │   TEST-ec.edu.ups.icc.events.registrations.services.RegistrationServiceTest.xml
│   │       │   TEST-ec.edu.ups.icc.events.security.config.SecurityBeansConfigTest.xml
│   │       │
│   │       └───binary
│   │               output-events.bin
│   │               results-generic.bin
│   │
│   └───tmp
│       ├───bootJar
│       │       MANIFEST.MF
│       │
│       ├───compileJava
│       │       previous-compilation-data.bin
│       │
│       ├───compileTestJava
│       │   │   previous-compilation-data.bin
│       │   │
│       │   └───compileTransaction
│       │       ├───backup-dir
│       │       └───stash-dir
│       │               AcademicEventsApiApplicationTests.class.uniqueId0
│       │
│       ├───jar
│       │       MANIFEST.MF
│       │
│       └───test
├───gradle
│   └───wrapper
│           gradle-wrapper.jar
│           gradle-wrapper.properties
│
├───proyecto especificaciones
│       academic-events-api.postman_collection.json
│       analisis_buenas_practicas.md
│       analisis_y_guia_correccion.md
│       checklist_entregables_rubrica.md
│       comparativa_y_limpieza_carpetas.md
│       desarrollo_paso_12.md
│       desarrollo_paso_7.md
│       docker-compose.yml
│       Dockerfile
│       especificaciones_backend_springboot.md
│       especificaciones_proyecto.md
│       estructura_proyecto_propuesta.md
│       guia_despliegue_produccion.md
│       guia_paso_a_paso.md
│       guia_pruebas_bruno.md
│       guia_video_exposicion.md
│       import pandas as pd.py
│       paso_7_inscripciones.md
│       plan_desarrollo.md
│       render y redis.md
│       walkthrough.md
│
└───src
    ├───main
    │   ├───java
    │   │   └───ec
    │   │       └───edu
    │   │           └───ups
    │   │               └───icc
    │   │                   └───events
    │   │                       │   AcademicEventsApiApplication.java
    │   │                       │
    │   │                       ├───audit
    │   │                       │   ├───annotations
    │   │                       │   │       Auditable.java
    │   │                       │   │
    │   │                       │   ├───aspects
    │   │                       │   │       AuditAspect.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       AuditLogEntity.java
    │   │                       │   │
    │   │                       │   ├───repositories
    │   │                       │   │       AuditLogRepository.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │           AuditLogService.java
    │   │                       │           AuditLogServiceImpl.java
    │   │                       │
    │   │                       ├───auth
    │   │                       │   ├───controllers
    │   │                       │   │       AuthController.java
    │   │                       │   │
    │   │                       │   ├───dtos
    │   │                       │   │       AuthResponseDto.java
    │   │                       │   │       LoginRequestDto.java
    │   │                       │   │       RegisterRequestDto.java
    │   │                       │   │
    │   │                       │   ├───mappers
    │   │                       │   │       AuthMapper.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │       │   AuthService.java
    │   │                       │       │
    │   │                       │       └───impl
    │   │                       │               AuthServiceImpl.java
    │   │                       │
    │   │                       ├───categories
    │   │                       │   ├───controllers
    │   │                       │   │       CategoryController.java
    │   │                       │   │
    │   │                       │   ├───dtos
    │   │                       │   │       CategoryDTO.java
    │   │                       │   │       CategoryResponseDto.java
    │   │                       │   │       CreateCategoryDto.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       CategoryEntity.java
    │   │                       │   │
    │   │                       │   ├───mappers
    │   │                       │   │       CategoryMapper.java
    │   │                       │   │
    │   │                       │   ├───repositories
    │   │                       │   │       CategoryRepository.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │       │   CategoryService.java
    │   │                       │       │
    │   │                       │       └───impl
    │   │                       │               CategoryServiceImpl.java
    │   │                       │
    │   │                       ├───core
    │   │                       │   ├───config
    │   │                       │   ├───dtos
    │   │                       │   │       ApiErrorResponse.java
    │   │                       │   │       ApiResponse.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       BaseEntity.java
    │   │                       │   │
    │   │                       │   ├───exceptions
    │   │                       │   │   │   BadRequestException.java
    │   │                       │   │   │   BusinessRuleException.java
    │   │                       │   │   │   ForbiddenException.java
    │   │                       │   │   │   GlobalExceptionHandler.java
    │   │                       │   │   │   RateLimitExceededException.java
    │   │                       │   │   │   ResourceNotFoundException.java
    │   │                       │   │   │   UnauthorizedException.java
    │   │                       │   │   │
    │   │                       │   │   └───base
    │   │                       │   │           ApplicationException.java
    │   │                       │   │
    │   │                       │   └───utils
    │   │                       ├───events
    │   │                       │   ├───controllers
    │   │                       │   │       EventController.java
    │   │                       │   │
    │   │                       │   ├───dtos
    │   │                       │   │       CreateEventDto.java
    │   │                       │   │       EventDTO.java
    │   │                       │   │       EventFilterDTO.java
    │   │                       │   │       EventResponseDto.java
    │   │                       │   │       UpdateEventDto.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       EventEntity.java
    │   │                       │   │       EventModality.java
    │   │                       │   │       EventStatus.java
    │   │                       │   │
    │   │                       │   ├───mappers
    │   │                       │   │       EventMapper.java
    │   │                       │   │
    │   │                       │   ├───repositories
    │   │                       │   │       EventRepository.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │       │   EventService.java
    │   │                       │       │
    │   │                       │       └───impl
    │   │                       │               EventServiceImpl.java
    │   │                       │
    │   │                       ├───registrations
    │   │                       │   ├───controllers
    │   │                       │   │       RegistrationController.java
    │   │                       │   │
    │   │                       │   ├───dtos
    │   │                       │   │       CreateRegistrationDto.java
    │   │                       │   │       RegistrationDTO.java
    │   │                       │   │       RegistrationResponseDto.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       RegistrationEntity.java
    │   │                       │   │       RegistrationStatus.java
    │   │                       │   │
    │   │                       │   ├───mappers
    │   │                       │   │       RegistrationMapper.java
    │   │                       │   │
    │   │                       │   ├───repositories
    │   │                       │   │       RegistrationRepository.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │       │   RegistrationService.java
    │   │                       │       │
    │   │                       │       └───impl
    │   │                       │               RegistrationServiceImpl.java
    │   │                       │
    │   │                       ├───reports
    │   │                       │   ├───controllers
    │   │                       │   │       ReportController.java
    │   │                       │   │
    │   │                       │   ├───services
    │   │                       │   │   │   ExcelReportService.java
    │   │                       │   │   │   PdfReportService.java
    │   │                       │   │   │   ReportAccessService.java
    │   │                       │   │   │
    │   │                       │   │   └───impl
    │   │                       │   │           ExcelReportServiceImpl.java
    │   │                       │   │           PdfReportServiceImpl.java
    │   │                       │   │           ReportAccessServiceImpl.java
    │   │                       │   │
    │   │                       │   └───utils
    │   │                       │           ReportDateTimeUtils.java
    │   │                       │
    │   │                       ├───security
    │   │                       │   ├───config
    │   │                       │   │       SecurityBeansConfig.java
    │   │                       │   │       SecurityConfig.java
    │   │                       │   │
    │   │                       │   ├───filters
    │   │                       │   │       JwtAuthenticationFilter.java
    │   │                       │   │       RateLimitingFilter.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │           JwtService.java
    │   │                       │
    │   │                       ├───sessions
    │   │                       │   ├───controllers
    │   │                       │   │       SessionController.java
    │   │                       │   │
    │   │                       │   ├───dtos
    │   │                       │   │       CreateSessionDto.java
    │   │                       │   │       SessionDTO.java
    │   │                       │   │       SessionResponseDto.java
    │   │                       │   │
    │   │                       │   ├───entities
    │   │                       │   │       SessionEntity.java
    │   │                       │   │
    │   │                       │   ├───mappers
    │   │                       │   │       SessionMapper.java
    │   │                       │   │
    │   │                       │   ├───repositories
    │   │                       │   │       SessionRepository.java
    │   │                       │   │
    │   │                       │   └───services
    │   │                       │       │   SessionService.java
    │   │                       │       │
    │   │                       │       └───impl
    │   │                       │               SessionServiceImpl.java
    │   │                       │
    │   │                       └───users
    │   │                           ├───controllers
    │   │                           ├───dtos
    │   │                           ├───entities
    │   │                           │       RoleEntity.java
    │   │                           │       UserEntity.java
    │   │                           │
    │   │                           ├───repositories
    │   │                           │       RoleRepository.java
    │   │                           │       UserRepository.java
    │   │                           │
    │   │                           └───services
    │   │                                   CustomUserDetailsService.java
    │   │
    │   └───resources
    │       │   application.yml
    │       │
    │       ├───db
    │       │   └───migration
    │       │           V1__initial_schema_and_data.sql
    │       │
    │       ├───static
    │       │   └───images
    │       │           ups-logo.png
    │       │
    │       └───templates
    └───test
        └───java
            └───ec
                └───edu
                    └───ups
                        └───icc
                            └───events
                                │   AcademicEventsApiApplicationTests.java
                                │
                                ├───core
                                │   └───exceptions
                                │           GlobalExceptionHandlerTest.java
                                │
                                ├───registrations
                                │   └───services
                                │           RegistrationServiceTest.java
                                │
                                └───security
                                    └───config
                                            SecurityBeansConfigTest.java


C:\Users\MSI\Desktop\PPW\Backend\SPRINGBOOT\academic-events-api> cd src                                

C:\Users\MSI\Desktop\PPW\Backend\SPRINGBOOT\academic-events-api\src>tree /F /A
Folder PATH listing
Volume serial number is 00000030 7AB0:7603
C:.
+---main
|   +---java
|   |   \---ec
|   |       \---edu
|   |           \---ups
|   |               \---icc
|   |                   \---events
|   |                       |   AcademicEventsApiApplication.java
|   |                       |
|   |                       +---audit
|   |                       |   +---annotations
|   |                       |   |       Auditable.java
|   |                       |   |
|   |                       |   +---aspects
|   |                       |   |       AuditAspect.java
|   |                       |   |
|   |                       |   +---entities
|   |                       |   |       AuditLogEntity.java
|   |                       |   |
|   |                       |   +---repositories
|   |                       |   |       AuditLogRepository.java
|   |                       |   |
|   |                       |   \---services
|   |                       |           AuditLogService.java
|   |                       |           AuditLogServiceImpl.java
|   |                       |
|   |                       +---auth
|   |                       |   +---controllers
|   |                       |   |       AuthController.java
|   |                       |   |
|   |                       |   +---dtos
|   |                       |   |       AuthResponseDto.java
|   |                       |   |       LoginRequestDto.java
|   |                       |   |       RegisterRequestDto.java
|   |                       |   |
|   |                       |   +---mappers
|   |                       |   |       AuthMapper.java
|   |                       |   |
|   |                       |   \---services
|   |                       |       |   AuthService.java
|   |                       |       |
|   |                       |       \---impl
|   |                       |               AuthServiceImpl.java
|   |                       |
|   |                       +---categories
|   |                       |   +---controllers
|   |                       |   |       CategoryController.java
|   |                       |   |
|   |                       |   +---dtos
|   |                       |   |       CategoryDTO.java
|   |                       |   |       CategoryResponseDto.java
|   |                       |   |       CreateCategoryDto.java
|   |                       |   |
|   |                       |   +---entities
|   |                       |   |       CategoryEntity.java
|   |                       |   |
|   |                       |   +---mappers
|   |                       |   |       CategoryMapper.java
|   |                       |   |
|   |                       |   +---repositories
|   |                       |   |       CategoryRepository.java
|   |                       |   |
|   |                       |   \---services
|   |                       |       |   CategoryService.java
|   |                       |       |
|   |                       |       \---impl
|   |                       |               CategoryServiceImpl.java
|   |                       |
|   |                       +---core
|   |                       |   +---config
|   |                       |   +---dtos
|   |                       |   |       ApiErrorResponse.java
|   |                       |   |       ApiResponse.java
|   |                       |   |       
|   |                       |   +---entities
|   |                       |   |       BaseEntity.java
|   |                       |   |
|   |                       |   +---exceptions
|   |                       |   |   |   BadRequestException.java
|   |                       |   |   |   BusinessRuleException.java
|   |                       |   |   |   ForbiddenException.java
|   |                       |   |   |   GlobalExceptionHandler.java
|   |                       |   |   |   RateLimitExceededException.java
|   |                       |   |   |   ResourceNotFoundException.java
|   |                       |   |   |   UnauthorizedException.java
|   |                       |   |   |
|   |                       |   |   \---base
|   |                       |   |           ApplicationException.java
|   |                       |   |
|   |                       |   \---utils
|   |                       +---events
|   |                       |   +---controllers
|   |                       |   |       EventController.java
|   |                       |   |
|   |                       |   +---dtos
|   |                       |   |       CreateEventDto.java
|   |                       |   |       EventDTO.java
|   |                       |   |       EventFilterDTO.java
|   |                       |   |       EventResponseDto.java
|   |                       |   |       UpdateEventDto.java
|   |                       |   |
|   |                       |   +---entities
|   |                       |   |       EventEntity.java
|   |                       |   |       EventModality.java
|   |                       |   |       EventStatus.java
|   |                       |   |
|   |                       |   +---mappers
|   |                       |   |       EventMapper.java
|   |                       |   |
|   |                       |   +---repositories
|   |                       |   |       EventRepository.java
|   |                       |   |
|   |                       |   \---services
|   |                       |       |   EventService.java
|   |                       |       |
|   |                       |       \---impl
|   |                       |               EventServiceImpl.java
|   |                       |
|   |                       +---registrations
|   |                       |   +---controllers
|   |                       |   |       RegistrationController.java
|   |                       |   |
|   |                       |   +---dtos
|   |                       |   |       CreateRegistrationDto.java
|   |                       |   |       RegistrationDTO.java
|   |                       |   |       RegistrationResponseDto.java
|   |                       |   |
|   |                       |   +---entities
|   |                       |   |       RegistrationEntity.java
|   |                       |   |       RegistrationStatus.java
|   |                       |   |
|   |                       |   +---mappers
|   |                       |   |       RegistrationMapper.java
|   |                       |   |
|   |                       |   +---repositories
|   |                       |   |       RegistrationRepository.java
|   |                       |   |
|   |                       |   \---services
|   |                       |       |   RegistrationService.java
|   |                       |       |
|   |                       |       \---impl
|   |                       |               RegistrationServiceImpl.java
|   |                       |
|   |                       +---reports
|   |                       |   +---controllers
|   |                       |   |       ReportController.java
|   |                       |   |
|   |                       |   +---services
|   |                       |   |   |   ExcelReportService.java
|   |                       |   |   |   PdfReportService.java
|   |                       |   |   |   ReportAccessService.java
|   |                       |   |   |
|   |                       |   |   \---impl
|   |                       |   |           ExcelReportServiceImpl.java
|   |                       |   |           PdfReportServiceImpl.java
|   |                       |   |           ReportAccessServiceImpl.java
|   |                       |   |
|   |                       |   \---utils
|   |                       |           ReportDateTimeUtils.java
|   |                       |
|   |                       +---security
|   |                       |   +---config
|   |                       |   |       SecurityBeansConfig.java
|   |                       |   |       SecurityConfig.java
|   |                       |   |
|   |                       |   +---filters
|   |                       |   |       JwtAuthenticationFilter.java
|   |                       |   |       RateLimitingFilter.java
|   |                       |   |
|   |                       |   \---services
|   |                       |           JwtService.java
|   |                       |
|   |                       +---sessions
|   |                       |   +---controllers
|   |                       |   |       SessionController.java
|   |                       |   |
|   |                       |   +---dtos
|   |                       |   |       CreateSessionDto.java
|   |                       |   |       SessionDTO.java
|   |                       |   |       SessionResponseDto.java
|   |                       |   |
|   |                       |   +---entities
|   |                       |   |       SessionEntity.java
|   |                       |   |
|   |                       |   +---mappers
|   |                       |   |       SessionMapper.java
|   |                       |   |
|   |                       |   +---repositories
|   |                       |   |       SessionRepository.java
|   |                       |   |
|   |                       |   \---services
|   |                       |       |   SessionService.java
|   |                       |       |
|   |                       |       \---impl
|   |                       |               SessionServiceImpl.java
|   |                       |
|   |                       \---users
|   |                           +---controllers
|   |                           +---dtos
|   |                           +---entities
|   |                           |       RoleEntity.java
|   |                           |       UserEntity.java
|   |                           |
|   |                           +---repositories
|   |                           |       RoleRepository.java
|   |                           |       UserRepository.java
|   |                           |
|   |                           \---services
|   |                                   CustomUserDetailsService.java
|   |
|   \---resources
|       |   application.yml
|       |
|       +---db
|       |   \---migration
|       |           V1__initial_schema_and_data.sql
|       |
|       +---static
|       |   \---images
|       |           ups-logo.png
|       |
|       \---templates
\---test
    \---java
        \---ec
            \---edu
                \---ups
                    \---icc
                        \---events
                            |   AcademicEventsApiApplicationTests.java
                            |
                            +---core
                            |   \---exceptions
                            |           GlobalExceptionHandlerTest.java
                            |
                            +---registrations
                            |   \---services
                            |           RegistrationServiceTest.java
                            |
                            \---security
                                \---config
                                        SecurityBeansConfigTest.java


