# Guía de Estudio para la Defensa del Proyecto Integrador
## API REST Segura para la Gestión de Eventos Académicos
*Asignatura: Programación y Plataformas Web*
*Docente: Ing. Pablo Torres*

---

Esta guía ha sido elaborada a partir de la rúbrica de evaluación y del código fuente real del proyecto. Contiene las preguntas técnicas más probables que el docente puede realizar durante la defensa, junto con sus respuestas detalladas y referencias exactas al código.

---

## 1. Conceptos Fundamentales de Infraestructura: Render y Redis

### **¿Qué es Render y cómo funciona en este proyecto?**
* **Respuesta:** Render es una plataforma en la nube (PaaS - Platform as a Service) que permite compilar, desplegar y ejecutar aplicaciones. En nuestro proyecto, el servicio web de Spring Boot se despliega mediante **Docker** utilizando el archivo [Dockerfile](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/Dockerfile). Render lee la configuración del archivo [render.yaml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/render.yaml) para aprovisionar automáticamente tres servicios independientes en su infraestructura:
  1. Una base de datos relacional **PostgreSQL** (`academic-events-db`).
  2. Una base de datos en caché **Redis** (`academic-events-redis`).
  3. El servicio web de la API en Spring Boot (`academic-events-api`).
* **Nota clave:** Render inyecta las credenciales de conexión y secretos mediante variables de entorno dinámicas (ej. `DB_HOST`, `REDIS_HOST`, `JWT_SECRET`), garantizando que no existan credenciales expuestas ("hardcodeadas") en el código.

### **¿Qué es Redis y cómo funciona en este proyecto?**
* **Respuesta:** Redis (Remote Dictionary Server) es un motor de almacenamiento de datos en memoria en estructura de clave-valor, ultrarrápido, utilizado comúnmente como caché y base de datos temporal. En esta API REST, Redis **no** se usa para persistir datos de negocio permanentes (los cuales van a PostgreSQL), sino para tres funciones críticas de seguridad y rendimiento:
  1. **Rate Limiting (Control de tasa de solicitudes):** Evita el abuso de la API guardando contadores temporales por IP o usuario.
  2. **Bloqueo temporal de usuarios:** Registra los intentos de inicio de sesión fallidos y bloquea temporalmente las peticiones tras múltiples fallos.
  3. **Revocación de Tokens (Lista negra / Blacklist):** Almacena los tokens de los usuarios que han cerrado sesión para invalidarlos inmediatamente.

### **¿Dónde se están guardando los datos y cómo veo los nuevos datos ingresados?**
* **Respuesta:** Todos los datos de negocio (usuarios, roles, categorías, eventos, sesiones, inscripciones y logs de auditoría) se guardan de forma permanente en la base de datos relacional **PostgreSQL**.
* Para ver los nuevos datos, se pueden utilizar dos métodos:
  1. **A nivel de API:** Realizando peticiones de lectura (`GET`) a los endpoints correspondientes (por ejemplo, `GET /api/events` para ver eventos, o mediante la interfaz de Swagger UI).
  2. **A nivel de Base de Datos:** Conectándose directamente al servidor de PostgreSQL usando herramientas cliente como **pgAdmin**, **DBeaver** o la consola `psql` (usando el Host, Puerto, Usuario, Contraseña y base de datos provistos por Render o Docker).

### **En Render, ¿dónde puedo ver las contraseñas, los token hashes y los archivos PDF/Excel generados?**
* **Respuesta:**
  * **Contraseñas:** **Nunca** se guardan en texto plano. Se encriptan utilizando el algoritmo de hash **BCrypt** antes de almacenarse en la columna `password` de la tabla `users` en PostgreSQL. Por lo tanto, ni en Render ni en la base de datos se pueden visualizar las contraseñas originales, solo su hash irreversible (ej. `$2a$10$...`).
  * **Token Hashes:** Los tokens JWT generados son efímeros y no se guardan en la base de datos. Sin embargo, cuando un usuario cierra sesión, el token se guarda en **Redis** con el estado `"blacklisted"` durante su tiempo de vida restante para invalidarlo.
  * **Archivos PDF y Excel:** El contenedor de Spring Boot desplegado en Render es **stateless (sin estado)**. Esto significa que **no** guarda físicamente los archivos PDF o Excel en el disco duro del servidor. En su lugar, los reportes se generan dinámicamente **en memoria** (como arreglos de bytes `byte[]`) bajo demanda en el momento en que el usuario llama al endpoint, y se retornan directamente en el cuerpo de la respuesta HTTP con las cabeceras `Content-Type` y `Content-Disposition` adecuadas. Una vez descargado por el cliente, el archivo desaparece del servidor.

---

## 2. Arquitectura y Modelo de Datos

### **¿Cómo está estructurada la arquitectura modular del proyecto?**
* **Respuesta:** Se sigue un diseño de monolito modular organizado por dominio (módulos como `auth`, `events`, `registrations`, `reports`, `audit`). Cada módulo separa de manera estricta sus responsabilidades en las siguientes capas:
  * **Controllers (Controladores):** Capa de presentación que expone los endpoints HTTP, valida los datos de entrada (`@Valid`) y define la documentación de Swagger.
  * **Services (Servicios):** Contienen las reglas y la lógica de negocio. Es donde se aplican las restricciones y se gestionan las transacciones.
  * **Repositories (Repositorios):** Interfaces que extienden `JpaRepository` para interactuar con la base de datos mediante Spring Data JPA.
  * **Entities (Entidades):** Clases Java anotadas con `@Entity` que representan las tablas en la base de datos relacional.
  * **DTOs (Data Transfer Objects):** Clases de transferencia de datos para separar la estructura de entrada/salida de la API de las entidades de base de datos, evitando exponer información sensible (como la contraseña).
  * **Mappers (Mapeadores):** Clases encargadas de transformar objetos entre entidades y DTOs de forma limpia (ej. `AuthMapper`).
  * **Excepciones específicas:** Clases de excepción personalizadas que se lanzan cuando falla una validación de negocio.

### **¿Por qué configuramos `ddl-auto: validate` en Hibernate? ¿Qué sucede si la estructura de una entidad no coincide con la base de datos?**
* **Respuesta:** La rúbrica exige que la aplicación no cree ni modifique automáticamente la base de datos, ya que esta estructura se maneja estrictamente mediante scripts SQL (`00_create_database.sql` y `01_schema_and_data.sql`). Configurar `spring.jpa.hibernate.ddl-auto=validate` hace que Hibernate verifique durante el arranque de la aplicación si el esquema mapeado en las clases `@Entity` coincide perfectamente con las tablas físicas de la base de datos.
* Si hay alguna discrepancia (por ejemplo, falta una columna, un tipo de dato no coincide, o hay una restricción mal declarada), la aplicación **fallará inmediatamente al iniciar** lanzando un error de validación de esquema, lo que evita comportamientos impredecibles en producción.

### **¿Qué restricciones de integridad a nivel de base de datos implementa el proyecto?**
* **Respuesta:** En el script SQL se aplican restricciones estrictas para asegurar la calidad de los datos:
  * `UNIQUE`: En `roles(name)`, `users(email)`, `categories(name)` e inscripciones (`uk_user_event` para evitar que un participante se inscriba dos veces en el mismo evento).
  * `CHECK` Constraints:
    * `events(modality IN ('ONLINE', 'PRESENTIAL', 'HYBRID'))` y `events(status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'FINISHED'))` para emular enums.
    * `events(capacity > 0)` y `events(available_seats >= 0)`.
    * `events(CONSTRAINT chk_dates CHECK (end_date > start_date))` para asegurar consistencia temporal.
    * `sessions(CONSTRAINT chk_session_times CHECK (end_time > start_time))`.
  * `FOREIGN KEY` con políticas referenciales:
    * `ON DELETE RESTRICT` en `events(organizer_id)` y `events(category_id)` para impedir borrar un usuario u organizador si tiene eventos asociados.
    * `ON DELETE CASCADE` en `sessions(event_id)` y `registrations(event_id)` para borrar en cascada las dependencias si se elimina el evento principal.

---

## 3. Seguridad y Control de Acceso (JWT, Spring Security y CORS)

### **¿En qué parte se encuentra configurada la seguridad de la aplicación?**
* **Respuesta:** La seguridad está centralizada en la clase de configuración [SecurityConfig.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/config/SecurityConfig.java). En este archivo se definen dos cadenas de filtros de seguridad (`SecurityFilterChain`):
  1. `swaggerSecurityFilterChain` (Prioridad 1): Maneja la protección de Swagger (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`). Si el perfil activo es `prod`, exige autenticación básica (`httpBasic`) usando el usuario y contraseña definidos en las variables de entorno; si es `dev`, permite acceso libre.
  2. `apiSecurityFilterChain` (Prioridad 2): Protege los endpoints de la API. Declara como públicos `/api/auth/**` (registro, login, refresh) y `/actuator/health`. Exige autenticación para cualquier otra petición (`anyRequest().authenticated()`), establece sesiones sin estado (`SessionCreationPolicy.STATELESS`) y añade los filtros personalizados `RateLimitingFilter` y `JwtAuthenticationFilter` antes del filtro estándar de Spring Security.

### **¿Dónde se encuentra el endpoint de renovación de tokens (Refresh Token) y cómo funciona?**
* **Respuesta:** El endpoint está ubicado en [AuthController.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/auth/controllers/AuthController.java) en la ruta `POST /api/auth/refresh`.
* **Lógica del Refresh:**
  1. El cliente envía el Refresh Token en la cabecera `Authorization` como un token Bearer.
  2. El método `refresh(authHeader)` en [AuthServiceImpl.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/auth/services/AuthServiceImpl.java) extrae el token y valida que no haya expirado y que la firma sea válida a través de `JwtService`.
  3. Si es válido, extrae el nombre de usuario (email) y genera un **nuevo Access Token** (con expiración corta, ej. 15 minutos) y un **nuevo Refresh Token** (con expiración larga, ej. 7 días), implementando la rotación de tokens (Token Rotation).
* **Por qué es necesario:** Permite mantener al usuario autenticado de forma segura sin pedirle credenciales constantemente, minimizando el impacto si el Access Token (que viaja en cada petición) es interceptado.

### **¿Cómo se aplica la autorización y seguridad por roles y propiedad de recursos?**
* **Respuesta:**
  * **Por Roles:** Se utiliza la anotación `@PreAuthorize` en los controladores con expresiones como `hasAnyRole('ADMIN', 'ORGANIZER')` o `hasRole('PARTICIPANT')`. Por ejemplo, en [ReportController.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/reports/controllers/ReportController.java), solo administradores u organizadores pueden descargar reportes de inscritos.
  * **Por Propiedad:** Además del rol, la lógica valida que un organizador solo pueda manipular o ver datos de sus propios eventos. Esto se resuelve mediante `ReportAccessService` en su implementación [ReportAccessServiceImpl.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/reports/services/ReportAccessServiceImpl.java), la cual obtiene el usuario del contexto de seguridad (`SecurityContextHolder.getContext().getAuthentication()`), carga el evento y verifica si el email del organizador del evento coincide con el nombre del usuario autenticado. De no coincidir, lanza una `ForbiddenException` (HTTP 403).

### **¿Cómo está configurado el CORS y por qué es restringido en producción?**
* **Respuesta:** En `SecurityConfig.java`, el método `corsConfigurationSource()` configura las políticas de origen cruzado:
  * Lee los orígenes permitidos desde la variable de entorno `cors.allowed-origins` (obtenida desde `application.yml`). Si no se define, por defecto se usa el localhost. En Render se configura un origen específico (como la URL de la aplicación Frontend).
  * Restringe los métodos permitidos estrictamente a `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`.
  * Restringe las cabeceras permitidas a `Authorization`, `Content-Type` y `Cache-Control`.
  * Expone la cabecera `Content-Disposition` para permitir que el frontend pueda leer el nombre de los archivos descargados (PDF/Excel).
  * **Por qué es restringido:** En producción no se debe usar `*` (cualquier origen) porque abre la API a vulnerabilidades de CSRF (Cross-Site Request Forgery) y fugas de datos si sitios web maliciosos intentan interactuar con la API en nombre de un usuario autenticado.

---

## 4. Control de Flujo, Transacciones y Reglas de Negocio

### **¿Cómo se gestiona el registro de una inscripción y la reducción del cupo en un evento?**
* **Respuesta:** Esta operación es crítica y requiere consistencia. Se maneja dentro de un método anotado con `@Transactional` en la capa de servicio.
* Al registrar una inscripción:
  1. Se verifica si el evento existe, si está en estado `PUBLISHED` y si la fecha de inicio es futura.
  2. Se valida que el participante no esté inscrito previamente en el mismo evento (evitando duplicados).
  3. Se verifica si hay cupo disponible (`availableSeats > 0`).
  4. Si cumple todo, se crea el registro de inscripción en la tabla `registrations`.
  5. Se decrementa en 1 el contador `available_seats` del evento y se guarda el estado actualizado en la tabla `events`.
* **Transaccionalidad:** Al usar `@Transactional`, Spring envuelve ambas consultas en una sola transacción de base de datos. Si ocurre un fallo en cualquiera de los pasos (por ejemplo, si nos quedamos sin cupo a mitad de proceso o se cae la red), la base de datos realiza un **rollback** automático, dejando el cupo y las inscripciones intactos, evitando inconsistencias (ej. cupos negativos o registros huérfanos).

---

## 5. Uso de Redis Avanzado: Rate Limiting, Bloqueo y Logout

### **¿Cómo se implementa el Rate Limiting distribuido usando Redis?**
* **Respuesta:** Se implementa mediante el filtro [RateLimitingFilter.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/filters/RateLimitingFilter.java) que intercepta todas las peticiones entrantes.
  1. Identifica el endpoint y define el límite correspondiente (según la tabla de la rúbrica).
  2. Resuelve el identificador del cliente: para endpoints públicos usa la dirección IP (obtenida de la cabecera `X-Forwarded-For` para soportar proxies como Render, o `request.getRemoteAddr()`); para endpoints autenticados usa el nombre de usuario de `SecurityContextHolder`.
  3. Genera una clave en Redis con el formato `rate:<tipo>:<identificador>` (ej: `rate:auth:student@ups.edu.ec`).
  4. Llama a `redisTemplate.opsForValue().increment(key)`. Este comando es **atómico** (hilo-seguro).
  5. Si el resultado es `1`, significa que es la primera solicitud en la ventana temporal, por lo que establece un TTL (Tiempo de vida) en Redis usando `redisTemplate.expire(key, duration)` (ej: 60 segundos).
  6. Si el valor devuelto supera el límite permitido, el filtro detiene la petición, establece el código de estado `429 Too Many Requests`, agrega la cabecera `Retry-After` con los segundos restantes, escribe una respuesta estructurada en formato JSON y realiza un `return` para no procesar la petición.

### **¿Cómo funciona el bloqueo temporal de usuarios tras intentos fallidos de inicio de sesión?**
* **Respuesta:** En [AuthServiceImpl.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/auth/services/AuthServiceImpl.java), en el método `login`:
  1. Antes de autenticar, comprueba si existe la clave de bloqueo en Redis: `blocked-user:<ipAddress>:<email>`. Si existe, lanza inmediatamente un `AccountLockedException` (HTTP 423 / 400).
  2. Si no está bloqueado, intenta autenticar con el `AuthenticationManager`.
  3. **Si tiene éxito:** Borra los intentos fallidos acumulados en la clave `login:failed-attempts:<ipAddress>:<email>`.
  4. **Si falla (AuthenticationException):**
     * Incrementa de forma atómica el contador en Redis: `login:failed-attempts:<ipAddress>:<email>`.
     * Si es el primer fallo, le asigna un TTL (por ejemplo, 1 minuto).
     * Si el contador llega al límite (5 intentos fallidos), crea la clave de bloqueo temporal en Redis `blocked-user:<ipAddress>:<email>` con un valor `"true"` y un TTL de 15 minutos.
     * Adicionalmente, busca al usuario en PostgreSQL y actualiza su estado `account_locked = true` para bloquearlo a nivel de persistencia.
     * Lanza `AccountLockedException`.

### **¿Cómo funciona el cierre de sesión (Logout) y la revocación de tokens?**
* **Respuesta:** En `AuthServiceImpl.java`, el método `logout(authHeader)` extrae el token JWT del encabezado `Authorization`.
  * Guarda dicho token como clave en Redis con el valor `"blacklisted"` y un TTL igual al tiempo de expiración restante del token (por defecto 15 minutos en el Access Token).
  * En [JwtAuthenticationFilter.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/filters/JwtAuthenticationFilter.java), antes de validar el JWT y establecer el contexto de seguridad, se verifica si el token existe en Redis con `redisTemplate.hasKey(jwt)`.
  * Si el token está en la lista negra de Redis, el filtro ignora el token y continúa la cadena de filtros sin autenticar al usuario, bloqueando efectivamente cualquier petición subsiguiente con ese token invalidado.

---

## 6. Validación, Excepciones y Reportes

### **¿Cómo funciona el manejo centralizado de excepciones y la validación de campos?**
* **Respuesta:**
  * **Excepciones:** Se implementa un controlador global de excepciones usando la anotación `@RestControllerAdvice` (por ejemplo, en `GlobalExceptionHandler`). Captura excepciones específicas del framework y excepciones de negocio personalizadas (como `ResourceNotFoundException`, `BadRequestException`, `AccountLockedException`). Retorna una respuesta JSON uniforme que contiene: fecha y hora, código de estado HTTP, código de error interno (ej: `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`), mensaje amigable y ruta del endpoint solicitado.
  * **Validación:** Se usa Spring Bean Validation en los DTOs de entrada mediante anotaciones en las propiedades (como `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Min`). En los controladores se coloca la anotación `@Valid` al lado del `@RequestBody`. Si algún campo no cumple las reglas, se lanza un `MethodArgumentNotValidException`, el cual es interceptado por el manejador global para mapear los errores por campo y devolverlos detallados al cliente.

### **¿Cómo se implementa la generación de reportes y qué librerías se utilizan?**
* **Respuesta:** Los reportes se generan bajo demanda en el controlador [ReportController.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/reports/controllers/ReportController.java):
  * **Excel (.xlsx):** Se utiliza la librería **Apache POI (poi-ooxml)** en [ExcelReportServiceImpl.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/reports/services/ExcelReportServiceImpl.java). Crea un libro de trabajo (`SXSSFWorkbook`), añade hojas, filas y celdas con el estilo de cabecera y el listado de participantes inscritos, y escribe todo en un flujo de salida de bytes (`ByteArrayOutputStream`) que se retorna como `byte[]`.
  * **PDF (.pdf):** Se utiliza la librería **OpenPDF (librepdf)** en [PdfReportServiceImpl.java](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/reports/services/PdfReportServiceImpl.java). Genera un documento PDF estructurado (con cabecera del congreso, tablas de datos formateadas, firmas) y escribe los datos en un flujo de bytes.
  * **Entrega al cliente:** El controlador recibe los bytes del reporte y construye un `ResponseEntity<byte[]>` estableciendo el tipo de contenido adecuado (`application/pdf` o `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) y la cabecera `Content-Disposition` con el valor `attachment; filename="nombre_archivo.pdf"`, lo que fuerza al navegador del cliente a iniciar la descarga del archivo con el nombre correcto.

---

## 7. Swagger, Actuator y Observabilidad

### **¿Cómo se protegen los endpoints de documentación de Swagger UI en producción?**
* **Respuesta:** En `SecurityConfig.java`, la cadena de filtros `swaggerSecurityFilterChain` captura las rutas de Swagger. En desarrollo las permite a todos, pero en producción comprueba si el perfil de la aplicación es `prod`.
* Si es producción, activa autenticación básica (`httpBasic()`) y configura un administrador de usuarios en memoria (`InMemoryUserDetailsManager`) con las credenciales cargadas de las variables de entorno `SWAGGER_USER` y `SWAGGER_PASSWORD`.
* **Detalle técnico / Bug potencial a mencionar:** En el archivo [render.yaml](file:///C:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/render.yaml) del despliegue se definieron las variables como `SWAGGER_USERNAME=admin` y `SWAGGER_PASSWORD=adminpassword`. Sin embargo, en `application.yml` y `SecurityConfig.java`, el código busca `SWAGGER_USER` (o sea, `swagger.user: ${SWAGGER_USER:swagger}`). Si la variable en Render está como `SWAGGER_USERNAME` en lugar de `SWAGGER_USER`, el usuario por defecto será `"swagger"` (el fallback del archivo YML) y la contraseña será `"adminpassword"`. Esto demuestra un análisis riguroso de la consistencia entre el despliegue y la configuración.

### **¿Qué es Spring Boot Actuator y qué endpoints expone?**
* **Respuesta:** Spring Boot Actuator es un subproyecto que añade funcionalidades de producción a nuestra aplicación, permitiendo monitorizar y gestionar la aplicación a través de endpoints HTTP.
* En `application.yml`, para cumplir con la rúbrica y no exponer detalles sensibles de la infraestructura en producción, se configura `management.endpoints.web.exposure.include=health` y `management.endpoint.health.show-details=never`.
* Esto significa que solo se expone públicamente la ruta `/actuator/health` (o `/api/actuator/health`), la cual responde con un JSON básico `{"status": "UP"}` si la aplicación y la conexión a base de datos están correctas, sin mostrar información sensible como espacio en disco, detalles de conexión o estado detallado de bases de datos.
--------


----
Viewed Dockerfile:1-23
Viewed 00_create_database.sql:1-6

Para ejecutar tu proyecto de manera local usando Docker, es necesario seguir un orden específico. Esto se debe a que la base de datos se valida al iniciar (`ddl-auto: validate`), por lo que las tablas deben existir **antes** de que el servidor web de Java termine de arrancar.

Aquí tienes el paso a paso detallado para hacerlo hoy mismo:

---

### **Requisito previo:**
Asegúrate de que **Docker Desktop** esté abierto y ejecutándose en tu computadora.

---

### **Paso 1: Abrir la terminal en la carpeta del proyecto**
Abre una terminal (PowerShell o CMD) y navega a la carpeta de tu proyecto final:
```powershell
cd "C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api"
```

---

### **Paso 2: Iniciar solo la Base de Datos y Redis**
Para evitar que Spring Boot falle al buscar las tablas, primero encenderemos únicamente los motores de base de datos:
```powershell
docker compose up -d postgres redis
```
*Espera unos **5 a 10 segundos** a que los contenedores inicien por completo.*

---

### **Paso 3: Cargar el esquema y los datos iniciales**
Ahora inyectaremos las tablas y la precarga de usuarios del script `01_schema_and_data.sql` dentro del contenedor de PostgreSQL:
```powershell
docker exec -i academic_events_db_container psql -U postgres_user -d academic_events_db < 01_schema_and_data.sql
```

*(Opcional) **¿Cómo verificar que se cargaron?** Ejecuta este comando para ver el listado de tablas:*
```powershell
docker exec -it academic_events_db_container psql -U postgres_user -d academic_events_db -c "\dt"
```
*Deberías ver una lista con las tablas `users`, `roles`, `events`, `registrations`, etc.*

---

### **Paso 4: Compilar e iniciar la aplicación de Spring Boot**
Una vez que la base de datos tiene las tablas, ya podemos compilar e iniciar el contenedor de nuestra API:
```powershell
docker compose up --build -d app
```
*Este comando compilará el código de Java dentro del contenedor de Docker (no necesitas tener Java o Gradle instalados localmente en tu computadora) y encenderá la aplicación.*

---

### **Paso 5: Verificar que todo funcione**
1. **Comprobar que los 3 contenedores estén activos:**
   ```powershell
   docker compose ps
   ```
   *Los tres servicios (`academic_events_db_container`, `academic_events_redis_container` y `academic_events_api_app`) deben mostrar el estado `Up` o `running`.*

2. **Ver los logs de arranque de la aplicación:**
   ```powershell
   docker compose logs -f app
   ```
   *Deberías ver el logo animado de Spring y al final un mensaje indicando que la aplicación inició en el puerto `8080` (ej: `Started AcademicEventsApiApplication in X seconds`). Puedes presionar `Ctrl + C` para salir de los logs.*

---

### **Paso 6: Probar en el navegador**
* **Swagger UI:** Abre en tu navegador la ruta [http://localhost:8080/api/swagger-ui.html](http://localhost:8080/api/swagger-ui.html) para ver y probar todos los endpoints locales.
* **Health Check:** Entra a [http://localhost:8080/api/actuator/health](http://localhost:8080/api/actuator/health) y debería responderte `{"status":"UP"}`.

---

### **Comandos Útiles Adicionales:**

* **Apagar el entorno local:**
  ```powershell
  docker compose down
  ```
* **Apagar y borrar los datos (limpiar base de datos para empezar de cero):**
  ```powershell
  docker compose down -v
  ```