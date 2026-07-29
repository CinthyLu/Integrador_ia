# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – Servicios, Lógica de Negocio e Inyección de Dependencias

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="100" alt="Spring Boot Logo">
</div>

---

# Práctica 4 (Spring Boot): Controladores + Servicios + Lógica de Negocio

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En la práctica anterior se implementó un CRUD REST completo colocando toda la lógica dentro del controlador.

El controlador realizaba varias tareas al mismo tiempo:

* recibía peticiones HTTP
* almacenaba usuarios en memoria
* buscaba usuarios por ID
* creaba nuevos usuarios
* actualizaba usuarios
* eliminaba usuarios
* convertía modelos a DTOs de respuesta

Este enfoque sirve para comprender cómo funcionan los endpoints REST, pero no es adecuado para una aplicación más organizada.

En esta práctica se introduce el uso de servicios mediante `@Service`.

El objetivo es mover la lógica del controlador hacia una clase de servicio, de manera que el controlador quede responsable únicamente de recibir la petición HTTP y delegar la operación.

En esta práctica se trabajará con:

* controladores
* DTOs
* modelos
* mappers
* servicios
* almacenamiento en memoria con `List<UserModel>`

Todavía no se utiliza:

* repositorios
* entidades JPA
* base de datos

---


# 2. Flujo después de aplicar servicios

Ahora el flujo será:

```txt
Cliente
  ↓
UsersController
  ↓
UserService
  ↓
UserServiceImpl
  ↓
List<UserModel>
  ↓
UserMapper
  ↓
UserResponseDto
  ↓
Cliente
```

El controlador ya no manejará directamente la lista de usuarios.

La lista en memoria se moverá al servicio.

---

## 2.1. Responsabilidad de cada clase

| Clase                  | Responsabilidad                                |
| ---------------------- | ---------------------------------------------- |
| `UsersController`      | Recibir peticiones HTTP y llamar al servicio   |
| `UserService`          | Definir las operaciones disponibles del módulo |
| `UserServiceImpl`      | Implementar la lógica de negocio               |
| `UserModel`            | Representar el usuario dentro de la aplicación |
| `UserMapper`           | Convertir entre DTOs y modelos                 |
| `CreateUserDto`        | Recibir datos para crear usuario               |
| `UpdateUserDto`        | Recibir datos para actualización completa      |
| `PartialUpdateUserDto` | Recibir datos para actualización parcial       |
| `UserResponseDto`      | Devolver datos seguros al cliente              |
| `ErrorResponseDto`     | Devolver mensajes de error                     |

---

# 3. Creación del servicio

Dentro de las carpetas correspondiente se crearán los archivos:

```txt
UserService.java
UserServiceImpl.java
```

---

## UserService.java

Archivo `UserService.java`:

```java
/*
 * Servicio que define las operaciones disponibles
 * para la gestión de usuarios.
 *
 * En esta interfaz se declaran las acciones del módulo,
 * pero no se implementa la lógica.
 */
public interface UserService {

    List<UserResponseDto> findAll();

    Object findOne(Long id);

    UserResponseDto create(CreateUserDto dto);

    Object update(Long id, UpdateUserDto dto);

    Object partialUpdate(Long id, PartialUpdateUserDto dto);

    Object delete(Long id);
}
```

---

### Explicación de UserService

`UserService` es una interfaz.

Su función es declarar qué operaciones estarán disponibles para el módulo de usuarios.

No contiene lógica.

Define el contrato que luego será implementado por `UserServiceImpl`.

Ejemplo:

```java
List<UserResponseDto> findAll();
```

Esto significa que cualquier clase que implemente `UserService` debe tener un método para listar usuarios y devolver una lista de `UserResponseDto`.

---

## UserServiceImpl.java

Archivo `UserServiceImpl.java`:

```java
/*
 * Implementación del servicio de usuarios.
 *
 * En esta clase se mueve la lógica que antes estaba dentro del controlador:
 * listar, buscar, crear, actualizar y eliminar usuarios.
 *
 * En esta práctica todavía no se usa repository ni base de datos.
 * Por eso se mantiene una lista en memoria dentro del servicio.
 */
@Service
public class UserServiceImpl implements UserService {

    private List<UserModel> users = new ArrayList<>();
    private Long currentId = 1L;

    /*
     * Retorna todos los usuarios registrados en memoria.
     *
     * Convierte cada UserModel a UserResponseDto para no exponer
     * datos internos como password o passwordHash.
     */
    @Override
    public List<UserResponseDto> findAll() {

        // Programación tradicional iterativa
        /*
        List<UserResponseDto> dtos = new ArrayList<>();

        for (UserModel user : users) {
            dtos.add(UserMapper.toResponse(user));
        }

        return dtos;
        */

        // Programación funcional
        return users.stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    /*
     * Busca un usuario por id.
     *
     * Si el usuario existe, devuelve UserResponseDto.
     * Si no existe, devuelve ErrorResponseDto.
     */
    @Override
    public Object findOne(Long id) {

        // Programación tradicional iterativa
        /*
        for (UserModel user : users) {
            if (user.getId().equals(id)) {
                return UserMapper.toResponse(user);
            }
        }

        return new ErrorResponseDto("User not found");
        */

        // Programación funcional
        return users.stream()
                .filter(user -> user.getId().equals(id))
                .findFirst()
                .map(user -> (Object) UserMapper.toResponse(user))
                .orElseGet(() -> new ErrorResponseDto("User not found"));
    }

    /*
     * Crea un nuevo usuario.
     *
     * Recibe un CreateUserDto, lo convierte a UserModel,
     * asigna un id generado en memoria y devuelve UserResponseDto.
     */
    @Override
    public UserResponseDto create(CreateUserDto dto) {

        UserModel user = UserMapper.toModel(dto);

        user.setId(currentId);
        currentId++;

        users.add(user);

        return UserMapper.toResponse(user);
    }

    /*
     * Actualiza completamente un usuario existente.
     *
     * En PUT se reemplazan los campos editables enviados en el DTO.
     * No se modifica el id ni createdAt.
     */
    @Override
    public Object update(Long id, UpdateUserDto dto) {

        // Programación tradicional iterativa
        /*
        for (UserModel user : users) {
            if (user.getId().equals(id)) {
                user.setName(dto.getName());
                user.setEmail(dto.getEmail());

                return UserMapper.toResponse(user);
            }
        }

        return new ErrorResponseDto("User not found");
        */

        // Programación funcional
        UserModel user = users.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return new ErrorResponseDto("User not found");
        }

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());

        return UserMapper.toResponse(user);
    }

    /*
     * Actualiza parcialmente un usuario existente.
     *
     * En PATCH solo se actualizan los campos que llegan en el DTO.
     * Los campos nulos se ignoran.
     */
    @Override
    public Object partialUpdate(Long id, PartialUpdateUserDto dto) {

        // Programación tradicional iterativa
        /*
        for (UserModel user : users) {
            if (user.getId().equals(id)) {

                if (dto.getName() != null) {
                    user.setName(dto.getName());
                }

                if (dto.getEmail() != null) {
                    user.setEmail(dto.getEmail());
                }

                return UserMapper.toResponse(user);
            }
        }

        return new ErrorResponseDto("User not found");
        */

        // Programación funcional
        UserModel user = users.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (user == null) {
            return new ErrorResponseDto("User not found");
        }

        if (dto.getName() != null) {
            user.setName(dto.getName());
        }

        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }

        return UserMapper.toResponse(user);
    }

    /*
     * Elimina un usuario por id.
     *
     * Si el usuario existe, se elimina de la lista en memoria.
     * Si no existe, se devuelve un DTO de error.
     */
    @Override
    public Object delete(Long id) {

        boolean removed = users.removeIf(user -> user.getId().equals(id));

        if (!removed) {
            return new ErrorResponseDto("User not found");
        }

        return new Object() {
            public String message = "Deleted successfully";
        };
    }
}
```

---

### Explicación de UserServiceImpl

`UserServiceImpl` es la clase que implementa la lógica definida en `UserService`.

Se marca con:

```java
@Service
```

Esta anotación le indica a Spring Boot que esta clase debe ser registrada como un bean de servicio.

Cuando una clase tiene `@Service`, Spring puede crear una instancia automáticamente y entregarla a otras clases mediante inyección de dependencias.

En esta práctica, la lista:

```java
private List<UserModel> users = new ArrayList<>();
```

ya no vive en el controlador.

Ahora vive en el servicio.

Esto permite que el controlador quede más limpio.

---


### ErrorResponseDto

`ErrorResponseDto` es un DTO simple que se utiliza para devolver mensajes de error al cliente.

Lo crearemos en `core/dto/`:

```java
public class ErrorResponseDto {
    private String message;

    public ErrorResponseDto(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
```

# 4. Actualizar UsersController

Archivo `UsersController.java`:

```java
/*
 * Controlador REST encargado de exponer los endpoints HTTP
 * para la gestión de usuarios.
 *
 * En esta práctica el controlador ya no contiene la lógica del CRUD.
 * Solo recibe la petición y delega la operación al servicio.
 */
@RestController
@RequestMapping("/users")
public class UsersController {

    private final UserService service;

    /*
     * Inyección de dependencias por constructor.
     *
     * Spring Boot busca una implementación de UserService,
     * encuentra UserServiceImpl porque tiene @Service,
     * crea el objeto y lo inyecta automáticamente.
     */
    public UsersController(UserService service) {
        this.service = service;
    }

    /*
     * Endpoint para listar todos los usuarios.
     *
     * GET /users
     */
    @GetMapping
    public List<UserResponseDto> findAll() {
        return service.findAll();
    }

    /*
     * Endpoint para buscar un usuario por id.
     *
     * GET /users/{id}
     */
    @GetMapping("/{id}")
    public Object findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    /*
     * Endpoint para crear un nuevo usuario.
     *
     * POST /users
     */
    @PostMapping
    public UserResponseDto create(@RequestBody CreateUserDto dto) {
        return service.create(dto);
    }

    /*
     * Endpoint para actualizar completamente un usuario.
     *
     * PUT /users/{id}
     */
    @PutMapping("/{id}")
    public Object update(
            @PathVariable Long id,
            @RequestBody UpdateUserDto dto
    ) {
        return service.update(id, dto);
    }

    /*
     * Endpoint para actualizar parcialmente un usuario.
     *
     * PATCH /users/{id}
     */
    @PatchMapping("/{id}")
    public Object partialUpdate(
            @PathVariable Long id,
            @RequestBody PartialUpdateUserDto dto
    ) {
        return service.partialUpdate(id, dto);
    }

    /*
     * Endpoint para eliminar un usuario.
     *
     * DELETE /users/{id}
     */
    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
```

---

## Explicación del controlador actualizado

El controlador ya no tiene:

```java
private List<UserModel> users = new ArrayList<>();
private Long currentId = 1L;
```

Tampoco contiene directamente la lógica de búsqueda, creación, actualización o eliminación.

Ahora solo tiene una dependencia:

```java
private final UserService service;
```

Esta dependencia se recibe por constructor:

```java
public UsersController(UserService service) {
    this.service = service;
}
```

Esto se conoce como inyección de dependencias por constructor.

Spring Boot detecta que el controlador necesita un `UserService`.

Luego busca una clase que implemente esa interfaz.

Encuentra:

```java
@Service
public class UserServiceImpl implements UserService
```

Entonces crea una instancia de `UserServiceImpl` y la inyecta en el controlador.

---

# 5. Inyección de dependencias

La inyección de dependencias permite que una clase no cree manualmente los objetos que necesita.

En lugar de hacer esto:

```java
private UserService service = new UserServiceImpl();
```

Spring Boot se encarga de crear e inyectar el objeto:

```java
private final UserService service;

public UsersController(UserService service) {
    this.service = service;
}
```

Esto mejora la organización del código y facilita futuras pruebas.

---

## Implemetación de una Interfaz y su Inyección

Se usa una interfaz porque permite separar:

```txt
qué operaciones existen
```

de:

```txt
cómo se implementan esas operaciones
```

La interfaz declara:

```java
Object findOne(Long id);
```

La implementación define cómo se busca el usuario:

```java
return users.stream()
        .filter(user -> user.getId().equals(id))
        .findFirst()
        .map(user -> (Object) UserMapper.toResponse(user))
        .orElseGet(() -> new ErrorResponseDto("User not found"));
```

Esto permite que más adelante se pueda cambiar la implementación interna sin modificar el controlador.

---

# 6. Pruebas sugeridas en Postman / Bruno

## Crear usuario

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
  "password": "123456"
}
```

---

## Listar usuarios

Método:

```txt
GET
```

Ruta:

```txt
/api/users
```

---

## Buscar usuario por ID

Método:

```txt
GET
```

Ruta:

```txt
/api/users/1
```

---

## Actualizar usuario completo

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
  "email": "juan.actualizado@ups.edu.ec"
}
```

---

## Actualizar usuario parcialmente

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
  "email": "nuevo.correo@ups.edu.ec"
}
```

---

## Eliminar usuario

Método:

```txt
DELETE
```

Ruta:

```txt
/api/users/1
```

---

# 7. Actividad práctica

## 1. Replicar la estructura implementada en `users/` para el recurso `products/`.

# 8. Resultados y evidencias

En la nueva entrada del README, se debe agregar:


##  Captura completa de ProductServiceImpl.java

Debe evidenciarse:

* uso de `@Service`
* lista en memoria
* generación de ID
* uso del mapper
* métodos CRUD implementados

---

## Captura de ProductsController.java

Debe evidenciarse:

* inyección de `ProductService`
* endpoints llamando al servicio
* ausencia de lógica CRUD dentro del controlador

## 6. Explicación breve


```txt
¿Cómo se inyecta el servicio en el controlador?
```

