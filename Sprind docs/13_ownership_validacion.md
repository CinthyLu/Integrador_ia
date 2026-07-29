# Programación y Plataformas Web

# Spring Boot – Validación de Ownership

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

---

# Práctica 13 (Spring Boot): Validación de Propiedad de Recursos

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En las prácticas anteriores se implementó seguridad en dos niveles:

```txt
Práctica 11 → Autenticación con JWT
Práctica 12 → Autorización por roles con @PreAuthorize
```

La práctica 11 resolvió la pregunta:

```txt
¿Quién eres?
```

La práctica 12 resolvió la pregunta:

```txt
¿Qué rol tienes?
```

Ahora se debe resolver una tercera pregunta:

```txt
¿Este recurso te pertenece?
```

A esto se le conoce como **ownership** o validación de propiedad del recurso.

---

## Problema actual

Hasta este punto, cualquier usuario autenticado puede consumir endpoints como:

```txt
PUT /api/products/{id}
PATCH /api/products/{id}
DELETE /api/products/{id}
```

Esto significa que un usuario podría modificar o eliminar productos que pertenecen a otro usuario.

Ejemplo del problema:

```txt
Usuario A crea Producto #1
Producto #1 tiene owner_id = Usuario A

Usuario B inicia sesión
Usuario B intenta eliminar Producto #1

Resultado actual:
Podría eliminarlo si solo se valida que tenga token.
```

Esto no es correcto.

La regla de negocio debe ser:

```txt
Un usuario con ROLE_USER solo puede modificar o eliminar sus propios productos.
Un usuario con ROLE_ADMIN puede modificar o eliminar cualquier producto.
```

---

## Objetivo de la práctica

 ¿Qué es Ownership?**

**Ownership (Propiedad)** significa que un recurso pertenece a un usuario específico. Solo el dueño (o usuarios con permisos especiales) pueden modificarlo o eliminarlo.

## **1.1. Ejemplos de Ownership**

| Recurso | Owner | Quién puede modificar/eliminar |
|---------|-------|--------------------------------|
| Producto #1 | Usuario A | Usuario A, ADMIN, MODERATOR |
| Producto #2 | Usuario B | Usuario B, ADMIN, MODERATOR |
| Comentario #5 | Usuario C | Usuario C, ADMIN, MODERATOR |
| Orden #10 | Usuario D | Usuario D, ADMIN |

## **1.2. Flujo de Validación**

```
Request: DELETE /api/products/1
Header: Authorization: Bearer <token-Usuario-B>
        ↓
1. JwtAuthenticationFilter valida token → OK (Usuario B autenticado)
2. .anyRequest().authenticated() → OK (Usuario B autenticado)
3. Servicio: productService.delete(1)
   → Busca Producto #1 → owner_id = Usuario A
   → Usuario actual = Usuario B
   → validateOwnership(producto)
      - ¿Usuario B es ADMIN? → NO
      - ¿Usuario B es MODERATOR? → NO
      - ¿Usuario B es dueño? → NO (dueño es Usuario A)
      → Lanza AccessDeniedException
        ↓
4. GlobalExceptionHandler captura AccessDeniedException
   → Devuelve 403 Forbidden con mensaje personalizado
        ↓
Response: 403 Forbidden
{
  "status": 403,
  "error": "Forbidden",
  "message": "No puedes modificar productos ajenos"
}
```


## Flujo general de ownership

```txt
Cliente
  ↓
PUT /api/products/{id}
  ↓
Authorization: Bearer <token>
  ↓
JwtAuthenticationFilter
  ↓
SecurityContext con UserDetailsImpl
  ↓
ProductsController
  ↓
@AuthenticationPrincipal UserDetailsImpl currentUser
  ↓
ProductServiceImpl.update(id, dto, currentUser)
  ↓
findActiveProductOrThrow(id)
  ↓
validateOwnership(product, currentUser)
  ↓
Si es ADMIN → permite
Si es owner → permite
Si no es owner → 403 Forbidden
```

---

## Reglas de autorización contextual

En esta práctica se aplicarán estas reglas:

| Acción | ROLE_USER | ROLE_ADMIN |
| ------ | --------- | ---------- |
| Crear producto | Sí | Sí |
| Editar producto propio | Sí | Sí |
| Editar producto ajeno | No | Sí |
| Eliminar producto propio | Sí | Sí |
| Eliminar producto ajeno | No | Sí |
| Consultar productos paginados | Sí | Sí |
| Consultar todos los productos sin paginación | No | Sí |

La consulta total sin paginación ya fue protegida en la práctica 12:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping
public List<ProductResponseDto> findAll() {
    return service.findAll();
}
```

En esta práctica se protegerán las acciones que dependen del dueño del recurso.

---

# 2. Ajuste necesario en creación de productos

Antes de esta práctica, el `CreateProductDto` podía tener un campo como:

```java
private Long userId;
```

Eso no es recomendable en una API segura.

Si el cliente puede enviar el `userId`, un usuario podría crear productos a nombre de otro usuario.

Ejemplo del problema:

```json
{
  "name": "Laptop",
  "price": 900,
  "stock": 10,
  "userId": 5,
  "categoryIds": [1, 2]
}
```

Un usuario autenticado con id `2` podría intentar crear un producto para el usuario con id `5`.

Por eso, desde esta práctica el owner debe salir del token JWT, no del body.

La regla será:

```txt
El usuario autenticado será automáticamente el owner del producto.
```



# 3. Actualización de ProductService

Archivo:

```txt
products/services/ProductService.java
```

Se deben actualizar los métodos que modifican datos para recibir al usuario autenticado.

Código:

```java
/*
 * Servicio que define las operaciones disponibles
 * para la gestión de productos.
 */
public interface ProductService {

    // Métodos de consulta existentes

    /*
     * Crea un producto usando como owner al usuario autenticado.
     */
    ProductResponseDto create(
            CreateProductDto dto,
            UserDetailsImpl currentUser
    );

    /*
     * Actualiza completamente un producto.
     *
     * Se valida ownership en el servicio.
     */
    ProductResponseDto update(
            Long id,
            UpdateProductDto dto,
            UserDetailsImpl currentUser
    );

    /*
     * Actualiza parcialmente un producto.
     *
     * Se valida ownership en el servicio.
     */
    ProductResponseDto partialUpdate(
            Long id,
            PartialUpdateProductDto dto,
            UserDetailsImpl currentUser
    );

    /*
     * Elimina lógicamente un producto.
     *
     * Se valida ownership en el servicio.
     */
    void delete(
            Long id,
            UserDetailsImpl currentUser
    );
}
```

---

# 4. Actualización de ProductsController

Archivo:

```txt
products/controllers/ProductsController.java
```

Se debe extraer el usuario autenticado usando:

```java
@AuthenticationPrincipal UserDetailsImpl currentUser
```

Código actualizado de los endpoints que modifican datos:

```java
/*
 * Controlador REST encargado de exponer endpoints HTTP
 * para la gestión de productos.
 */
@RestController
@RequestMapping("/products")
public class ProductsController {

      /*
     * Crear producto.
     *
     * POST /api/products
     *
     * El owner ya no se toma desde el body.
     * El owner se obtiene desde el token JWT mediante @AuthenticationPrincipal.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponseDto create(
            @Valid @RequestBody CreateProductDto dto,
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        return service.create(dto, currentUser);
    }


    // Implementación de update, partialUpdate y delete similar, usando @AuthenticationPrincipal
    // @AuthenticationPrincipal UserDetailsImpl currentUser

}
```

Imports necesarios:

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
```

---

# 5. Actualización de UserRepository

Para obtener el usuario autenticado como entidad JPA, se recomienda agregar este método.

Archivo:

```txt
users/repositories/UserRepository.java
```

Código:

```java
// Adcionar en UserRepository
Optional<UserEntity> findByIdAndDeletedFalse(Long id);
```

Este método se usará para convertir el `UserDetailsImpl` del token en un `UserEntity` persistente:

```java
findByIdAndDeletedFalse(currentUser.getId())
```

---

# 6. Implementación en ProductServiceImpl

Archivo:

```txt
products/services/ProductServiceImpl.java
```


Debe integrarse con los métodos que ya existen en el servicio.


```java
/*
 * Servicio encargado de la lógica de negocio de productos.
 */
@Service
public class ProductServiceImpl implements ProductService {

    /*
     * Crea un producto usando como owner al usuario autenticado.
     *
     * El owner ya no se toma desde el DTO.
     * Esto evita que un usuario cree productos a nombre de otro usuario.
     */
    @Override
    @Transactional
    public ProductResponseDto create(
            CreateProductDto dto,
            UserDetailsImpl currentUser
    ) {

        // Se obtiene el usuario autenticado como entidad JPA
        UserEntity owner = findCurrentUserEntity(currentUser);

        // resto de la lógica de creación...
    }

    /*
     * Actualiza completamente un producto.
     *
     * Primero se busca el producto activo.
     * Luego se valida si el usuario actual puede modificarlo.
     */
    @Override
    @Transactional
    public ProductResponseDto update(
            Long id,
            UpdateProductDto dto,
            UserDetailsImpl currentUser
    ) {
        ProductEntity entity = findActiveProductOrThrow(id);

        // SE VALIDA QUE EL USUARIO AUTENTICADO PUEDA MODIFICAR ESTE PRODUCTO
        validateOwnership(entity, currentUser);
        
        // resto de la lógica de actualización...
    }

    /*
     * Actualiza parcialmente un producto.
     *
     * Solo modifica los campos que llegan en el DTO.
     * También valida ownership antes de hacer cambios.
     */
    @Override
    @Transactional
    public ProductResponseDto partialUpdate(
            Long id,
            PartialUpdateProductDto dto,
            UserDetailsImpl currentUser
    ) {
        ProductEntity entity = findActiveProductOrThrow(id);

        // SE VALIDA QUE EL USUARIO AUTENTICADO PUEDA MODIFICAR ESTE PRODUCTO
        validateOwnership(entity, currentUser);
        
        // resto de la lógica de actualización parcial...
    }

    /*
     * Elimina lógicamente un producto.
     *
     * No se elimina físicamente de la base de datos.
     * Se marca como deleted = true.
     */
    @Override
    @Transactional
    public void delete(
            Long id,
            UserDetailsImpl currentUser
    ) {
        ProductEntity entity = findActiveProductOrThrow(id);

        // SE VALIDA QUE EL USUARIO AUTENTICADO PUEDA ELIMINAR ESTE PRODUCTO
        validateOwnership(entity, currentUser);

        // REsto de la lógica de eliminación...



    }

```


## 6.1 . Métodos privados de validación

Como el validar un producto y el usuario autenticado se repite en varios métodos, se recomienda extraerlo a un método privado:

```java
    /*
     * Busca un producto activo.
     *
     * Si no existe o está eliminado, devuelve 404.
     */
    private ProductEntity findActiveProductOrThrow(Long id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

```


## 6.2 Métodos privados de ownership 

Crear metodos privados para validar ownership y obtener el usuario autenticado como entidad JPA:


```java
    /*
     * Obtiene el usuario autenticado como entidad JPA.
     *
     * currentUser viene desde el token JWT.
     * Luego se consulta en base para asegurar que siga existiendo
     * y no esté eliminado lógicamente.
     */
    private UserEntity findCurrentUserEntity(UserDetailsImpl currentUser) {

        if (currentUser == null) {
            throw new AccessDeniedException("Usuario no autenticado");
        }

        return userRepository.findByIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
    }
```

Adicionar un método privado para verificar roles, el cual se usará en `validateOwnership`, y otros métodos que requieran validación de rol. 

```java

    /*
     * Valida si el usuario autenticado puede modificar o eliminar el producto.
     *
     * Reglas:
     * 1. ROLE_ADMIN puede modificar cualquier producto.
     * 2. ROLE_USER solo puede modificar productos propios.
     */
    private void validateOwnership(
            ProductEntity product,
            UserDetailsImpl currentUser
    ) {
        if (currentUser == null) {
            throw new AccessDeniedException("Usuario no autenticado");
        }

        // Esta validación permite que un ADMIN pueda modificar cualquier producto
        if (hasRole(currentUser, "ROLE_ADMIN")) {
            return;
        }

        if (product.getOwner() == null || product.getOwner().getId() == null) {
            throw new AccessDeniedException("El producto no tiene propietario válido");
        }

        if (!product.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("No puedes modificar productos ajenos");
        }
    }
```

## 6.3 Métodos privados de validación de roles

Puede ser usado para validar si el usuario tiene `ROLE_ADMIN` o cualquier otro rol que se requiera y realizar acciones específicas según el rol del usuario.


```java
    /*
     * Verifica si el usuario tiene un rol específico.
     *
     * Las authorities vienen desde UserDetailsImpl.
     * Ejemplo:
     * ROLE_USER
     * ROLE_ADMIN
     */
    private boolean hasRole(
            UserDetailsImpl user,
            String role
    ) {
        return user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(role));
    }
```

## 6.4 Métodos privados helper de validación de producto y categorías

El metodo `validateProductNameForCreate`, `validateProductNameForUpdate` y `findActiveCategories` son métodos de validación que ya existían en la clase `ProductServiceImpl`. Se mantienen para asegurar que los nombres de productos sean únicos y que las categorías asociadas sean válidas y activas.

```java

    /*
     * Valida que el nombre no esté registrado al crear.
     */
    private void validateProductNameForCreate(String name) {

        if (productRepository.findByNameIgnoreCaseAndDeletedFalse(name.trim()).isPresent()) {
            throw new ConflictException("Product name already registered");
        }
    }

    /*
     * Valida que el nombre no esté registrado en otro producto al actualizar.
     *
     * Se permite que el producto conserve su mismo nombre.
     */
    private void validateProductNameForUpdate(
            Long currentProductId,
            String name
    ) {
        productRepository.findByNameIgnoreCaseAndDeletedFalse(name.trim())
                .filter(product -> !product.getId().equals(currentProductId))
                .ifPresent(product -> {
                    throw new ConflictException("Product name already registered");
                });
    }

    /*
     * Busca categorías activas por ids.
     *
     * Si una categoría no existe o está eliminada, se rechaza la operación.
     */
    private Set<CategoryEntity> findActiveCategories(Set<Long> categoryIds) {

        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BadRequestException("Debe seleccionar al menos una categoría");
        }

        Set<Long> uniqueIds = new HashSet<>(categoryIds);

        Set<CategoryEntity> categories = categoryRepository.findAllById(uniqueIds)
                .stream()
                .filter(category -> !category.isDeleted())
                .collect(Collectors.toSet());

        if (categories.size() != uniqueIds.size()) {
            throw new NotFoundException("One or more categories were not found");
        }

        return categories;
    }

   
}
```

---

# 7. Consideración sobre `ProductMapper`

Si el proyecto ya tiene un método directo:

```java
ProductMapper.toResponseFromEntity(entity)
```

puedes reemplazar:

```java
ProductModel model = ProductMapper.toModelFromEntity(entity);
return ProductMapper.toResponse(model);
```

por:

```java
return ProductMapper.toResponseFromEntity(entity);
```

Esto es útil cuando `ProductResponseDto` necesita devolver información anidada como:

```txt
owner
categories
```

---

# 8. Actualización de GlobalExceptionHandler

En la práctica 12 ya se agregó manejo para `AccessDeniedException`.

Para esta práctica se recomienda ajustar el handler para usar el mensaje específico de la excepción.

Archivo:

```txt
core/exceptions/handler/GlobalExceptionHandler.java
```

Código actualizado:

```java
/*
 * Maneja errores de acceso denegado.
 *
 * Se usa cuando:
 * - Un usuario autenticado no tiene permiso.
 * - Un usuario intenta modificar un producto ajeno.
 */
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDeniedException(
        AccessDeniedException ex,
        HttpServletRequest request
) {
    String message = ex.getMessage();

    if (message == null || message.isBlank()) {
        message = "Acceso denegado";
    }

    ErrorResponse response = new ErrorResponse(
            HttpStatus.FORBIDDEN,
            message,
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
}
```

Con esto, cuando el servicio lance:

```java
throw new AccessDeniedException("No puedes modificar recursos ajenos");
```

la API responderá:

```json
{
  "timestamp": "2026-01-15T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "No puedes modificar recursos ajenos",
  "path": "/api/products/1"
}
```

---

# 9. Diferencia entre 401, 403 por rol y 403 por ownership

| Caso | Validación | Dónde ocurre | Código |
| ---- | ---------- | ------------ | ------ |
| Sin token | Autenticación | `JwtAuthenticationEntryPoint` | `401 Unauthorized` |
| Token inválido | Autenticación | `JwtAuthenticationEntryPoint` | `401 Unauthorized` |
| Token válido sin rol requerido | Autorización por rol | `@PreAuthorize` | `403 Forbidden` |
| Token válido pero recurso ajeno | Ownership | `ProductServiceImpl` | `403 Forbidden` |

---

# 10. Flujo completo de validación

## 10.1. Usuario modifica producto propio

```txt
PUT /api/products/1
Authorization: Bearer <token-user-a>
```

Flujo:

```txt
JwtAuthenticationFilter valida token
  ↓
SecurityContext contiene Usuario A
  ↓
ProductsController.update()
  ↓
@AuthenticationPrincipal currentUser = Usuario A
  ↓
ProductServiceImpl.update()
  ↓
Producto #1 pertenece a Usuario A
  ↓
validateOwnership() permite
  ↓
Producto actualizado
  ↓
200 OK
```

---

## 10.2. Usuario modifica producto ajeno

```txt
PUT /api/products/1
Authorization: Bearer <token-user-b>
```

Flujo:

```txt
JwtAuthenticationFilter valida token
  ↓
SecurityContext contiene Usuario B
  ↓
ProductsController.update()
  ↓
@AuthenticationPrincipal currentUser = Usuario B
  ↓
ProductServiceImpl.update()
  ↓
Producto #1 pertenece a Usuario A
  ↓
validateOwnership() rechaza
  ↓
AccessDeniedException
  ↓
GlobalExceptionHandler
  ↓
403 Forbidden
```

---

## 10.3. ADMIN modifica cualquier producto

```txt
PUT /api/products/1
Authorization: Bearer <token-admin>
```

Flujo:

```txt
JwtAuthenticationFilter valida token
  ↓
SecurityContext contiene usuario con ROLE_ADMIN
  ↓
ProductServiceImpl.update()
  ↓
validateOwnership() detecta ROLE_ADMIN
  ↓
permite modificar cualquier producto
  ↓
200 OK
```

---

# 11. Consultas SQL de apoyo

## 11.1. Ver usuarios

```sql
SELECT id, name, email, deleted
FROM users
ORDER BY id;
```

---

## 11.2. Ver roles por usuario

```sql
SELECT 
    u.id AS user_id,
    u.email,
    r.name AS role
FROM users u
INNER JOIN user_roles ur ON ur.user_id = u.id
INNER JOIN roles r ON r.id = ur.role_id
ORDER BY u.id;
```

---

## 11.3. Ver productos y propietarios

Si la columna de usuario en `products` se llama `user_id`:

```sql
SELECT 
    p.id AS product_id,
    p.name AS product_name,
    p.user_id AS owner_id,
    u.email AS owner_email,
    p.deleted
FROM products p
INNER JOIN users u ON u.id = p.user_id
ORDER BY p.id;
```

Si la columna se llama `owner_id`, usa:

```sql
SELECT 
    p.id AS product_id,
    p.name AS product_name,
    p.owner_id AS owner_id,
    u.email AS owner_email,
    p.deleted
FROM products p
INNER JOIN users u ON u.id = p.owner_id
ORDER BY p.id;
```

---

## 11.4. Asignar ROLE_ADMIN a un usuario

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT 1, r.id
FROM roles r
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
```

---

# 12. Pruebas sugeridas en Bruno o Postman

## 12.1. Registrar usuario A

```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "Usuario A",
  "email": "usera@ups.edu.ec",
  "password": "Password123"
}
```

Resultado esperado:

```txt
201 Created
ROLE_USER
token
```

---

## 12.2. Registrar usuario B

```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "Usuario B",
  "email": "userb@ups.edu.ec",
  "password": "Password123"
}
```

Resultado esperado:

```txt
201 Created
ROLE_USER
token
```

---

## 12.3. Crear producto con Usuario A

```http
POST /api/products
Authorization: Bearer <token-user-a>
Content-Type: application/json

{
  "name": "Laptop Usuario A",
  "price": 900,
  "stock": 10,
  "categoryIds": [1, 2]
}
```

Resultado esperado:

```txt
201 Created
```

El producto debe quedar con owner igual al Usuario A.

---

## 12.4. Usuario A actualiza su propio producto

```http
PUT /api/products/1
Authorization: Bearer <token-user-a>
Content-Type: application/json

{
  "name": "Laptop Usuario A Actualizada",
  "price": 950,
  "stock": 8,
  "categoryIds": [1, 2]
}
```

Resultado esperado:

```txt
200 OK
```

---

## 12.5. Usuario B intenta actualizar producto de Usuario A

```http
PUT /api/products/1
Authorization: Bearer <token-user-b>
Content-Type: application/json

{
  "name": "Intento de modificación ajena",
  "price": 1000,
  "stock": 5,
  "categoryIds": [1, 2]
}
```

Resultado esperado:

```txt
403 Forbidden
```

Respuesta esperada:

```json
{
  "timestamp": "2026-01-15T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "No puedes modificar productos ajenos",
  "path": "/api/products/1"
}
```

---

## 12.6. Usuario B intenta eliminar producto de Usuario A

```http
DELETE /api/products/1
Authorization: Bearer <token-user-b>
```

Resultado esperado:

```txt
403 Forbidden
```

---

## 12.7. ADMIN actualiza producto de Usuario A

Primero iniciar sesión con un usuario que tenga `ROLE_ADMIN`.

```http
PUT /api/products/1
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "name": "Laptop modificada por ADMIN",
  "price": 1100,
  "stock": 20,
  "categoryIds": [1, 2]
}
```

Resultado esperado:

```txt
200 OK
```

---

## 12.8. ADMIN elimina producto de cualquier usuario

```http
DELETE /api/products/1
Authorization: Bearer <token-admin>
```

Resultado esperado:

```txt
204 No Content
```

---

# 17. Actividad práctica

Se debe implementar validación de ownership en productos.

---

- 1. Actualizar `CreateProductDto`

- 2. Actualizar `ProductService`

- 3. Actualizar `ProductsController`

- 4. Actualizar `ProductServiceImpl`

- 5. Actualizar `GlobalExceptionHandler`

- 6. Probar escenarios

Probar:

```txt
usuario actualiza producto propio
usuario actualiza producto ajeno
usuario elimina producto propio
usuario elimina producto ajeno
ADMIN actualiza producto ajeno
ADMIN elimina producto ajeno
```

---

# 18. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

---

## Captura de creación de producto con usuario autenticado

Endpoint:

```txt
POST /api/products
```

Debe evidenciar:

```txt
201 Created
producto creado
owner corresponde al usuario autenticado
```

---

## Captura de bloqueo por producto ajeno

Endpoint:

```txt
PUT /api/products/{id}
```

Usar token de otro usuario.

Debe evidenciar:

```txt
403 Forbidden
No puedes modificar productos ajenos
```

---

## Captura de eliminación de producto ajeno bloqueada

Endpoint:

```txt
DELETE /api/products/{id}
```

Usar token de otro usuario.

Debe evidenciar:

```txt
403 Forbidden
```

---

## Captura de ADMIN modificando producto ajeno

Endpoint:

```txt
PUT /api/products/{id}
```

Usar token con:

```txt
ROLE_ADMIN
```

Debe evidenciar:

```txt
200 OK
```

---

## Explicación breve

Se debe  responder:

```txt
¿Qué es ownership?
```

También debe responder:

```txt
¿Por qué no es seguro recibir userId en CreateProductDto?
```

Y:

```txt
¿Cuál es la diferencia entre autorización por rol y autorización por ownership?
```

