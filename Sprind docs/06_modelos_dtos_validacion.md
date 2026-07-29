# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – DTOs, Validación y Reglas de Entrada

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="95">
</div>

---

# Práctica 6 (Spring Boot): Validación de DTOs y Control de Datos de Entrada

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En las prácticas anteriores se implementó:

* controladores
* servicios
* modelos
* entidades persistentes
* mappers
* repositorios JPA
* conexión a PostgreSQL

Hasta este punto, la API ya puede recibir datos, procesarlos y guardarlos en base de datos.

Sin embargo, todavía falta un componente esencial: validar correctamente los datos que entran al sistema.

Sin validación, la API podría recibir:

* nombres vacíos
* correos inválidos
* contraseñas débiles
* precios negativos
* stock negativo
* campos obligatorios incompletos

En esta práctica se introduce la validación de DTOs usando Jakarta Validation.

El objetivo es validar los datos antes de que lleguen al servicio y antes de que se guarden en PostgreSQL.

En esta práctica se trabajará con:

* DTOs con anotaciones de validación
* `@Valid` en controladores
* reglas básicas de entrada
* validaciones en servicios
* validaciones reforzadas por base de datos

Todavía no se implementa:

* manejo centralizado de errores
* handlers globales
* respuestas de error personalizadas

Eso se trabajará en una práctica posterior.

---

# 2. Flujo después de aplicar validación

Ahora el flujo será:

```txt
Cliente
  ↓
UsersController
  ↓
@Valid
  ↓
CreateUserDto / UpdateUserDto / PartialUpdateUserDto
  ↓
UserService
  ↓
UserServiceImpl
  ↓
UserRepository
  ↓
PostgreSQL
  ↓
UserEntity
  ↓
UserMapper
  ↓
UserModel
  ↓
UserResponseDto
  ↓
Cliente
```

La validación ocurre antes de ejecutar la lógica del servicio.

Si los datos no cumplen las reglas del DTO, Spring Boot detiene la petición y devuelve un error `400 Bad Request`.

---


# 3. Instalación y configuración de dependencias

Spring Boot utiliza Jakarta Validation para validar DTOs.

---

## 3.1. Dependencia necesaria en `build.gradle.kts`

Agregar la dependencia:

```gradle
dependencies {
    // Dependencias existentes

    implementation("org.springframework.boot:spring-boot-starter-validation")
}
```



# 4. Diseño de DTOs con validación

Los DTOs se validan antes de llegar al servicio.

Esto evita que datos incorrectos entren a la lógica de negocio.

---

## 4.1. CreateUserDto

Archivo:

```txt
users/dtos/CreateUserDto.java
```

Código:

```java
/*
 * DTO utilizado para recibir los datos necesarios
 * para crear un nuevo usuario desde una petición HTTP.
 *
 * No incluye id porque el backend lo genera.
 * No incluye createdAt porque el backend asigna la fecha de creación.
 */
public class CreateUserDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ingresar un email válido")
    @Size(max = 150, message = "El email no debe superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.2. UpdateUserDto

Archivo:

```txt
users/dtos/UpdateUserDto.java
```

Código:

```java
/*
 * DTO utilizado para recibir los datos necesarios
 * para actualizar completamente un usuario existente.
 *
 * No incluye id porque el id llega por la URL.
 * No incluye createdAt porque la fecha de creación no debe modificarse.
 */
public class UpdateUserDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ingresar un email válido")
    @Size(max = 150, message = "El email no debe superar los 150 caracteres")
    private String email;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.3. PartialUpdateUserDto

Archivo:

```txt
users/dtos/PartialUpdateUserDto.java
```

Código:

```java
/*
 * DTO utilizado para recibir los datos que se desean
 * actualizar parcialmente en un usuario existente.
 *
 * Los campos pueden venir nulos cuando no se desean actualizar.
 * Solo se validan los campos enviados.
 */
public class PartialUpdateUserDto {

    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @Email(message = "Debe ingresar un email válido")
    @Size(max = 150, message = "El email no debe superar los 150 caracteres")
    private String email;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

# 5. Activar validación en UsersController

Para que Spring Boot valide los DTOs, se debe agregar `@Valid` antes de `@RequestBody`.

Archivo:

```txt
users/controllers/UsersController.java
```


Ejemplo:

```java
@PostMapping
public UserResponseDto create(@Valid @RequestBody CreateUserDto dto) {
    return service.create(dto);
}
```
---

## 5.1. ¿Qué hace `@Valid`?

`@Valid` indica que el objeto recibido debe evaluarse con las anotaciones de Jakarta Validation.




Si el cliente envía un nombre vacío, un email inválido o una contraseña corta, Spring Boot detiene la ejecución antes de entrar al servicio.

---

# 6. Ejemplo de petición inválida

Endpoint:

```txt
POST /api/users
```

Body inválido:

```json
{
  "name": "",
  "email": "correo-invalido",
  "password": "123"
}
```

El DTO tiene estas reglas:

```java
@NotBlank
@Email
@Size
```

Por tanto, la petición no debe llegar al servicio.

---

## 6.1. Respuesta generada por Spring Boot

En esta práctica todavía no se implementa un handler global.

Por eso Spring Boot puede devolver una respuesta técnica parecida a:

```json
{
  "timestamp": "2025-12-26T17:36:43.035Z",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/users"
}
```

El manejo amigable de errores se trabajará después.

> Respuesta HTTP: `400 Bad Request` que deberemos capturar y manejar antes de enviar ya que no es amigable para el cliente.

> No incluye stack trace real, ni que campo falló.

---

# 7. Validación en el servicio

Los DTOs validan reglas de formato.

El servicio puede validar reglas de negocio.

Ejemplos de reglas de negocio:

* no registrar dos usuarios con el mismo email
* no actualizar un usuario eliminado
* no buscar registros eliminados lógicamente

---

## 7.1. Validar email duplicado

Como el repositorio ya tiene:

```java
Optional<UserEntity> findByEmail(String email);
```

se puede validar antes de guardar.

Archivo:

```txt
users/services/UserServiceImpl.java
```

Método `create` actualizado:

```java
/*
 * Crea un nuevo usuario.
 *
 * Valida que el email no esté registrado.
 * Convierte DTO a Model.
 * Convierte Model a Entity.
 * Guarda Entity en PostgreSQL.
 * Convierte Entity guardada a Model.
 * Devuelve Response DTO.
 */
@Override
public UserResponseDto create(CreateUserDto dto) {

    if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
        throw new IllegalStateException("Email already registered");
    }

   // Resto del método...
}
```


# 8. Validación en la entidad y base de datos

La validación del DTO protege la entrada desde la API.

La base de datos también refuerza reglas mediante la entidad JPA.

Ejemplo:

```java
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

Estas restricciones generan reglas en PostgreSQL:

```txt
name no puede ser null
email no puede ser null
email debe ser único
passwordHash no puede ser null
```

---


# 9. Factory Methods para conversión

El dominio debe saber construirse desde:

### (1) un DTO

### (2) una entidad

### (3) a qué entidad debe convertirse

Ejemplo:

```java
public static User fromDto(CreateUserDto dto) {
    return new User(0, dto.name, dto.email, dto.password);
}

public static User fromEntity(UserEntity entity) {
    return new User(
        entity.getId().intValue(),
        entity.getName(),
        entity.getEmail(),
        entity.getPassword()
    );
}

public UserEntity toEntity() {
    UserEntity entity = new UserEntity();
    if (id > 0) entity.setId((long) id);
    entity.setName(this.name);
    entity.setEmail(this.email);
    entity.setPassword(this.password);
    return entity;
}
```

> Actualmente esto lo hace el mapper, pero en proyectos grandes es mejor que el dominio sepa cómo construirse y convertirse a entidad.


# 11. Pruebas sugeridas en Postman / Bruno

## Crear usuario inválido

Método:

```txt
POST
```

Ruta:

```txt
/api/users
```

Body:

```json
{
  "name": "",
  "email": "correo-invalido",
  "password": "123"
}
```

Resultado esperado:

```txt
400 Bad Request
```

---

## Crear usuario válido

Método:

```txt
POST
```

Ruta:

```txt
/api/users
```

Body:

```json
{
  "name": "Juan Pérez",
  "email": "juan@ups.edu.ec",
  "password": "12345678"
}
```

Resultado esperado:

```txt
Usuario creado correctamente
```

---

## Crear usuario con email repetido

Método:

```txt
POST
```

Ruta:

```txt
/api/users
```

Body:

```json
{
  "name": "Juan Repetido",
  "email": "juan@ups.edu.ec",
  "password": "12345678"
}
```

Resultado esperado:

```txt
Error por email ya registrado
```

---

## Actualizar usuario con email inválido

Método:

```txt
PUT
```

Ruta:

```txt
/api/users/1
```

Body:

```json
{
  "name": "Juan Actualizado",
  "email": "correo-invalido"
}
```

Resultado esperado:

```txt
400 Bad Request
```

---

## Actualizar parcialmente con nombre inválido

Método:

```txt
PATCH
```

Ruta:

```txt
/api/users/1
```

Body:

```json
{
  "name": "A"
}
```

Resultado esperado:

```txt
400 Bad Request
```

---

# 12. Actividad práctica

Se debe implementar validación en el módulo:

```txt
products/
```

---

## 12.1. Actualizar DTOs con validación

Aplicar validaciones a:

```txt
CreateProductDto
UpdateProductDto
PartialUpdateProductDto
```

Reglas mínimas:

| Campo   | Regla                             |
| ------- | --------------------------------- |
| `name`  | obligatorio, mínimo 3, máximo 150 |
| `price` | obligatorio, mínimo 0             |
| `stock` | obligatorio, mínimo 0             |

---

## 12.2. Crear modelo de dominio Product


Con métodos:

* `Product.fromDto()`
* `Product.fromEntity()`
* `product.toEntity()`
* `product.toResponseDto()`
* `product.update()`
* `product.partialUpdate()`

> Productos ya no usara el mapper, sino que el dominio sabrá cómo construirse y convertirse a entidad.

## 12.3. Activar `@Valid` en ProductsController

Usar `@Valid` en cada endpoint que reciba un DTO.

---

## 12.4. Validar reglas de negocio en ProductServiceImpl

Validar:

* no actualizar productos eliminados
* no devolver productos eliminados en findAll
* no eliminar dos veces el mismo producto

---

## 12.5. Validar casos erróneos desde Postman / Bruno

Probar:

* `price: -5`
* `stock: -1`
* `name: ""`
* `name: "A"`

---

# 13. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

## Captura de respuesta de error al enviar un POST inválido

Ejemplo:

```json
{
  "name": "",
  "price": -5,
  "stock": -1
}
```

---

## Captura de CRUD de productos validado correctamente

Debe evidenciarse:

* error al crear producto con precio negativo
* error al actualizar producto eliminado
* findAll no devuelve productos eliminados

