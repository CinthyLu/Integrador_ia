# Plan de Desarrollo: Cronograma por Días y Horas

Este documento contiene la planificación detallada para el desarrollo y despliegue del proyecto integrador **API REST Segura para la Gestión de Eventos Académicos**. Está diseñado para completarse en un transcurso de **10 días de desarrollo activo** por un equipo de 2 personas.

---

## 📅 Resumen del Cronograma

El plan asume una dedicación estimada de **3 a 4 horas diarias por integrante** (alrededor de 35-40 horas totales por persona, sumando unas 70-80 horas de esfuerzo de desarrollo conjunto).

| Día | Fase / Tarea Principal | Horas Estimadas | Responsable Sugerido |
| :--- | :--- | :--- | :--- |
| **Día 1** | Configuración de entorno local, Git y Base de Datos | 5h | Ambos |
| **Día 2** | Estructura modular y mapeo de Entidades JPA | 6h | Ambos (dividido por entidades) |
| **Día 3** | Configuración de Spring Security y cifrado BCrypt | 5h | Integrante A |
| **Día 4** | Implementación de JWT (Tokens) y Refresh Tokens | 6h | Integrante B |
| **Día 5** | Módulos de Categorías, Eventos y Sesiones | 6h | Integrante A |
| **Día 6** | Gestión de Inscripciones, transacciones y cupos | 6h | Integrante B |
| **Día 7** | Manejo de excepciones, Bean Validation e Interceptor de Auditoría | 6h | Ambos |
| **Día 8** | Configuración de Redis, Rate Limiting y Bloqueos temporales | 6h | Integrante A |
| **Día 9** | Generación de reportes Excel y PDF descargables | 6h | Integrante B |
| **Día 10**| Docker, pruebas unitarias y despliegue público (Render) | 7h | Ambos |

---

## 🛠️ Detalle Diario de Actividades

### Fase 1: Cimientos y Persistencia (Días 1 y 2)

#### Día 1: Inicialización del Proyecto, Git y PostgreSQL local (5 horas)
*   **Actividades:**
    *   **Configuración del Repositorio Git:** Inicializar Git, crear archivo `.gitignore` adaptado para Spring Boot (excluyendo carpetas como `.gradle`, `target`, `build`, etc.) y configurar la rama principal `main`. (*1 hora*)
    *   **Inicializar Proyecto Spring Boot:** Generar la estructura del proyecto en [Spring Initializr](https://start.spring.io/) con Java 17/21 y dependencias: *Spring Web, Spring Data JPA, Lombok, Validation, Actuator, PostgreSQL Driver, Redis*. (*1.5 horas*)
    *   **Contenedor local de PostgreSQL:** Escribir un archivo `docker-compose.yml` para levantar PostgreSQL y pgAdmin de forma local. (*1.5 horas*)
    *   **Carga del Esquema SQL docente:** Ejecutar en el contenedor PostgreSQL los scripts entregados: `00_create_database.sql` y `01_schema_and_data.sql`. (*1 hora*)
*   **Entregable del día:** Repositorio Git inicializado con estructura de carpetas de Spring Boot y base de datos local corriendo con los datos de prueba.

#### Día 2: Arquitectura Modular y Entidades JPA (6 horas)
*   **Actividades:**
    *   **Estructuración de Carpetas:** Crear paquetes organizados por dominio en el backend (ej. `com.universidad.eventos.modules.user`, `com.universidad.eventos.modules.event`, etc.). (*1 hora*)
    *   **Mapeo de Entidades JPA:** Escribir las clases entidad (`User`, `Role`, `Category`, `Event`, `Session`, `Registration`, `AuditLog`) relacionándolas según el esquema físico precargado. (*3.5 horas*)
    *   **Configuración y Validación de Hibernate:** Configurar el archivo `application.yml` con `spring.jpa.hibernate.ddl-auto=validate`. Arrancar la aplicación y resolver cualquier discrepancia de tipos de datos o nombres de columnas entre JPA y PostgreSQL. (*1.5 horas*)
*   **Entregable del día:** Proyecto compila y arranca con validación de esquema JPA exitosa contra PostgreSQL.

---

### Fase 2: Autenticación, Seguridad y JWT (Días 3 y 4)

#### Día 3: Spring Security y Cifrado de Contraseñas (5 horas)
*   **Actividades:**
    *   **Configuración de Seguridad:** Escribir la clase de configuración de Spring Security (`SecurityConfig`) y definir la cadena de filtros de seguridad (`SecurityFilterChain`). (*2 horas*)
    *   **Carga de Detalles de Usuario:** Implementar `UserDetailsService` y `UserDetails` personalizados para conectar las credenciales de la tabla `users` con el contexto de seguridad de Spring. (*1.5 horas*)
    *   **Cifrado de Credenciales:** Configurar el bean de codificación de contraseñas (`BCryptPasswordEncoder`) y crear el servicio y controlador de registro (`POST /api/auth/register`) encriptando la contraseña en BD. (*1.5 horas*)
*   **Entregable del día:** Endpoint de registro en funcionamiento, contraseñas almacenadas de forma segura y endpoints bloqueados por defecto.

#### Día 4: Autenticación JWT y Refresh Tokens (6 horas)
*   **Actividades:**
    *   **Utilidades JWT:** Escribir el componente `JwtProvider` o `JwtService` para firmar y validar tokens (tiempo de expiración corto, ej. 15 minutos). (*2 horas*)
    *   **Filtro de Autenticación:** Crear `JwtAuthenticationFilter` para interceptar cada petición, extraer la cabecera `Authorization: Bearer <token>` y colocar al usuario en el contexto de seguridad. (*1.5 horas*)
    *   **Estrategia de Refresh Token:** Implementar el endpoint para refrescar el token de acceso (`POST /api/auth/refresh`) y la lógica de cierre de sesión (`POST /api/auth/logout`) con expiración y revocación. (*2.5 horas*)
*   **Entregable del día:** Flujo completo de login (`POST /api/auth/login`), obtención de tokens JWT, renovación mediante refresh tokens e invalidación segura de sesión.

---

### Fase 3: Reglas de Negocio, Transacciones y Controladores (Días 5 a 7)

#### Día 5: Módulos de Categorías, Eventos y Sesiones (6 horas)
*   **Actividades:**
    *   **Capa CRUD de Eventos y Categorías:** Escribir controladores, servicios y repositorios para la gestión de categorías de eventos. (*2 horas*)
    *   **Búsqueda y Paginación:** Implementar en `GET /api/events` búsquedas por texto libre, paginación (`Pageable`), filtros combinados (modalidad, fechas) y ordenamiento dinámico desde base de datos. (*2.5 horas*)
    *   **Autorización de Roles:** Proteger los métodos de creación, modificación y eliminación lógica de eventos asegurándose de que solo usuarios con rol `ADMIN` u `ORGANIZER` propietarios del evento puedan ejecutarlos. (*1.5 horas*)
*   **Entregable del día:** Endpoints de eventos y categorías listos con paginación y validación de permisos de propietario.

#### Día 6: Inscripciones y Transaccionalidad de Cupos (6 horas)
*   **Actividades:**
    *   **Lógica de Inscripción:** Implementar el endpoint `POST /api/events/{eventId}/registrations` para que un participante autenticado se registre en un evento. (*2 horas*)
    *   **Validaciones de Negocio:** Programar validaciones de cupos disponibles, verificar que el evento no haya terminado y validar que el participante no esté registrado previamente en el mismo evento. (*2 horas*)
    *   **Transaccionalidad Atómica:** Anotar el método del servicio con `@Transactional` para garantizar que si la inscripción falla, la cantidad de cupos disponibles del evento no se reduzca o se revierta en caso de fallos. (*2 horas*)
*   **Entregable del día:** Flujo de inscripciones seguro, transaccional y robusto con rechazo automático de solicitudes inválidas.

#### Día 7: Excepciones Centralizadas, Validaciones y Auditoría (6 horas)
*   **Actividades:**
    *   **Manejador de Excepciones Centralizado:** Implementar `@RestControllerAdvice` para capturar todos los errores comunes y transformarlos en respuestas estructuradas en JSON uniformes. (*2.5 horas*)
    *   **Validación de Campos (Bean Validation):** Agregar anotaciones de validación (`@NotNull`, `@Email`, `@Size`, etc.) en los DTOs de entrada y manejar los errores de formato en el advice de excepciones. (*1.5 horas*)
    *   **Sistema de Auditoría:** Implementar un interceptor HTTP, filtro o aspecto AOP que registre en la tabla `audit_logs` todas las llamadas y operaciones críticas (logins fallidos, modificaciones de eventos, inscripciones). (*2 horas*)
*   **Entregable del día:** Respuestas de error estandarizadas y trazabilidad completa de acciones críticas en la base de datos de auditoría.

---

### Fase 4: Redis, Rate Limiting y Reportes (Días 8 y 9)

#### Día 8: Redis y Rate Limiting Distribuido (6 horas)
*   **Actividades:**
    *   **Configuración de Redis:** Configurar las propiedades de conexión de Redis local en Docker y escribir la configuración en Spring Boot. (*1.5 horas*)
    *   **Rate Limiting:** Implementar un filtro que use contadores de Redis con TTL para rechazar peticiones que excedan la tasa máxima permitida por IP o usuario autenticado (ej. máximo 5 logins por minuto). Retornar `429 Too Many Requests`. (*2.5 horas*)
    *   **Bloqueo Temporal de Cuentas/IPs:** Implementar lógica para registrar en Redis los intentos fallidos de inicio de sesión de un usuario y bloquear temporalmente su IP/cuenta si supera los 5 intentos. (*2 horas*)
*   **Entregable del día:** API protegida contra ataques de denegación de servicio (DoS) y fuerza bruta mediante rate limiting y bloqueos automáticos en caché.

#### Día 9: Módulo de Reportes Descargables (6 horas)
*   **Actividades:**
    *   **Generador Excel:** Implementar con Apache POI un servicio que exporte en formato `.xlsx` la lista de participantes inscritos en un evento. (*2 horas*)
    *   **Generador PDF:** Integrar la librería OpenPDF para dar formato y generar un reporte de inscritos en `.pdf` y los certificados individuales de inscripción. (*2.5 horas*)
    *   **Descarga Directa (Streams):** Configurar los endpoints de descarga escribiendo el flujo de bytes directamente en la respuesta HTTP con los headers `Content-Disposition: attachment; filename="..."` y el `Content-Type` correcto. (*1.5 horas*)
*   **Entregable del día:** Botones/endpoints de Swagger que permiten descargar reportes en tiempo real sin guardar archivos temporales en el disco del servidor.

---

### Fase 5: Docker y Despliegue en la Nube (Día 10)

#### Día 10: Contenedores, Pruebas y Despliegue Público (7 horas)
*   **Actividades:**
    *   **Dockerización del Backend:** Crear un `Dockerfile` multi-stage optimizado que compile el código con JDK y cree una imagen ligera de ejecución en JRE. (*1.5 horas*)
    *   **Pruebas Unitarias de Reglas Críticas:** Escribir test unitarios usando JUnit 5 y Mockito para validar las reglas de negocio del servicio de inscripciones y autenticación. (*2.5 horas*)
    *   **Despliegue y Variables de Entorno:** Levantar los servicios en la nube (Render o similar), configurando PostgreSQL y Redis administrados externamente, inyectando variables de entorno seguras (`DB_URL`, `JWT_SECRET`, `REDIS_HOST`, etc.) y limitando la memoria de la JVM. (*3 horas*)
*   **Entregable del día:** API REST pública con Swagger UI accesible y protegido, base de datos e instancia de Redis conectadas en producción.

---

## ❓ Preguntas Abiertas para Coordinar con tu Compañero

Para terminar de afinar los detalles de este plan, consideren responder a las siguientes preguntas:

1.  **¿Tienen fechas límites fijas?** Si la fecha de entrega es en menos o más de 10 días, podemos contraer o expandir el plan para ajustarlo al calendario.
2.  **¿Cuál es la disponibilidad diaria de cada uno?** Esto definirá si las 3-4 horas diarias del plan son realistas o si prefieren organizarlo para fines de semana.
3.  **¿Cómo repartirán la participación en Git?** Recuerden que la rúbrica exige commits en al menos **3 días distintos** por integrante. Es recomendable seguir la asignación sugerida del plan para mantener un historial ordenado y balanceado.
4.  **¿Cuentan ya con los scripts SQL?** Si no los tienen en la carpeta actual, indíquenme si necesitan que los analicemos o simulemos.
