# Comparativa de Estructura: Estado Actual vs Propuesta de Buenas Prácticas

Este documento detalla las discrepancias entre la estructura de tu proyecto (`C:\Users\MSI\Desktop\PPW\Proyecto final\academic-events-api`) según el comando `tree` y el diseño modular requerido en la asignatura de Programación y Plataformas Web.

---

## 🔍 Análisis de Discrepancias (Qué está mal)

### 1. Desorganización en el Paquete de Seguridad y Autenticación
* **Estado actual:**
  * Tienes clases críticas de seguridad en `core/security/` (`AuthController.java`, `JwtAuthenticationFilter.java`, `JwtService.java`, `SecurityConfig.java`).
  * Tienes otras configuraciones en `security/config/SecurityBeansConfig.java`.
  * Tienes carpetas vacías en `security/filters/` y `security/services/`.
  * Tienes carpetas vacías en el módulo `auth/` (`auth/controllers/`, `auth/dtos/`, `auth/services/`).
* **Problema:** Hay una duplicidad de responsabilidades. La autenticación (`auth/`) y la seguridad del filtro (`security/`) están mezcladas y dispersas entre `core/` y la raíz.

### 2. Ausencia de Interfaces en los Servicios (`ServiceImpl`)
* **Estado actual:** Todos tus servicios son clases directas dentro de la carpeta `services/` (ej. `EventService.java`, `CategoryService.java`).
* **Problema:** En el curso se exige definir un contrato mediante una **interfaz** de servicio (ej. `EventService.java`) y colocar la lógica en una clase de **implementación** (ej. `EventServiceImpl.java` dentro de `services/impl/`).

### 3. Falta de DTOs Específicos y Mapeadores (`Mappers`)
* **Estado actual:** No existe ninguna carpeta `mappers/` en tus dominios. Además, solo existe un único DTO genérico por módulo (ej. `EventDTO.java`).
* **Problema:** Se está violando el principio de responsabilidad única al reutilizar el mismo DTO para crear, actualizar y retornar datos, y además se están copiando los campos manualmente en los servicios.

---

## 🛠️ Plan de Limpieza y Reorganización (Qué hacer con las carpetas vacías)

### 📂 Carpeta `auth/` (Llenar y Activar)
Actualmente tienes vacías `auth/controllers/`, `auth/dtos/` y `auth/services/`. No las borres; debes usarlas para estructurar el login y registro:
1. Mueve `AuthController.java` de `core/security/` a `auth/controllers/`.
2. Mueve las clases anidadas `LoginRequest` y `RegisterRequest` (que están dentro de `AuthController.java`) a archivos independientes en `auth/dtos/`, renombrándolos a `LoginRequestDto.java` y `RegisterRequestDto.java` (CamelCase).
3. Mueve la lógica de autenticación (los métodos `register` y `login`) a una interfaz `AuthService.java` en `auth/services/` e impleméntalos en `AuthServiceImpl.java` en `auth/services/impl/`.

### 📂 Carpeta `security/` (Centralizar y Ordenar)
Tienes las carpetas `security/filters/` y `security/services/` vacías, y configuraciones en `core/security/`.
1. Mueve `SecurityConfig.java` de `core/security/` a `security/config/SecurityConfig.java`.
2. Mueve `JwtAuthenticationFilter.java` de `core/security/` a `security/filters/JwtAuthenticationFilter.java`.
3. Mueve el filtro de Rate Limiting que vas a crear a `security/filters/RateLimitingFilter.java`.
4. Borra `security/services/` si no vas a meter ninguna lógica adicional allí, ya que la carga de usuarios se maneja en `users/services/CustomUserDetailsService.java`.
5. Elimina por completo la carpeta `core/security/` una vez que esté vacía.

### 📂 Carpeta `users/` (Control de Usuario Autenticado)
Tienes `users/controllers/` y `users/dtos/` vacías.
1. **No las borres** si deseas implementar el endpoint `GET /api/users/me` (para consultar el perfil del usuario autenticado, requerido en el Paso 5 de la guía).
2. Si decides implementarlo:
   * Crea `UserController.java` en `users/controllers/`.
   * Crea `UserResponseDto.java` en `users/dtos/`.
   * Modifica `CustomUserDetailsService.java` para que implemente una interfaz `UserService` en `users/services/` y su lógica esté en `UserServiceImpl.java`.
3. Si la rúbrica no te exige un controlador de usuarios (solo el de auth), entonces puedes **borrar** `users/controllers/` y `users/dtos/` para mantener limpio el proyecto.

### 📂 Carpeta `audit/` (Limpieza)
Tienes `audit/controllers/` y `audit/dtos/` vacías.
* **Bórralas.** El registro de auditoría funciona en segundo plano mediante un Aspecto AOP (`AuditAspect.java`) y se guarda directo en base de datos. Como la API no expone endpoints para que un usuario consulte los logs de auditoría, estas carpetas no se utilizarán y deben eliminarse para mantener el proyecto limpio de carpetas sin usar.

---

## 📈 Resumen de Eliminación y Creación de Carpetas

### ❌ Carpetas a Eliminar (Innecesarias o Duplicadas):
* `src/main/java/.../core/security/` (Se mueve a `security/` y `auth/`).
* `src/main/java/.../audit/controllers/` (No se exponen logs por HTTP).
* `src/main/java/.../audit/dtos/` (No se envían logs por HTTP).
* `src/main/java/.../security/services/` (La lógica se maneja en `users/services/`).

### ➕ Carpetas a Crear (Para cumplir con la Rúbrica):
* `src/main/java/.../events/mappers/` (Conversión de datos).
* `src/main/java/.../categories/mappers/` (Conversión de datos).
* `src/main/java/.../sessions/mappers/` (Conversión de datos).
* `src/main/java/.../registrations/mappers/` (Conversión de datos).
* `src/main/java/.../events/services/impl/` (Clases de implementación).
* `src/main/java/.../categories/services/impl/` (Clases de implementación).
* `src/main/java/.../sessions/services/impl/` (Clases de implementación).
* `src/main/java/.../registrations/services/impl/` (Clases de implementación).
* `src/main/resources/db/migration/` (Para Flyway y base de datos).
