# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – API REST y CRUD Inicial sin Servicios

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="100" alt="Spring Boot Logo">
</div>

---

# Práctica 3 (Spring Boot): Construcción de una API REST usando controladores, DTOs, modelos y mappers

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

 GitHub: PabloT18

---

# 1. Introducción

En esta práctica se construye un **CRUD REST completo** usando únicamente:

* controladores (`@RestController`)
* modelos (entidades sin BD)
* DTOs de entrada y salida
* mappers para transformar datos
* almacenamiento en memoria con `List<UserModel>`

Aún **no se utiliza**:

* servicios (`@Service`)
* repositorios JPA
* base de datos

El objetivo de esta práctica es comprender:

* cómo se estructuran endpoints REST en Spring Boot
* cómo se reciben datos con DTOs
* cómo se retorna información segura con DTOs de respuesta
* cómo implementar CRUD desde el controlador antes de aplicar MVCS completo

---

# 2. Estructura que se usará

Dentro de:

```
src/main/java/ec/edu/ups/icc/fundamentos01/users/
```

solo se usarán estas carpetas en este tema:

```
src/main/java/ec/edu/ups/icc/fundamentos01/users/
├── controllers/
│   └── UserController.java
│
├── services/
│   ├── UserService.java
│   └── UserServiceImpl.java
│
├── repository/
│   └── UserRepository.java
│
├── dto/
│   ├── CreateUserDto.java
│   ├── UpdateUserDto.java
│   └── UserResponseDto.java
│
├── models/
│   └── UserModel.java
│
├── entity/
│   └── UserEntity.java
│
└── mappers/
    └── UserMapper.java
```

Recordatorio:

```
DTO = lo que entra o sale por la API
Model = lo que usa la lógica de negocio
Entity = lo que se guarda en base de datos
Mapper = lo que convierte entre DTO, Model y Entity
Repository = lo que habla con la base de datos
Service = lo que aplica reglas de negocio
Controller = lo que expone endpoints
```

---

# 3. Modelo de dominio 

Aquí se define el modelo interno del usuario, incluyendo campos que **no se enviarán al cliente**:

```java
/**
 * Modelo de dominio del recurso users.
 *
 * Representa al usuario dentro de la lógica de negocio.
 * No es una entidad de base de datos y no debe tener anotaciones JPA.
 */
public class UserModel {

    /**
     * Identificador del usuario.
     */
    private Long id;

    
    private String name;

    
    private String email;


    private LocalDateTime createdAt;


    /**
     * Contraseña recibida desde la API.
     *
     * Se usa temporalmente antes de generar el passwordHash.
     */
    private String password;

    /**
     * Contraseña encriptada.
     *
     * Es el valor que posteriormente puede guardarse en la entidad.
     */
    private String passwordHash;

    public UserModel() {
    }

    public UserModel(Long id, String name, String email, String password, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.passwordHash = passwordHash;
    }

    // getters y setters
}
```

---

# 4. DTOs de entrada (para recibir datos)

Los DTOs controlan **qué datos acepta la API**. Dependiendo del endpoint, y de si es creación o actualización, se usan distintos DTOs. No suelen ser idénticos a la entidad, ni tampoco se usan siempre todos los campos o de uno para cada acción pero siempre es la mejor practica definir DTOs específicos por **principio de responsabilidad única**. 

---

## 4.1. DTO para crear usuario

Archivo:
`dtos/CreateUserDto.java`

```java
/*
 * DTO utilizado para recibir los datos necesarios
 * para crear un nuevo usuario desde una petición HTTP.
 * 
 * No incluye id porque el backend lo genera.
 * No incluye createdAt porque el backend asigna la fecha de creación.
 */
public class CreateUserDto {

    private String name;
    private String email;
    private String password;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.2. DTO para actualizar completamente (PUT)

Archivo:
`dtos/UpdateUserDto.java`

```java
/*
 * DTO utilizado para recibir los datos necesarios
 * para actualizar completamente un usuario existente.
 * 
 * No incluye id porque el id llega por la URL.
 * No incluye createdAt porque la fecha de creación no debe modificarse.
 */
public class UpdateUserDto {

    private String name;
    private String email;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.3. DTO para actualización parcial (PATCH)

Archivo:
`dtos/PartialUpdateUserDto.java`

```java
/*
 * DTO utilizado para recibir los datos que se desean
 * actualizar parcialmente en un usuario existente.
 *
 * Los campos pueden venir nulos cuando no se desean actualizar.
 * No incluye createdAt porque la fecha de creación no debe modificarse.
 */
public class PartialUpdateUserDto {

    private String name;
    private String email;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

> **El ID no se coloca en el DTO** porque viene en la ruta del endpoint.

---

# 5. DTO de salida (Response DTO)

Este DTO define **qué datos se devuelven al cliente**.

Archivo:
`dtos/UserResponseDto.java`

```java
/*
 * DTO utilizado para devolver al cliente los datos públicos
 * de un usuario como respuesta de la API.
 * 
 * No incluye password.
 * No incluye passwordHash.
 */
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

> No se expone: `password`, `createdAt` ni ningún dato interno.

---

# 6. Mapper User ↔ DTO

Archivo:
`mappers/UserMapper.java`

```java
/*
 * Clase encargada de convertir objetos entre DTOs y modelos.
 *
 * En esta práctica se usa para separar los datos que llegan desde la API
 * de los datos que maneja internamente la aplicación.
 *
 * El mapper evita que el controlador copie manualmente los campos
 * entre CreateUserDto, UserModel y UserResponseDto.
 */
public class UserMapper {

       /*
     * Convierte un CreateUserDto en un UserModel.
     *
     * Se usa cuando llega una petición POST para crear un usuario.
     * El DTO contiene los datos enviados por el cliente.
     * El modelo representa el usuario dentro de la aplicación.
     *
     * En este método también se asigna createdAt porque la fecha de creación
     * debe generarla el backend y no el cliente.
     */
      public static UserModel toModel(CreateUserDto dto) {

        UserModel model = new UserModel();

        model.setName(dto.getName());
        model.setEmail(dto.getEmail());
        model.setPassword(dto.getPassword());

        model.setPasswordHash("HASH_" + dto.getPassword());
        model.setCreatedAt(LocalDateTime.now());

        return model;
    }

    /*
     * Convierte un UserModel en un UserResponseDto.
     *
     * Se usa para construir la respuesta que se devuelve al cliente.
     * El DTO de respuesta solo debe contener datos seguros.
     *
     * No se copia password ni passwordHash porque esos datos
     * no deben exponerse en la respuesta de la API.
     */
    public static UserResponseDto toResponse(UserModel model) {

        UserResponseDto response = new UserResponseDto();

        response.setId(model.getId());
        response.setName(model.getName());
        response.setEmail(model.getEmail());

        return response;
    }
}
```

---

# 7. Controlador con CRUD completo

Archivo:
`controllers/UsersController.java`


Clase controladora donde se implementan todos los endpoints REST:
Se empieza creado solo un Lista que simula la base de datos en memoria.
```java

/*
 * Controlador REST encargado de exponer los endpoints HTTP
 * para la gestión de usuarios.
 *
 */
@RestController
@RequestMapping("/users")
public class UsersController {

    private List<UserModel> users = new ArrayList<>();
    private int currentId = 1;
```


## GET Users
Este endpoint devuelve la lista de usuarios mapeados a DTOs de respuesta.
```java
    @GetMapping
    public List<UserResponseDto> findAll() {

        // Programación tradicional iterativa para mapear cada User a UserResponseDto
        List<UserResponseDto> dtos = new ArrayList<>();
        for (UserModel user : users) {
            dtos.add(UserMapper.toResponse(user));
        }
        return dtos;

        // Programación funcional para mapear cada User a UserResponseDto
        return users.stream()
                .map(UserMapper::toResponse)
                .toList();
    }
```

## GET User por ID
Aqui se obtiene un usuario por su ID. buscamos en la lista y si no se encuentra devolvemos un mensaje de error.

Si quieren un mejor rendimiento podrían usar:
* Busqueda binaria si la lista está ordenada por ID. Pedir al repositorio la consulta ordenada por ID o un valor especifico.

* `Map<Integer, User>` en lugar de una `List<UserModel>`. con un HashMap la búsqueda por ID sería O(1) en promedio. Mas costo en complejidad espacial.

```java
    @GetMapping("/{id}")
    public Object findOne(@PathVariable int id) {

      // Programación tradicional iterativa para mapear cada User a UserResponseDto
      // Busqueda Lineal
        for (UserModel user : users) {
            if (user.getId().equals(id)) {
                return UserMapper.toResponse(user);
            }
        }
        return new Object() {
            public String error = "User not found";
        };

      // Programación funcional para mapear cada User a UserResponseDto
      // Busqueda Lineal
    return users.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
              .map(user -> (Object) UserMapper.toResponse(user))
            .orElseGet(() -> new Object() {
                public String error = "User not found";
            });
    }
```

## POST
Este endpoint crea un nuevo usuario a partir del DTO de creación. Usa los atributos de `CreateUserDto` para crear la modelo `UserModel`, asigna un ID único y lo agrega a la lista en memoria. Finalmente, devuelve el DTO de respuesta del usuario creado.

* El ID deberia ser secuencial o generado por la base de datos en un entorno real.
```java
    @PostMapping
    public UserResponseDto create(@RequestBody CreateUserDto dto) {
        UserModel user = UserMapper.toModel(dto);
        users.add(user);
        return UserMapper.toResponse(user);
    }
```


## PUT
Endpoint `PUT` para reemplazar completamente un usuario existente. Busca el usuario por ID, si no lo encuentra devuelve un error. Si lo encuentra, actualiza todos los campos con los del DTO de actualización y devuelve el DTO de respuesta actualizado.
```java
 @PutMapping("/{id}")
    public Object update(@PathVariable int id, @RequestBody UpdateUserDto dto) {

        // Programacion tradicional iterativa
        for (UserModel user : users) {
        if (user.getId().equals(id)) {
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        return UserMapper.toResponse(user);
        }
        }
        return new Object() {
        public String error = "UserModel not found";
        };

        // Programacion funcional
        UserModel user = users.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        if (user == null)
            return new Object() {
                public String error = "UserModel not found";
            };

        user.setName(dto.getName());
        user.setEmail(dto.getEmail());

        return UserMapper.toResponse(user);
    }
```
## PATCH
Endpoint `PATCH` para actualizar parcialmente un usuario existente. Busca el usuario por ID, si no lo encuentra devuelve un error. Si lo encuentra, actualiza solo los campos proporcionados en el DTO de actualización parcial y devuelve el DTO de respuesta actualizado.
```java

@PatchMapping("/{id}")
    public Object partialUpdate(@PathVariable int id, @RequestBody PartialUpdateUserDto dto) {

        // Programacion tradicional iterativa
        for (UserModel user : users) {
            // ESTE ES EL CAMBIO pero deberia estar en un metodo aparte para evitar
            // duplicacion de codigo y mejorar mantenibilidad con separacion de
            // responsabilidades.
            if (user.getId().equals(id)) {
                if (dto.getName() != null)
                    user.setName(dto.getName());
                if (dto.getEmail() != null)
                    user.setEmail(dto.getEmail());
                return UserMapper.toResponse(user);
            }
        }
        return new Object() {
            public String error = "UserModel not found";
        };

        // Programación funcional
        // Búsqueda lineal del usuario por id
        UserModel user = users.stream().filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (user == null)
            return new Object() {
                public String error = "UserModel not found";
            };

        if (dto.getName() != null)
            user.setName(dto.getName());
        if (dto.getEmail() != null)
            user.setEmail(dto.getEmail());

        return UserMapper.toResponse(user);
    }
```

## DELETE
// Endpoint `DELETE` para eliminar un usuario por ID. Busca el usuario en la lista y lo elimina si existe. Si no lo encuentra, devuelve un mensaje de error.
```java

   @DeleteMapping("/{id}")
    public Object delete(@PathVariable int id) {
        
        // Programacion funcional
        boolean exists = users.removeIf(u -> u.getId().equals(id));
        if (!exists)
            return new Object() {
                public String error = "User not found";
            };

        return new Object() {
            public String message = "Deleted successfully";
        };
    }
}
```

---

# 8. Endpoints disponibles

| Método | Ruta             | Descripción                |
| ------ | ---------------- | -------------------------- |
| GET    | `/api/users`     | Lista usuarios             |
| GET    | `/api/users/:id` | Obtiene usuario            |
| POST   | `/api/users`     | Crea usuario               |
| PUT    | `/api/users/:id` | Reemplaza usuario completo |
| PATCH  | `/api/users/:id` | Actualiza parcialmente     |
| DELETE | `/api/users/:id` | Elimina usuario            |

---

# 9. Actividad práctica

En esta práctica se debe:

### 1. Corrección del POST `/api/users` para que el ID se genere automáticamente y no se reciba desde el cliente. El ID debe ser único para cada usuario creado.

### 2. Replicar toda la estructura para el recurso de productos.

Los campos del producto den ser:

```java
private Long id;
private String name;
private Double price;
private Integer stock;
private LocalDateTime createdAt;
```

### 2. Implementar los 6 endpoints REST para productos

Con funcionamiento idéntico al de usuarios.

---

# 10. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

### 1. Captura de consumo de endpoints de Products desde Postman.

Incluyendo:

* GET /api/products Con 3 preductos creados
* GET /api/products/:id Con un producto existente 
* DELETE /api/products/:id Eliminando un producto existente
* DELETE /api/products/:id Eliminando un producto que no existe


### 2. Todo lo adicional que indique el format de entrega.