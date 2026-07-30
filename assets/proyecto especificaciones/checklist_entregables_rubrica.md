# 📋 Lista de Control (Checklist) para Nota Máxima - Proyecto Integrador

Este documento consolida todos los **entregables**, **requisitos técnicos** y la **rúbrica de evaluación** (basados en las especificaciones del docente y la guía del proyecto) para que puedas realizar el seguimiento de lo que ya has completado y lo que te falta para obtener la nota máxima (70/70 puntos).

---

## 📂 1. Entregables Obligatorios

Estos son los elementos físicos que debes subir a la plataforma (AVAC) o tener listos para la entrega:

- [ ] **Repositorio Git Público/Privado:**
  - [ ] Código fuente completo y limpio de la API Spring Boot.
  - [ ] Historial de commits completo con participación equilibrada de ambos integrantes.
  - [ ] Uso de ramas (`feature/xxx`) o Pull Requests (PR) simulados.
- [ ] **Base de Datos:**
  - [ ] Diagrama Entidad-Relación (DER) del modelo.
  - [ ] Migraciones de base de datos (con Flyway o similar si es requerido) o scripts SQL de datos iniciales.
- [ ] **Documentación (README.md):**
  - [ ] Guía paso a paso para instalación y ejecución local.
  - [ ] Listado de variables de entorno configurables.
  - [ ] Instrucciones para ejecutar pruebas unitarias y de integración.
  - [ ] Detalles y enlaces de despliegue en la nube.
- [ ] **API Desplegada:**
  - [ ] Enlace público del backend de la API REST funcionando.
  - [ ] Enlace público de Swagger UI (bajo protección de credenciales básicas).
- [ ] **Pruebas:**
  - [ ] Evidencias y reporte de ejecución de las pruebas (cobertura/tests pasados).
- [ ] **Cliente de Prueba:**
  - [ ] Colección exportada de Postman o archivo de configuración de Bruno con todos los endpoints preparados y parametrizados.
- [ ] **Video de Exposición Técnica:**
  - [ ] Video explicativo de máximo 10 minutos.
  - [ ] Demostración del funcionamiento desde Swagger UI local y desde la URL desplegada.
  - [ ] Explicación del código por parte de ambos integrantes.

---

## 📊 2. Rúbrica de Evaluación Detallada (70 Puntos)

### 🧱 Arquitectura y Calidad del Código (7 Puntos)
- [ ] **Monolito Modular por Dominios/Recursos:** Estructura organizada por paquetes de dominio (`users`, `categories`, `events`, `sessions`, `registrations`, `audit`, `reports`) y un paquete transversal `core/` (configuraciones globales, excepciones generales, utilidades).
- [ ] **Aislamiento de Capas:** Separación limpia de:
  - [ ] Controladores (`@RestController`).
  - [ ] Servicios e interfaces (`@Service`).
  - [ ] Repositorios (`@Repository`).
  - [ ] Entidades JPA (`@Entity`).
  - [ ] DTOs de Entrada (`RequestDto`) y Respuesta (`ResponseDto`).
  - [ ] Mapeadores estáticos o con MapStruct para conversión entre DTOs, Modelos y Entidades.
  - [ ] Excepciones personalizadas por dominio.
- [ ] **Auditoría JPA Transversal:** Entidades de negocio heredando de una superclase persistente (`@MappedSuperclass`) como `BaseEntity` que autogestione `createdAt`, `updatedAt` y el estado lógico `deleted`.

### 🗄️ Modelo Relacional, Migraciones y Persistencia (6 Puntos)
- [ ] **Esquema de BD:** Compatibilidad exacta con las tablas del docente: `users`, `roles`, `user_roles`, `categories`, `events`, `sessions`, `registrations`, `audit_logs`.
- [ ] **Hibernate Validado:** Configuración `spring.jpa.hibernate.ddl-auto=validate` activa. La aplicación **no** debe crear ni alterar las tablas al arrancar.
- [ ] **Optimización de Cargas (Fetch Type):** Configuración explícita de `FetchType.LAZY` en todas las relaciones `@ManyToOne`, `@ManyToMany` y `@OneToMany` para evitar consultas redundantes y mitigar el problema de consultas N+1.
- [ ] **Uso de Set:** Uso de `Set<T>` en lugar de `List<T>` para colecciones de relaciones para evitar duplicados en tablas intermedias.

### 🔑 Autenticación JWT y Autorización por Roles (10 Puntos)
- [ ] **Cifrado de Contraseñas:** Cifrado seguro utilizando `BCrypt` (PasswordEncoder) al registrar usuarios.
- [ ] **Filtro de Seguridad:** Filtro personalizado (`JwtAuthenticationFilter`) que extrae, valida y establece la autenticación en el `SecurityContextHolder`.
- [ ] **Estrategia Dual de Tokens:**
  - [ ] **Access Token JWT:** Expiración corta (ej. 15 minutos).
  - [ ] **Refresh Token:** Expiración larga (ej. 7 días) con soporte para renovación automática y revocación en base de datos o Redis.
- [ ] **Autorización Fina:** Control de acceso en endpoints mediante `@PreAuthorize` en base a los roles `ADMIN`, `ORGANIZER` y `PARTICIPANT`.
- [ ] **Validación de Propiedad (Ownership):** Validación en la capa de servicios para que un organizador solo modifique/elimine sus eventos propios, y un participante solo acceda/cancele sus propias inscripciones (prevención de vulnerabilidades IDOR/BOLA).
- [ ] **Mensajes de Seguridad Genéricos:** Mensajes de error generales en login para evitar revelar si un correo electrónico ya está registrado.

### 📅 Endpoints, Reglas de Negocio y Transacciones (10 Puntos)
- [ ] **Endpoints Completos de la API:**
  - [ ] **Autenticación (`/api/auth`):** register, login, refresh, logout, me (perfil actual).
  - [ ] **Categorías (`/api/categories`):** CRUD (GET público, POST/PUT/DELETE restringido a ADMIN).
  - [ ] **Eventos y Sesiones (`/api/events`, `/api/sessions`):** GET público paginado con filtros, POST/PUT/DELETE de eventos y sesiones (restringido a ADMIN/ORGANIZER y validando ownership).
  - [ ] **Inscripciones (`/api/registrations`):** POST inscripción, GET paginado/filtrado por rol, DELETE cancelación.
- [ ] **Reglas de Negocio Críticas:**
  - [ ] Evitar correos duplicados de usuarios y nombres repetidos de categorías.
  - [ ] Un participante no puede inscribirse más de una vez en el mismo evento.
  - [ ] No permitir inscripciones si el evento no cuenta con cupos disponibles (`availableSeats > 0`).
  - [ ] No permitir inscripciones en eventos que ya hayan finalizado.
  - [ ] **Eliminación Lógica (Soft-Delete):** No eliminar físicamente eventos publicados que ya contengan inscripciones (se debe desactivar o usar campo `deleted`).
- [ ] **Paginación Uniforme:** Retorno de datos paginados (`Pageable`), ordenados y filtrados desde la base de datos para todas las consultas de colecciones.
- [ ] **Control Transaccional:** Registro de la inscripción y reducción del cupo del evento ejecutados bajo una transacción atómica (`@Transactional`).

### ⚡ Redis, Rate Limiting, Bloqueos y CORS (7 Puntos)
- [ ] **Rate Limiting Distribuido con Redis:** Control de peticiones atómico según la especificación:
  - [ ] *Login:* Máx 5 req/min (IP + correo) -> `429 Too Many Requests` + cabecera `Retry-After`.
  - [ ] *Registro:* Máx 3 req/hora (IP) -> `429 Too Many Requests` + cabecera `Retry-After`.
  - [ ] *Endpoints Públicos:* Máx 60 req/min (IP) -> `429`.
  - [ ] *Endpoints Autenticados:* Máx 120 req/min (User ID) -> `429`.
  - [ ] *Generación de Reportes:* Máx 5 req/min (User ID) -> `429`.
- [ ] **Bloqueo Temporal de Cuentas:** Bloqueo en Redis (ej. clave `blocked-user:{email}`) tras 5 intentos fallidos de login consecutivos, con TTL de 15 a 30 minutos.
- [ ] **CORS Restringido:**
  - [ ] Orígenes permitidos cargados dinámicamente desde la variable de entorno `ALLOWED_ORIGINS` (no usar `*` en producción).
  - [ ] Métodos HTTP restringidos a: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`.
  - [ ] Cabeceras de peticiones restringidas a las necesarias.

### ⚠️ Validaciones y Manejo Centralizado de Excepciones (5 Puntos)
- [ ] **Validación de Datos:** Uso de `jakarta.validation` (`@NotBlank`, `@Size`, `@Email`, etc.) en DTOs de entrada y `@Valid` en controladores REST.
- [ ] **Controlador de Excepciones Global (`@RestControllerAdvice`):** Capturar y estructurar respuestas JSON uniformes que incluyan:
  - [ ] `timestamp` (fecha y hora del error).
  - [ ] `status` (código de estado HTTP, ej. 400, 404, 409, 429, 500).
  - [ ] `error` (descripción del error HTTP).
  - [ ] `message` (mensaje semántico o de negocio legible).
  - [ ] `path` (URI de la solicitud que falló).
  - [ ] `details` (mapa de errores por campo para fallos de validación).

### 🩺 Swagger Protegido, Actuator y Observabilidad (5 Puntos)
- [ ] **Documentación OpenAPI (Swagger):** 
  - [ ] Detalle de todos los controladores, esquemas DTO, códigos de respuesta HTTP y descripciones.
  - [ ] Configuración del esquema de seguridad *Bearer JWT* para permitir pruebas de endpoints bloqueados directamente en el navegador.
- [ ] **Protección de Swagger en Producción:** Rutas `/swagger-ui/**` y `/v3/api-docs/**` protegidas con **Basic Auth** mediante credenciales configuradas en variables de entorno.
- [ ] **Spring Boot Actuator:** Exponer públicamente de forma exclusiva el endpoint `/actuator/health` con información de estado básica (sin revelar detalles internos).

### 🐳 Docker, Despliegue y Disponibilidad Pública (3 Puntos)
- [ ] **Dockerfile Optimizado:** Construcción multi-stage para compilar la aplicación Spring Boot y correrla sobre un JRE alpino optimizado.
- [ ] **Orquestación Local (Docker Compose):** Archivo `docker-compose.yml` que orqueste la API, PostgreSQL y Redis de manera local para pruebas integradas rápidas.
- [ ] **Despliegue en la Nube (Render, Railway, Fly.io, etc.):**
  - [ ] API backend ejecutándose en un contenedor en la nube.
  - [ ] Instancia de base de datos PostgreSQL en la nube (como servicio independiente).
  - [ ] Instancia de base de datos Redis en la nube (como servicio independiente).
  - [ ] Sin persistencia local de archivos (los reportes Excel y PDF se generan en memoria y se transmiten al cliente como stream de bytes).
  - [ ] Configuración de la JVM para control de memoria en contenedores gratuitos mediante `JAVA_TOOL_OPTIONS` (`-XX:MaxRAMPercentage=75.0` o `-Xmx`).
- [ ] **Configuraciones de Render Específicas:** `server.port=${PORT:8080}`, `spring.jpa.open-in-view=false`, `spring.datasource.hikari.maximum-pool-size=5` (para no agotar conexiones del tier gratuito).

### 📊 Reportes y Comprobantes Descargables
- [ ] **Módulo de Reportes (`/api/reports`):**
  - [ ] **PDF de Inscritos por Evento:** Generado en memoria usando OpenPDF/iText (`/api/reports/events/{eventId}/registrations.pdf`) para ADMIN o el ORGANIZER propietario del evento.
  - [ ] **Excel de Inscritos por Evento:** Generado en memoria usando Apache POI (`/api/reports/events/{eventId}/registrations.xlsx`) para ADMIN o el ORGANIZER propietario.
  - [ ] **Certificado de Inscripción PDF:** Generado en memoria (`/api/registrations/{id}/certificate.pdf`) para el PARTICIPANT dueño de la inscripción.
- [ ] **Formato y Cabeceras:** Retorno de byte stream con `Content-Type: application/pdf` o `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` y `Content-Disposition: attachment; filename="..."`.
- [ ] **Conversión de Zonas Horarias:** Fechas y horas formateadas en la zona de negocio (`America/Guayaquil`) para la visualización del reporte y certificado.

### 👥 Participación en Git y Exposición (3 Puntos + 14 Puntos de Video/Informe)
- [ ] **Frecuencia de Commits:** Mínimo 5 commits funcionales por integrante.
- [ ] **Distribución Temporal:** Commits de cada integrante realizados en al menos 3 días distintos.
- [ ] **Mensajes Descriptivos:** Commits con comentarios claros (evitar mensajes vacíos, arreglos cosméticos excesivos o subir todo el último día).
- [ ] **Video e Informe (14 pts):** Video claro demostrando Swagger local y de producción, demostración de endpoints y participación equitativa.

---

## 📅 3. Gestión y Configuración de Zonas Horarias

- [ ] **PostgreSQL (Almacenamiento):** Todos los campos de tipo `timestamp` almacenados en formato UTC.
- [ ] **Intercambio REST:** Fechas y horas en formato **ISO 8601** con indicador de zona (ej. `2026-07-28T01:30:00Z`).
- [ ] **Presentación en Reportes:** Transformación a la zona horaria `America/Guayaquil` al renderizar PDFs y hojas de cálculo de Excel.
