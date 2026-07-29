# 🎤 Guion de Exposición - Cinthya Ramón (CinthyLu)

Este documento contiene tu guion personalizado para la grabación del video de exposición del **Proyecto Integrador (API REST de Gestión de Eventos Académicos)**, enfocado en los componentes que tú implementaste: **Pasos 1, 2, 7 y 12**.

---

## 🎬 1. Primera Intervención (Persistencia JPA - Paso 2)

* **Momento estimado:** Minuto `01:30` al `03:00` (después de la arquitectura general).
* **Qué mostrar en pantalla (en tu IDE):**
  1. El archivo `application.yml` resaltando la línea `spring.jpa.hibernate.ddl-auto: validate`.
  2. La entidad `EventEntity.java` mostrando las anotaciones `@SQLDelete` y `@Where`.
  3. La relación `@ManyToMany(fetch = FetchType.LAZY)` en `UserEntity.java`.

### 🎙️ Guion de voz:
> *"Para el acceso a datos utilizamos Spring Data JPA sobre una base de datos PostgreSQL. Cumpliendo rigurosamente con los requisitos, hemos establecido la propiedad `ddl-auto` de Hibernate en `validate`. Esto garantiza que la aplicación no modifique ni altere las tablas en caliente durante la ejecución, sino que dependa de nuestro script de base de datos `01_schema_and_data.sql`.
> 
> En el código de nuestras entidades, optimizamos la carga de relaciones configurando explícitamente `FetchType.LAZY` para evitar el problema de consultas N+1, es decir, para evitar que la aplicación realice múltiples subconsultas innecesarias a la base de datos al listar registros. Asimismo, utilizamos colecciones de tipo `Set` para prevenir duplicados innecesarios y aplicamos Soft-Delete o borrado lógico en eventos usando las anotaciones `@SQLDelete` y `@Where` de Hibernate en conjunto con el atributo `deleted` de la superclase `BaseEntity`."*

---

## 🎬 2. Segunda Intervención (Inscripciones y Concurrencia - Paso 7)

* **Momento estimado:** Minuto `04:30` al `06:00`.
* **Qué mostrar en pantalla (en tu IDE):**
  1. La clase `RegistrationServiceImpl.java` mostrando el método `registerUserToEvent`.
  2. Resaltar la anotación `@Transactional(rollbackFor = Exception.class)`.
  3. Resaltar la llamada a `eventRepository.findByIdForUpdate(eventId)`.

### 🎙️ Guion de voz:
> *"Las reglas de negocio críticas del sistema se gestionan en la capa de servicios. Por ejemplo, al inscribir a un participante en un evento, el método está protegido por la anotación `@Transactional`. Esto asegura la atomicidad del proceso: si ocurre algún error, se realiza un rollback automático y no se persiste ningún cambio en la base de datos.
> 
> Para mitigar problemas de sobreventa de cupos cuando múltiples participantes intentan inscribirse al mismo tiempo, aplicamos un bloqueo pesimista a nivel de base de datos mediante el método `findByIdForUpdate` en el repositorio. Dentro de este flujo validamos tres reglas de negocio clave: primero, que el participante no esté inscrito previamente en el mismo evento; segundo, que existan asientos disponibles comparando la capacidad límite del evento; y tercero, que el evento esté publicado y no haya finalizado al momento del registro."*

---

## 🎬 3. Tercera Intervención (Docker y Despliegue en Render - Pasos 1 y 12)

* **Momento estimado:** Minuto `09:30` al `10:00` (duración aproximada: 30-40 segundos).
* **Qué mostrar en pantalla:**
  1. El archivo `docker-compose.yml` en tu IDE.
  2. El archivo `render.yaml` en tu IDE.
  3. El navegador mostrando Swagger UI corriendo públicamente en la URL de Render.

### 🎙️ Guion de voz:
> *"Finalmente, para la dockerización y despliegue del sistema, empaquetamos nuestra aplicación utilizando un `Dockerfile` optimizado en múltiples etapas, y construimos un archivo `docker-compose.yml` para coordinar y levantar juntos la API, PostgreSQL y Redis localmente con un solo comando.
> 
> Para el despliegue público en la nube, diseñamos el archivo `render.yaml` que configura de forma automática e integrada todos estos servicios en la plataforma de Render, enlazando las variables de entorno de manera segura sin exponer contraseñas en nuestro código de Git. Adicionalmente, limitamos el pool de conexiones del datasource a un máximo de 5 y configuramos el límite de memoria de la JVM mediante la propiedad `JAVA_TOOL_OPTIONS` en el contenedor para garantizar la estabilidad de la API en el plan gratuito de la nube. Muchas gracias."*
