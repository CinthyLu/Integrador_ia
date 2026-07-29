# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – Persistencia con JPA, Entidades, Repositorios y Base de Datos

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

---

# Práctica 5 (Spring Boot): Persistencia real con PostgreSQL, Entidades JPA y Repositorios

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En la práctica anterior se organizó el CRUD REST usando servicios.

La lógica dejó de estar directamente dentro del controlador y se movió a `UserServiceImpl`.

Hasta ese momento, los datos todavía se almacenaban en memoria usando:

```java
private List<UserModel> users = new ArrayList<>();
private Long currentId = 1L;
```

Ese enfoque sirve para practicar el flujo de una API REST, pero tiene una limitación importante: los datos se pierden cada vez que se reinicia la aplicación.

En esta práctica se reemplaza la lista en memoria por una base de datos real usando:

* PostgreSQL
* Spring Data JPA
* Hibernate
* entidades JPA
* repositorios
* conexión mediante `application.yml`


A partir de esta práctica ya no se utilizará:

* lista en memoria dentro del servicio
* generación manual de ID con `currentId`

---

# 2. Flujo después de aplicar repositorios y base de datos

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

El servicio ya no manejará directamente una lista en memoria.

La persistencia se delega al repositorio.

El repositorio se comunica con PostgreSQL mediante JPA e Hibernate.

---

## 2.1. Responsabilidad de cada clase

| Clase                  | Responsabilidad                                        |
| ---------------------- | ------------------------------------------------------ |
| `UsersController`      | Recibir peticiones HTTP y llamar al servicio           |
| `UserService`          | Definir las operaciones disponibles del módulo         |
| `UserServiceImpl`      | Implementar la lógica de negocio y usar el repositorio |
| `UserRepository`       | Ejecutar operaciones de persistencia                   |
| `UserEntity`           | Representar la tabla en la base de datos               |
| `UserModel`            | Representar el usuario dentro de la aplicación         |
| `UserMapper`           | Convertir entre DTOs, modelos y entidades              |
| `CreateUserDto`        | Recibir datos para crear usuario                       |
| `UpdateUserDto`        | Recibir datos para actualización completa              |
| `PartialUpdateUserDto` | Recibir datos para actualización parcial               |
| `UserResponseDto`      | Devolver datos seguros al cliente                      |
| `ErrorResponseDto`     | Devolver mensajes de error                             |

---

# 3. Instalación y configuración de dependencias

Para trabajar con PostgreSQL desde Spring Boot se necesitan dos dependencias principales:

* Spring Data JPA
* Driver de PostgreSQL

---

## 3.1. Dependencias necesarias en `build.gradle.kts`

Agregar las siguientes dependencias:

```gradle
dependencies {
    // Dependencias existentes

    implementation ("org.springframework.boot:spring-boot-starter-data-jpa")
	testImplementation ("org.springframework.boot:spring-boot-starter-data-jpa-test")
	runtimeOnly ("org.postgresql:postgresql")
}
```

`spring-boot-starter-data-jpa` permite trabajar con JPA, Hibernate y repositorios.

`postgresql` permite que la aplicación se conecte al motor PostgreSQL.

---

# 4. Configuración de conexión en `application.yml`

Archivo:

```txt
src/main/resources/application.yml
```

Configuración:

```yaml
# server: ..
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/devdb
    username: ups
    password: ups123
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

```

---

## 4.1. Explicación de cada propiedad

### `spring.datasource`

Define la conexión hacia PostgreSQL.

```yaml
url: jdbc:postgresql://localhost:5432/devdb
```

La URL indica:

* `jdbc:postgresql://` → protocolo JDBC para PostgreSQL
* `localhost` → servidor donde se ejecuta PostgreSQL
* `5432` → puerto de PostgreSQL
* `devdb` → base de datos usada por Spring Boot

```yaml
username: ups
password: ups123
```

Estos valores corresponden al usuario y contraseña configurados en el contenedor Docker.

---

### `spring.jpa.hibernate`

```yaml
ddl-auto: update
```

Define cómo Hibernate gestiona el esquema de base de datos.

Valores comunes:

| Valor         | Uso                                               |
| ------------- | ------------------------------------------------- |
| `update`      | Actualiza tablas existentes sin eliminar datos    |
| `create`      | Crea el esquema desde cero                        |
| `create-drop` | Crea al iniciar y elimina al cerrar               |
| `validate`    | Valida que las tablas coincidan con las entidades |
| `none`        | No realiza cambios automáticos                    |

Para esta práctica se usará:

```yaml
ddl-auto: update
```

porque permite que Hibernate cree o actualice las tablas durante el desarrollo.

---

### `spring.jpa.properties.hibernate`

```yaml
format_sql: true
```

Permite ver el SQL generado de forma más legible en consola.

```yaml
dialect: org.hibernate.dialect.PostgreSQLDialect
```

Indica que Hibernate debe generar SQL compatible con PostgreSQL.

---


# 5. Base de datos PostgreSQL mediante Docker

Esta práctica utiliza la base de datos levantada mediante Docker en la guía complementaria `05-B`.

Los datos de conexión para Spring Boot son:

| Parámetro     | Valor       |
| ------------- | ----------- |
| Host          | `localhost` |
| Puerto        | `5432`      |
| Usuario       | `ups`       |
| Contraseña    | `ups123`    |
| Base de datos | `devdb`     |

Para verificar que el contenedor está activo:

```bash
docker ps
```

Debe aparecer el contenedor:

```txt
postgres-dev
```

Si está detenido, iniciarlo con:

```bash
docker start postgres-dev
```

---

# 6. Modelo vs Entidad persistente

Hasta la práctica anterior se trabajó con:

```txt
UserModel
```

Ese modelo representa los datos internos de la aplicación.

Pero para guardar datos en PostgreSQL se necesita una entidad JPA:

```txt
UserEntity
```

---

## 6.1. Diferencia entre Model y Entity

| Elemento          | Función                                                    |
| ----------------- | ---------------------------------------------------------- |
| `UserModel`       | Representa el usuario dentro de la lógica de la aplicación |
| `UserEntity`      | Representa cómo se guarda el usuario en la base de datos   |
| `UserResponseDto` | Representa lo que se devuelve al cliente                   |
| `CreateUserDto`   | Representa lo que el cliente envía para crear un usuario   |

---

## 6.2. Por qué no usar directamente la entidad como respuesta

No se debe devolver directamente `UserEntity` al cliente porque:

* representa la estructura de la base de datos
* puede tener campos internos
* puede exponer información sensible
* acopla la API a la persistencia
* dificulta cambios futuros en la base de datos

Por eso se mantiene el flujo:

```txt
Entity → Model → ResponseDto
```

---

# 7. Superclase de auditoría `BaseEntity`

Todas las entidades pueden compartir campos comunes como:

* id
* fecha de creación
* fecha de actualización
* eliminado lógico

Para eso se crea una clase base.

Archivo:

```txt
src/main/java/ec/edu/ups/icc/fundamentos01/core/entities/BaseEntity.java
```

Las anotaciones de JPA permiten que las entidades hijas hereden estos campos sin crear una tabla propia. y estas deberan estar importadas desde `jakarta.persistence.*`

Código:

```java
/*
 * Superclase base para entidades JPA.
 *
 * Contiene campos comunes de persistencia como id,
 * createdAt, updatedAt y deleted.
 *
 * No genera una tabla propia.
 * Sus atributos se heredan en las entidades hijas.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private boolean deleted;

    @PrePersist
    protected void onCreate() {
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor vacío

    // Getters y setters
}
```

---

## 7.1. Explicación de anotaciones

| Anotación           | Función                                             |
| ------------------- | --------------------------------------------------- |
| `@MappedSuperclass` | Permite que otras entidades hereden sus atributos   |
| `@Id`               | Marca el identificador principal                    |
| `@GeneratedValue`   | Indica que el ID será generado por la base de datos |
| `@PrePersist`       | Ejecuta lógica antes de insertar                    |
| `@PreUpdate`        | Ejecuta lógica antes de actualizar                  |

---

# 8. Creación de la entidad persistente UserEntity

Archivo:

```txt
users/entities/UserEntity.java
```

Código:

```java
/*
 * Entidad JPA del recurso users.
 *
 * Representa la tabla users en PostgreSQL.
 * Esta clase sí pertenece a la capa de persistencia.
 */
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

---

## 8.1. Explicación de anotaciones

| Anotación          | Función                                        |
| ------------------ | ---------------------------------------------- |
| `@Entity`          | Indica que la clase representa una tabla       |
| `@Table`           | Define el nombre de la tabla                   |
| `@Column`          | Configura propiedades de las columnas          |
| `nullable = false` | Indica que la columna no permite valores nulos |
| `unique = true`    | Indica que el correo no se puede repetir       |
| `length = 150`     | Define la longitud máxima de la columna        |

---

# 9. Actualización del modelo UserModel

El modelo se mantiene como clase de dominio.

Archivo:

```txt
users/models/UserModel.java
```

Código:

```java
/*
 * Modelo de dominio del recurso users.
 *
 * Representa al usuario dentro de la lógica de negocio.
 * No es una entidad de base de datos y no debe tener anotaciones JPA.
 */
public class UserModel {

    private Long id;

    private String name;

    private String email;

    private LocalDateTime createdAt;

    private String password;

    private String passwordHash;



    private LocalDateTime updatedAt;

    private boolean deleted;

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

# 10. Creación del repositorio

Los repositorios reemplazan completamente las listas en memoria.

Archivo:

```txt
users/repositories/UserRepository.java
```

Código:

```java
/*
 * Repositorio encargado de gestionar la persistencia
 * de usuarios usando Spring Data JPA.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);
}
```

---

## 10.1. Explicación de `JpaRepository<UserEntity, Long>`

`JpaRepository` usa dos tipos genéricos:

```java
JpaRepository<T, ID>
```

Donde:

| Genérico | Significado                             |
| -------- | --------------------------------------- |
| `T`      | Entidad que administrará el repositorio |
| `ID`     | Tipo del identificador principal        |

En este caso:

```java
JpaRepository<UserEntity, Long>
```

Significa:

```txt
UserEntity → entidad gestionada
Long       → tipo del ID
```

---

## 10.2. Métodos automáticos de JpaRepository

Al extender `JpaRepository`, Spring Data JPA proporciona métodos como:

```java
save(entity)
findById(id)
findAll()
delete(entity)
deleteById(id)
existsById(id)
count()
```

Por eso ya no se necesita crear manualmente una lista ni recorrerla para hacer operaciones básicas.

---

## 10.3. Métodos personalizados por convención

Este método:

```java
Optional<UserEntity> findByEmail(String email);
```

Spring Data JPA lo interpreta automáticamente como una consulta por el campo `email`.

Equivale conceptualmente a:

```sql
SELECT * FROM users WHERE email = ?
```

---

# 11. Actualización del UserMapper

Hasta la práctica anterior, el mapper convertía:

```txt
CreateUserDto → UserModel
UserModel → UserResponseDto
```

Ahora también debe convertir:

```txt
UserModel → UserEntity
UserEntity → UserModel
```

Archivo:

```txt
users/mappers/UserMapper.java
```

CAmbios el nombre de algunos metodos para reflejar mejor su función.

Código:

```java
/*
 * Clase encargada de convertir objetos entre DTOs, modelos y entidades.
 *
 * En esta práctica se agrega la conversión hacia UserEntity
 * porque ya se trabaja con persistencia real en PostgreSQL.
 */
public class UserMapper {

    /*
     * Convierte un CreateUserDto en UserModel.
     *
     * El DTO contiene los datos recibidos desde la API.
     * El modelo representa el usuario dentro de la lógica de la aplicación.
     */
    public static UserModel toModelFormDTO(CreateUserDto dto) {

            // CODIGO
    }

    /*
     * Convierte una entidad JPA en UserModel.
     *
     * Se usa cuando el repositorio devuelve datos desde PostgreSQL.
     */
    public static UserModel toModelFromEntity(UserEntity entity) {

        UserModel model = new UserModel();

        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setEmail(entity.getEmail());
        model.setPasswordHash(entity.getPasswordHash());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        model.setDeleted(entity.isDeleted());

        return model;
    }

    /*
     * Convierte un UserModel en UserEntity.
     *
     * Se usa antes de guardar datos en la base de datos.
     */
    public static UserEntity toEntityFromModel(UserModel model) {

        UserEntity entity = new UserEntity();

        entity.setId(model.getId());
        entity.setName(model.getName());
        entity.setEmail(model.getEmail());
        entity.setPasswordHash(model.getPasswordHash());

        return entity;
    }

    /*
     * Convierte un UserModel en UserResponseDto.
     *
     * No se expone password ni passwordHash.
     */

    // public static UserResponseDto toResponse
    // Queda con el mismo nombre porque no cambia su función, solo se le agrega un sufijo para diferenciarlo de los nuevos métodos.
    
    
}
```

---

# 12. Actualización de UserService

El servicio mantiene las mismas operaciones, pero ya no devuelve `Object`.

En esta práctica, cuando no exista un registro, se lanzará un error simple con `IllegalStateException`.

El manejo centralizado de errores se trabajará posteriormente.

Archivo:

```txt
users/services/UserService.java
```

Código:

```java
/*
 * Servicio que define las operaciones disponibles
 * para la gestión de usuarios.
 */
public interface UserService {

    List<UserResponseDto> findAll();

    UserResponseDto findOne(Long id);

    UserResponseDto create(CreateUserDto dto);

    UserResponseDto update(Long id, UpdateUserDto dto);

    UserResponseDto partialUpdate(Long id, PartialUpdateUserDto dto);

    void delete(Long id);
}
```

---

# 13. Actualización de UserServiceImpl

La implementación del servicio ya no usa lista en memoria. Se debe elminar:

```java
private List<UserModel> users = new ArrayList<>();
private Long currentId = 1L;
```

Ahora usa:

```java
private final UserRepository userRepository;
```

Archivo:

```txt
users/services/UserServiceImpl.java
```

Código:

```java
/*
 * Implementación del servicio de usuarios.
 *
 * En esta clase se reemplaza la lista en memoria por UserRepository.
 * El repositorio se encarga de comunicarse con PostgreSQL mediante JPA.
 */
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

```


En el constructor se inyecta el repositorio para poder usarlo en los métodos del servicio.

Ahoara cada método del servicio debe usar el repositorio para realizar las operaciones de persistencia, en lugar de manipular una lista en memoria.

```java
    /*
     * Retorna todos los usuarios almacenados en PostgreSQL.
     *
     * El repositorio devuelve entidades.
     * El mapper convierte entidades a modelos.
     * Luego convierte modelos a DTOs de respuesta.
     */
    @Override
    public List<UserResponseDto> findAll() {

        return userRepository.findAll()
                .stream()
                .map(UserMapper::toModel)
                .map(UserMapper::toResponse)
                .toList();
    }

    /*
     * Busca un usuario por id.
     *
     * Si no existe, lanza un error simple.
     * El manejo formal de errores se implementará después.
     */
    @Override
    public UserResponseDto findOne(Long id) {

        return userRepository.findById(id)
                .map(UserMapper::toModelFromEntity)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    /*
     * Crea un nuevo usuario.
     *
     * Convierte DTO a Model.
     * Convierte Model a Entity.
     * Guarda Entity en PostgreSQL.
     * Convierte Entity guardada a Model.
     * Devuelve Response DTO.
     */
    @Override
    public UserResponseDto create(CreateUserDto dto) {

        UserModel model = UserMapper.toModelFromDTO(dto);

        UserEntity entity = UserMapper.toEntityFromModel(model);

        UserEntity savedEntity = userRepository.save(entity);

        UserModel savedModel = UserMapper.toModelFromEntity(savedEntity);

        return UserMapper.toResponse(savedModel);
    }

    /*
     * Actualiza completamente un usuario.
     *
     * Busca la entidad existente.
     * Actualiza los campos editables.
     * Guarda los cambios.
     * Devuelve DTO de respuesta.
     */
    @Override
    public UserResponseDto update(Long id, UpdateUserDto dto) {

        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());

        UserEntity savedEntity = userRepository.save(entity);

        UserModel model = UserMapper.toModelFromEntity(savedEntity);

        return UserMapper.toResponse(model);
    }

    /*
     * Actualiza parcialmente un usuario.
     *
     * Solo actualiza los campos enviados en el DTO.
     */
    @Override
    public UserResponseDto partialUpdate(Long id, PartialUpdateUserDto dto) {

        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getEmail() != null) {
            entity.setEmail(dto.getEmail());
        }

        UserEntity savedEntity = userRepository.save(entity);

        UserModel model = UserMapper.toModelFromEntity(savedEntity);

        return UserMapper.toResponse(model);
    }

    /*
 * Elimina lógicamente un usuario por id.
 *
 * Primero verifica que exista.
 * Luego marca la entidad como eliminada usando deleted = true.
 * No elimina físicamente el registro de la base de datos.
 */
@Override
public void delete(Long id) {

    UserEntity entity = userRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("User not found"));

    entity.setDeleted(true);

    userRepository.save(entity);
}
}
```

---

# 14. Actualización de UsersController

El controlador casi no cambia.

La diferencia es que ahora el servicio ya no usa una lista en memoria, sino un repositorio conectado a PostgreSQL.



---


# 16. Verificación en PostgreSQL

Para verificar las tablas desde el contenedor:

```bash
docker exec -it postgres-dev psql -U ups -d devdb
```

Dentro de `psql`:

```sql
\dt
```

Para consultar usuarios:

```sql
SELECT * FROM users;
```

Para salir:

```sql
\q
```

También se puede verificar desde:

* DBeaver
* DataGrip
* TablePlus
* VS Code PostgreSQL

---


# 19. Actividad práctica

Se debe replicar toda la arquitectura aprendida, pero ahora en el módulo:

```txt
products/
```

Debe implementar:

### 19.1 Crear `ProductEntity`

La entidad debe extender de `BaseEntity`.

---

### 19.2 Crear `ProductRepository`

Debe extender de `JpaRepository`.

---

### 19.3 Actualizar `ProductMapper`

Debe permitir conversiones entre:

```txt
CreateProductDto → ProductModel
ProductModel → ProductEntity
ProductEntity → ProductModel
ProductModel → ProductResponseDto
```

---

### 19.4 Actualizar `ProductService` y `ProductServiceImpl`

El servicio debe usar repositorio, no lista en memoria.

Debe implementar:

```txt
findAll()
findOne()
create()
update()
partialUpdate()
delete()
```

---

### 19.5 Actualizar `ProductsController`

El controlador debe seguir delegando al servicio.

No debe contener lógica de persistencia.

---

### 19.6 Probar el CRUD completo con PostgreSQL

Probar:

* POST create product
* GET list products
* GET product by id
* PUT update product
* PATCH partial update product
* DELETE remove product

### Datos para revisión

Ingresar 5 productos mediante API REST.

---

# 20. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

---

## Captura de 5 productos creados en PostgreSQL

Puede ser desde:

* DBeaver
* VS Code PostgreSQL
* terminal con `psql`

Consulta sugerida:

```sql
SELECT * FROM products;
```

## Explicar brevemente el flujo de datos desde la API REST hasta PostgreSQL y viceversa, destacando el uso de BaseEntity.