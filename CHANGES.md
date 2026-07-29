# Justificación de Cambios y Decisiones de Diseño

Este documento detalla la justificación técnica detrás de las decisiones de diseño y arquitectura tomadas en el proyecto desde el último commit. No solo describe *qué* cambió, sino la razón de ingeniería de software para hacerlo.

---

## 🏗️ 1. Desacoplamiento mediante Interfaces (`*ServiceImpl`)
**Decisión:** Convertir los servicios que eran clases directas en interfaces de nivel de paquete e implementar la lógica de negocio en clases `*ServiceImpl` dedicadas.

* **Por qué:** 
  * **Principio de Inversión de Dependencias (SOLID):** Los controladores y demás componentes ahora dependen de abstracciones (interfaces) y no de implementaciones concretas. Esto reduce fuertemente el acoplamiento del sistema.
  * **Facilidad para Pruebas Unitarias:** Permite la creación de mocks de manera limpia, asegurando que las pruebas de los controladores validen la capa HTTP de forma aislada sin levantar contextos pesados.
  * **Extensibilidad:** Facilita la incorporación de nuevas implementaciones (por ejemplo, caching de reportes, llamadas asíncronas o proxies de seguridad) sin alterar el código de los clientes que consumen el servicio.

---

## 📦 2. Segmentación de DTOs Específicos e Integridad de la API
**Decisión:** Reemplazar el uso genérico de `EventDTO` o `CategoryDTO` por DTOs especializados de Entrada (`CreateEventDto`, `UpdateEventDto`, `CreateSessionDto`) y Salida (`EventResponseDto`, `SessionResponseDto`, `CategoryResponseDto`).

* **Por qué:**
  * **Prevención de Ataques de Asignación Masiva (Mass Assignment):** Un DTO genérico expone campos internos como el `id`, fechas de auditoría o el ID del organizador. Si un cliente envía maliciosamente un `id` en una solicitud `POST` o altera datos del organizador, la API podría procesarlo de manera insegura.
  * **Flexibilidad en las Validaciones:** Las reglas de negocio para crear un recurso son distintas a las necesarias para actualizarlo. Con DTOs independientes, se pueden declarar validaciones `@NotNull`, `@NotBlank` o `@Size` específicas en los atributos de entrada, mientras que los de salida permanecen como POJOs limpios de representación.
  * **Seguridad de Información (Encapsulación):** Protege la base de datos evitando exponer columnas de persistencia interna hacia el exterior que no aportan valor al frontend o cliente REST.

---

## 🔒 3. Reorganización del Módulo de Autenticación
**Decisión:** Eliminar el controlador original de la sección de utilidades/core y consolidar un módulo modular `ec.edu.ups.icc.events.auth` con DTOs propios y lógica de seguridad dedicada.

* **Por qué:**
  * **Cohesión Alta y Bajo Acoplamiento:** Los servicios y controladores de autenticación forman un dominio cerrado. Su separación en un subpaquete dedicado mantiene el núcleo (`core`) enfocado únicamente en la configuración global y middlewares de seguridad del framework, mejorando la mantenibilidad.
  * **Mitigación de Fuerza Bruta y Seguridad Activa:** Se incorporó en `AuthServiceImpl` una lógica robusta de seguridad:
    1. Bloqueo temporal por IP en Redis tras 5 intentos fallidos consecutivos en un lapso corto (evita ataques distribuidos por fuerza bruta).
    2. Bloqueo permanente de la cuenta de usuario en base de datos (`accountLocked = true`) hasta que sea restablecido administrativamente, protegiendo las credenciales comprometidas.

---

## 🚨 4. Reintegración de Características Críticas Originales

### A. Borrado Lógico en la Eliminación de Eventos
**Decisión:** Reemplazar la eliminación física de la base de datos por un borrado lógico en `EventServiceImpl.deleteEvent`, actualizando el estado a `CANCELLED`.

* **Por qué:**
  * **Integridad Referencial:** Un evento que ya posee participantes registrados (`RegistrationEntity`) o cronogramas establecidos (`SessionEntity`) no puede eliminarse físicamente sin causar fallos graves de claves foráneas en la base de datos, o requerir eliminaciones en cascada que borrarían los datos históricos del participante.
  * **Trazabilidad:** Preserva los registros históricos de las actividades universitarias e inscripciones previas para auditoría académica, ocultando a la vez el evento de las consultas de búsqueda de carteleras activas.

### B. Auditoría de Seguridad en Login (`@Auditable`)
**Decisión:** Mantener el decorador `@Auditable` en el endpoint `/login`.

* **Por qué:**
  * **Cumplimiento de Políticas de Auditoría:** Registrar todos los accesos exitosos y fallidos a la plataforma es indispensable para detectar inicios de sesión anómalos o sospechosos, y es un requerimiento estándar de cumplimiento y seguridad.

### C. Endpoints de Refresco y Cierre de Sesión (`/refresh` y `/logout`)
**Decisión:** Volver a implementar los flujos de `/refresh` y `/logout` integrándolos a la nueva estructura de DTOs.

* **Por qué:**
  * **Seguridad Exponencial (Blacklisting):** La invalidez de un token JWT suele depender únicamente de su expiración. La ruta `/logout` permite invalidar el token inmediatamente guardándolo en una lista negra en Redis durante 15 minutos, impidiendo que sea robado y reutilizado.
  * **Experiencia de Usuario Ininterrumpida:** La ruta `/refresh` permite al frontend obtener un nuevo token de acceso sin forzar al usuario a ingresar sus credenciales nuevamente una vez vencidos los 15 minutos iniciales.

---

## 🛠️ 5. Bloqueo Pesimista en la Reserva de Asientos (`findByIdForUpdate`)
**Decisión:** Introducir el método `findByIdForUpdate(Long id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `EventRepository.java` para el flujo de inscripciones.

* **Por qué:**
  * **Garantía de No Sobreventa (Prevención de Condiciones de Carrera):** En eventos con alta demanda y pocos cupos libres, si varios usuarios intentan inscribirse simultáneamente, es probable que ocurra una condición de carrera donde múltiples hilos lean la misma cantidad de asientos disponibles antes de que se guarde el descuento de asientos. Al aplicar bloqueo pesimista en base de datos, la fila del evento queda reservada en exclusiva durante la transacción de inscripción de cada usuario, asegurando la consistencia e impidiendo sobreventa.
