# Especificaciones del Proyecto Integrador: API REST Segura para Gestión de Eventos Académicos

Este documento detalla los requerimientos obligatorios, la arquitectura, el modelo de datos, las reglas de negocio y los criterios de evaluación del proyecto de fin de ciclo, con base en la rúbrica de la asignatura **Programación y Plataformas Web** de la carrera de **Computación** (UPS).

---

## 🛠️ Tecnologías Obligatorias

El backend debe desarrollarse utilizando los siguientes frameworks y herramientas (no se permite el uso de Backend as a Service como Firebase o Strapi):

*   **Lenguaje y Framework:** Java / Spring Boot (Spring Web)
*   **Base de Datos Relacional:** PostgreSQL
*   **Persistencia:** Spring Data JPA / Hibernate (configurado con `spring.jpa.hibernate.ddl-auto=validate`)
*   **Seguridad y Autenticación:** Spring Security & JWT (JSON Web Tokens) + BCrypt para cifrado de contraseñas.
*   **Caché y Seguridad Temporal:** Spring Data Redis (Rate limiting y bloqueos temporales).
*   **Validación de Datos:** Bean Validation (`jakarta.validation`).
*   **Documentación:** Springdoc OpenAPI (Swagger UI).
*   **Observabilidad:** Spring Boot Actuator.
*   **Pruebas Unitarias y de Integración:** JUnit 5 & Mockito.
*   **Contenedores y Despliegue:** Docker, Docker Compose, Render (u otra plataforma compatible como Railway/Fly.io).
*   **Generación de Reportes:** Apache POI (para Excel) y OpenPDF/iText (para PDF).

---

## 🏗️ 1. Arquitectura General (Monolito Modular)

El proyecto debe estar organizado por dominio y separar las siguientes capas dentro de cada módulo:
1.  **Controladores (Controllers):** Exposición de endpoints REST e interacción HTTP.
2.  **Servicios e Interfaces (Services):** Lógica de negocio y reglas de validación.
3.  **Repositorios (Repositories):** Consultas e interacción con la base de datos (Spring Data JPA).
4.  **Entidades (Entities):** Mapeo objeto-relacional de las tablas.
5.  **DTOs (Data Transfer Objects):** Objetos de transferencia para datos de entrada (Request) y salida (Response).
6.  **Mapeadores (Mappers):** Conversión limpia entre Entidades y DTOs (e.g., MapStruct o mapeo manual).
7.  **Excepciones Específicas:** Clases de excepción del dominio para manejo centralizado.

---

## 🗄️ 2. Modelo de Datos y Base de Datos

La estructura de la base de datos es proporcionada por los scripts SQL entregados por el docente. La aplicación **no debe crear ni alterar las tablas automáticamente** (`ddl-auto=validate`).

### Tablas Principales
1.  `users`: Información de usuarios, credenciales cifradas y estado de la cuenta (activo, bloqueado).
2.  `roles`: Definición de los roles del sistema (`ADMIN`, `ORGANIZER`, `PARTICIPANT`).
3.  `user_roles`: Relación de muchos a muchos entre usuarios y roles.
4.  `categories`: Categorías temáticas de los eventos académicos.
5.  `events`: Registro del evento (título, descripción, modalidad, cupo, fechas, organizador y estado).
6.  `sessions`: Sesiones, fechas y horarios específicos que componen un evento.
7.  `registrations`: Inscripciones de los participantes en los eventos.
8.  `audit_logs`: Registro histórico de auditoría para operaciones críticas (creación, edición, eliminación de recursos y accesos erróneos).

### Scripts de inicialización
*   `00_create_database.sql`: Crea la base de datos `academic_events_db`.
*   `01_schema_and_data.sql`: Crea las tablas, relaciones, índices, restricciones y precarga de datos iniciales.

---

## 👥 3. Roles, Autorización y Seguridad

La API debe restringir el acceso a los recursos según el rol del usuario autenticado y validar la propiedad del recurso cuando sea aplicable.

### Roles del Sistema
*   **ADMIN:** Acceso completo. Administra usuarios, roles, categorías, estados y puede ver/generar reportes generales de todo el sistema.
*   **ORGANIZER (Organizador):** Gestiona **únicamente** sus propios eventos, sesiones de sus eventos e inscripciones asociadas a sus eventos.
*   **PARTICIPANT (Participante/Estudiante):** Consulta eventos disponibles (públicos), realiza y cancela sus propias inscripciones, y descarga sus certificados de inscripción.

> **Propiedad del Recurso:** Un `ORGANIZER` no puede modificar o eliminar eventos de otro organizador. Un `PARTICIPANT` no puede ver o cancelar inscripciones de otros usuarios. Esto debe validarse a nivel de servicio/seguridad (prevención de vulnerabilidades BOLA/IDOR).

### Requisitos de Seguridad (JWT y BCrypt)
*   Contraseñas guardadas en la base de datos cifradas usando **BCrypt**.
*   **Access Token JWT:** Corto tiempo de expiración (ej. 15 minutos).
*   **Refresh Token:** Almacenado de forma segura, con expiración y soporte de revocación (cierre de sesión).
*   Protección de endpoints con Spring Security y anotaciones `@PreAuthorize`.
*   Mensajes de error de autenticación genéricos (evitar revelar si el correo electrónico ingresado existe en el sistema).

---

## ⚡ 4. Redis, Rate Limiting y Bloqueos Temporales

El uso de **Redis** es obligatorio para almacenar datos temporales que optimicen la seguridad y resiliencia de la API. No se debe usar para persistir datos del dominio.

### Casos de Uso de Redis
1.  **Rate Limiting Distribuido:** Controlar el número máximo de solicitudes por IP o usuario utilizando contadores con TTL (Time-To-Live).
2.  **Bloqueo Temporal de Cuentas / IPs:** Si un usuario falla varios intentos de inicio de sesión seguidos, bloquear temporalmente la cuenta o su IP en Redis (e.g., prefijo `blocked-user:username`).

### Límites de Solicitudes Obligatorios

| Operación | Identificador | Límite Máximo Permitido | Acción ante Exceso |
| :--- | :--- | :--- | :--- |
| **Inicio de sesión (Login)** | IP + Correo | 5 solicitudes por minuto | `429 Too Many Requests` + `Retry-After` |
| **Registro (Register)** | Dirección IP | 3 solicitudes por hora | `429 Too Many Requests` + `Retry-After` |
| **Endpoints públicos** | Dirección IP | 60 solicitudes por minuto | `429 Too Many Requests` |
| **Endpoints autenticados** | Usuario (User ID) | 120 solicitudes por minuto | `429 Too Many Requests` |
| **Generación de reportes** | Usuario (User ID) | 5 solicitudes por minuto | `429 Too Many Requests` |

---

## 🔀 5. CORS Restringido (Cross-Origin Resource Sharing)

Configuración obligatoria para entornos de producción:
*   Orígenes permitidos cargados dinámicamente desde la variable de entorno `ALLOWED_ORIGINS` (no usar `*` en producción).
*   Métodos HTTP restringidos a: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`.
*   Headers restringidos a los necesarios (e.g., `Authorization`, `Content-Type`).
*   No habilitar credenciales si la estrategia de autenticación por tokens JWT en cabeceras no lo requiere.

---

## 📁 6. Flujo Funcional y Endpoints Sugeridos

### Módulo de Autenticación (`/api/auth`)
*   `POST /api/auth/register` (Público) -> Registro de un nuevo participante.
*   `POST /api/auth/login` -> Autenticación con credenciales. Devuelve JWT y Refresh Token.
*   `POST /api/auth/refresh` -> Renueva el Access Token usando un Refresh Token válido.
*   `POST /api/auth/logout` -> Invalida el Refresh Token actual.
*   `GET /api/auth/me` -> Obtiene los datos del perfil del usuario autenticado actual.

### Módulo de Categorías (`/api/categories`)
*   `GET /api/categories` -> Lista las categorías de eventos (Público).
*   `POST /api/categories` -> Crea una categoría (ADMIN).
*   `PUT /api/categories/{id}` -> Edita una categoría (ADMIN).
*   `DELETE /api/categories/{id}` -> Elimina una categoría (ADMIN).

### Módulo de Eventos y Sesiones (`/api/events`, `/api/sessions`)
*   `GET /api/events` -> Lista eventos con paginación, filtros (por fecha, modalidad, categoría), ordenamiento y búsqueda. (Público).
*   `GET /api/events/{id}` -> Detalle de un evento y sus sesiones. (Público).
*   `POST /api/events` -> Crea un evento (ORGANIZER, ADMIN).
*   `PUT /api/events/{id}` -> Edita un evento (Solo el creador/ORGANIZER o ADMIN).
*   `DELETE /api/events/{id}` -> Eliminación lógica de un evento (No se permite eliminar físicamente si tiene inscritos).
*   `POST /api/events/{eventId}/sessions` -> Añade una sesión de horario a un evento (ORGANIZER, ADMIN).
*   `PUT /api/sessions/{id}` -> Modifica una sesión de horario (ORGANIZER, ADMIN).
*   `DELETE /api/sessions/{id}` -> Elimina una sesión de horario (ORGANIZER, ADMIN).

### Módulo de Inscripciones (`/api/registrations`)
*   `POST /api/events/{eventId}/registrations` -> Inscribe al usuario autenticado actual en un evento (PARTICIPANT).
*   `GET /api/registrations` -> Lista inscripciones. Paginadas y filtradas. (PARTICIPANT ve solo las suyas; ORGANIZER las de sus eventos; ADMIN todas).
*   `DELETE /api/registrations/{id}` -> Cancela una inscripción (PARTICIPANT la suya; ORGANIZER/ADMIN).

### Módulo de Reportes e Indicadores (`/api/reports`)
*   `GET /api/reports/events/{eventId}/registrations.pdf` -> Genera y descarga en PDF la lista de inscritos. (ORGANIZER del evento o ADMIN).
*   `GET /api/reports/events/{eventId}/registrations.xlsx` -> Genera y descarga en Excel la lista de inscritos. (ORGANIZER del evento o ADMIN).
*   `GET /api/registrations/{id}/certificate.pdf` -> Descarga el comprobante de inscripción del participante en formato PDF. (PARTICIPANT propietario de la inscripción).

---

## 🧠 7. Reglas de Negocio Críticas y Transaccionalidad

1.  **Sin Duplicados:** No permitir correos repetidos en el registro de usuarios ni nombres duplicados en categorías.
2.  **Inscripción Única:** Un participante no puede inscribirse más de una vez en el mismo evento.
3.  **Límite de Cupos:** No permitir inscripciones si el evento no cuenta con cupos disponibles.
4.  **Fecha Límite:** No permitir inscripciones en eventos que ya hayan finalizado.
5.  **Transaccionalidad:** El registro de la inscripción y la reducción/actualización del cupo disponible del evento deben realizarse dentro de una transacción de base de datos (`@Transactional`).
6.  **Protección de Datos Históricos:** No eliminar físicamente eventos publicados con inscripciones. Implementar soft-delete o cambiar su estado.
7.  **Paginación:** Todos los endpoints de consulta de colecciones (`events`, `registrations`, etc.) deben retornar datos paginados (`Pageable`), ordenados y filtrados desde la base de datos.

---

## 🚨 8. Manejo Centralizado de Excepciones

Se debe implementar una clase anotada con `@RestControllerAdvice` para capturar todas las excepciones y retornar respuestas estructuradas en formato JSON con la siguiente estructura base:

```json
{
  "timestamp": "2026-07-19T20:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Mensaje detallado del error",
  "path": "/api/events/10/registrations"
}
```

### Excepciones mínimas a manejar
*   Errores de validación de campos (`MethodArgumentNotValidException`) mostrando qué campos fallaron y por qué.
*   Recurso no encontrado (`EntityNotFoundException` o personalizadas).
*   Recurso duplicado / Violación de restricción única (`DataIntegrityViolationException`).
*   Reglas de negocio insatisfechas (ej. falta de cupos, eventos finalizados).
*   Acceso denegado o prohibido (`AccessDeniedException`, `BadCredentialsException`).
*   Tokens JWT inválidos o expirados.
*   Exceso de peticiones/Rate limiting (`TooManyRequestsException`).

---

## 📊 9. OpenAPI (Swagger) y Actuator

*   **Swagger UI:** Toda la API debe estar documentada detallando los controladores, DTOs, códigos de respuesta HTTP y esquemas de datos. Se debe configurar el esquema de seguridad *Bearer JWT* en Swagger para permitir pruebas de endpoints bloqueados.
*   **Protección en Producción:** La documentación de Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) debe protegerse en producción mediante credenciales básicas (Basic Auth) para que solo usuarios autorizados (evaluadores) accedan.
*   **Actuator:** Exponer públicamente únicamente la ruta `/actuator/health` con información de estado simplificada (sin detalles internos de base de datos o disco).

---

## 🗺️ 10. Gestión de Zona Horaria

*   **Zona Horaria de Negocio:** `America/Guayaquil` (Ecuador).
*   **Almacenamiento en Base de Datos:** Los instantes de tiempo deben guardarse en UTC en PostgreSQL.
*   **Intercambio de Datos:** Las fechas y horas en las solicitudes y respuestas de la API REST deben utilizar el formato estandarizado **ISO 8601** (ej. `YYYY-MM-DDThh:mm:ssZ`).
*   **Presentación:** Realizar la conversión de zona horaria a la hora de Ecuador al generar reportes o certificados.

---

## 🐳 11. Docker y Despliegue en la Nube

*   **Contenedor del Backend:** Crear un `Dockerfile` optimizado para la aplicación Spring Boot.
*   **Orquestación Local:** Un archivo `docker-compose.yml` que orqueste la aplicación, la base de datos PostgreSQL y la instancia de Redis local.
*   **Despliegue Público (Render o similar):**
    *   La base de datos PostgreSQL y el servidor Redis deben ser servicios independientes de nube (no locales del contenedor del backend).
    *   Configurar variables de entorno y secretos en la plataforma de despliegue.
    *   La aplicación backend no debe almacenar archivos de reportes en su disco local (los reportes PDF/Excel se generan en memoria y se transmiten al cliente como stream).
    *   Configurar límites de memoria JVM en la variable `JAVA_TOOL_OPTIONS` de la plataforma (ej. `-XX:MaxRAMPercentage=75.0` o `-Xmx` adecuado) para no sobrepasar el límite de memoria del tier gratuito.

---

## 📈 12. Reglas de Git y Trabajo en Parejas

El historial del repositorio Git es un criterio clave de evaluación para validar el trabajo colaborativo.

*   **Frecuencia de Commits:** Cada integrante debe realizar un **mínimo de 5 commits funcionales**.
*   **Distribución Temporal:** Los commits de cada integrante deben estar distribuidos en al menos **3 días diferentes**.
*   **Mensajes Claros:** No se permiten commits vacíos, cambios masivos de formato para simular actividad, o subir todo el proyecto en un único commit al final.
*   **Ramas y Pull Requests:** Utilizar ramas (`feature/xxx`) y simular Pull Requests para unir código, demostrando orden y flujo de trabajo en Git.
