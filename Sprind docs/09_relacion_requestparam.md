# Programación y Plataformas Web

# Frameworks Backend: Spring Boot – Request Parameters, Consultas Relacionadas y Filtrado

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

---

# Práctica 9 (Spring Boot): Request Parameters, Consultas Relacionadas y Filtrado con JPA

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En la práctica anterior se implementaron relaciones entre entidades usando JPA.

El producto quedó relacionado con:

```txt
UserEntity
CategoryEntity
```

mediante relaciones:

```java
@ManyToOne
@JoinColumn
```

Esto permitió que cada producto tenga:

* un usuario propietario
* una categoría asociada

En esta práctica se trabajará en dos fases.

Primero se implementarán consultas relacionadas y filtros usando la relación actual:

User 1 ──── N Product
Category 1 ──── N Product

Luego se evolucionará la relación entre productos y categorías hacia:

Product N ──── N Category

Esto permitirá comparar una relación directa mediante category_id frente a una relación flexible mediante tabla intermedia.


El objetivo es aprender a consultar datos relacionados usando:

* endpoints semánticos
* `@PathVariable`
* `@RequestParam`
* `@ModelAttribute`
* consultas derivadas en repositorios
* consultas personalizadas con `@Query`
* validación de filtros
* manejo global de errores


---

# 2. Problema actual

En la práctica anterior se agregaron endpoints como:

```txt
GET /api/products/user/{userId}
GET /api/products/category/{categoryId}
```

Estos endpoints funcionan, pero no expresan completamente el contexto del dominio.

Cuando se desea consultar los productos de un usuario, el usuario es el contexto principal.

Una ruta más semántica sería:

```txt
GET /api/users/{id}/products
```

Cuando se desea consultar los productos de una categoría, la categoría es el contexto principal.

Una ruta más semántica sería:

```txt
GET /api/categories/{id}/products
```

Además, en una API real no basta con listar todo.

También se necesitan filtros como:

```txt
name
minPrice
maxPrice
categoryId
userId
```

Ejemplos:

```txt
GET /api/users/1/products?name=laptop
GET /api/users/1/products?minPrice=500&maxPrice=1500
GET /api/users/1/products?categoryId=2
GET /api/categories/2/products?name=gaming
```

---

# 3. Flujo después de aplicar filtros

El flujo será:

```txt
Cliente
  ↓
UserProductsController / CategoryProductsController
  ↓
ProductFilterByUserDto
  ↓
ProductService
  ↓
ProductServiceImpl
  ↓
ProductRepository
  ↓
PostgreSQL
  ↓
ProductEntity
  ↓
ProductResponseDto
  ↓
Cliente
```

El controlador define el contexto de la consulta.

El servicio valida el contexto y los filtros.

El repositorio ejecuta la consulta sobre productos.

---

## 3.1. Responsabilidad de cada clase

| Clase                  | Responsabilidad                                        |
| ---------------------- | ------------------------------------------------------ |
| `UsersController`      | Exponer endpoint semántico `/users/{id}/products`      |
| `CategoriesController` | Exponer endpoint semántico `/categories/{id}/products` |
| `ProductFilterByUserDto`     | Recibir filtros opcionales por query params            |
| `ProductService`       | Definir operaciones de consulta filtrada               |
| `ProductServiceImpl`   | Validar contexto y delegar al repositorio              |
| `ProductRepository`    | Ejecutar consultas relacionales con filtros            |
| `ProductResponseDto`   | Devolver producto con relaciones anidadas              |
| `NotFoundException`    | Reportar usuario, categoría o producto inexistente     |
| `BadRequestException`  | Reportar filtros inválidos                             |

---

# 4. Contexto semántico en endpoints REST

El contexto semántico indica desde qué recurso se consulta la información.

---

## 4.1. Productos desde el contexto de usuario

Ruta recomendada:

```txt
GET /api/users/{id}/products
```

Significa:

```txt
Obtener los productos del usuario con id indicado.
```

Ejemplo:

```txt
GET /api/users/1/products
```

Con filtros:

```txt
GET /api/users/1/products?name=laptop&minPrice=500&maxPrice=1500&categoryId=2
```

---


## 4.3. Diferencia con endpoints técnicos

Endpoint técnico:

```txt
GET /api/products/user/1
```

Endpoint semántico:

```txt
GET /api/users/1/products
```

Ambos pueden funcionar, pero el segundo expresa mejor la relación del dominio.

La ruta indica que:

```txt
products
```

es una subcolección consultada desde:

```txt
users/{id}
```

---

# 5. Navegación de relaciones vs consulta explícita

En JPA existen dos formas de obtener datos relacionados.

---

## 5.1. Navegación de relaciones

La navegación consiste en acceder a una colección desde la entidad padre.

Ejemplo:

```java
UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found"));

Set<ProductEntity> products = user.getProducts();
```

Este enfoque no se usará en esta práctica.

No se recomienda para este caso porque:

* obliga a agregar una colección en `UserEntity`
* puede generar problemas de carga perezosa
* puede producir consultas innecesarias
* dificulta aplicar filtros a nivel de base de datos
* puede cargar demasiados registros en memoria

---

## 5.2. Consulta explícita

La consulta explícita usa el repositorio del recurso que se desea obtener.

Si se van a consultar productos, se usa:

```java
ProductRepository
```

Ejemplo:

```java
List<ProductEntity> products = productRepository.findByOwner_IdAndDeletedFalse(userId);
```

Este enfoque es el recomendado en esta práctica porque:

* consulta directamente la tabla `products`
* permite aplicar filtros en base de datos
* evita recorrer colecciones en memoria
* mantiene control sobre el SQL generado
* escala mejor con muchos registros


---

# 6. DTO para filtros de productos

Se creará un DTO para recibir filtros opcionales desde query params.

Archivo:

```txt
products/dtos/ProductFilterByUserDto.java
```

Código:

```java
/*
 * DTO utilizado para recibir filtros opcionales
 * en consultas de productos.
 *
 * Sus campos llegan desde query params.
 * Ejemplo:
 * /api/users/1/products?name=laptop&minPrice=500&maxPrice=1500&categoryId=2
 */
public class ProductFilterByUserDto {

    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio mínimo no puede ser negativo")
    private Double minPrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio máximo no puede ser negativo")
    private Double maxPrice;

    @Min(value = 1, message = "El ID de categoría debe ser mayor a 0")
    private Long categoryId;


    /*
     * Valida que el rango de precios sea coherente.
     *
     * Si ambos valores existen, maxPrice debe ser mayor o igual a minPrice.
     */
    public boolean hasValidPriceRange() {
        if (minPrice != null && maxPrice != null) {
            return maxPrice >= minPrice;
        }

        return true;
    }

    /*
     * Retorna true si el filtro name viene vacío.
     */
    public boolean hasEmptyName() {
        return name == null || name.isBlank();
    }

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

# 7. Validación de `@ModelAttribute`

Para recibir filtros desde query params se puede usar:

```java
@ModelAttribute
```

Ejemplo:

```java
@GetMapping("/{id}/products")
public List<ProductResponseDto> findProductsByUser(
        @PathVariable Long id,
        @Valid @ModelAttribute ProductFilterByUserDto filters
) {
    return productService.findByUserIdWithFilters(id, filters);
}
```

Esto permite que Spring construya el DTO usando los query params.

Ejemplo:

```txt
GET /api/users/1/products?name=laptop&minPrice=500
```

Spring asigna:

```txt
filters.name = "laptop"
filters.minPrice = 500
```

---

# 8. Actualización del handler global para filtros

En prácticas anteriores se manejó:

```java
MethodArgumentNotValidException
```

Ese error ocurre principalmente cuando falla `@Valid` sobre un `@RequestBody`.

Cuando se valida un DTO recibido con `@ModelAttribute`, Spring puede lanzar:

```java
BindException
```

Por eso se debe agregar un handler adicional.

Archivo:

```txt
core/exceptions/handler/GlobalExceptionHandler.java
```

Agregar:

```java
/*
 * Maneja errores de validación en query params
 * recibidos mediante @ModelAttribute.
 */
@ExceptionHandler(BindException.class)
public ResponseEntity<ErrorResponse> handleBindException(
        BindException ex,
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
            "Parámetros de consulta inválidos",
            request.getRequestURI(),
            errors
    );

    return ResponseEntity
            .badRequest()
            .body(response);
}
```

Con esto, los errores en filtros también mantienen el formato estándar.

Ejemplo de respuesta:

```json
{
  "timestamp": "2026-01-15T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Parámetros de consulta inválidos",
  "path": "/api/users/1/products",
  "details": {
    "minPrice": "El precio mínimo no puede ser negativo"
  }
}
```

---

# 9. Actualización de ProductRepository

El repositorio de productos debe incluir consultas con filtros dinámicos.

Archivo:

```txt
products/repositories/ProductRepository.java
```

Código:

```java
/*
 * Repositorio encargado de gestionar la persistencia
 * de productos usando Spring Data JPA.
 *
 * Incluye consultas relacionales y filtros opcionales.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    /// Otros métodos existentes ...

    /*
     * Busca productos activos de un usuario aplicando filtros opcionales.
     *
     * Si un filtro llega como null, no se aplica.
     */
    @Query("""
            SELECT p
            FROM ProductEntity p
            WHERE p.deleted = false
              AND p.owner.id = :userId
              AND p.owner.deleted = false
              AND (COALESCE(:name, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:categoryId IS NULL OR p.category.deleted = false)
            """)
    List<ProductEntity> findByOwnerIdWithFilters(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("categoryId") Long categoryId
    );

    
}
```

---

## 9.1. Explicación de la consulta

Esta parte:

```java
(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
```

significa:

```txt
Si name es null, no se filtra por nombre.
Si name tiene valor, se busca coincidencia parcial sin importar mayúsculas o minúsculas.
```

Esta parte:

```java
(:minPrice IS NULL OR p.price >= :minPrice)
```

significa:

```txt
Si minPrice es null, no se filtra por precio mínimo.
Si minPrice tiene valor, se buscan productos con precio mayor o igual.
```

Esta parte:

```java
(:categoryId IS NULL OR p.category.id = :categoryId)
```

significa:

```txt
Si categoryId es null, no se filtra por categoría.
Si categoryId tiene valor, se buscan productos de esa categoría.
```

---

# 10. Actualización de ProductService

## 10.1. ProductService

Archivo:

```txt
products/services/ProductService.java
```

Agregar métodos:

```java
/*
 * Servicio que define las operaciones disponibles
 * para la gestión de productos.
 */
public interface ProductService {

  
    // Otros métodos existentes ...
  
    List<ProductResponseDto> findByUserIdWithFilters(
            Long userId,
            ProductFilterByUserDto filters
    );

}
```

---

# 11. Actualización de ProductServiceImpl

Archivo:

```txt
products/services/ProductServiceImpl.java
```

La clase ya debe tener inyectados:

```java
private final ProductRepository productRepository;

private final UserRepository userRepository;

private final CategoryRepository categoryRepository;
```

---

## 11.1. Consulta de productos por usuario con filtros

```java
/*
 * Retorna productos activos de un usuario aplicando filtros opcionales.
 *
 * Primero valida que el usuario exista y no esté eliminado.
 * Luego valida el rango de precios.
 * Finalmente consulta los productos desde ProductRepository.
 */
@Override
public List<ProductResponseDto> findByUserIdWithFilters(
        Long userId,
        ProductFilterByUserDto filters
) {
    if (!userRepository.existsByIdAndDeletedFalse(userId)) {
        throw new NotFoundException("User not found");
    }

    validateFilters(filters);

    String name = normalizeName(filters.getName());

    return productRepository.findByOwnerIdWithFilters(
                    userId,
                    name,
                    filters.getMinPrice(),
                    filters.getMaxPrice(),
                    filters.getCategoryId()
            )
            .stream()
           .map(ProductMapper::toModelFromEntity)
                .map(ProductMapper::toResponse)
            .toList();
}
```

---

## 11.2. Método helper para validar filtros

```java
/*
 * Valida reglas de negocio relacionadas con filtros.
 */
private void validateUserFilters(ProductFilterByUserDto filters) {

    if (filters == null) {
        return;
    }

    if (!filters.hasValidPriceRange()) {
        throw new BadRequestException("El precio máximo debe ser mayor o igual al precio mínimo");
    }

    if (filters.getCategoryId() != null &&
            !categoryRepository.existsByIdAndDeletedFalse(filters.getCategoryId())) {
        throw new NotFoundException("Category not found");
    }


}
```

---

## 11.3. Método helper para normalizar nombre

```java
/*
 * Convierte un texto vacío en null.
 *
 * Esto permite que el repositorio ignore el filtro por nombre
 * cuando el query param llega vacío.
 */
private String normalizeName(String name) {

    if (name == null || name.isBlank()) {
        return null;
    }

    return name.trim();
}
```

---


# 12. Actualización de UsersController

Se agrega un endpoint semántico para consultar productos de un usuario.

Archivo:

```txt
users/controllers/UserProductsController.java
```

Código:

```java
/*
 * Controlador REST encargado de exponer consultas relacionadas
 * entre usuarios y productos.
 *
 * La ruta pertenece al contexto semántico de usuarios:
 * /users/{id}/products
 *
 * Sin embargo, la lógica se delega a ProductService
 * porque el recurso consultado es products.
 */
@RestController
@RequestMapping("/users")
public class UserProductsController {

    private final ProductService productService;

    public UserProductsController(

            ProductService productService
    ) {
        this.productService = productService;
    }


    /*
     * Endpoint para consultar productos de un usuario.
     *
     * GET /api/users/{id}/products
     * GET /api/users/{id}/products?name=laptop
     * GET /api/users/{id}/products?minPrice=500&maxPrice=1500
     * GET /api/users/{id}/products?categoryId=2
     */
    @GetMapping("/{id}/products")
    public List<ProductResponseDto> findProductsByUser(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductFilterByUserDto filters
    ) {
        return productService.findByUserIdWithFilters(id, filters);
    }
}
```

Creamos un controlador separado `UserProductsController` para mantener la semántica de la ruta y delegar la lógica al servicio de productos.

```java
UserProductsController - > ProductService
``` 

```java
UsersController - > UserService
```

---

## 12.1. Explicación

El endpoint final será:

```txt
GET /api/users/{id}/products
```

El controlador está bajo:

```java
@RequestMapping("/users")
```

porque el context-path global ya es:

```txt
/api
```

Por eso no se debe colocar:

```java
@RequestMapping("/api/users")
```

---


# 13. Endpoints disponibles

## Productos por usuario

| Método | Ruta                                                             | Descripción                   |
| ------ | ---------------------------------------------------------------- | ----------------------------- |
| GET    | `/api/users/{id}/products`                                       | Lista productos de un usuario |
| GET    | `/api/users/{id}/products?name=laptop`                           | Filtra por nombre             |
| GET    | `/api/users/{id}/products?minPrice=500`                          | Filtra por precio mínimo      |
| GET    | `/api/users/{id}/products?maxPrice=1500`                         | Filtra por precio máximo      |
| GET    | `/api/users/{id}/products?categoryId=2`                          | Filtra por categoría          |
| GET    | `/api/users/{id}/products?name=gaming&minPrice=800&categoryId=2` | Combina filtros               |


# 14. Relación ManyToMany entre ProductEntity y CategoryEntity

Hasta este punto, la práctica trabaja con la relación:

```txt
Category 1 ──── N Product
```

Es decir, cada producto pertenece a una sola categoría.

En la base de datos esto se representa mediante una columna:

```txt
products.category_id
```

y en JPA mediante:

```java
@ManyToOne
@JoinColumn(name = "category_id")
private CategoryEntity category;
```

Sin embargo, en una aplicación real un producto puede pertenecer a varias categorías.

Ejemplo:

```txt
Laptop Gaming → Electrónicos, Gaming, Oficina
Mouse Inalámbrico → Electrónicos, Oficina
Libro Java → Libros, Programación, Educación
```

Para representar este caso se modifica la relación hacia:

```txt
Product N ──── N Category
```

Esto requiere:

* eliminar la relación directa `ProductEntity.category`
* agregar una colección `Set<CategoryEntity>`
* crear una tabla intermedia
* actualizar DTOs
* actualizar repositorios
* actualizar servicios
* actualizar mappers
* actualizar consultas con filtros

---


## 14.1. ProductEntity actualizado

Archivo:

```txt
products/entities/ProductEntity.java
```

La relación anterior:

```java
@ManyToOne(optional = false, fetch = FetchType.LAZY)
@JoinColumn(name = "category_id", nullable = false)
private CategoryEntity category;
```

debe reemplazarse por:

```java
/*
 * Relación muchos a muchos entre productos y categorías.
 *
 * Un producto puede pertenecer a varias categorías.
 * Una categoría puede tener varios productos.
 */
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
)
private Set<CategoryEntity> categories = new HashSet<>();
```



## 14.2. CategoryEntity actualizado

Archivo:

```txt
categories/entities/CategoryEntity.java
```

Si se desea una relación bidireccional, se puede agregar:

```java
/*
 * Relación inversa con productos.
 *
 * mappedBy indica que la relación principal se define
 * en el atributo categories de ProductEntity.
 */
@ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
private Set<ProductEntity> products = new HashSet<>();
```



## 14.3. Tabla intermedia generada

JPA generará una tabla intermedia:

```txt
product_categories
```

Con columnas:

```txt
product_id
category_id
```

Conceptualmente:

```sql
CREATE TABLE product_categories (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (category_id) REFERENCES categories(id)
);
```

Esta tabla permite que un producto tenga varias categorías sin duplicar productos ni categorías.

---

# 16. Actualización de DTOs para ManyToMany

## 16.1. CreateProductDto

Antes se recibía:

```java
private Long categoryId;
```

Ahora se debe recibir:

```java
    @NotEmpty(message = "Debe seleccionar al menos una categoría")
    private Set<Long> categoryIds;
```

Archivo:

```txt
products/dtos/CreateProductDto.java
```




---

## 16.2. UpdateProductDto

Archivo:

```txt
products/dtos/UpdateProductDto.java
```

Código, ahora se debe recibir:

```java
   @NotEmpty(message = "Debe seleccionar al menos una categoría")
    private Set<Long> categoryIds;
```


## 16.3. PartialUpdateProductDto

Archivo:

```txt
products/dtos/PartialUpdateProductDto.java
```


Código, ahora se debe recibir:

```java
    private Set<Long> categoryIds;
```


## 16.4. ProductResponseDto

Antes se devolvía una sola categoría.

Ahora se devuelve una lista de categorías.

Archivo:

```txt
products/dtos/ProductResponseDto.java
```

Código, a actualizar:

```java
 private List<CategoryResponseDto> categories;   
```

Ejemplo de respuesta:

```json
{
  "id": 1,
  "name": "Laptop Gaming",
  "price": 1200.0,
  "stock": 5,
  "owner": {
    "id": 1,
    "name": "Juan Pérez",
    "email": "juan@ups.edu.ec"
  },
  "categories": [
    {
      "id": 1,
      "name": "Electrónicos"
    },
    {
      "id": 2,
      "name": "Gaming"
    }
  ],
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:35:00"
}
```

---

# 17. Actualización de ProductMapper

El mapper debe cambiar porque ya no existe una única categoría.

Ahora debe convertir:

```txt
ProductEntity.categories → ProductResponseDto.categories
```

Primero tamebien se debe actualizar `ProductModel` para que contenga:

```java
private List<Long> categories;
```

Archivo:

```txt
products/mappers/ProductMapper.java
```

Código de `ProductMapper.toModelFromEntity` actualizado:

```java
        model.setCategories(entity.getCategories().stream().toList());

```

Código de `ProductMapper.toResponse` actualizado, se recomienda actualizar `ProductMapper.toResponse` para que el `ProductModel` contenga los datos mínimos necesarios de las categorías.

```java
        response.setCategories(model.setCategories().stream().map(ProductMapper::toResponse).toList());

```
Explicación:

- `stream()` sobre la colección de categorías del modelo
- `map()` para convertir cada categoría en un `CategoryResponseDto`
- `ProductMapper::toResponse` para convertir cada categoría usa el método estatico `toResponse` que convierte cada `CategoryEntity` del stream a `CategoryResponseDto`.


---

# 18. Actualización de ProductRepository para ManyToMany

La consulta anterior usaba:

```java
p.category.id
```

Eso ya no aplica después de la relación muchos a muchos.

Ahora se debe hacer JOIN con:

```java
p.categories
```

Archivo:

```txt
products/repositories/ProductRepository.java
```

Código:

```java
/*
 * Repositorio encargado de gestionar la persistencia
 * de productos usando Spring Data JPA.
 *
 * Incluye consultas relacionales y filtros opcionales.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    // Otros métodos existentes ...
    /*
     * Busca productos activos de una categoría aplicando filtros opcionales.
     *
     * La categoría se consulta a través de la tabla intermedia product_categories.
     */
    @Query("""
            SELECT DISTINCT p
            FROM ProductEntity p
            JOIN p.categories c
            WHERE p.deleted = false
              AND c.id = :categoryId
              AND c.deleted = false
              AND p.owner.deleted = false
              AND (COALESCE(:name, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:userId IS NULL OR p.owner.id = :userId)
            """)
    List<ProductEntity> findByCategoryIdWithFilters(
            @Param("categoryId") Long categoryId,
            @Param("name") String name,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("userId") Long userId
    );
}
```




---

## 18.1. Por qué se usa DISTINCT

En una relación muchos a muchos, un producto puede estar asociado a varias categorías.

Cuando se hace:

```java
LEFT JOIN p.categories c
```

el mismo producto puede aparecer más de una vez en el resultado SQL si coincide con varias filas relacionadas.

Por eso se usa:

```java
SELECT DISTINCT p
```

para evitar productos duplicados en la respuesta.

---



# 19. Filtro para productos desde el contexto de categoría

Cuando se consulta:

```txt
GET /api/categories/{id}/products
```

la categoría ya viene en la URL.

Por eso el filtro no debe tener `categoryId`.

Debe permitir filtrar por usuario.

Archivo:

```txt
products/dtos/ProductFilterByCategoryDto.java
```

Código:

```java
/*
 * DTO utilizado para recibir filtros opcionales
 * en consultas de productos desde el contexto de categorías.
 *
 * Ejemplo:
 * /api/categories/1/products?name=laptop&minPrice=500&userId=2
 */
public class ProductFilterByCategoryDto {

    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio mínimo no puede ser negativo")
    private Double minPrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio máximo no puede ser negativo")
    private Double maxPrice;

    @Min(value = 1, message = "El ID de usuario debe ser mayor a 0")
    private Long userId;

    /*
     * Valida que el rango de precios sea coherente.
     */
    public boolean hasValidPriceRange() {
        if (minPrice != null && maxPrice != null) {
            return maxPrice >= minPrice;
        }

        return true;
    }

    // Constructor vacío

    // Constructor lleno

    // Getters y setters
}
```

---

# 20. Actualización de ProductService

## 20.1. ProductService

Archivo:

```txt
products/services/ProductService.java
```

Agregar:

```java
List<ProductResponseDto> findByCategoryIdWithFilters(
        Long categoryId,
        ProductFilterByCategoryDto filters
);
```

---

## 20.2. ProductServiceImpl para productos por categoría

Archivo:

```txt
products/services/ProductServiceImpl.java
```

Código:

```java
/*
 * Retorna productos activos de una categoría aplicando filtros opcionales.
 *
 * Primero valida que la categoría exista y no esté eliminada.
 * Luego valida el rango de precios.
 * Si viene userId como filtro, valida que el usuario exista.
 * Finalmente consulta los productos desde ProductRepository.
 */
@Override
public List<ProductResponseDto> findByCategoryIdWithFilters(
        Long categoryId,
        ProductFilterByCategoryDto filters
) {
    if (!categoryRepository.existsByIdAndDeletedFalse(categoryId)) {
        throw new NotFoundException("Category not found");
    }

    validateCategoryFilters(filters);

    String name = normalizeName(filters.getName());

    return productRepository.findByCategoryIdWithFilters(
                    categoryId,
                    name,
                    filters.getMinPrice(),
                    filters.getMaxPrice(),
                    filters.getUserId()
            )
            .stream()
            .map(ProductMapper::toModelFromEntity)
            .map(ProductMapper::toResponse)
            .toList();
}
```

Helper:

```java
/*
 * Valida reglas de negocio relacionadas con filtros
 * usados desde el contexto de categoría.
 */
// CREA EL METODO  QUE VALIDA LOS PARAMETROS DE FILTRO PARA CATEGORIA
```

---

# 21. Actualización de creación y actualización de productos

Al pasar a muchos a muchos, los métodos `create`, `update` y `partialUpdate` ya no deben usar:

```java
dto.getCategoryId()
entity.setCategory(category)
```

Ahora deben usar:

```java
dto.getCategoryIds()
entity.setCategories(categories)
```

---

## 21.1. Helper para validar categorías

```java
/*
 * Valida que todas las categorías existan y estén activas.
 *
 * Retorna el conjunto de entidades CategoryEntity
 * que se asociarán al producto.
 */
private Set<CategoryEntity> validateAndGetCategories(Set<Long> categoryIds) {

    if (categoryIds == null || categoryIds.isEmpty()) {
        throw new BadRequestException("Debe seleccionar al menos una categoría");
    }

    Set<CategoryEntity> categories = new HashSet<>();

    for (Long categoryId : categoryIds) {
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (category.isDeleted()) {
            throw new NotFoundException("Category not found");
        }

        categories.add(category);
    }

    return categories;
}
```

---

## 21.2. create actualizado

```java
/*
 * Crea un producto asociado a un usuario y a varias categorías.
 */
@Override
public ProductResponseDto create(CreateProductDto dto) {
    // El metodo de creación ahora debe que las categorías sean validadas (existan y no estén eliminadas) para poderasociar al producto.
    Set<CategoryEntity> categories = validateAndGetCategories(dto.getCategoryIds());

}
```

---

## 21.3. update actualizado

Actualmente, el método `update` reemplaza todas las categorías asociadas al producto.

```java
  Set<CategoryEntity> categories = validateAndGetCategories(dto.getCategoryIds());
```


## 21.4. partialUpdate actualizado

Actualmente, el método `partialUpdate` reemplaza todas las categorías asociadas al producto si `getCategoryIds` viene con valor.

```java
            Set<CategoryEntity> categories = validateAndGetCategories(dto.getCategoryIds());
```



# 22. Actualización de CategoriesController

Se agrega un endpoint semántico para consultar productos de una categoría.

Archivo:

```txt
categories/controllers/CategoryProductsController.java
```

Código:

```java
/*
 * Controlador REST encargado de exponer consultas relacionadas
 * entre categorías y productos.
 *
 * La ruta pertenece al contexto semántico de categorías:
 * /categories/{id}/products
 *
 * La lógica se delega a ProductService porque el recurso consultado
 * es products.
 */
@RestController
@RequestMapping("/categories")
public class CategoryProductsController {

    private final ProductService productService;

    public CategoryProductsController(ProductService productService) {
        this.productService = productService;
    }

    /*
     * Endpoint para consultar productos de una categoría.
     *
     * GET /api/categories/{id}/products
     * GET /api/categories/{id}/products?name=laptop
     * GET /api/categories/{id}/products?minPrice=500&maxPrice=1500
     * GET /api/categories/{id}/products?userId=1
     */
    @GetMapping("/{id}/products")
    public List<ProductResponseDto> findProductsByCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductFilterByCategoryDto filters
    ) {
        return productService.findByCategoryIdWithFilters(id, filters);
    }
}
```

Al consumir el endpoint, se deberia obtener productos activos asociados a la categoría indicada, aplicando los filtros opcionales.:


```json
[
  {
    "id": 1,
    "name": "Monitor 4Kk",
    "price": 500.0,
    "stock": 5,
    "owner": {
      "id": 5,
      "name": "Pabloa",
      "email": "pablo3@test.com"
    },
    "categories": [
      {
        "id": 1,
        "name": "Electrónicos",
        "description": "Dispositivos electrónicos"
      }
    ],
    "createdAt": "2026-07-02T13:18:23.353491",
    "updatedAt": null,
  }
]
```


---


# 23. Actualización del flujo `findByOwnerIdWithFilters`

En este flujo de filtros se tenia como parametro `categoryId` que era un `Long` y se comparaba con `p.category.id`.

Esto causara error porque ahora `p.categories` es un `Set<CategoryEntity>`.

Eliminando el filtro `categoryId` de `ProductFilterByUserDto` y `ProductFilterByCategoryDto`, se evita la confusión y se mantiene la semántica de los endpoints.

Tambien en la consulta de `ProductRepository` se elimina la parte:

```java
AND (:categoryId IS NULL OR p.category.id = :categoryId)
```

Implica tambien actualizar el método `findByOwnerIdWithFilters` para que ya no reciba `categoryId` como parámetro. Ni mandar como argumento en la llamada a `productRepository.findByOwnerIdWithFilters`.


El metodo del repository `findByCategory_IdAndDeletedFalse` ya no se usara porque ahora se tiene la consulta con filtros dinámicos. Lo que incluye el filtro por  categoría `findByCategoryId` del servicio.


# 23. Endpoints disponibles

## Productos por usuario

| Método | Ruta                                                             | Descripción                   |
| ------ | ---------------------------------------------------------------- | ----------------------------- |
| GET    | `/api/users/{id}/products`                                       | Lista productos de un usuario |
| GET    | `/api/users/{id}/products?name=laptop`                           | Filtra por nombre             |
| GET    | `/api/users/{id}/products?minPrice=500`                          | Filtra por precio mínimo      |
| GET    | `/api/users/{id}/products?maxPrice=1500`                         | Filtra por precio máximo      |
| GET    | `/api/users/{id}/products?categoryId=2`                          | Filtra por categoría          |
| GET    | `/api/users/{id}/products?name=gaming&minPrice=800&categoryId=2` | Combina filtros               |

## Productos por categoría

| Método | Ruta                                                 | Descripción                      |
| ------ | ---------------------------------------------------- | -------------------------------- |
| GET    | `/api/categories/{id}/products`                      | Lista productos de una categoría |
| GET    | `/api/categories/{id}/products?name=laptop`          | Filtra por nombre                |
| GET    | `/api/categories/{id}/products?minPrice=500`         | Filtra por precio mínimo         |
| GET    | `/api/categories/{id}/products?maxPrice=1500`        | Filtra por precio máximo         |
| GET    | `/api/categories/{id}/products?userId=1`             | Filtra por usuario propietario   |
| GET    | `/api/categories/{id}/products?name=gaming&userId=1` | Combina filtros                  |

---

# 24. Pruebas sugeridas en Postman / Bruno

## 24.1. Productos de un usuario

Método:

```txt
GET
```

Ruta:

```txt
/api/users/1/products
```

Resultado esperado:

```txt
Lista de productos activos del usuario 1.
```

---

## 24.2. Productos de un usuario filtrados por nombre

```txt
GET /api/users/1/products?name=laptop
```

Resultado esperado:

```txt
Productos del usuario 1 cuyo nombre contenga laptop.
```

---

## 24.3. Productos de un usuario filtrados por rango de precio

```txt
GET /api/users/1/products?minPrice=500&maxPrice=1500
```

Resultado esperado:

```txt
Productos del usuario 1 con precio entre 500 y 1500.
```

---

## 24.4. Productos de un usuario filtrados por categoría

```txt
GET /api/users/1/products?categoryId=2
```

Resultado esperado:

```txt
Productos del usuario 1 que pertenecen a la categoría 2.
```

---

## 24.5. Crear producto con múltiples categorías

Método:

```txt
POST
```

Ruta:

```txt
/api/products
```

Body:

```json
{
  "name": "Laptop Gaming",
  "price": 1200.0,
  "stock": 5,
  "userId": 1,
  "categoryIds": [1, 2, 3]
}
```

Resultado esperado:

```txt
Producto creado con varias categorías asociadas.
```

---

## 24.6. Productos de una categoría

```txt
GET /api/categories/2/products
```

Resultado esperado:

```txt
Productos activos asociados a la categoría 2.
```

---

## 24.7. Productos de una categoría filtrados por usuario

```txt
GET /api/categories/2/products?userId=1
```

Resultado esperado:

```txt
Productos de la categoría 2 creados por el usuario 1.
```

---

## 24.8. Error por usuario inexistente

```txt
GET /api/users/999/products
```

Resultado esperado:

```txt
404 Not Found
```

---

## 24.9. Error por categoría inexistente

```txt
GET /api/categories/999/products
```

Resultado esperado:

```txt
404 Not Found
```

---

## 24.10. Error por rango inválido

```txt
GET /api/users/1/products?minPrice=1500&maxPrice=500
```

Resultado esperado:

```txt
400 Bad Request
```

Mensaje esperado:

```txt
El precio máximo debe ser mayor o igual al precio mínimo
```

---

## 24.11. Error por parámetro inválido

```txt
GET /api/users/1/products?minPrice=-5
```

Resultado esperado:

```txt
400 Bad Request
```

Debe incluir campo `details`.

---



# 25. Actividad práctica

Se debe implementar consultas relacionadas con filtros usando request parameters y luego evolucionar la relación Producto–Categoría a muchos a muchos.

---

## 26.1. Fase A: filtros con relación Category 1 ──── N Product

Implementar primero:

```txt
GET /api/users/{id}/products
```

con filtros:

```txt
name
minPrice
maxPrice
categoryId
```

Debe usarse:

```txt
ProductFilterByUserDto
ProductRepository.findByOwnerIdWithFilters()
UserProductsController
ProductService.findByUserIdWithFilters()
```

---

## 26.2. Fase B: evolución a relación Product N ──── N Category

Actualizar:

```txt
ProductEntity
CategoryEntity
CreateProductDto
UpdateProductDto
PartialUpdateProductDto
ProductResponseDto
ProductMapper
ProductRepository
ProductServiceImpl
```

Debe eliminarse el uso de:

```txt
categoryId como única categoría del producto
```

y reemplazarse por:

```txt
categoryIds como colección de categorías
```

---

## 26.3. Fase C: consulta desde categorías

Crear:

```txt
ProductFilterByCategoryDto
CategoryProductsController
ProductService.findByCategoryIdWithFilters()
ProductRepository.findByCategoryIdWithFilters()
```

Endpoint requerido:

```txt
GET /api/categories/{id}/products
```

Filtros permitidos:

```txt
name
minPrice
maxPrice
userId
```

---

## 26.4. Probar filtros combinados

Probar:

```txt
GET /api/users/1/products?name=laptop
GET /api/users/1/products?minPrice=500&maxPrice=1500
GET /api/users/1/products?categoryId=2
GET /api/users/1/products?name=gaming&minPrice=800&categoryId=2
GET /api/categories/2/products
GET /api/categories/2/products?userId=1
GET /api/categories/2/products?name=gaming&userId=1
```

---

# 27. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

---


## Captura de producto creado con varias categorías

Ejemplo:

```json
{
  "name": "Laptop Gaming",
  "price": 1200.0,
  "stock": 5,
  "userId": 1,
  "categoryIds": [1, 2, 3]
}
```

---

## Captura de consulta con filtros por usuario

Ejemplo:

```txt
GET /api/users/1/products?name=laptop&minPrice=500
```

---

## Captura de consulta con filtros por categoría

Ejemplo:

```txt
GET /api/categories/2/products?userId=1
```

-


## Explicación breve

Se debe  explicar:

```txt
¿Por qué se usa ProductService y ProductRepository para consultar productos aunque el endpoint esté dentro del contexto /users/{id}/products o /categories/{id}/products?
```

También debe explicar:

```txt
¿Qué cambió al pasar de Product N ──── 1 Category a Product N ──── N Category?
```
