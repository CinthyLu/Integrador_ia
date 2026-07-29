
Basado en el archivo de especificaciones [analisis_y_guia_correccion.md](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/proyecto%20especificaciones/analisis_y_guia_correccion.md) y una revisión del estado actual del código en la carpeta limpia `C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api`, aquí tienes el desglose detallado de los cambios que **ya están listos** y los que **aún están faltantes**:

---

# 📊 Estado de Avance y Cambios Faltantes

## ✅ Cambios YA Realizados
Estos puntos descritos en la guía de corrección ya se encuentran correctamente implementados en la base de código actual:

1. **Registro de Usuarios y Rol por Defecto:**
   * Se creó e implementó [RoleRepository.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/users/repositories/RoleRepository.java).
   * El servicio [AuthServiceImpl.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/auth/services/AuthServiceImpl.java#L66-L70) asigna correctamente el rol `ROLE_PARTICIPANT` por defecto a los nuevos usuarios y genera tanto el Access Token como el Refresh Token.
2. **Rate Limiting Distribuido y Bloqueo de Cuentas:**
   * Existe el filtro [RateLimitingFilter.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/filters/RateLimitingFilter.java) configurado en Redis.
   * [AuthServiceImpl.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/auth/services/AuthServiceImpl.java#L80-L119) implementa el bloqueo temporal por IP de 15 minutos en Redis al alcanzar 5 intentos fallidos de login y bloquea la cuenta (`accountLocked = true`) en la base de datos.
3. **Seguridad y CORS:**
   * En [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/config/SecurityConfig.java), se retiró `/api/registrations/**` de los accesos públicos sin token y ahora requiere autenticación.
   * Se integró el filtro de Rate Limiting en la cadena de filtros de Spring Security.
4. **Protección de Swagger UI en Producción:**
   * [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/security/config/SecurityConfig.java#L55-L81) incluye una validación de perfil activo (`prod`) que protege la documentación de Swagger mediante Basic Auth (`httpBasic`) en producción, y permite acceso libre en entorno de desarrollo (`dev`).

---

## ❌ Cambios FALTANTES (Pendientes por implementar)
Estos cambios aún no están en la carpeta limpia `C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api` y son necesarios para completar al 100% la guía de corrección:

### 1. Configurar Flyway Migrations (Paso 2.3)
* **En [build.gradle.kts](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/build.gradle.kts):** Agregar las dependencias de Flyway:
  ```kotlin
  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  ```
* **En directorios:** Crear la estructura de carpetas `src/main/resources/db/migration/`.
* **Archivo de migración:** Mover o copiar el script de base de datos (`01_schema_and_data.sql` que está en la raíz) al nuevo directorio con el nombre:
  `src/main/resources/db/migration/V1__initial_schema_and_data.sql`

### 2. Configurar el Soft-Delete con Hibernate (Paso 2.3)
* **En [BaseEntity.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/entities/BaseEntity.java):** Añadir la propiedad `deleted`:
  ```java
  @Column(name = "deleted", nullable = false)
  private Boolean deleted = false;

  public Boolean getDeleted() { return deleted; }
  public void setDeleted(Boolean deleted) { this.deleted = deleted; }
  ```
* **En [EventEntity.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/events/entities/EventEntity.java):** Importar y aplicar las anotaciones de Hibernate `@SQLDelete` y `@Where` para activar el borrado lógico automático:
  ```java
  import org.hibernate.annotations.SQLDelete;
  import org.hibernate.annotations.Where;

  @SQLDelete(sql = "UPDATE events SET deleted = true WHERE id = ?")
  @Where(clause = "deleted = false")
  ```
* **En [EventServiceImpl.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/events/services/EventServiceImpl.java#L99-L104):** Cambiar la lógica manual actual por un delete regular de JPA, para que Hibernate ejecute la sentencia de soft-delete de manera transparente:
  ```diff
  public void deleteEvent(Long id) {
      EventEntity event = findEventById(id);
      verifyOwnership(event);
-     event.setStatus(EventStatus.CANCELLED);
-     eventRepository.save(event);
+     eventRepository.delete(event);
  }
  ```

### 3. Cambiar Relación Eager por Lazy (Paso 2.3)
* **En [UserEntity.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/main/java/ec/edu/ups/icc/events/users/entities/UserEntity.java#L29):** La relación de roles con `@ManyToMany` está usando `FetchType.EAGER`. Para evitar cargas innecesarias y cumplir con la arquitectura recomendada, se debe cambiar a `LAZY`:
  ```java
  @ManyToMany(fetch = FetchType.LAZY)
  ```

### 4. Integrar el Servicio de la API en el Docker Compose (Paso 12.2)
* **En [docker-compose.yml](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/docker-compose.yml):** Solo están definidos los contenedores de `postgres` y `redis`. Falta añadir el servicio `app` para orquestar la construcción y ejecución de la aplicación API completa de manera local:
  ```yaml
    app:
      build: .
      container_name: academic_events_api_app
      ports:
        - "8080:8080"
      depends_on:
        postgres:
          condition: service_healthy
        redis:
          condition: service_healthy
      environment:
        PORT: 8080
        DB_HOST: postgres
        DB_PORT: 5432
        DB_NAME: academic_events_db
        DB_USER: postgres
        DB_PASSWORD: postgrespassword
        REDIS_HOST: redis
        REDIS_PORT: 6379
        JWT_SECRET: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
        ALLOWED_ORIGINS: "http://localhost:3000,http://localhost:8080"
        SWAGGER_USERNAME: admin
        SWAGGER_PASSWORD: adminpassword
        SPRING_PROFILES_ACTIVE: prod
  ```

### 5. Crear el archivo de infraestructura `render.yaml` (Paso 12.3)
* **En la raíz:** Falta crear el archivo `render.yaml` para habilitar el despliegue automático de la base de datos PostgreSQL, la base Redis y el servicio web Spring Boot en Render utilizando la configuración de variables de entorno recomendadas en la sección 9 del análisis.

### 6. Agregar Pruebas Unitarias / de Integración de Concurrencia (Paso 11)
* **En la carpeta de pruebas:** Aunque existen pruebas básicas en [RegistrationServiceTest.java](file:///c:/Users/MSI/Desktop/PPW/Proyecto%20final/academic-events-api/src/test/java/ec/edu/ups/icc/events/registrations/services/RegistrationServiceTest.java), falta añadir una prueba de concurrencia simulando múltiples hilos intentando registrarse a la misma vez para corroborar que el bloqueo pesimista en el repositorio funciona correctamente y previene la sobreventa de cupos.

---

### Resumen de lo realizado:
* Se contrastó el archivo [analisis_y_guia_correccion.md](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/proyecto%20especificaciones/analisis_y_guia_correccion.md) con los archivos reales del proyecto limpio `C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api`.
* Se clasificaron las observaciones en cambios ya aplicados y cambios pendientes.
* Se estructuró este informe en formato Markdown para que puedas proceder con los ajustes sabiendo exactamente qué falta.