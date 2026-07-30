# Análisis de Buenas Prácticas y Cumplimiento con la Lógica de Clase (Spring Boot)

Este reporte analiza el proyecto `academic-events-api` para determinar si cumple con los estándares, la arquitectura modular y las prácticas y patrones de diseño backend en Spring Boot enseñados durante el curso (según las guías proporcionadas).

---

## 📊 Cuadro Comparativo: Proyecto vs Prácticas del Curso

| Concepto de Diseño | Lo Aprendido en Clase (Guías) | Estado en el Proyecto Actual | ¿Cumple? |
| :--- | :--- | :--- | :---: |
| **Interfaces de Servicio** | Separación en interfaz (`UserService`) e implementación (`UserServiceImpl`). | Los servicios son clases directas (ej. `EventService.java`) sin interfaces. | ❌ **No** |
| **Responsabilidad de DTOs** | DTOs específicos e independientes para creación, actualización y respuestas. | Un único DTO para todas las acciones (creación, edición y salida). | ❌ **No** |
| **Validación de Entradas** | DTOs anotados con `@NotBlank`, `@Size`, etc. y `@Valid` en controladores. | DTOs sin ninguna anotación de validación. Controladores sin `@Valid`. | ❌ **No** |
| **Mapeadores Separados** | Clases Mapper independientes (ej. `UserMapper.java`) para aislar la conversión de datos. | Conversión hecha manualmente dentro de los servicios, acoplándolos. | ❌ **No** |
| **Modelo de Dominio** | Modelos internos sin anotaciones JPA (ej. `UserModel.java`) intermedios. | El proyecto no tiene capa de Modelos. Pasa de DTO directamente a Entidad. | ❌ **No** |
| **Nomenclatura de DTOs** | Convención CamelCase (ej. `CreateUserDto`). | Nomenclatura en mayúsculas (ej. `EventDTO`). | ⚠️ **Parcial** |
| **Excepción Base** | Excepciones heredan de `ApplicationException` (asociada a un `HttpStatus`). | Excepciones heredan de `RuntimeException` directamente. | ⚠️ **Parcial** |

---

## 🔍 Detalle de Desviaciones y Qué Debería Cambiar

### 1. Ausencia de Interfaces de Servicio (`Service` e `ServiceImpl`)
* **Problema:** En el proyecto, clases como `EventService`, `CategoryService` y `RegistrationService` son declaradas directamente como clases concretas annotated con `@Service`. Sin embargo, en el curso (Práctica 4) se enseña que se debe definir un contrato a través de una **interfaz** (ej. `UserService.java`) y luego una clase concreta de **implementación** (ej. `UserServiceImpl.java`).
* **Qué debería cambiar:** 
  * Crear interfaces para todos los servicios de negocio (ej. `IEventService.java` o `EventService.java` como interfaz).
  * Renombrar las clases de servicio actuales a `EventServiceImpl.java`, `CategoryServiceImpl.java`, etc., y hacer que implementen sus respectivas interfaces.

### 2. DTO Único sin Responsabilidad Única y sin Validaciones de Entrada
* **Problema:** El proyecto utiliza, por ejemplo, `EventDTO` tanto para recibir la petición de creación (`POST`), la de actualización (`PUT`), como para devolver la información en las consultas (`GET`). Además, **ninguno de los DTOs contiene anotaciones de validación** (como `@NotBlank`, `@Email`, `@Min`, `@Size`, etc.), y los controladores no usan `@Valid`. Esto viola las prácticas de las Prácticas 3 y 6 del curso.
* **Qué debería cambiar:** 
  * Dividir los DTOs por acción. Ejemplo para eventos:
    * `CreateEventDto`: Con validaciones de campos obligatorios como `@NotBlank String title`, `@NotNull LocalDateTime startDate`, `@Min(1) Integer capacity`.
    * `UpdateEventDto`: Para actualizaciones completas.
    * `EventResponseDto`: Para retornar la información (no requiere validaciones de entrada, solo define los campos de salida seguros).
  * Usar la anotación `@Valid` en los parámetros `@RequestBody` de todos los controladores para interceptar errores de validación.

### 3. Acoplamiento de Conversión de Datos (Falta de Mapeadores)
* **Problema:** La lógica para convertir un DTO a Entidad o viceversa se encuentra escrita directamente en los métodos de los servicios. En el curso (Práctica 3, sección 6) se enseña a aislar esta lógica en clases **Mapper** especializadas (como `UserMapper.java`) para mantener los controladores y servicios limpios y desacoplados.
* **Qué debería cambiar:** 
  * Crear clases mappers independientes para cada recurso, por ejemplo, `EventMapper.java` con los métodos estáticos `toEntity(CreateEventDto dto)` y `toResponse(EventEntity entity)`.
  * Quitar la lógica manual de mapeo del código de los servicios y delegarla a estas clases Mapper.

### 4. Omisión del Modelo de Dominio (`Model`)
* **Problema:** En las guías de clase se plantea la existencia de una capa intermedia de **Modelo de Dominio** (`UserModel`) que representa al recurso dentro de la lógica de negocio pero libre de anotaciones de base de datos JPA (a diferencia de `UserEntity`). En la API analizada, esta capa no existe y se pasa directamente de DTO a Entidad persistente.
* **Qué debería cambiar:** 
  * Si se desea apegarse estrictamente a la lógica teórica del curso, se deberían introducir clases de modelo (ej. `EventModel.java`) y mapear en el siguiente orden: `DTO -> Model -> Entity -> DB` y de regreso. 

### 5. Excepciones sin Jerarquía de Aplicación
* **Problema:** Las excepciones como `ResourceNotFoundException` heredan directamente de `RuntimeException`. En la Práctica 7 se introduce la buena práctica de crear una excepción abstracta base llamada `ApplicationException` que contiene el `HttpStatus` asociado, y que las excepciones de dominio extiendan de ella.
* **Qué debería cambiar:**
  * Crear la clase abstracta `ApplicationException extends RuntimeException` con un atributo `HttpStatus`.
  * Modificar `ResourceNotFoundException` y demás excepciones del proyecto para que extiendan de `ApplicationException` e inicialicen el constructor pasándole el estado HTTP (ej. `HttpStatus.NOT_FOUND`).
  * En `GlobalExceptionHandler.java`, simplificar el mapeo capturando directamente `ApplicationException` y usando `exception.getStatus()` dinámicamente.
