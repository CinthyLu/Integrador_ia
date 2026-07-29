# Programación y Plataformas Web

# Spring Boot – Roles y @PreAuthorize

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

---

# Práctica 12 (Spring Boot): Protección de Endpoints con Roles

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En la práctica anterior se implementó autenticación JWT usando:

* Spring Security
* JWT
* `JwtAuthenticationFilter`
* `JwtAuthenticationEntryPoint`
* `UserDetailsImpl`
* `UserDetailsServiceImpl`
* `AuthService`
* `AuthController`
* roles mediante `RoleEntity`
* relación `ManyToMany` entre `UserEntity` y `RoleEntity`

Hasta este punto, la API ya puede:

```txt
registrar usuarios
iniciar sesión
generar token JWT
validar token JWT
proteger endpoints con autenticación
````

Además, en `SecurityConfig` se configuró:

```java
.anyRequest().authenticated()
```

Esto significa que todos los endpoints, excepto los públicos, requieren un token válido.

Sin embargo, todavía existe un problema importante: todos los usuarios autenticados tienen el mismo nivel de acceso.

Un usuario normal con `ROLE_USER` podría consumir endpoints que deberían ser solo para administradores.

Ahora vamos a implementar **protección por roles** usando:

- **@PreAuthorize con roles**: hasRole(), hasAnyRole()
- **Roles específicos**: ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR
- **@AuthenticationPrincipal**: Acceder al usuario autenticado
- **Dos enfoques**: Configuración global vs. anotaciones por método

---

# 2. Objetivo de la práctica

El objetivo es diferenciar entre:

```txt
usuario autenticado
usuario autorizado
```

Un usuario autenticado es alguien que tiene un token válido.

Un usuario autorizado es alguien que, además de tener token válido, posee el rol necesario para ejecutar una acción.

Ejemplo:

```txt
GET /api/products/page
```

Puede ser consumido por cualquier usuario autenticado.

Pero:

```txt
GET /api/products
```

debe ser consumido solo por usuarios administradores, porque devuelve todos los productos sin paginación.

---

# 4. Autenticación vs autorización

## 4.1. Autenticación

La autenticación responde a la pregunta:

```txt
¿Quién eres?
```

En esta aplicación se resuelve mediante JWT.

Ejemplo:

```http
Authorization: Bearer <token>
```

Si el token es válido, Spring Security crea un usuario autenticado en el `SecurityContext`.

---

## 4.2. Autorización

La autorización responde a la pregunta:

```txt
¿Qué puedes hacer?
```

En esta práctica se resuelve mediante roles.

Ejemplo:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Esto significa:

```txt
solo usuarios con ROLE_ADMIN pueden ejecutar este método
```

---

# 5. Validar configuración de seguridad

Para usar `@PreAuthorize`, la clase `SecurityConfig` debe tener:

Archivo:

```txt
security/config/SecurityConfig.java
```

Código relevante:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // Configuración existente
}
```

---

## 5.1. Qué hace `@EnableMethodSecurity`

La anotación:

```java
@EnableMethodSecurity(prePostEnabled = true)
```

habilita seguridad a nivel de método.

Permite usar anotaciones como:

```java
@PreAuthorize
@PostAuthorize
```

Sin esta configuración, una anotación como esta no se aplicaría:

```java
@PreAuthorize("hasRole('ADMIN')")
```

---


# 6. Dos Enfoques para Protección por Roles

## 6.1. Enfoque 1: Configuración Global (SecurityConfig)

Proteger rutas completas por patrón de URL:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(unauthorizedHandler)
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            // Endpoints públicos
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/status/**").permitAll()
            
            // Endpoints por rol
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
            
            // Resto requiere autenticación
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    
    return http.build();
}
```

**Ventajas**:
- Centralizado: Toda la configuración en un lugar
- Primera línea de defensa: Bloquea antes de llegar al controlador
- Ideal para proteger rutas administrativas completas

**Desventajas**:
-  Menos granular: Protege por patrón de URL, no por método específico
-  No permite expresiones complejas

## 6.2. Enfoque 2: Anotaciones por Método (@PreAuthorize)

Proteger métodos individuales con expresiones:

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")  // Solo ADMIN puede eliminar
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")  // ADMIN o MODERATOR
    public ResponseEntity<ProductResponseDto> update(@PathVariable Long id) {
        // ...
    }
}
```

**Ventajas**:
- Granular: Control por método específico
- Expresiones complejas: Permite SpEL avanzado
- Visible: La seguridad está junto al código

**Desventajas**:
-  Distribuido: Seguridad esparcida por múltiples controladores
-  Segunda línea: Se ejecuta después del filtro (más tarde en el flujo)

## 6.3. Mejor Práctica: COMBINAR AMBOS

```java
// SecurityConfig - Protección básica por rutas
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")  // ← Primera barrera
    .anyRequest().authenticated()
)

// Controlador - Validación granular adicional
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")  // ← Segunda barrera + control fino
public ResponseEntity<Void> adminDelete(@PathVariable Long id) {
    productService.adminDelete(id);
    return ResponseEntity.noContent().build();
}
```


---

# 7. Uso de `@PreAuthorize`

`@PreAuthorize` evalúa una condición antes de ejecutar el método.

Ejemplo:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Si el usuario tiene el rol requerido, el método se ejecuta.

Si no tiene el rol requerido, Spring Security bloquea la ejecución y responde:

```txt
403 Forbidden
```

---

## 7.1. Diferencia entre `hasRole` y `hasAuthority`

Cuando el usuario inicia sesión, los roles se convierten en authorities.

Ejemplo:

```txt
ROLE_USER
ROLE_ADMIN
```

Con `hasRole`, Spring agrega automáticamente el prefijo `ROLE_`.

```java
@PreAuthorize("hasRole('ADMIN')")
```

Busca internamente:

```txt
ROLE_ADMIN
```

Con `hasAuthority`, se debe escribir el nombre completo:

```java
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
```

En esta práctica se usará:

```java
hasRole
```

porque es más claro para los estudiantes.

---

## 7.2. Expresiones principales

| Expresión                     | Significado                             |
| ----------------------------- | --------------------------------------- |
| `hasRole('ADMIN')`            | Solo usuarios con `ROLE_ADMIN`          |
| `hasRole('USER')`             | Solo usuarios con `ROLE_USER`           |
| `hasAnyRole('USER', 'ADMIN')` | Usuarios con `ROLE_USER` o `ROLE_ADMIN` |
| `isAuthenticated()`           | Cualquier usuario autenticado           |

---

# 8. Actualización de ProductsController

En la práctica de paginación se mantuvo el endpoint:

```txt
GET /api/products
```

Este endpoint devuelve todos los productos activos sin paginación.

Como no tiene paginación, puede devolver demasiados registros y debe quedar restringido solo para administradores.

Los endpoints paginados se mantienen disponibles para cualquier usuario autenticado:

```txt
GET /api/products/page
GET /api/products/slice
```

---

## 8.1. ProductsController actualizado

Archivo:

```txt
products/controllers/ProductsController.java
```

Código:

```java
/*
 * Controlador REST encargado de exponer endpoints HTTP
 * para la gestión de productos.
 *
 * En esta práctica se agrega autorización por roles
 * usando @PreAuthorize.
 */
@RestController
@RequestMapping("/products")
public class ProductsController {

/*
     * Endpoint administrativo.
     *
     * GET /api/products
     *
     * Devuelve todos los productos activos sin paginación.
     * Por esa razón, solo debe ser consumido por usuarios ADMIN.
     *
     * hasRole('ADMIN') busca internamente ROLE_ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductResponseDto> findAll() {
        return service.findAll();
    }

    /*
   
}
```

---

## 8.2. Resultado de esta protección

Con esta configuración:

| Endpoint                    | Protección          |
| --------------------------- | ------------------- |
| `GET /api/products`         | Solo `ROLE_ADMIN`   |
| `GET /api/products/page`    | Usuario autenticado |
| `GET /api/products/slice`   | Usuario autenticado |
| `GET /api/products/{id}`    | Usuario autenticado |
| `POST /api/products`        | Usuario autenticado |
| `PUT /api/products/{id}`    | Usuario autenticado |
| `PATCH /api/products/{id}`  | Usuario autenticado |
| `DELETE /api/products/{id}` | Usuario autenticado |

---

# 9. Crear endpoint para usuario autenticado

Para demostrar el uso de `@AuthenticationPrincipal`, se creará un endpoint que devuelva los datos del usuario autenticado.

Ruta esperada:

```txt
GET /api/users/me
```

Este endpoint permitirá verificar qué usuario está autenticado y qué roles tiene.

---

## 9.1. DTO de usuario autenticado

Archivo:

```txt
security/dtos/CurrentUserResponseDto.java
```

Código:

```java
/*
 * DTO usado para devolver la información básica
 * del usuario autenticado.
 */
public class CurrentUserResponseDto {

    private Long id;

    private String name;

    private String email;

    private Set<String> roles;

    public CurrentUserResponseDto() {
    }

    public CurrentUserResponseDto(
            Long id,
            String name,
            String email,
            Set<String> roles
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.roles = roles;
    }

    // Getters y setters
}
```

---

## 9.2. CurrentUserController

Archivo:

```txt
users/controllers/CurrentUserController.java
```

Código:

```java


import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Controlador REST para consultar información
 * del usuario autenticado.
 *
 * La ruta final es:
 * GET /api/users/me
 */
@RestController
@RequestMapping("/users")
public class CurrentUserController {

    /*
     * Retorna los datos del usuario autenticado.
     *
     * @AuthenticationPrincipal obtiene el usuario que fue colocado
     * en el SecurityContext por JwtAuthenticationFilter.
     */
    @GetMapping("/me")
    public CurrentUserResponseDto me(
            @AuthenticationPrincipal UserDetailsImpl currentUser
    ) {
        Set<String> roles = currentUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return new CurrentUserResponseDto(
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getEmail(),
                roles
        );
    }
}
```

---

## 9.3. Flujo de `@AuthenticationPrincipal`

```txt
Cliente envía token JWT
  ↓
JwtAuthenticationFilter valida token
  ↓
UserDetailsServiceImpl carga el usuario
  ↓
UserDetailsImpl se coloca en SecurityContext
  ↓
@AuthenticationPrincipal recibe UserDetailsImpl
  ↓
Controller devuelve datos del usuario autenticado
```

---

# 10. Manejo de errores 403 Forbidden

Cuando un usuario autenticado no tiene el rol requerido, Spring Security lanza una excepción de autorización.

Ejemplo:

```txt
Usuario con ROLE_USER consume GET /api/products
```

Como ese endpoint tiene:

```java
@PreAuthorize("hasRole('ADMIN')")
```

el usuario no debe acceder.

La respuesta correcta debe ser:

```txt
403 Forbidden
```

No debe responder:

```txt
500 Internal Server Error
```

---

## 10.1. Actualización de GlobalExceptionHandler

Archivo:

```txt
core/exceptions/handler/GlobalExceptionHandler.java
```

Agregar estos handlers antes del handler genérico de `Exception`.

Código:

```java
/*
 * Handler global de excepciones.
 *
 * También maneja excepciones de seguridad para devolver
 * respuestas uniformes con ErrorResponse.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Otros handlers existentes:
    // ApplicationException
    // MethodArgumentNotValidException
    // BindException

    /*
     * Maneja errores de autorización generados por @PreAuthorize.
     *
     * Ocurre cuando el usuario está autenticado,
     * pero no tiene el rol necesario.
     *
     * Ejemplo:
     * ROLE_USER intenta consumir un endpoint con hasRole('ADMIN').
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDeniedException(
            AuthorizationDeniedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN,
                "No tienes permisos para acceder a este recurso",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    /*
     * Maneja errores de acceso denegado.
     *
     * También se usará en la práctica siguiente cuando se valide
     * ownership desde los servicios.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    /*
     * Maneja errores de autenticación producidos dentro del flujo
     * de controladores o servicios, por ejemplo credenciales inválidas
     * durante login.
     *
     * Los errores de token inválido o token ausente normalmente son
     * manejados por JwtAuthenticationEntryPoint.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Credenciales inválidas o sesión expirada",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    // El handler genérico Exception debe quedar al final.
}
```

---

## 10.2. Diferencia entre 401 y 403

| Código             | Significado                         | Ejemplo                                                |
| ------------------ | ----------------------------------- | ------------------------------------------------------ |
| `401 Unauthorized` | No hay autenticación válida         | Sin token o token inválido                             |
| `403 Forbidden`    | Hay autenticación, pero no permisos | `ROLE_USER` intenta acceder a endpoint de `ROLE_ADMIN` |

---

# 11. Asignar rol ADMIN a un usuario

El registro normal asigna:

```txt
ROLE_USER
```

Para probar endpoints administrativos, se necesita al menos un usuario con:

```txt
ROLE_ADMIN
```

Se puede asignar manualmente en PostgreSQL.

---

## 11.1. Ver usuarios registrados

```sql
SELECT id, name, email, deleted
FROM users;
```

---

## 11.2. Ver roles existentes

```sql
SELECT id, name, description
FROM roles;
```

---

## 11.3. Asignar ROLE_ADMIN a un usuario

Ejemplo para asignar `ROLE_ADMIN` al usuario con id `1`:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT 1, r.id
FROM roles r
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
```

---

## 11.4. Verificar roles por usuario

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

# 12. Flujo completo de autorización por rol


```
Request: DELETE /api/products/5/admin
Header: Authorization: Bearer <token>
        ↓
┌─────────────────────────────────────────┐
│ 1. JwtAuthenticationFilter              │
│    - Extrae token del header            │
│    - Valida con JwtUtil                 │
│    - Carga usuario con roles            │
│    - Establece SecurityContext          │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ 2. SecurityConfig                       │
│    .anyRequest().authenticated()        │
│    Usuario autenticado → Continúa    │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ 3. @PreAuthorize("hasRole('ADMIN')")   │
│    - Extrae authorities del usuario     │
│    - Busca "ROLE_ADMIN"                 │
│    - Tiene → Ejecuta método          │
│    -  NO tiene → 403 Forbidden        │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ 4. Método del controlador               │
│    adminDelete(5)                       │
│    → productService.adminDelete(5)      │
└─────────────────────────────────────────┘
        ↓
 Response 204 No Content
```

## Escenario: usuario normal intenta acceder a endpoint ADMIN

Request:

```http
GET /api/products
Authorization: Bearer <token-role-user>
```

Flujo:

```txt
Request HTTP
  ↓
JwtAuthenticationFilter
  ↓
Token válido
  ↓
SecurityContext contiene usuario con ROLE_USER
  ↓
ProductsController.findAll()
  ↓
@PreAuthorize("hasRole('ADMIN')")
  ↓
La expresión evalúa false
  ↓
AuthorizationDeniedException
  ↓
GlobalExceptionHandler
  ↓
Response HTTP 403
```

Respuesta esperada:

```json
{
  "timestamp": "2026-01-15T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "No tienes permisos para acceder a este recurso",
  "path": "/api/products"
}
```

---

## Escenario: usuario ADMIN accede al endpoint

Request:

```http
GET /api/products
Authorization: Bearer <token-role-admin>
```

Flujo:

```txt
Request HTTP
  ↓
JwtAuthenticationFilter
  ↓
Token válido
  ↓
SecurityContext contiene usuario con ROLE_ADMIN
  ↓
@PreAuthorize("hasRole('ADMIN')")
  ↓
La expresión evalúa true
  ↓
ProductsController.findAll()
  ↓
ProductService.findAll()
  ↓
Response HTTP 200
```

---

# 13. Endpoints disponibles para prueba

## Autenticación

| Método | Ruta                 | Descripción       |
| ------ | -------------------- | ----------------- |
| POST   | `/api/auth/register` | Registrar usuario |
| POST   | `/api/auth/login`    | Iniciar sesión    |

---

## Usuario autenticado

| Método | Ruta            | Protección          |
| ------ | --------------- | ------------------- |
| GET    | `/api/users/me` | Usuario autenticado |

---

## Productos

| Método | Ruta                  | Protección          |
| ------ | --------------------- | ------------------- |
| GET    | `/api/products`       | Solo `ROLE_ADMIN`   |
| GET    | `/api/products/page`  | Usuario autenticado |
| GET    | `/api/products/slice` | Usuario autenticado |
| GET    | `/api/products/{id}`  | Usuario autenticado |
| POST   | `/api/products`       | Usuario autenticado |
| PUT    | `/api/products/{id}`  | Usuario autenticado |
| PATCH  | `/api/products/{id}`  | Usuario autenticado |
| DELETE | `/api/products/{id}`  | Usuario autenticado |

---

# 14. Pruebas sugeridas en Bruno o Postman

## 14.1. Login con usuario normal

Request:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@ups.edu.ec",
  "password": "Password123"
}
```

Resultado esperado:

```txt
200 OK
```

Debe devolver:

```txt
token
ROLE_USER
```

---

## 14.2. Consultar usuario autenticado

Request:

```http
GET /api/users/me
Authorization: Bearer <token-role-user>
```

Resultado esperado:

```txt
200 OK
```

Respuesta esperada:

```json
{
  "id": 2,
  "name": "Usuario Normal",
  "email": "user@ups.edu.ec",
  "roles": [
    "ROLE_USER"
  ]
}
```

---

## 14.3. Usuario normal intenta consumir endpoint ADMIN

Request:

```http
GET /api/products
Authorization: Bearer <token-role-user>
```

Resultado esperado:

```txt
403 Forbidden
```

---

## 14.4. Usuario normal consume endpoint paginado

Request:

```http
GET /api/products/page?page=0&size=5
Authorization: Bearer <token-role-user>
```

Resultado esperado:

```txt
200 OK
```

---

## 14.5. Login con usuario ADMIN

Request:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@ups.edu.ec",
  "password": "Password123"
}
```

Resultado esperado:

```txt
200 OK
```

Debe devolver:

```txt
token
ROLE_ADMIN
```

---

## 14.6. Usuario ADMIN consume endpoint administrativo

Request:

```http
GET /api/products
Authorization: Bearer <token-role-admin>
```

Resultado esperado:

```txt
200 OK
```

---

## 14.7. Endpoint protegido sin token

Request:

```http
GET /api/products/page?page=0&size=5
```

Resultado esperado:

```txt
401 Unauthorized
```

---

# 15. Actividad práctica

Se debe implementar autorización por roles en el backend.

## 15.1. Crear endpoint `/users/me`

Crear:

```txt
security/dtos/CurrentUserResponseDto.java
users/controllers/CurrentUserController.java
```

El endpoint debe devolver:

```txt
id
name
email
roles
```

del usuario autenticado.

---

## 15.4. Actualizar `GlobalExceptionHandler`

Agregar handlers para:

```java
AuthorizationDeniedException
AccessDeniedException
AuthenticationException
```

---

## 15.5. Asignar rol ADMIN

Asignar manualmente `ROLE_ADMIN` a un usuario existente usando SQL:
 con la sentencia INSERT en la tabla correspondiente.


# 16. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

---

## Captura de usuario autenticado

Endpoint:

```txt
GET /api/users/me
```

Debe evidenciar:

```txt
id
name
email
roles
```

---

## Captura de acceso denegado por rol

Endpoint:

```txt
GET /api/products
```

Usar token de usuario con:

```txt
ROLE_USER
```

Debe evidenciar:

```txt
403 Forbidden
```

---

## Captura de acceso permitido por rol ADMIN

Endpoint:

```txt
GET /api/products
```

Usar token de usuario con:

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
¿Cuál es la diferencia entre autenticación y autorización?
```

También debe responder:

```txt
¿Por qué GET /api/products debe ser solo para ADMIN, mientras GET /api/products/page puede ser consumido por cualquier usuario autenticado?
```

---

