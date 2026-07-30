# 🎬 Guía Completa de Guion y Estructura: Video de Exposición Técnica

Este documento es una guía paso a paso para la grabación de su video de exposición del **Proyecto Integrador (API REST de Gestión de Eventos Académicos)**. Ha sido estructurado para garantizar que cumplan con todos los requisitos de la rúbrica del docente (Ing. Pablo Torres) y logren la calificación máxima (**14/14 puntos en el rubro de Video/Informe** y aportes en los demás rubros técnicos).

---

## 📌 Requisitos Clave del Video
1. **Duración Máxima:** 10 minutos (¡estricto!). El guion está diseñado para durar entre **8 y 9 minutos** para darles un margen de seguridad.
2. **Participación Equitativa:** 50% de tiempo de voz y explicación para cada uno de los dos integrantes.
3. **Demostración Dual:** Se debe mostrar el funcionamiento tanto en **Local (Swagger UI / Docker)** como en **Producción (Render con URLs públicas)**.
4. **Calidad Visual/Audio:** Graben a resolución 1080p, con micrófonos limpios de ruido y compartiendo pantalla en pantalla completa. Se recomienda usar OBS Studio, Microsoft Teams, Zoom o Discord para grabar en conjunto.

---

## 👥 Distribución de Roles Sugerida
*   **Integrante A (`[Nombre Integrante A]`):** Enfocado en Arquitectura modular, Seguridad JWT, Redis (Rate Limiting y Bloqueos) y Observabilidad (Actuator).
*   **Integrante B (`[Nombre Integrante B]`):** Enfocado en Persistencia (PostgreSQL y Hibernate), Lógica de Negocio y Transacciones (Inscripciones), Reportes (PDF/Excel) y Despliegue en Docker/Render.

---

## ⏱️ Estructura del Video (Resumen de Tiempos)

```mermaid
gantt
    title Cronograma del Video (Máx 10 minutos)
    dateFormat  m s
    axisFormat %M:%S
    section Integrante A
    Intro y Arquitectura (00:00 - 01:30) :active, a1, 00m 00s, 01m 30s
    Seguridad JWT & OAuth2 (03:00 - 04:30) : a3, 03m 00s, 04m 30s
    Redis, Rate Limiting & Locks (06:00 - 07:30) : a5, 06m 00s, 07m 30s
    Demo Swagger Local (09:00 - 09:30) : a7, 09m 00s, 09m 30s
    section Integrante B
    Persistencia y Hibernate (01:30 - 03:00) : b2, 01m 30s, 03m 00s
    Lógica de Negocio (04:30 - 06:00) : b4, 04m 30s, 06m 00s
    Excepciones y Reportes (07:30 - 09:00) : b6, 07m 30s, 09m 00s
    Demo Swagger Render & Cierre (09:30 - 10:00) : b8, 09m 30s, 10m 00s
```

---

## 📝 Guion Detallado Escena por Escena

### Escena 1: Introducción y Arquitectura de Código
*   **Duración:** 00:00 a 01:30 (1:30 min)
*   **Orador:** Integrante A
*   **Qué mostrar en pantalla:** IDE (VS Code o IntelliJ) mostrando el árbol de directorios del proyecto expandido. Resaltar la estructura modular por dominios (`users`, `events`, `registrations`, etc.).
*   **Guion de Voz (Qué decir):**
    > *"Buenas con todos. En este video presentaremos el backend del Proyecto Integrador para la gestión de eventos académicos. Hemos diseñado una arquitectura monolítica modular orientada a dominios y recursos. Como se aprecia en la estructura de paquetes, cada módulo como `users`, `events`, `categories` y `registrations` contiene de forma aislada sus controladores, servicios, repositorios, entidades JPA, DTOs de petición y respuesta, y sus mapeadores específicos. Además, contamos con una capa transversal `core` para configuraciones generales de seguridad, excepciones globales y auditoría automática usando una superclase persistente `BaseEntity` que registra la creación, modificación y eliminación lógica."*

---

### Escena 2: Modelo de Persistencia y Hibernate Validado
*   **Duración:** 01:30 a 03:00 (1:50 min)
*   **Orador:** Integrante B
*   **Qué mostrar en pantalla:**
    1. El archivo `application.properties` o `application.yml` donde se vea `spring.jpa.hibernate.ddl-auto=validate`.
    2. El archivo SQL `01_schema_and_data.sql` en el IDE.
    3. Código de una clase entidad (ej. `Event.java`) mostrando `@ManyToOne(fetch = FetchType.LAZY)` y colecciones usando `Set<T>` en lugar de `List<T>`.
*   **Guion de Voz (Qué decir):**
    > *"Para el acceso a datos utilizamos Spring Data JPA sobre una base de datos PostgreSQL. Cumpliendo rigurosamente con los requisitos, hemos establecido la propiedad `ddl-auto` de Hibernate en `validate`. Esto garantiza que la aplicación no modifique ni altere las tablas en caliente durante la ejecución, sino que dependa de nuestro script de base de datos `01_schema_and_data.sql`. En el código de nuestras entidades, optimizamos la carga de relaciones configurando explícitamente `FetchType.LAZY` para evitar problemas de consultas N+1, y utilizamos colecciones de tipo `Set` para prevenir duplicados innecesarios al realizar operaciones de persistencia."*

---

### Escena 3: Seguridad, Autenticación JWT y Roles
*   **Duración:** 03:00 a 04:30 (1:30 min)
*   **Orador:** Integrante A
*   **Qué mostrar en pantalla:**
    1. Código de `SecurityConfig.java` con el filtro de autenticación y los bloques `@PreAuthorize` o autorizaciones por ruta.
    2. El método de registro mostrando el uso de `PasswordEncoder` (BCrypt).
    3. Código de renovación de tokens (Refresh Token) y cierre de sesión (Logout).
*   **Guion de Voz (Qué decir):**
    > *"La seguridad se implementó con Spring Security y tokens JWT de doble estrategia. Las contraseñas se almacenan cifradas en la base de datos mediante el algoritmo BCrypt. El flujo consiste en que el usuario inicia sesión en `/api/auth/login`, recibe un Access Token con validez de 15 minutos y un Refresh Token de larga duración. Mediante anotaciones `@PreAuthorize` controlamos los roles: los administradores tienen control total, los organizadores gestionan únicamente sus propios eventos, y los participantes pueden registrarse en eventos públicos y consultar sus datos. Al hacer logout, el token se invalida del lado del servidor."*

---

### Escena 4: Reglas de Negocio Críticas y Transaccionalidad
*   **Duración:** 04:30 a 06:00 (1:30 min)
*   **Orador:** Integrante B
*   **Qué mostrar en pantalla:**
    1. Código del servicio `RegistrationServiceImpl.java` donde se ejecuta el registro a un evento.
    2. Resaltar la anotación `@Transactional(rollbackFor = Exception.class)`.
    3. Resaltar las validaciones: no duplicar registros, validar cupo máximo y verificar que el evento no haya finalizado.
*   **Guion de Voz (Qué decir):**
    > *"Las reglas de negocio críticas del sistema se gestionan en la capa de servicios. Por ejemplo, al inscribir a un participante en un evento, el método está protegido por la anotación `@Transactional`. Esto asegura la atomicidad: si ocurre un error, se realiza un rollback automático de la base de datos. Dentro de este flujo, validamos tres reglas clave: primero, que el participante no esté inscrito previamente en el mismo evento; segundo, que existan cupos disponibles comparando las inscripciones con la capacidad límite del evento; y tercero, que el evento no haya finalizado al momento de la inscripción."*

---

### Escena 5: Redis, Rate Limiting y Concurrencia
*   **Duración:** 06:00 a 07:30 (1:30 min)
*   **Orador:** Integrante A
*   **Qué mostrar en pantalla:**
    1. Configuración de conexión de Spring Data Redis.
    2. Código del filtro de Rate Limiting o el interceptor que limita peticiones por IP o usuario.
    3. Lógica del bloqueo de inicio de sesión tras múltiples fallos (ej. bloqueo temporal en Redis por 10 minutos).
*   **Guion de Voz (Qué decir):**
    > *"Para robustecer el sistema, integramos Redis en memoria. Implementamos un Rate Limiting distribuido que restringe el número de peticiones (como un límite de 5 intentos de inicio de sesión por minuto, o 60 solicitudes generales para endpoints públicos). Además, Redis nos sirve para el bloqueo temporal de cuentas o IPs: si un usuario ingresa credenciales incorrectas en 5 ocasiones seguidas, bloqueamos su dirección de correo en Redis con un TTL dinámico, devolviendo un estado HTTP 429 de forma segura y previniendo ataques de fuerza bruta."*

---

### Escena 6: Excepciones Centralizadas, Reportes y Swagger Protegido
*   **Duración:** 07:30 a 09:00 (1:30 min)
*   **Orador:** Integrante B
*   **Qué mostrar en pantalla:**
    1. La clase global `@RestControllerAdvice` y capturas de excepciones personalizadas.
    2. El controlador de reportes `/api/reports` mostrando la generación en memoria de Excel (Apache POI) y PDF (OpenPDF).
    3. La configuración de seguridad de Swagger UI protegida por credenciales básicas.
*   **Guion de Voz (Qué decir):**
    > *"Diseñamos un manejador centralizado de excepciones con `@RestControllerAdvice` que captura errores de validación, violaciones de negocio o fallos de autenticación, respondiendo con un formato JSON amigable y consistente. También implementamos la generación de reportes en memoria para evitar saturar el disco. Con Apache POI generamos reportes de inscritos en formato Excel y con OpenPDF generamos certificados en PDF, retornando un flujo de bytes directo al cliente. Finalmente, el acceso a la documentación en vivo a través de Swagger UI se encuentra restringido bajo credenciales de seguridad básica."*

---

### Escena 7: Demostración en Vivo (Entorno Local y Producción en Render)
*   **Duración:** 09:00 a 10:00 (1:00 min)
*   **Orador:** Ambos
*   **Qué mostrar en pantalla:**
    *   **Integrante A (Local):** Swagger UI en `http://localhost:8080/swagger-ui.html` mostrando el login de un usuario, cómo se guarda el token JWT automáticamente y la llamada al endpoint `/actuator/health` mostrando el estado UP.
    *   **Integrante B (Producción):** Swagger UI en la URL pública de Render (`https://...onrender.com/swagger-ui/index.html`). Ejecutar la creación de una inscripción y descargar un certificado PDF generado en vivo para validar el correcto funcionamiento en la nube.
*   **Guion de Voz (Qué decir):**
    *   **Integrante A:**
        > *"A continuación, vemos la interfaz de Swagger UI ejecutándose en nuestro entorno local contenedorizado con Docker Compose. Nos autenticamos y, como observan, obtenemos el token de acceso. Si llamamos a Spring Boot Actuator en `/actuator/health`, vemos que todo el sistema y la base de datos reportan un estado saludable."*
    *   **Integrante B:**
        > *"Para concluir, mostramos nuestra API desplegada de manera pública en la nube sobre Render. Haremos una petición para descargar el comprobante de inscripción de un participante. Como ven en el navegador, se descarga y se genera en vivo un documento PDF estructurado con la zona horaria correcta. Esto demuestra el flujo completo y la resiliencia del sistema. Muchas gracias."*

---

## 🛠️ Lista de Verificación Antes de Grabar (¡Evita Errores!)

- [ ] **Limpiar Base de Datos:** Ejecuta los scripts SQL para que la base de datos tenga los registros iniciales limpios y no haya datos corruptos de pruebas previas.
- [ ] **Levantar Docker Local:** Asegúrate de que `docker-compose up` esté corriendo localmente con PostgreSQL y Redis activos en los puertos correspondientes.
- [ ] **Revisar Despliegue en Render:** Entra a tu dashboard de Render y comprueba que tanto la base de datos, Redis y el servicio del backend estén activos. Si están inactivos, haz una llamada inicial para despertarlos, ya que los tiers gratuitos tardan unos minutos en iniciar.
- [ ] **Probar Credenciales de Swagger:** Comprueba que puedes iniciar sesión en el Swagger UI público usando el usuario y contraseña básicos configurados.
- [ ] **Prueba de Audio:** Hagan una pequeña grabación de prueba de 10 segundos para verificar que la voz de ambos integrantes se escuche con volumen y claridad similares.
