# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – Control Global de Errores y Excepciones

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
</div>

---

# Práctica 7 (Spring Boot): Manejo Global de Errores y Excepciones

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En las prácticas anteriores se implementó:

* controladores
* servicios
* DTOs con validación
* modelos de dominio
* entidades persistentes
* mappers
* repositorios JPA
* conexión a PostgreSQL
* eliminado lógico mediante `deleted`

Hasta este punto, la API ya puede recibir datos, validarlos, procesarlos y guardarlos en base de datos.

Sin embargo, todavía existe un problema importante: los errores no se manejan de forma centralizada.

Actualmente pueden existir errores como:

```java
throw new IllegalStateException("User not found");
```

o respuestas manuales como:

```java
return new ErrorResponseDto("User not found");
```

Este enfoque funciona para prácticas iniciales, pero no es adecuado para una API más organizada.

Un backend no debe manejar errores de forma distinta en cada controlador o servicio.

En esta práctica se implementa un sistema global de manejo de errores usando:

* excepciones propias de la aplicación
* excepciones de dominio
* un DTO único de error
* `@RestControllerAdvice`
* `@ExceptionHandler`

El objetivo es que todos los errores de la API tengan un formato uniforme y que los servicios solo expresen el error, sin construir respuestas HTTP manualmente.

---

# 2. Problema actual

Antes de esta práctica, cuando un usuario no existía, el servicio podía tener algo como:

```java
@Override
public UserResponseDto findOne(Long id) {

    return userRepository.findById(id)
            .map(UserMapper::toModelFromEntity)
            .map(UserMapper::toResponse)
            .orElseThrow(() -> new IllegalStateException("User not found"));
}
```

Esto genera una excepción genérica.

El problema es que `IllegalStateException` no indica claramente qué tipo de error HTTP debe devolver la API.

El cliente podría recibir una respuesta técnica o inconsistente:

```json
{
  "timestamp": "2025-12-26T20:02:02.067Z",
  "status": 500,
  "error": "Internal Server Error",
  "trace": "java.lang.IllegalStateException: User not found..."
}
```

Ese resultado no es correcto porque un recurso inexistente no debe responder como error interno del servidor.

Debe responder como:

```txt
404 Not Found
```

---

# 3. Flujo después de aplicar manejo global de errores

El flujo será:

```txt
Cliente
  ↓
UsersController
  ↓
UserService
  ↓
UserServiceImpl
  ↓
lanza NotFoundException / ConflictException / BadRequestException
  ↓
GlobalExceptionHandler
  ↓
ErrorResponse
  ↓
Cliente
```

El servicio ya no construye respuestas de error.

El controlador ya no usa `try/catch`.

El handler global se encarga de convertir excepciones en respuestas HTTP.

---

# 4. Estructura del paquete de excepciones

Se creará una estructura global para errores dentro de:

```txt
src/main/java/ec/edu/ups/icc/fundamentos01/core/exceptions/
```

Estructura recomendada:

```txt
core/
└── exceptions/
    ├── base/
    │   └── ApplicationException.java
    │
    ├── domain/
    │   ├── NotFoundException.java
    │   ├── ConflictException.java
    │   └── BadRequestException.java
    │
    ├── handler/
    │   └── GlobalExceptionHandler.java
    │
    └── response/
        └── ErrorResponse.java
```

Esta estructura permite separar:

```txt
base      → excepción raíz de la aplicación
domain    → errores del negocio
handler   → conversión global de excepciones a HTTP
response  → formato único de respuesta de error
```

---

# 5. Excepción base de la aplicación

Archivo:

```txt
core/exceptions/base/ApplicationException.java
```

Código:

```java
/*
 * Excepción base de la aplicación.
 *
 * Todas las excepciones propias del sistema deben extender de esta clase.
 * Permite asociar cada error con un HttpStatus específico.
 */
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    protected ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
```

---

## 5.1. Explicación

`ApplicationException` es la clase padre de las excepciones propias del sistema.

Permite que cada error tenga asociado un estado HTTP.

Ejemplo:

```txt
NotFoundException  → 404 Not Found
ConflictException  → 409 Conflict
BadRequestException → 400 Bad Request
```

Esto evita lanzar excepciones genéricas como:

```java
IllegalStateException
RuntimeException
Exception
```

---

# 6. Excepciones de dominio

Las excepciones de dominio representan errores propios de la lógica de negocio.

No construyen respuestas HTTP.

Solo expresan qué error ocurrió.

---

## 6.1. NotFoundException

Se utiliza cuando un recurso no existe o está eliminado lógicamente.

Archivo:

```txt
core/exceptions/domain/NotFoundException.java
```

Código:

```java
/*
 * Excepción usada cuando un recurso no existe
 * o no está disponible para la operación solicitada.
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
```

### Cuándo usarla

Usar `NotFoundException` cuando:

* se busca un usuario inexistente
* se intenta actualizar un producto inexistente
* se intenta eliminar un registro ya eliminado
* el recurso existe en base de datos pero tiene `deleted = true`

Ejemplo:

```java
throw new NotFoundException("User not found");
```

---

## 6.2. ConflictException

Se utiliza cuando existe un conflicto con el estado actual del sistema.

Archivo:

```txt
core/exceptions/domain/ConflictException.java
```

Código:

```java
/*
 * Excepción usada cuando existe un conflicto
 * con el estado actual del recurso.
 */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
```

### Cuándo usarla

Usar `ConflictException` cuando:

* se intenta crear un usuario con email duplicado
* se intenta crear un producto con nombre duplicado
* se viola una regla de unicidad
* existe un conflicto lógico con datos ya registrados

Ejemplo:

```java
throw new ConflictException("Email already registered");
```

---

## 6.3. BadRequestException

Se utiliza cuando la solicitud tiene datos que no pueden procesarse por reglas de negocio.

Archivo:

```txt
core/exceptions/domain/BadRequestException.java
```

Código:

```java
/*
 * Excepción usada cuando la solicitud es inválida
 * según reglas de negocio.
 */
public class BadRequestException extends ApplicationException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
```

### Cuándo usarla

Usar `BadRequestException` cuando:

* los datos son válidos sintácticamente, pero inválidos para el negocio
* se intenta realizar una operación no permitida
* se incumple una regla que no puede validarse solo con anotaciones

Ejemplo:

```java
throw new BadRequestException("Stock insufficient");
```

---

# 7. Contrato único de respuesta de error

Archivo:

```txt
core/exceptions/response/ErrorResponse.java
```

Código:

```java
/*
 * DTO estándar para devolver errores al cliente.
 *
 * Define un formato único para errores de dominio,
 * errores de validación y errores inesperados.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private LocalDateTime timestamp;

    private int status;

    private String error;

    private String message;

    private String path;

    private Map<String, String> details;

    public ErrorResponse(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> details
    ) {
        this.timestamp = LocalDateTime.now();
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = message;
        this.path = path;
        this.details = details;
    }

    public ErrorResponse(HttpStatus status, String message, String path) {
        this(status, message, path, null);
    }

    // Getters y setters
}
```

---

## 7.1. Explicación de campos

| Campo       | Función                       |
| ----------- | ----------------------------- |
| `timestamp` | Fecha y hora del error        |
| `status`    | Código HTTP                   |
| `error`     | Nombre del error HTTP         |
| `message`   | Mensaje general del error     |
| `path`      | Ruta donde ocurrió el error   |
| `details`   | Errores específicos por campo |

---

## 7.2. Uso de `details`

El campo `details` se usa principalmente para errores de validación.

Ejemplo:

```json
{
  "timestamp": "2025-12-26T15:12:42.301031",
  "status": 400,
  "error": "Bad Request",
  "message": "Datos de entrada inválidos",
  "path": "/api/users",
  "details": {
    "name": "El nombre es obligatorio",
    "email": "Debe ingresar un email válido"
  }
}
```

Cuando no existen detalles, el campo no se muestra gracias a:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

---

# 8. Handler global de excepciones

Archivo:

```txt
core/exceptions/handler/GlobalExceptionHandler.java
```

Código:

```java
/*
 * Handler global de excepciones.
 *
 * Captura las excepciones lanzadas desde cualquier controlador o servicio
 * y las convierte en una respuesta HTTP uniforme.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
```

`handleApplicationException` maneja las excepciones propias de la aplicación, como `NotFoundException`, `ConflictException` y `BadRequestException`.

```java
    /*
     * Maneja excepciones propias de la aplicación.
     *
     * Captura NotFoundException, ConflictException,
     * BadRequestException y cualquier excepción que extienda
     * de ApplicationException.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            ApplicationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                ex.getStatus(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(ex.getStatus())
                .body(response);
    }
```

`handleValidationException` maneja errores de validación de DTOs, como cuando `@Valid` falla en un `@RequestBody`.
```java
    /*
     * Maneja errores de validación de DTOs.
     *
     * Se ejecuta cuando falla @Valid en un @RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Datos de entrada inválidos",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }
```

`handleUnexpectedException` maneja cualquier excepción inesperada que no haya sido capturada por los handlers anteriores. Esto evita exponer detalles técnicos al cliente y devuelve un error genérico de servidor.


```java
    /*
     * Maneja errores inesperados.
     *
     * Evita exponer stack traces o mensajes técnicos al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
```

---

# 9. Reemplazo de errores en UserServiceImpl

En la práctica anterior se usaba:

```java
throw new IllegalStateException("User not found");
```

Ahora se reemplaza por excepciones de dominio.

---

## 9.1. findOne con NotFoundException

Antes:

```java
@Override
public UserResponseDto findOne(Long id) {

    return userRepository.findById(id)
            .map(UserMapper::toModelFromEntity)
            .map(UserMapper::toResponse)
            .orElseThrow(() -> new IllegalStateException("User not found"));
}
```

Ahora:

```java
/*
 * Busca un usuario activo por id.
 *
 * Si no existe o está eliminado, lanza NotFoundException.
 */
@Override
public UserResponseDto findOne(Long id) {

    UserEntity entity = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));

    if (entity.isDeleted()) {
        throw new NotFoundException("User not found");
    }

    UserModel model = UserMapper.toModelFromEntity(entity);

    return UserMapper.toResponse(model);
}
```

---

## 9.2. create con ConflictException

Antes:

```java
if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
    throw new IllegalStateException("Email already registered");
}
```

Ahora:

```java
/*
 * Crea un nuevo usuario.
 *
 * Valida que el email no esté registrado.
 * Si ya existe, lanza ConflictException.
 */
@Override
public UserResponseDto create(CreateUserDto dto) {

    if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
        throw new ConflictException("Email already registered");
    }

    // Resto del código de creación de usuario
}
```

---

## 9.3. update con NotFoundException

```java
/*
 * Actualiza completamente un usuario activo.
 *
 * Si no existe o está eliminado, lanza NotFoundException.
 */
@Override
public UserResponseDto update(Long id, UpdateUserDto dto) {

    UserEntity entity = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));

    if (entity.isDeleted()) {
        throw new NotFoundException("User not found");
    }

  // Resto del código de actualización de usuario
}
```

---

## 9.4. partialUpdate con NotFoundException

```java
/*
 * Actualiza parcialmente un usuario activo.
 *
 * Si no existe o está eliminado, lanza NotFoundException.
 */
@Override
public UserResponseDto partialUpdate(Long id, PartialUpdateUserDto dto) {

    UserEntity entity = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));

    if (entity.isDeleted()) {
        throw new NotFoundException("User not found");
    }

  // Resto del código de actualización parcial de usuario
}
```

---

## 9.5. delete con NotFoundException

```java
/*
 * Elimina lógicamente un usuario por id.
 *
 * Si no existe o ya está eliminado, lanza NotFoundException.
 */
@Override
public void delete(Long id) {

    UserEntity entity = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));

    if (entity.isDeleted()) {
        throw new NotFoundException("User not found");
    }

// Resto del código de eliminación lógica de usuario
}
```

---

# 10. Validación automática de DTOs

En la práctica anterior ya se agregó `@Valid`.

Ejemplo:

```java
@PostMapping
public UserResponseDto create(@Valid @RequestBody CreateUserDto dto) {
    return service.create(dto);
}
```

Cuando se envía una petición inválida:

```json
{
  "name": "",
  "email": "correo-invalido",
  "password": "123"
}
```

Spring Boot lanza automáticamente:

```java
MethodArgumentNotValidException
```

Esta excepción es capturada por:

```java
handleValidationException()
```

y devuelve una respuesta uniforme.

---

## 10.1. Respuesta de validación esperada

```json
{
  "timestamp": "2025-12-26T15:12:42.301031",
  "status": 400,
  "error": "Bad Request",
  "message": "Datos de entrada inválidos",
  "path": "/api/users",
  "details": {
    "name": "El nombre es obligatorio",
    "email": "Debe ingresar un email válido",
    "password": "La contraseña debe tener al menos 8 caracteres"
  }
}
```

---

# 11. Flujo completo en ejecución

## Escenario 1: Error de validación

Request:

```http
POST /api/users
Content-Type: application/json

{
  "name": "",
  "email": "correo-invalido",
  "password": "123"
}
```

Flujo:

```txt
Request HTTP con datos inválidos
 ↓
Controller con @Valid
 ↓
MethodArgumentNotValidException
 ↓
GlobalExceptionHandler.handleValidationException()
 ↓
Extrae errores de campos
 ↓
ErrorResponse con details
 ↓
Response HTTP 400
```

---

## Escenario 2: Recurso no encontrado

Request:

```http
GET /api/users/999
```

Flujo:

```txt
Request HTTP
 ↓
Controller
 ↓
Service.findOne(999)
 ↓
Repository.findById(999)
 ↓
NotFoundException("User not found")
 ↓
GlobalExceptionHandler.handleApplicationException()
 ↓
ErrorResponse sin details
 ↓
Response HTTP 404
```

Respuesta:

```json
{
  "timestamp": "2025-12-26T15:07:20.967935",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/999"
}
```

---

## Escenario 3: Conflicto por email duplicado

Request:

```http
POST /api/users
Content-Type: application/json

{
  "name": "Juan Pérez",
  "email": "juan@ups.edu.ec",
  "password": "12345678"
}
```

Si el email ya existe, el servicio lanza:

```java
throw new ConflictException("Email already registered");
```

Respuesta:

```json
{
  "timestamp": "2025-12-26T15:07:20.967935",
  "status": 409,
  "error": "Conflict",
  "message": "Email already registered",
  "path": "/api/users"
}
```

---

# 12. Comparación de escenarios

| Aspecto             | Validación de DTOs                | Excepción de dominio                                            |
| ------------------- | --------------------------------- | --------------------------------------------------------------- |
| Cuándo ocurre       | Antes del servicio                | Dentro del servicio                                             |
| Excepción           | `MethodArgumentNotValidException` | `NotFoundException`, `ConflictException`, `BadRequestException` |
| Handler             | `handleValidationException()`     | `handleApplicationException()`                                  |
| Código HTTP         | 400                               | 400, 404, 409                                                   |
| Campo `details`     | Sí                                | No                                                              |
| Servicio se ejecuta | No                                | Sí                                                              |

---


# 13. Buenas prácticas reforzadas

Con esta práctica se refuerza:

* un solo formato de error
* sin `try/catch` en controladores
* servicios sin `ResponseEntity`
* excepciones semánticas
* separación entre lógica de negocio y respuesta HTTP
* validación estructurada
* errores útiles para frontend
* no exposición de stack trace al cliente

---

# 15. Actividad práctica

Se debe implementar el sistema global de errores en el módulo:

```txt
products/
```

---

## 15.1. Reemplazar errores genéricos

Cambiar en `ProductServiceImpl`:

```java
throw new IllegalStateException(...)
```

por:

```java
throw new NotFoundException(...)
throw new ConflictException(...)
throw new BadRequestException(...)
```

---

## 15.2. Validar producto inexistente

En métodos como:

```txt
findOne()
update()
partialUpdate()
delete()
```

si el producto no existe o tiene `deleted = true`, lanzar:

```java
throw new NotFoundException("Product not found");
```

---

## 15.3. Validar conflicto lógico

Agregar una regla de negocio:

```txt
No se puede crear un producto con nombre duplicado.
```

Para eso, el repositorio de productos puede tener un método como:

```java
Optional<ProductEntity> findByName(String name);
```

Si ya existe un producto activo con el mismo nombre:

```java
throw new ConflictException("Product name already registered");
```

---

## 15.4. Validar error de datos

Enviar datos inválidos desde Bruno o Postman:

```json
{
  "name": "",
  "price": -5,
  "stock": -1
}
```

Debe responder con:

```txt
400 Bad Request
```

y el formato estándar de `ErrorResponse`.

---

## 15.5. Verificar eliminado lógico

Después de eliminar un producto:

```txt
DELETE /api/products/{id}
```

Probar nuevamente:

```txt
GET /api/products/{id}
```

Debe responder:

```txt
404 Not Found
```

---

# 16. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

## Captura de error por producto inexistente

Ejemplo:

```txt
GET /api/products/999
```

Debe evidenciar respuesta:

```txt
404 Not Found
```

---

## Captura de error por producto duplicado

Ejemplo:

```txt
POST /api/products
```

con un nombre ya registrado.

Debe evidenciar respuesta:

```txt
409 Conflict
```

---

## Captura de error por validación de DTO

Ejemplo:

```json
{
  "name": "",
  "price": -5,
  "stock": -1
}
```

Debe evidenciar respuesta:

```txt
400 Bad Request
```

con campo `details`.

---

