# Plan de Desarrollo - Paso 7: Módulo de Inscripciones y Control Transaccional de Cupos

Este documento detalla cada una de las acciones necesarias para el desarrollo del **Paso 7**, especificando los archivos nuevos a crear, las modificaciones en archivos existentes y las reglas de negocio críticas a implementar.

---

## 📂 1. Resumen de Archivos

### 🆕 Archivos Nuevos Creados
Para implementar este módulo desde cero, se deben crear los siguientes **7 archivos** dentro del paquete `ec.edu.ups.icc.events.registrations` (y en el directorio de pruebas):

| # | Archivo | Ruta | Propósito |
|---|---------|------|-----------|
| 1 | [RegistrationStatus.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/entities/RegistrationStatus.java) | `src/main/java/ec/edu/ups/icc/events/registrations/entities/` | Enumerado para representar los estados de una inscripción (`CONFIRMED`, `CANCELLED`). |
| 2 | [RegistrationEntity.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/entities/RegistrationEntity.java) | `src/main/java/ec/edu/ups/icc/events/registrations/entities/` | Entidad JPA mapeada a la tabla `registrations` en PostgreSQL. |
| 3 | [RegistrationRepository.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/repositories/RegistrationRepository.java) | `src/main/java/ec/edu/ups/icc/events/registrations/repositories/` | Repositorio Spring Data JPA con consultas especializadas para inscripciones. |
| 4 | [RegistrationDTO.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/dtos/RegistrationDTO.java) | `src/main/java/ec/edu/ups/icc/events/registrations/dtos/` | Record DTO para estructurar las respuestas JSON de inscripciones. |
| 5 | [RegistrationService.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/services/RegistrationService.java) | `src/main/java/ec/edu/ups/icc/events/registrations/services/` | Capa de servicio que ejecuta la lógica transaccional de cupos, validaciones y filtros por roles. |
| 6 | [RegistrationController.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/controllers/RegistrationController.java) | `src/main/java/ec/edu/ups/icc/events/registrations/controllers/` | Controlador REST que expone los endpoints HTTP del módulo. |
| 7 | [RegistrationServiceTest.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/test/java/ec/edu/ups/icc/events/registrations/services/RegistrationServiceTest.java) | `src/test/java/ec/edu/ups/icc/events/registrations/services/` | Clase de pruebas unitarias utilizando JUnit 5 y Mockito para las reglas de negocio del servicio. |

### 🛠️ Archivos Existentes a Modificar
El módulo interactúa con componentes ya existentes de seguridad y de negocio:

| # | Archivo | Ruta | Modificación Requerida |
|---|---------|------|------------------------|
| 1 | [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/security/SecurityConfig.java) | `src/main/java/ec/edu/ups/icc/events/core/security/` | Asegurar que los endpoints bajo `/api/registrations/**` estén protegidos y requieran autenticación (`.anyRequest().authenticated()`). |
| 2 | [EventEntity.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/events/entities/EventEntity.java) | `src/main/java/ec/edu/ups/icc/events/events/entities/` | Asegurar que existan los getters y setters para `availableSeats` y `status`. |

---

## 📋 2. Detalle de Acciones a Realizar

### Acción 1: Crear el enumerado de estado
* **Archivo:** [RegistrationStatus.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/entities/RegistrationStatus.java) [NUEVO]
* **Detalle:** Definir la estructura básica del enum:
  ```java
  public enum RegistrationStatus {
      CONFIRMED,
      CANCELLED
  }
  ```

### Acción 2: Definir la Entidad de Inscripción (JPA)
* **Archivo:** [RegistrationEntity.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/entities/RegistrationEntity.java) [NUEVO]
* **Detalle:** 
  - Mapear a la tabla `"registrations"`.
  - Heredar de `BaseEntity` (para registrar automáticamente campos de fecha de auditoría `createdAt` y `updatedAt`).
  - Declarar relación `@ManyToOne` con `UserEntity` (columna `user_id`, no nula).
  - Declarar relación `@ManyToOne` con `EventEntity` (columna `event_id`, no nula).
  - Declarar campo `@Enumerated(EnumType.STRING)` para `status` (columna `status`, no nula).
  - Declarar campo `registrationDate` (`LocalDateTime`, columna `registration_date`).

### Acción 3: Crear el DTO para el intercambio de datos
* **Archivo:** [RegistrationDTO.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/dtos/RegistrationDTO.java) [NUEVO]
* **Detalle:** Crear un record de Java para optimizar la transferencia de datos:
  ```java
  public record RegistrationDTO(
      Long id,
      Long userId,
      String userEmail,
      Long eventId,
      String eventTitle,
      LocalDateTime registrationDate,
      RegistrationStatus status
  ) {}
  ```

### Acción 4: Configurar los métodos del Repositorio
* **Archivo:** [RegistrationRepository.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/repositories/RegistrationRepository.java) [NUEVO]
* **Detalle:** Extender de `JpaRepository<RegistrationEntity, Long>`. Definir las siguientes firmas de consulta necesarias:
  1. `boolean existsByEventId(Long eventId)`: Verifica si hay inscripciones vinculadas a un evento antes de realizar cambios estructurales en él.
  2. `Optional<RegistrationEntity> findByUserIdAndEventId(Long userId, Long eventId)`: Para buscar si un usuario específico ya posee un registro (activo o cancelado) en un evento específico.
  3. `Page<RegistrationEntity> findByUserId(Long userId, Pageable pageable)`: Permite obtener de manera paginada las inscripciones de un usuario (para rol PARTICIPANT).
  4. `Page<RegistrationEntity> findByEventOrganizerId(Long organizerId, Pageable pageable)`: Obtiene de manera paginada las inscripciones asociadas a los eventos que han sido organizados por un usuario en específico (para rol ORGANIZER).

### Acción 5: Implementar la Lógica de Negocio Transaccional
* **Archivo:** [RegistrationService.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/services/RegistrationService.java) [NUEVO]
* **Detalle:** 
  1. **Anotar la clase** con `@Service` y `@Transactional` para garantizar la consistencia en base de datos.
  2. **Inyección de dependencias:** Inyectar `RegistrationRepository`, `EventRepository` y `UserRepository`.
  3. **Método `registerUserToEvent(Long eventId)`**:
     - Obtener usuario logueado llamando a un método utilitario (`findCurrentUser()`).
     - Buscar el evento por `eventId` (lanzar `ResourceNotFoundException` si no existe).
     - **Regla 1:** Validar que el estado del evento sea estrictamente `EventStatus.PUBLISHED`. De lo contrario, lanzar `BusinessRuleException`.
     - **Regla 2:** Validar que la fecha actual no sea posterior a la fecha de inicio del evento (`LocalDateTime.now().isAfter(event.getStartDate())`). De lo contrario, lanzar `BusinessRuleException`.
     - **Regla 3 (Unicidad y Reactivación):** Buscar si existe inscripción previa:
       - Si existe y está `CONFIRMED`: lanzar `BusinessRuleException` ("El usuario ya se encuentra inscrito...").
       - Si existe y está `CANCELLED`:
         - Validar cupos (`event.getAvailableSeats() > 0`). Si no hay, lanzar `BusinessRuleException`.
         - Disminuir el cupo del evento en 1 (`event.setAvailableSeats(event.getAvailableSeats() - 1)`).
         - Guardar el evento modificado.
         - Actualizar el estado de la inscripción a `CONFIRMED`, renovar `registrationDate` a la fecha actual y guardar en base de datos.
     - **Regla 4 (Inscripción nueva):** Si no existe inscripción previa:
       - Validar cupos (`event.getAvailableSeats() > 0`). Si no hay, lanzar `BusinessRuleException`.
       - Disminuir el cupo del evento en 1.
       - Guardar el evento modificado.
       - Crear una nueva `RegistrationEntity` vinculando al usuario y evento, con estado `CONFIRMED` y fecha actual, y guardarla en base de datos.
     - Retornar el DTO mapeado.
  4. **Método `cancelRegistration(Long id)`**:
     - Obtener usuario logueado.
     - Buscar la inscripción por ID (lanzar `ResourceNotFoundException` si no existe).
     - **Regla de Autorización:** Validar propiedad (ownership). Si el usuario logueado no es `ROLE_ADMIN` y no es el dueño de la inscripción (`!registration.getUser().getId().equals(currentUser.getId())`), lanzar `ForbiddenException`.
     - **Regla de Estado:** Si ya está cancelada, lanzar `BusinessRuleException`.
     - Cambiar estado a `RegistrationStatus.CANCELLED`.
     - Devolver el cupo al evento sumando 1 a `availableSeats`.
     - Guardar evento e inscripción, y retornar el DTO.
  5. **Método `getRegistrations(int page, int size)`**:
     - Utilizar `@Transactional(readOnly = true)`.
     - Obtener el usuario autenticado.
     - **Visibilidad según Rol:**
       - Si es `ROLE_ADMIN`: Llamar a `registrationRepository.findAll(pageable)`.
       - Si es `ROLE_ORGANIZER`: Llamar a `registrationRepository.findByEventOrganizerId(organizerId, pageable)`.
       - Si es `ROLE_PARTICIPANT`: Llamar a `registrationRepository.findByUserId(userId, pageable)`.
     - Mapear el resultado paginado a `RegistrationDTO`.
  6. **Método de Utilidad `findCurrentUser()`**:
     - Obtener el principal desde `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`.
     - Buscar el usuario en la BD mediante su email. Si no existe o no está autenticado, lanzar `UnauthorizedException`.

### Acción 6: Exponer los Endpoints REST
* **Archivo:** [RegistrationController.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/registrations/controllers/RegistrationController.java) [NUEVO]
* **Detalle:**
  - Anotar con `@RestController` y `@RequestMapping("/api/registrations")`.
  - Inyectar `RegistrationService`.
  - Definir los siguientes mapeos HTTP:
    1. `GET /api/registrations`: Retorna `ResponseEntity<Page<RegistrationDTO>>` con parámetros `page` (default 0) y `size` (default 10).
    2. `POST /api/registrations/events/{eventId}`: Retorna `ResponseEntity<RegistrationDTO>` tras procesar la inscripción de un usuario autenticado al evento especificado.
    3. `POST /api/registrations/{id}/cancel`: Retorna `ResponseEntity<RegistrationDTO>` tras cambiar el estado a cancelado.

### Acción 7: Desarrollar Suite de Pruebas Unitarias
* **Archivo:** [RegistrationServiceTest.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/test/java/ec/edu/ups/icc/events/registrations/services/RegistrationServiceTest.java) [NUEVO]
* **Detalle:** Utilizar Mockito para aislar la base de datos y mockear el contexto de seguridad. Se deben probar los siguientes escenarios:
  - `registerUserToEvent_Success`: Registro exitoso. Verifica el decremento del cupo y estado `CONFIRMED`.
  - `registerUserToEvent_EventNotFound`: Lanzamiento de `ResourceNotFoundException`.
  - `registerUserToEvent_EventNotPublished`: Lanzamiento de `BusinessRuleException` si el evento está en `DRAFT` o `CANCELLED`.
  - `registerUserToEvent_EventDatePassed`: Lanzamiento de `BusinessRuleException` si la fecha actual es posterior a `startDate`.
  - `registerUserToEvent_NoSeats`: Lanzamiento de `BusinessRuleException` si no quedan cupos (`availableSeats = 0`).
  - `registerUserToEvent_AlreadyRegisteredActive`: Lanzamiento de `BusinessRuleException` si ya tiene una inscripción activa.
  - `registerUserToEvent_Reactivation`: Reactivación exitosa de una inscripción previamente cancelada (`CANCELLED` -> `CONFIRMED`), descontando el cupo de forma regular.
  - `cancelRegistration_Success`: Cancelación exitosa. Cambia el estado a `CANCELLED` y aumenta el cupo disponible del evento en 1.
