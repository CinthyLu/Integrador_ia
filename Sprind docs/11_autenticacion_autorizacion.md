# Programación y Plataformas Web

# Spring Boot – Autenticación y Autorización con JWT: Seguridad y Control de Acceso

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

## Práctica 11 (Spring Boot): Autenticación JWT, Autorización por Roles y Protección de Endpoints

### Autores

**Pablo Torres**

 [ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

# 1. Introducción a la Seguridad en Spring Boot

En los temas anteriores implementamos **CRUD completo, relaciones, filtros y paginación**. Sin embargo, **TODOS los endpoints están completamente abiertos**.

Cualquier persona puede:
* Ver todos los usuarios
* Crear/modificar/eliminar productos de cualquier usuario
* Acceder a cualquier información sin restricción

En aplicaciones reales esto es **inaceptable**. Necesitamos:

* **Autenticación**: Verificar quién es el usuario (login)
* **Autorización**: Verificar qué puede hacer el usuario (permisos/roles)
* **Protección de endpoints**: Solo usuarios autenticados pueden acceder
* **Control de ownership**: Solo el propietario puede modificar sus recursos

## 1.1. Estrategia de implementación

En este tema implementaremos:

* **Token-based authentication con JWT**: Stateless, escalable, ideal para APIs REST
* **Tabla separada de Roles**: Mejores prácticas (separación de responsabilidades)
* **Relación ManyToMany User-Role**: Un usuario puede tener múltiples roles
* **Spring Security**: Framework estándar de Spring para seguridad
* **BCrypt**: Algoritmo seguro para hash de contraseñas
* **Filtros de autorización**: Proteger endpoints automáticamente

## 1.2. Niveles de protección

| Endpoint | Protección | Ejemplo |
|----------|------------|---------|
| **Público** | Sin autenticación | `POST /auth/login`, `POST /auth/register` |
| **Protegido** | Requiere autenticación | `GET /api/users/me` |
| **Con roles** | Requiere rol específico | `DELETE /api/users/{id}` (solo ADMIN) |
| **Con ownership** | Requiere ser propietario | `PUT /api/products/{id}` (solo owner o ADMIN) |

# 2. Configuración Inicial del Proyecto


## Estructura de paquetes

Se creará el paquete:

```txt
security/
```

Estructura recomendada:

```txt
security/
├── config
│   ├── JwtProperties.java
│   ├── SecurityConfig.java
│   └── SecurityDataInitializer.java
├── controllers
│   └── AuthController.java
├── dtos
│   ├── AuthResponseDto.java
│   ├── LoginRequestDto.java
│   └── RegisterRequestDto.java
├── entities
│   └── RoleEntity.java
├── enums
│   └── RoleName.java
├── filters
│   ├── JwtAuthenticationEntryPoint.java
│   └── JwtAuthenticationFilter.java
├── repositories
│   └── RoleRepository.java
├── services
│   ├── AuthService.java
│   ├── UserDetailsImpl.java
│   └── UserDetailsServiceImpl.java
└── utils
    └── JwtUtil.java
```


Para crear todas las carpetas se puede usar el comando desde la raiz del proyecto (asegurarse tener los mismos nombres de paquetes):

```bash
mkdir -p src/main/java/ec/edu/ups/icc/fundamentos01/security/{config,controllers,dtos,entities,enums,filters,repositories,services,utils}
```

o desde la carpeta `fundamentos01`s

```bash
mkdir -p security/{config,controllers,dtos,entities,enums,filters,repositories,services,utils}
```


## 2.1. Dependencias en Gradle

Archivo: `build.gradle.kts`

```kotlin
dependencies {
	// Otras dependencias existentes...

	// ============== NUEVAS DEPENDENCIAS DE SEGURIDAD ==============
	
	// Spring Security
	implementation("org.springframework.boot:spring-boot-starter-security")
	
	// JWT - JSON Web Token
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
}
```

## 2.2. Configuración en application.yml

Archivo: `src/main/resources/application.yml`

```yaml
# ============== CONFIGURACIÓN DE JWT ==============
jwt:
    # Secret key para firmar tokens (EN PRODUCCIÓN USAR VARIABLE DE ENTORNO)
    secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}
    
    # Tiempo de expiración del access token (30 minutos)
    expiration: 1800000  # 30 minutos en milisegundos
    
    # Tiempo de expiración del refresh token (7 días)
    refresh-expiration: 604800000  # 7 días en milisegundos
    
    # Issuer del token
    issuer: fundamentos01-api
    
    # Header donde se envía el token
    header: Authorization
    
    # Prefijo del token
    prefix: "Bearer "
```

### Explicación de configuración JWT*

* **secret**: Clave secreta para firmar tokens (debe ser >=256 bits para HS256)
* **expiration**: 30 minutos para access tokens (corto para seguridad)
* **refresh-expiration**: 7 días para refresh tokens (permite renovar sin re-login)
* **issuer**: Identifica quién emitió el token
* **header**: Authorization (estándar HTTP)
* **prefix**: "Bearer " (convención OAuth 2.0)

# 3. Modelo de Datos para Seguridad

## 3.1. Entidad Role (nueva)

Archivo: `security/entity/RoleEntity.java`

```java
/**
 * ENTIDAD: Role (Rol de usuario)
 * 
 * Representa un rol en el sistema (ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR).
 * Se relaciona ManyToMany con usuarios → Un usuario puede tener múltiples roles.
 * 
 * Tabla en BD: roles
 * Tabla intermedia: user_roles (creada automáticamente por JPA)
 */
@Entity
@Table(name = "roles")  // Nombre de la tabla en PostgreSQL
public class RoleEntity extends BaseEntity {  // Hereda id, createdAt, updatedAt

    /**
     * Nombre del rol (enum para type-safety)
     * 
     * @Enumerated(EnumType.STRING): Guarda "ROLE_USER" en lugar de ordinal (0, 1, 2)
     * nullable = false: Campo obligatorio
     * unique = true: No pueden existir roles duplicados
     * length = 50: Máximo 50 caracteres en BD
     * 
     * Ejemplo en BD: "ROLE_USER", "ROLE_ADMIN"
     */
    @Column(nullable = false, unique = true, length = 50)
    @Enumerated(EnumType.STRING)  // Guardar nombre del enum, no el número
    private RoleName name;

    /**
     * Descripción del rol (opcional)
     * 
     * Ejemplo: "Usuario estándar con permisos básicos"
     */
    @Column(length = 200)
    private String description;

    

    // ============== CONSTRUCTORES ==============

    // ============== GETTERS Y SETTERS ==============

   
}
```

## 3.2. Enum RoleName

Archivo: `security/enums/RoleName.java`

```java
public enum RoleName {
    ROLE_USER("Usuario estándar con permisos básicos"),
    ROLE_ADMIN("Administrador con permisos completos");

    private final String description;

    RoleName(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

**¿Por qué usar enum?**
* Type-safe: Solo valores válidos
* Fácil de mantener: Cambios en un solo lugar
* Evita typos: "ADMIN" vs "ADMNI"

## 3.3. Actualización de UserEntity


Archivo:

```txt
users/entities/UserEntity.java
```

La entidad actual debe mantener:

```txt
name
email
passwordHash
```

 y demas atributos, a esta entidad se debe agregar la relación con roles.

Código:

```java

/*
 * Entidad JPA del recurso users.
 *
 * Representa la tabla users en PostgreSQL.
 * Se agrega relación con roles para autenticación y autorización.
 */
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    // Otros atributos existentes: name, email, passwordHash, deleted, createdAt, updatedAt


    // ============== NUEVA RELACIÓN CON ROLES ==============

    /**
     * Relación ManyToMany con Roles
     * 
     * @ManyToMany: Un usuario puede tener múltiples roles
     *              Un rol puede estar asignado a múltiples usuarios
     * 
     * fetch = FetchType.EAGER:
     *   - Carga los roles AUTOMÁTICAMENTE al consultar el usuario
     *   - Necesario porque Spring Security necesita los roles al autenticar
     *   - Sin EAGER, tendríamos LazyInitializationException al acceder a roles
     * 
     * @JoinTable: Crea tabla intermedia user_roles
     *   name = "user_roles": Nombre de la tabla intermedia en BD
     *   joinColumns: Columna que referencia a esta entidad (users.id)
     *   inverseJoinColumns: Columna que referencia a RoleEntity (roles.id)
     * 
     * Tabla user_roles en BD:
     *   CREATE TABLE user_roles (
     *       user_id BIGINT REFERENCES users(id),
     *       role_id BIGINT REFERENCES roles(id),
     *       PRIMARY KEY (user_id, role_id)
     *   );
     * 
     * Set<RoleEntity>:
     *   - Set (no List) evita duplicados
     *   - HashSet inicializado para evitar NullPointerException
     * 
     * Ejemplo de uso:
     *   UserEntity user = userRepository.findById(1L);
     *   user.getRoles();  // ← Ya cargados por EAGER
     *   // → [RoleEntity(ROLE_USER), RoleEntity(ROLE_ADMIN)]
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    public UserEntity() {
    }

    // Getters y setters
}
```




### Decisión de diseño: ¿Por qué tabla separada de Roles?

**VENTAJAS de tabla separada (RECOMENDADO)**:
* Separación de responsabilidades
* Reutilización de roles entre usuarios
* Fácil agregar/quitar roles
* Escalable para permisos granulares
* Auditoría independiente


### ¿Cómo funciona esta arquitectura de roles?

**1. Tabla `roles`**:
```sql
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,  -- ROLE_USER, ROLE_ADMIN, etc.
    description VARCHAR(200),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**2. Tabla intermedia `user_roles`** (ManyToMany):
```sql
CREATE TABLE user_roles (
    user_id BIGINT REFERENCES users(id),
    role_id BIGINT REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

**3. Flujo de asignación de roles**:
```
Usuario Nuevo → Registro → Se asigna ROLE_USER por defecto → Tabla user_roles
                ↓
        Admin puede agregar más roles (ROLE_ADMIN, ROLE_MODERATOR)
                ↓
        Roles se cargan en el token JWT → Validación en cada request
```

**Ejemplo de datos**:
```sql
-- Tabla roles
id | name           | description
1  | ROLE_USER      | Usuario estándar
2  | ROLE_ADMIN     | Administrador
3  | ROLE_MODERATOR | Moderador

-- Tabla user_roles (usuario con múltiples roles)
user_id | role_id
1       | 1        -- Pablo tiene ROLE_USER
1       | 2        -- Pablo también tiene ROLE_ADMIN
2       | 1        -- María solo tiene ROLE_USER
```

**¿Por qué necesitamos roles separados?**

1. **Seguridad granular**: Puedes controlar exactamente qué puede hacer cada usuario
2. **Flexibilidad**: Fácil agregar nuevos roles sin modificar la estructura de datos
3. **Reutilización**: Un rol se define una vez y se asigna a múltiples usuarios
4. **Mantenimiento**: Cambiar permisos de un rol afecta automáticamente a todos los usuarios con ese rol
5. **Auditoría**: Puedes rastrear quién tiene qué roles y cuándo se asignaron
6. **Escalabilidad**: En el futuro puedes agregar permisos específicos a cada rol

**Alternativas NO recomendadas**:

```java
//  OPCIÓN 1: Campo simple en User (NO ESCALABLE)
@Column
private String role; // "USER" o "ADMIN" - Solo un rol

//  OPCIÓN 2: Lista de strings (DIFÍCIL DE MANTENER)
@ElementCollection
private List<String> roles; // ["USER", "ADMIN"] - Propenso a errores

//  OPCIÓN 3: Tabla separada con enum (RECOMENDADO - Lo que usamos)
@ManyToMany
private Set<RoleEntity> roles; // Relación con tabla roles
```

# 4. DTOs de Autenticación

## 4.1. LoginRequestDto

Archivo:

```txt
security/dtos/LoginRequestDto.java
```

Código:

```java
package ec.edu.ups.icc.fundamentos01.security.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * DTO usado para recibir credenciales de login.
 */
public class LoginRequestDto {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ingresar un email válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    public LoginRequestDto() {
    }

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.2. RegisterRequestDto

Archivo:

```txt
security/dtos/RegisterRequestDto.java
```

Código:

```java

/*
 * DTO usado para registrar usuarios desde /auth/register.
 */
public class RegisterRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ingresar un email válido")
    @Size(max = 150, message = "El email no debe superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).*$",
            message = "La contraseña debe contener al menos una mayúscula, una minúscula y un número"
    )
    private String password;

    public RegisterRequestDto() {
    }

    // Constructor lleno

    // Getters y setters
}
```

---

## 4.3. AuthResponseDto

Archivo:

```txt
security/dtos/AuthResponseDto.java
```

Código:

```java


/*
 * DTO de respuesta para login y register.
 *
 * Devuelve el token y datos básicos del usuario autenticado.
 */
public class AuthResponseDto {

    private String token;

    private String type = "Bearer";

    private Long userId;

    private String name;

    private String email;

    private Set<String> roles;

    public AuthResponseDto() {
    }

    public AuthResponseDto(
            String token,
            Long userId,
            String name,
            String email,
            Set<String> roles
    ) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.roles = roles;
    }

    // Getters y setters
}
```

---

# 5. Configuración de JWT

## 5.1. JwtProperties (propiedades centralizadas)

Archivo: `security/config/JwtProperties.java`

```java
// imports packages y clases....


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    private String issuer;
    private String header;
    private String prefix;

    // Getters y Setters
   
}
```

### ¿Cómo funciona @ConfigurationProperties?

Esta anotación permite mapear automáticamente propiedades del `application.yml` a campos de la clase Java.

**Ventajas**:
- **Type-safe**: Validación en tiempo de compilación
- **Centralizado**: Todas las propiedades JWT en un solo lugar
- **Reutilizable**: Se inyecta como bean en cualquier clase
- **Fácil de testear**: Puedes crear instancias con valores de prueba

**Mapeo automático**:
```yaml
# application.yml
jwt:
  secret: "mySecretKey..."
  expiration: 1800000
  issuer: "fundamentos01-api"
  ↓
# Automáticamente se mapea a:
JwtProperties {
  secret = "mySecretKey..."
  expiration = 1800000
  issuer = "fundamentos01-api"
}
```

### Explicación de cada propiedad*

**1. secret (String)**

La **clave secreta** utilizada para firmar y validar tokens JWT con el algoritmo HS256.

```java
private String secret;
```

**Características**:
- **Requisito mínimo**: 256 bits (32 caracteres) para HS256
- **Producción**: Usar variable de entorno, NUNCA hardcodear
- **Desarrollo**: Puede usar valor por defecto en application.yml
- **Sensible**: Si se compromete, todos los tokens son vulnerables

**Ejemplo**:
```yaml
# Desarrollo (application.yml)
jwt:
  secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}
  #        ^^^^^^^^^^^  Variable de entorno (producción)
  #                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^  Valor por defecto (desarrollo)

# Producción (variable de entorno)
export JWT_SECRET="tu-clave-super-secreta-generada-aleatoriamente-de-64-caracteres"
```

**Mejores prácticas**:
```bash
# Generar clave segura aleatoria de 64 caracteres
openssl rand -base64 64

# Usar en producción
export JWT_SECRET="clave-generada-aleatoriamente"
java -jar app.jar
```

**2. expiration (Long)**

Tiempo de **expiración del access token** en milisegundos.

```java
private Long expiration;
```

**Características**:
- **Valor recomendado**: 15-60 minutos (900000-3600000 ms)
- **Balance**: Seguridad vs experiencia de usuario
- **Corto**: Más seguro (token robado expira rápido)
- **Largo**: Mejor UX (usuario no necesita re-autenticarse seguido)

**Conversiones comunes**:
```yaml
jwt:
  expiration: 1800000   # 30 minutos
  # 1000 ms = 1 segundo
  # 60000 ms = 1 minuto
  # 1800000 ms = 30 minutos
  # 3600000 ms = 1 hora
```

**Tabla de referencia**:
| Tiempo | Milisegundos | Uso recomendado |
|--------|--------------|-----------------|
| 15 min | 900000 | Alta seguridad (banking) |
| 30 min | 1800000 | Balance seguridad/UX (recomendado) |
| 1 hora | 3600000 | Aplicaciones internas |
| 24 horas | 86400000 | ❌ NO recomendado (inseguro) |

**3. refreshExpiration (Long)**

Tiempo de **expiración del refresh token** en milisegundos.

```java
private Long refreshExpiration;
```

**Características**:
- **Valor recomendado**: 7-30 días (604800000-2592000000 ms)
- **Propósito**: Renovar access tokens sin re-login
- **Más largo que access token**: Permite sesiones persistentes
- **Debe rotarse**: Al usarse, generar nuevo refresh token

**Uso**:
```
Access token expira (30 min)
        ↓
Usuario envía refresh token
        ↓
Servidor valida refresh token
        ↓
Genera NUEVO access token + NUEVO refresh token
        ↓
Usuario continúa sin re-login
```

**Conversiones comunes**:
```yaml
jwt:
  refresh-expiration: 604800000   # 7 días
  # 86400000 ms = 1 día
  # 604800000 ms = 7 días
  # 2592000000 ms = 30 días
```

**4. issuer (String)**

Identificador de **quién emitió el token** (claim "iss" en JWT).

```java
private String issuer;
```

**Características**:
- **Formato**: Nombre de la aplicación o servicio
- **Propósito**: Identificar origen del token
- **Validación**: Verificar que el token fue emitido por nosotros
- **Útil en microservicios**: Diferenciar tokens de diferentes servicios

**Ejemplo**:
```yaml
jwt:
  issuer: "fundamentos01-api"  # Nombre de tu aplicación
```

**En el token**:
```json
{
  "iss": "fundamentos01-api",
  "sub": "1",
  "email": "pablo@example.com"
}
```

**Validación**:
```java
Claims claims = Jwts.parser()
    .verifyWith(key)
    .requireIssuer("fundamentos01-api")  // Valida el issuer
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

**5. header (String)**

Nombre del **header HTTP** donde se envía el token.

```java
private String header;
```

**Características**:
- **Valor estándar**: "Authorization"
- **Convención HTTP**: Usada por OAuth 2.0, APIs REST
- **Alternativas**: "X-Auth-Token" (no estándar, menos común)

**Ejemplo**:
```yaml
jwt:
  header: "Authorization"  # Estándar HTTP
```

**En la petición HTTP**:
```http
GET /api/products HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
               ^^^^^^ prefix
                      ^^^^^^^^^^^ token
```

**Extracción en el filtro**:
```java
String headerAuth = request.getHeader(jwtProperties.getHeader());
// headerAuth = "Bearer eyJhbGci..."
```

**6. prefix (String)**

**Prefijo** del token en el header Authorization.

```java
private String prefix;
```

**Características**:
- **Valor estándar**: "Bearer " (con espacio al final)
- **Convención OAuth 2.0**: Bearer Token Authentication
- **Propósito**: Indicar tipo de autenticación

**Ejemplo**:
```yaml
jwt:
  prefix: "Bearer "  # ← Espacio al final es IMPORTANTE
```

**En el header**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
               ^^^^^^ 
               Prefijo (con espacio)
```

**Extracción del token**:
```java
String headerAuth = request.getHeader("Authorization");
// headerAuth = "Bearer eyJhbGci..."

if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
    String token = headerAuth.substring(7);  // Elimina "Bearer "
    // token = "eyJhbGci..."
}
```

**¿Por qué "Bearer"?**

```
┌──────────┬─────────────────────────────────────┐
│ Tipo     │ Descripción                         │
├──────────┼─────────────────────────────────────┤
│ Basic    │ Authorization: Basic base64(user:pw)│
│ Bearer   │ Authorization: Bearer <token>       │  ← JWT
│ Digest   │ Authorization: Digest username=...  │
│ OAuth    │ Authorization: OAuth oauth_token=...│
└──────────┴─────────────────────────────────────┘
```

"Bearer" significa "portador" → quien presente el token tiene acceso (como un ticket).

### Resumen de configuración completa*

```yaml
# application.yml
jwt:
  # Clave secreta (≥256 bits para HS256)
  secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}
  
  # Access token: 30 minutos
  expiration: 1800000
  
  # Refresh token: 7 días  
  refresh-expiration: 604800000
  
  # Identificador de la aplicación
  issuer: fundamentos01-api
  
  # Header HTTP estándar
  header: Authorization
  
  # Prefijo OAuth 2.0 (con espacio)
  prefix: "Bearer "
```

**Uso en código**:
```java
@Component
public class JwtUtil {
    private final JwtProperties jwtProperties;
    
    public JwtUtil(JwtProperties jwtProperties) {
        // Spring inyecta automáticamente con valores de application.yml
        this.jwtProperties = jwtProperties;
        
        // Acceso a propiedades
        String secret = jwtProperties.getSecret();
        Long expiration = jwtProperties.getExpiration();
        String issuer = jwtProperties.getIssuer();
    }
}
```

## 5.2. JwtUtil (generación y validación de tokens)

Archivo: `security/utils/JwtUtil.java`

```java
// imports packages y clases....

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    /**
     * Constructor: Inicializa JwtUtil con propiedades y clave secreta
     * 
     * @param jwtProperties: Inyectado automáticamente por Spring
     *                        Contiene: secret, expiration, issuer, etc.
     */
    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        
        /**
         * Genera clave segura para algoritmo HS256
         * 
         * Keys.hmacShaKeyFor(): Convierte String a SecretKey
         * .getBytes(): Convierte String a byte array
         * 
         * Requisitos:
         * - Mínimo 256 bits (32 caracteres) para HS256
         * - Si es menor, lanza WeakKeyException
         * 
         * Ejemplo:
         * secret = "mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm"
         * key = SecretKey basada en esos bytes
         * 
         * Esta key se usa para:
         * - Firmar tokens al generarlos (signWith)
         * - Verificar tokens al validarlos (verifyWith)
         */
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

    /**
     * Genera un token JWT desde la autenticación
     * 
     * Se usa en el FLUJO DE LOGIN:
     * 1. Usuario envía email/password
     * 2. AuthenticationManager valida credenciales
     * 3. Se llama a este método para generar el token
     * 
     * @param authentication: Objeto Authentication de Spring Security
     *                        Contiene el usuario autenticado
     * @return String: Token JWT completo ("eyJhbGciOiJIUzI1NiJ9...")
     */
    public String generateToken(Authentication authentication) {
        // 1. Extraer información del usuario autenticado
        //    Cast seguro porque siempre retorna UserDetailsImpl
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        // 2. Calcular fechas de emisión y expiración
        Date now = new Date();  // Fecha actual
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpiration());
        // Ejemplo: now = 2024-01-26 10:00:00
        //          expiration = 1800000 ms (30 minutos)
        //          expiryDate = 2024-01-26 10:30:00

        // 3. Extraer roles del usuario y convertir a String
        //    Ejemplo: [ROLE_USER, ROLE_ADMIN] → "ROLE_USER,ROLE_ADMIN"
        String roles = userPrincipal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)  // Extrae "ROLE_USER", "ROLE_ADMIN"
            .collect(Collectors.joining(","));     // Une con comas

        // 4. Construir y firmar el token JWT
        return Jwts.builder()
            // Subject: Identificador único del usuario (su ID)
            .subject(String.valueOf(userPrincipal.getId()))  // "1"
            
            // Claims personalizados (datos adicionales en el payload)
            .claim("email", userPrincipal.getEmail())     // "pablo@example.com"
            .claim("name", userPrincipal.getName())       // "Pablo Torres"
            .claim("roles", roles)                        // "ROLE_USER,ROLE_ADMIN"
            
            // Issuer: Quién emitió el token
            .issuer(jwtProperties.getIssuer())            // "fundamentos01-api"
            
            // Fechas
            .issuedAt(now)                                // Cuándo se creó
            .expiration(expiryDate)                       // Cuándo expira
            
            // Firma digital con algoritmo HS256
            .signWith(key, Jwts.SIG.HS256)                // Firma con clave secreta
            
            // Compactar: Genera el String final
            .compact();  // → "eyJhbGci...header.eyJzdWI...payload.firma"
    }

    /**
     * Genera un token JWT desde UserDetailsImpl directamente
     * 
     * Se usa en el FLUJO DE REGISTRO:
     * 1. Usuario se registra
     * 2. Se crea UserEntity en BD
     * 3. Se convierte a UserDetailsImpl
     * 4. Se llama a este método (sin necesidad de autenticar primero)
     * 
     * Similar a generateToken() pero sin objeto Authentication
     */
    public String generateTokenFromUserDetails(UserDetailsImpl userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpiration());

        String roles = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        return Jwts.builder()
            .subject(String.valueOf(userDetails.getId()))
            .claim("email", userDetails.getEmail())
            .claim("name", userDetails.getName())
            .claim("roles", roles)
            .issuer(jwtProperties.getIssuer())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Extrae el ID de usuario del token
     * 
     * Se usa en JwtAuthenticationFilter para:
     * 1. Validar el token
     * 2. Extraer el ID del usuario
     * 3. Cargar el usuario desde BD
     * 
     * @param token: Token JWT (sin "Bearer ")
     * @return Long: ID del usuario
     */
    public Long getUserIdFromToken(String token) {
        // 1. Parsear y validar el token
        Claims claims = Jwts.parser()
            .verifyWith(key)              // Verifica firma con clave secreta
            .build()                      // Construye el parser
            .parseSignedClaims(token)     // Parsea el token
            .getPayload();                // Obtiene el payload (claims)

        // 2. Extraer el subject (ID del usuario)
        //    subject = "1" (guardado como String en el token)
        //    Long.parseLong("1") = 1L
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extrae el email del token
     * 
     * Similar a getUserIdFromToken pero extrae un claim personalizado
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        // Extraer claim "email" como String
        return claims.get("email", String.class);
    }

    /**
     * Valida el token JWT
     * 
     * VERIFICA:
     * 1. Firma: ¿El token fue firmado por nosotros?
     * 2. Formato: ¿El token tiene estructura correcta?
     * 3. Expiración: ¿El token aún es válido?
     * 
     * Se usa en JwtAuthenticationFilter en CADA REQUEST
     * 
     * @param authToken: Token completo (sin "Bearer ")
     * @return boolean: true si válido, false si inválido
     */
    public boolean validateToken(String authToken) {
        try {
            // Intenta parsear el token
            // Si algo falla, lanza excepción
            Jwts.parser()
                .verifyWith(key)              // Verifica firma con nuestra clave
                .build()
                .parseSignedClaims(authToken);
            
            // Si llegamos aquí, el token es VÁLIDO
            return true;
            
        } catch (SignatureException ex) {
            // Firma inválida: Token modificado o clave incorrecta
            // Ejemplo: Alguien cambió el payload pero no puede firmar correctamente
            logger.error("Firma JWT inválida: {}", ex.getMessage());
            
        } catch (MalformedJwtException ex) {
            // Token malformado: No tiene estructura correcta (header.payload.signature)
            // Ejemplo: "abc123" en lugar de token válido
            logger.error("Token JWT malformado: {}", ex.getMessage());
            
        } catch (ExpiredJwtException ex) {
            // Token expirado: Pasaron más de 30 minutos desde su creación
            // Ejemplo: Token creado a las 10:00, ahora son las 10:35
            logger.error("Token JWT expirado: {}", ex.getMessage());
            
        } catch (UnsupportedJwtException ex) {
            // Token no soportado: Usa algoritmo que no soportamos
            // Ejemplo: Token firmado con RS256 pero esperamos HS256
            logger.error("Token JWT no soportado: {}", ex.getMessage());
            
        } catch (IllegalArgumentException ex) {
            // Claims vacío: Token sin payload
            logger.error("JWT claims string está vacío: {}", ex.getMessage());
        }
        
        // Si cayó en cualquier catch, el token es INVÁLIDO
        return false;
    }
}
```

### ¿Cómo funciona JWT?

#### Anatomía de un token JWT

Un JWT consta de 3 partes separadas por puntos:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJwYWJsb0BleGFtcGxlLmNvbSJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
   HEADER            .              PAYLOAD                  .           SIGNATURE
```

**1. HEADER (cabecera)**:
```json
{
  "alg": "HS256",      // Algoritmo de firma
  "typ": "JWT"         // Tipo de token
}
```

**2. PAYLOAD (datos del usuario)**:
```json
{
  "sub": "1",                           // Subject (ID del usuario)
  "email": "pablo@example.com",
  "name": "Pablo Torres",
  "roles": "ROLE_USER,ROLE_ADMIN",     // Roles del usuario
  "iss": "fundamentos01-api",          // Issuer (quién emitió el token)
  "iat": 1706268600,                    // Issued At (cuándo se creó)
  "exp": 1706270400                     // Expiration (cuándo expira)
}
```

**3. SIGNATURE (firma digital)**:
```
HMAC-SHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  SECRET_KEY
)
```

#### ¿Por qué JWT es seguro?

**1. Firma criptográfica (SIGNATURE)**:
- La firma se genera con una **clave secreta** que solo conoce el servidor
- Si alguien modifica el payload (ej: cambiar roles), la firma no coincidirá
- El servidor puede **verificar la autenticidad** del token en cada request

**Ejemplo de ataque fallido**:
```javascript
//  Atacante intenta modificar el token
Payload original: { "roles": "ROLE_USER" }
Payload modificado: { "roles": "ROLE_ADMIN" }  // Intento de escalada de privilegios

//  Servidor detecta la modificación
validateToken(token) → Firma inválida → 401 Unauthorized
```

**2. Stateless (sin estado en el servidor)**:
- El servidor **NO almacena** los tokens en memoria/base de datos
- Toda la información está en el token (self-contained)
- **Escalabilidad**: No necesitas sincronizar sesiones entre servidores

**Comparación con sesiones tradicionales**:
```
┌─────────────────────┬──────────────────────┬────────────────────┐
│                     │   Sesiones (Cookies) │   JWT (Tokens)     │
├─────────────────────┼──────────────────────┼────────────────────┤
│ Almacenamiento      │ Servidor (memoria/BD)│ Cliente (LocalStorage)
│ Escalabilidad       │  Complicado         │  Fácil           │
│ Stateful/Stateless  │ Stateful             │ Stateless          │
│ Revocación          │  Inmediata          │  Hasta que expire│
│ APIs REST           │  No ideal           │  Perfecto        │
│ Mobile/SPA          │  Problemas CORS     │  Nativo          │
└─────────────────────┴──────────────────────┴────────────────────┘
```

**3. Expiración automática**:
- Token expira después de 30 minutos (`expiration: 1800000`)
- Si roban el token, solo es útil hasta que expire
- Refresh tokens (7 días) permiten renovar sin re-login

#### Flujo completo de autenticación con JWT

```
┌────────────┐                                    ┌──────────────┐
│   Cliente  │                                    │   Servidor   │
└──────┬─────┘                                    └──────┬───────┘
       │                                                 │
       │ 1. POST /auth/login                             │
       │    { email, password }                          │
       ├─────────────────────────────────────────────────>│
       │                                                 │
       │                        2. Valida credenciales   │
       │                           con BCrypt            │
       │                                                 │
       │                        3. Genera JWT con:       │
       │                           - ID usuario          │
       │                           - Email, nombre       │
       │                           - Roles               │
       │                           - Firma con SECRET    │
       │                                                 │
       │ 4. Responde con token                           │
       │    { token: "eyJhbGci...", userId, roles }      │
       │<─────────────────────────────────────────────────┤
       │                                                 │
       │ 5. Cliente guarda token                         │
       │    localStorage.setItem('token', jwt)           │
       │                                                 │
       │ 6. GET /api/products                            │
       │    Authorization: Bearer eyJhbGci...            │
       ├─────────────────────────────────────────────────>│
       │                                                 │
       │                        7. JwtAuthenticationFilter
       │                           - Extrae token         │
       │                           - Valida firma         │
       │                           - Verifica expiración  │
       │                           - Carga usuario        │
       │                           - Establece SecurityContext
       │                                                 │
       │                        8. Controlador accede a:  │
       │                           @AuthenticationPrincipal
       │                           currentUser            │
       │                                                 │
       │ 9. Responde con productos                       │
       │    [{ id: 1, name: "...", owner: ... }]         │
       │<─────────────────────────────────────────────────┤
       │                                                 │
```

#### Métodos clave de JwtUtil explicados

**`generateToken(Authentication authentication)`**:
```java
// ¿Qué hace?
// 1. Extrae información del usuario autenticado
// 2. Construye el payload con claims personalizados
// 3. Firma el token con la clave secreta
// 4. Retorna el token como string

// Ejemplo de uso:
Authentication auth = authenticationManager.authenticate(...);
String token = jwtUtil.generateToken(auth);
// → "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIi..."
```

**`validateToken(String token)`**:
```java
// ¿Qué hace?
// 1. Intenta parsear el token con la clave secreta
// 2. Verifica la firma (¿coincide con nuestro secret?)
// 3. Verifica la expiración (¿aún es válido?)
// 4. Retorna true si todo está correcto

// Casos que detecta:
try {
    Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    return true;
} catch (SignatureException e) {
    // Token modificado o clave incorrecta
} catch (ExpiredJwtException e) {
    // Token expirado (pasaron más de 30 min)
} catch (MalformedJwtException e) {
    // Token mal formado (no tiene 3 partes)
}
```

**`getUserIdFromToken(String token)`**:
```java
// ¿Qué hace?
// 1. Parsea el token (ya validado previamente)
// 2. Extrae el claim "sub" (subject = ID usuario)
// 3. Lo convierte a Long

// Ejemplo:
String token = "eyJhbGci...";
Long userId = jwtUtil.getUserIdFromToken(token);
// → 1L

// Se usa en JwtAuthenticationFilter para:
// - Cargar el usuario desde la BD
// - Establecer la autenticación en SecurityContext
```

#### Configuración de seguridad JWT

**¿Por qué HS256 y no otros algoritmos?**

```
┌──────────┬─────────────────┬───────────┬──────────────┐
│ Algoritmo│ Tipo            │ Velocidad │ Caso de uso  │
├──────────┼─────────────────┼───────────┼──────────────┤
│ HS256    │ Simétrico       │ ⚡⚡⚡      │  APIs REST  │
│          │ (clave secreta) │           │    internas  │
├──────────┼─────────────────┼───────────┼──────────────┤
│ RS256    │ Asimétrico      │ ⚡        │ Federación   │
│          │ (clave pública/ │           │ de identidad │
│          │  privada)       │           │ (OAuth)      │
└──────────┴─────────────────┴───────────┴──────────────┘
```

**HS256** es ideal porque:
- Solo el servidor firma Y valida (no necesitamos clave pública)
- Más rápido que algoritmos asimétricos
- Suficientemente seguro con clave ≥256 bits
- Menor complejidad de configuración

**Configuración de la clave secreta**:
```yaml
jwt:
  #  IMPORTANTE: En producción NUNCA hardcodear
  secret: ${JWT_SECRET:mySecretKeyForJWT2024...}
  #        ^^^^^^^^^^^^  Variable de entorno
  #                      ^^^^^^^^^^^^^^^^^^^^  Valor por defecto (solo desarrollo)
```

**Mejores prácticas**:
```bash
# Producción: Usar variable de entorno
export JWT_SECRET="tu-clave-super-secreta-de-al-menos-256-bits"
java -jar app.jar

# Desarrollo: Usar valor por defecto (ya en application.yml)
./gradlew bootRun
```

### Aspectos clave del JwtUtil*

* **HS256**: Algoritmo de firma HMAC con SHA-256 (seguro y eficiente)
* **Claims personalizados**: email, name, roles en el payload
* **Validación robusta**: Manejo de todos los casos de error
* **Type-safe**: Usa el API moderno de jjwt 0.12.x
* **Stateless**: No almacena información en el servidor
* **Self-contained**: Token contiene toda la información necesaria

# 6. UserDetailsService Implementation

## 6.1. UserDetailsImpl

Archivo: `security/services/UserDetailsImpl.java`

```java
// imports packages y clases....

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String name, String email, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * Factory method para crear UserDetailsImpl desde UserEntity
     */
    public static UserDetailsImpl build(UserEntity user) {
        // Convertir roles a authorities de Spring Security
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getName().name()))
            .collect(Collectors.toList());

        return new UserDetailsImpl(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPassword(),
            authorities
        );
    }

    // ============== GETTERS ==============


    // ============== MÉTODOS DE UserDetails ==============

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;  // Usamos email como username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

### ¿Cómo funciona UserDetailsImpl?

Este adaptador convierte nuestro `UserEntity` en algo que Spring Security entiende. Es el **puente** entre nuestra base de datos y el sistema de seguridad.

**Propósito principal**:
- **Adaptador**: Convierte UserEntity → UserDetails (formato de Spring Security)
- **Inmutabilidad**: Campos `final` garantizan que el usuario no cambie durante la request
- **Autoridades**: Convierte Set<RoleEntity> → Collection<GrantedAuthority>

**Flujo de conversión**:
```java
// 1. UserEntity desde BD
UserEntity user = userRepository.findByEmail("pablo@example.com");
// user.getRoles() = [RoleEntity(ROLE_USER), RoleEntity(ROLE_ADMIN)]

// 2. Conversión a UserDetailsImpl
UserDetailsImpl userDetails = UserDetailsImpl.build(user);

// 3. Roles convertidos a authorities
userDetails.getAuthorities() 
// → [SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_ADMIN")]

// 4. Spring Security usa esto para:
@PreAuthorize("hasRole('ADMIN')")  // ← Busca "ROLE_ADMIN" en authorities
```

**Métodos adicionales (no estándar de UserDetails)**:
```java
// getId() - Para ownership y auditoría
Long userId = currentUser.getId();
if (product.getOwner().getId().equals(userId)) { ... }

// getName() - Para UI y mensajes
String name = currentUser.getName();  // "Pablo Torres"

// getEmail() - Para notificaciones y claims de JWT
String email = currentUser.getEmail();  // "pablo@example.com"
```

**Métodos del contrato UserDetails**:
| Método | Retorno | Uso en Spring Security |
|--------|---------|------------------------|
| `getAuthorities()` | Collection<GrantedAuthority> | Validar @PreAuthorize, @Secured |
| `getPassword()` | String | Validar credenciales en login |
| `getUsername()` | String | Identificador único (email en nuestro caso) |
| `isAccountNonExpired()` | boolean | ¿Cuenta activa? (siempre true en nuestro caso) |
| `isAccountNonLocked()` | boolean | ¿No bloqueada? (siempre true) |
| `isCredentialsNonExpired()` | boolean | ¿Contraseña válida? (siempre true) |
| `isEnabled()` | boolean | ¿Cuenta habilitada? (siempre true) |

## 6.2. UserDetailsServiceImpl

Archivo: `security/services/UserDetailsServiceImpl.java`

```java
// imports packages y clases....

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl: Carga usuarios desde la base de datos
 */
@Service // Componente de Spring (se inyecta automáticamente)
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * Repositorio para acceder a la base de datos
     * 
     * Inyectado por Spring automáticamente (constructor injection)
     */
    private final UserRepository userRepository;

    /**
     * Constructor: Spring inyecta UserRepository automáticamente
     * 
     * @param userRepository: Repositorio de usuarios
     */
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * loadUserByUsername: MÉTODO PRINCIPAL de UserDetailsService
     * 
     * SecurityContext.setAuthentication(userDetails)
     * 
     * @param email: Email del usuario (lo llamamos username por el contrato)
     * @return UserDetails: Usuario convertido a formato Spring Security
     * @throws UsernameNotFoundException: Si el usuario no existe
     * 
     * @Transactional(readOnly = true):
     *                         - readOnly = true: Optimización para consultas SELECT
     *                         - Permite a Hibernate/PostgreSQL optimizar la query
     *                         - NO permite operaciones de escritura (INSERT,
     *                         UPDATE, DELETE)
     *                         - Si intentamos modificar, lanza excepción
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        /**
         * 1. Buscar usuario por email en la base de datos
         * 
         * Nota: Los roles se cargan automáticamente por FetchType.EAGER
         */
        UserEntity user = userRepository.findByEmail(email)
                /**
                 * .orElseThrow(): Si Optional está vacío, lanza excepción
                 */
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con email: " + email));

        /**
         * 2. Convertir UserEntity → UserDetailsImpl
         * 
         * UserDetailsImpl.build(user):
         * - Factory method que convierte nuestro UserEntity
         * - Extrae roles y los convierte a authorities
         * - Retorna objeto compatible con Spring Security
         * 
         */
        return UserDetailsImpl.build(user);
    }
}
```

### ¿Cómo funciona UserDetailsServiceImpl?

Esta clase es el **CONECTOR** entre Spring Security y nuestra base de datos.

**Propósito**:
- Implementar la interfaz UserDetailsService de Spring Security
- Convertir UserEntity (JPA) → UserDetails (Spring Security)
- Manejar caso cuando usuario no existe
- Conectar Spring Security con nuestra base de datos

**¿CUÁNDO SE LLAMA?**
  1. Durante LOGIN: Para validar credenciales
  2. En cada REQUEST autenticado: Para cargar datos del usuario desde el token
  3. Por DaoAuthenticationProvider: Para obtener el usuario a autenticar
  
**FLUJO:**
Spring Security → UserDetailsService.loadUserByUsername() 
                 → userRepository.findByEmail() 
                 → UserDetailsImpl.build()
                 → Retorna UserDetails

**Flujos de uso**:

**FLUJO 1: Login (autenticación inicial)**
```
Usuario ingresa: { email: "pablo@example.com", password: "Secure123" }
        ↓
AuthenticationManager
        ↓
DaoAuthenticationProvider pregunta: "¿Quién es pablo@example.com?"
        ↓
loadUserByUsername("pablo@example.com")  ← ESTE MÉTODO
        ↓
userRepository.findByEmail("pablo@example.com")
        ↓
SELECT * FROM users WHERE email = 'pablo@example.com'
        ↓
¿Existe? 
   SÍ → UserDetailsImpl.build(user) → Retorna UserDetails
   NO → UsernameNotFoundException → 401 Unauthorized
        ↓
DaoAuthenticationProvider valida:
  passwordEncoder.matches("Secure123", userDetails.getPassword())
        ↓
 Correcto → Genera JWT
 Incorrecto → 401 Unauthorized
```

**FLUJO 2: Request con JWT (autenticación en cada petición)**
```
Cliente: GET /api/products
Header: Authorization: Bearer eyJhbGci...
        ↓
JwtAuthenticationFilter
        ↓
jwt.validateToken(token) →  Válido
        ↓
jwt.getEmailFromToken(token) → "pablo@example.com"
        ↓
loadUserByUsername("pablo@example.com")  ← ESTE MÉTODO
        ↓
userRepository.findByEmail("pablo@example.com")
        ↓
UserDetailsImpl.build(user)
        ↓
SecurityContext.setAuthentication(userDetails)
        ↓
Controlador recibe @AuthenticationPrincipal UserDetailsImpl currentUser
```

**Características clave**:

| Aspecto | Detalle |
|---------|---------|
| **@Service** | Spring lo registra como bean inyectable |
| **@Transactional(readOnly=true)** | Optimiza queries SELECT, evita writes accidentales |
| **UsernameNotFoundException** | Excepción específica de Spring Security → 401 |
| **Optional.orElseThrow()** | Manejo limpio de caso "usuario no encontrado" |
| **UserDetailsImpl.build()** | Conversión limpia UserEntity → UserDetails |

**Diferencia con repositorio normal**:
```java
//  Repositorio normal (NO para Spring Security)
UserEntity user = userRepository.findByEmail(email).orElse(null);
if (user != null) { ... }

//  UserDetailsService (requerido por Spring Security)
UserDetails user = userDetailsService.loadUserByUsername(email);
// Si no existe, lanza UsernameNotFoundException automáticamente
// Spring Security convierte la excepción en 401 Unauthorized
```

# 7. Filtros de Seguridad

## 7.1. JwtAuthenticationFilter

Archivo: `security/filters/JwtAuthenticationFilter.java`

```java
// imports packages y clases....
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter: Filtro que valida JWT en CADA REQUEST
 */
@Component // Spring lo registra automáticamente como bean
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Logger para debugging y errores
     * 
     * Niveles de log:
     * - logger.debug(): Solo en desarrollo (no aparece en producción)
     * - logger.error(): Errores críticos (aparece en producción)
     */
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * Dependencias inyectadas por Spring
     */
    private final JwtUtil jwtUtil; // Para validar y extraer datos del JWT
    private final UserDetailsServiceImpl userDetailsService; // Para cargar usuario desde BD
    private final JwtProperties jwtProperties; // Configuración JWT (header, prefix)

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            JwtProperties jwtProperties) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * doFilterInternal: MÉTODO PRINCIPAL del filtro
     * 
     * Se ejecuta UNA VEZ por cada request HTTP
     * 
     * @param request:     Petición HTTP entrante
     * @param response:    Respuesta HTTP saliente
     * @param filterChain: Cadena de filtros restantes
     * 
     *                     IMPORTANTE:
     *                     - Este método NO debe lanzar excepciones
     *                     - Si hay error, solo logueamos y continuamos
     *                     - El SecurityContext quedará vacío → Spring Security
     *                     rechazará la petición
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            /**
             * PASO 1: Extraer token del header Authorization
             */
            String jwt = getJwtFromRequest(request);

            /**
             * PASO 2: Validar y autenticar SOLO si hay token
             */
            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {

                /**
                 * PASO 3: Extraer email del token
                 */
                String email = jwtUtil.getEmailFromToken(jwt);

                /**
                 * PASO 4: Cargar usuario desde base de datos
                 */
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                /**
                 * PASO 5: Crear objeto Authentication
                 * 
                 * UsernamePasswordAuthenticationToken:
                 * - Implementación de Authentication de Spring Security
                 * - Aunque se llama "Password", NO usamos contraseña aquí
                 * - Ya validamos el JWT, no necesitamos validar password
                 * 
                 * Constructor con 3 parámetros:
                 * 
                 * @param principal:   El usuario (UserDetails)
                 * @param credentials: Credenciales (null porque ya autenticamos con JWT)
                 * @param authorities: Roles/permisos del usuario
                 */
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, // Principal (el usuario)
                        null, // Credentials (no necesarias)
                        userDetails.getAuthorities() // Authorities (roles/permisos)
                );

                /**
                 * Establecer detalles adicionales de la request
                 * 
                 * WebAuthenticationDetailsSource:
                 * - Extrae información de la HttpServletRequest
                 * - IP del cliente
                 * - Session ID (si existe)
                 * - Otros metadatos de la petición
                 * 
                 * .buildDetails(request):
                 * - Crea objeto WebAuthenticationDetails
                 * - Útil para auditoría y logs
                 * 
                 * Ejemplo de details:
                 * {
                 * remoteAddress: "192.168.1.100",
                 * sessionId: null (porque somos stateless)
                 * }
                 */
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                /**
                 * PASO 6: Establecer autenticación en SecurityContext
                 * 
                 * SecurityContextHolder:
                 * - ThreadLocal que almacena el contexto de seguridad
                 * - ThreadLocal: Una variable por thread (cada request = thread diferente)
                 * - Permite acceder al usuario autenticado desde cualquier parte del código
                 * 
                 * .getContext():
                 * - Obtiene o crea el SecurityContext para este thread
                 * 
                 * .setAuthentication(authentication):
                 * - Almacena el objeto Authentication
                 * - A partir de ahora, el usuario está AUTENTICADO
                 * - Spring Security permitirá acceso a endpoints protegidos
                 * 
                 * ¿Cómo se usa después?
                 * 
                 * En controladores:
                 * 
                 * @AuthenticationPrincipal UserDetailsImpl currentUser
                 * 
                 *                          En servicios:
                 *                          Authentication auth =
                 *                          SecurityContextHolder.getContext().getAuthentication();
                 *                          UserDetailsImpl user = (UserDetailsImpl)
                 *                          auth.getPrincipal();
                 * 
                 *                          En @PreAuthorize:
                 *                          @PreAuthorize("hasRole('ADMIN')") ← Lee authorities
                 *                          de aquí
                 */
                SecurityContextHolder.getContext().setAuthentication(authentication);

                /**
                 * Log de debug: Solo en desarrollo
                 * 
                 * logger.debug():
                 * - Solo aparece si logging.level.root=DEBUG
                 * - NO aparece en producción (logging.level.root=INFO)
                 * - Útil para debugging durante desarrollo
                 * 
                 * Mensaje de ejemplo:
                 * "Usuario autenticado: pablo@example.com"
                 */
                logger.debug("Usuario autenticado: {}", email);
            }

        } catch (Exception ex) {
            /**
             * Manejo de errores: Solo loguear, NO lanzar excepción
             * 
             * ¿Por qué no lanzar la excepción?
             * - Si lanzamos excepción, la request se aborta completamente
             * - Mejor: Dejar que continúe sin autenticación
             * - Spring Security se encargará de rechazarla con 401
             * 
             */
            logger.error("No se pudo establecer la autenticación del usuario", ex);
        }

        /**
         * PASO 7: Continuar con la cadena de filtros
         */
        filterChain.doFilter(request, response);
    }

    /**
     * getJwtFromRequest: Método helper para extraer JWT del header
     * 
     * FLUJO:
     * 1. Lee header "Authorization"
     * 2. Verifica que empiece con "Bearer "
     * 3. Extrae solo el token (sin "Bearer ")
     * 4. Retorna token o null
     * 
     * @param request: Petición HTTP
     * @return String: Token JWT o null si no existe
     * 
     *         Ejemplo:
     *         Header: "Authorization: Bearer eyJhbGci..."
     *         Retorna: "eyJhbGci..."
     * 
     *         Header: "Authorization: Basic abc123" (no es Bearer)
     *         Retorna: null
     * 
     *         Sin header Authorization
     *         Retorna: null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        /**
         * 1. Leer header Authorization
         * 
         * request.getHeader(jwtProperties.getHeader()):
         * - jwtProperties.getHeader() = "Authorization"
         * - Lee el valor del header
         * - Retorna null si no existe
         * 
         * Ejemplo:
         * bearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9..."
         */
        String bearerToken = request.getHeader(jwtProperties.getHeader());

        /**
         * 2. Validar y extraer token
         * 
         * StringUtils.hasText(bearerToken):
         * - Verifica que NO sea null, vacío o solo espacios
         * 
         * bearerToken.startsWith(jwtProperties.getPrefix()):
         * - jwtProperties.getPrefix() = "Bearer "
         * - Verifica que el header comience con "Bearer "
         * - Importante: Incluye el espacio después de "Bearer"
         * 
         * bearerToken.substring(jwtProperties.getPrefix().length()):
         * - Extrae desde la posición 7 (longitud de "Bearer ")
         * - Ejemplo: "Bearer abc123".substring(7) = "abc123"
         * - Retorna solo el token, sin el prefijo
         * 
         * Si NO cumple las condiciones:
         * - Retorna null
         * - El filtro NO procesará autenticación
         * - La request continuará sin autenticación
         */
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtProperties.getPrefix())) {
            return bearerToken.substring(jwtProperties.getPrefix().length());
        }

        return null;
    }
}
```

### ¿Cómo funciona JwtAuthenticationFilter?

Este filtro es el **GUARDIÁN** que protege todos los endpoints. Se ejecuta en CADA petición HTTP.

**Flujo completo de una petición**:
```
Cliente envía: GET /api/products
Header: Authorization: Bearer eyJhbGci...
        ↓
┌─────────────────────────────────────────┐
│ JwtAuthenticationFilter                 │  ← AQUÍ ESTAMOS
│ 1. Extrae token del header              │
│ 2. Valida token con JwtUtil             │
│ 3. Extrae email del token               │
│ 4. Carga usuario desde BD               │
│ 5. Crea Authentication                  │
│ 6. Establece en SecurityContext         │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ Spring Security                         │
│ Verifica @PreAuthorize                  │
│ Verifica .authorizeHttpRequests()       │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ Controlador                             │
│ @AuthenticationPrincipal currentUser    │
│ Ejecuta lógica de negocio              │
└─────────────────────────────────────────┘
        ↓
 Response 200 OK con datos
```

**Casos de uso**:

| Situación | Token | Validación | SecurityContext | Resultado |
|-----------|-------|------------|-----------------|-----------|
| **Request público** |  No | N/A | Vacío |  Pasa (si endpoint es público) |
| **Token válido** |  Sí |  Válido | Usuario autenticado |  Pasa a controlador |
| **Token expirado** |  Sí |  Inválido | Vacío |  401 Unauthorized |
| **Token modificado** |  Sí |  Inválido | Vacío |  401 Unauthorized |
| **Sin token en endpoint protegido** |  No | N/A | Vacío |  401 Unauthorized |

**Ventajas de OncePerRequestFilter**:
- **Garantía de ejecución única**: No se ejecuta múltiples veces por forward/include
- **Método doFilterInternal**: API más simple que Filter estándar
- **Excepciones manejadas**: Spring Security maneja excepciones automáticamente

**Optimizaciones posibles**:
```java
// ACTUAL: Consulta BD en cada request
UserDetails userDetails = userDetailsService.loadUserByUsername(email);

// OPTIMIZACIÓN 1: Cache con Redis (para alto tráfico)
@Cacheable(value = "users", key = "#email")
public UserDetails loadUserByUsername(String email) { ... }

// OPTIMIZACIÓN 2: Incluir más datos en JWT (menos seguro)
// NO recomendado: Si cambian los roles, JWT viejo tendría roles viejos
```

## 7.2. JwtAuthenticationEntryPoint

Archivo: `security/filters/JwtAuthenticationEntryPoint.java`

```java
// imports packages y clases....

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JwtAuthenticationEntryPoint: Maneja errores de autenticación
 * 
 * PROPÓSITO:
 * - Capturar TODOS los errores de autenticación
 * - Retornar respuesta JSON consistente con formato 401 Unauthorized
 * - Reemplazar el comportamiento por defecto de Spring Security
 * 
 * ¿CUÁNDO SE EJECUTA?
 * - Cuando NO hay token JWT en request a endpoint protegido
 * - Cuando el token JWT es inválido (firma incorrecta, expirado, malformado)
 * - Cuando JwtAuthenticationFilter NO establece autenticación en SecurityContext
 * - Cuando Spring Security detecta falta de autenticación
 * 
 * ¿POR QUÉ NO USAR @RestControllerAdvice?
 * - @RestControllerAdvice captura excepciones DENTRO de controladores
 * - AuthenticationException se lanza ANTES de llegar al controlador
 * - Ocurre en la cadena de FILTROS de seguridad
 * - Por eso necesitamos AuthenticationEntryPoint
 * 
 * DIFERENCIA CON GlobalExceptionHandler:
 * ┌──────────────────────────────────────────────────────────┐
 * │ Request → Filtros → ¿Autenticado? → Controlador → Response│
 * │            ↑                          ↑                   │
 * │     AuthenticationEntryPoint    @RestControllerAdvice    │
 * │     (errores ANTES controlador) (errores EN controlador) │
 * └──────────────────────────────────────────────────────────┘
 * 
 * INTERFAZ AuthenticationEntryPoint:
 * - Parte de Spring Security
 * - Se configura en SecurityConfig con:
 *   .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
 * - Método principal: commence() → Se ejecuta cuando falla autenticación
 */
@Component  // Spring lo registra como bean para inyección
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Logger para registrar errores de autenticación
     * 
     * Útil para:
     * - Debugging de problemas de autenticación
     * - Auditoría de intentos de acceso no autorizados
     * - Monitoreo de ataques (múltiples 401 desde misma IP)
     */
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    /**
     * ObjectMapper: Convierte objetos Java a JSON
     * 
     * Jackson ObjectMapper:
     * - Serializa ErrorResponse a JSON
     * - Configurado automáticamente por Spring Boot
     * - Incluye JavaTimeModule para fechas
     * 
     * Inyección:
     * - Spring proporciona su ObjectMapper configurado
     * - Es el MISMO ObjectMapper que usan los @RestController
     * - Garantiza consistencia en formato de respuestas
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor: Inyección de dependencias
     * 
     * Spring inyecta su ObjectMapper configurado
     */
    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * commence: MÉTODO PRINCIPAL que maneja errores de autenticación
     * 
     * Se ejecuta AUTOMÁTICAMENTE cuando:
     * 1. JwtAuthenticationFilter NO encuentra token válido
     * 2. SecurityContext está VACÍO al llegar a endpoint protegido
     * 3. Spring Security detecta falta de autenticación
     * 
     * FLUJO:
     * 1. Spring Security detecta falta de autenticación
     * 2. Llama a commence() con detalles del error
     * 3. Este método construye respuesta JSON 401
     * 4. Escribe respuesta directamente en HttpServletResponse
     * 5. La request se termina (NO llega al controlador)
     * 
     * @param request: Petición HTTP que causó el error
     * @param response: Respuesta HTTP donde escribimos el error
     * @param authException: Excepción de autenticación con detalles del error
     * 
     * IMPORTANTE:
     * - Este método escribe DIRECTAMENTE en response
     * - NO retorna nada (void)
     * - Después de ejecutar, la request se termina
     * - El controlador NUNCA se ejecuta
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        /**
         * 1. Loguear el error
         * 
         * logger.error():
         * - Registra error en logs de aplicación
         * - Incluye mensaje de la excepción
         * - Útil para debugging y auditoría
         * 
         * authException.getMessage():
         * - Descripción del error de autenticación
         * - Ejemplos:
         *   * "Full authentication is required to access this resource"
         *   * "JWT token is expired"
         *   * "Bad credentials"
         * 
         * Ejemplo de log:
         * ERROR JwtAuthenticationEntryPoint - Error de autenticación: 
         *   Full authentication is required to access this resource
         */
        logger.error("Error de autenticación: {}", authException.getMessage());

        /**
         * 2. Crear respuesta de error estructurada
         * 
         * ErrorResponse:
         * - Clase personalizada de nuestro GlobalExceptionHandler
         * - Formato CONSISTENTE con otros errores de la API
         * - Incluye: status, message, timestamp, path
         * 
         * ¿Por qué usar ErrorResponse?
         * - Consistencia: Mismo formato para todos los errores
         * - Reutilización: Ya existe en GlobalExceptionHandler
         * - Claridad: Cliente recibe estructura conocida
         * 
         * Estructura de ErrorResponse:
         * {
         *   "timestamp": "2024-01-15T10:30:00",
         *   "status": 401,
         *   "error": "Unauthorized",
         *   "message": "Token de autenticación inválido...",
         *   "path": "/api/products"
         * }
         * 
         * Parámetros:
         * 1. HttpStatus.UNAUTHORIZED = 401
         * 2. Mensaje descriptivo en español
         * 3. request.getRequestURI() = path del endpoint que causó error
         * 
         * Mensaje detallado:
         * - Explica QUÉ salió mal: "Token inválido o no proporcionado"
         * - Explica CÓMO solucionarlo: "Debe incluir token en header"
         * - Muestra formato esperado: "Authorization: Bearer <token>"
         */
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.UNAUTHORIZED,  // Status 401
            "Token de autenticación inválido o no proporcionado. " +
                "Debe incluir un token válido en el header Authorization: Bearer <token>",
            request.getRequestURI()   // Path del endpoint (ej: /api/products)
        );

        /**
         * 3. Configurar Content-Type de la respuesta
         * 
         * MediaType.APPLICATION_JSON_VALUE = "application/json"
         * 
         * ¿Por qué es importante?
         * - Cliente sabrá que la respuesta es JSON
         * - Navegadores/clientes parsearán como JSON automáticamente
         * - Evita errores de parsing en frontend
         * 
         * Si olvidamos esto:
         * - Content-Type sería "text/html" por defecto
         * - Cliente intentaría parsear JSON como HTML
         * - Errores en frontend: "Unexpected token < in JSON"
         */
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        /**
         * 4. Establecer código de estado HTTP
         * 
         * HttpServletResponse.SC_UNAUTHORIZED = 401
         * 
         * Códigos de autenticación:
         * - 401 Unauthorized: Falta autenticación o token inválido
         * - 403 Forbidden: Autenticado pero sin permisos (lo maneja Spring Security)
         * 
         * ¿Qué ve el cliente?
         * HTTP/1.1 401 Unauthorized
         * Content-Type: application/json
         * { "status": 401, "message": "..." }
         */
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        /**
         * 5. Escribir JSON en la respuesta
         * 
         * objectMapper.writeValueAsString(errorResponse):
         * - Convierte ErrorResponse a String JSON
         * - Usa configuración de Jackson (fechas, null handling, etc.)
         * 
         * response.getWriter().write(...):
         * - Escribe el JSON en el cuerpo de la respuesta
         * - PrintWriter escribe directamente en el stream de salida
         * - La respuesta se envía al cliente
         * 
         * Resultado final enviado al cliente:
         * HTTP/1.1 401 Unauthorized
         * Content-Type: application/json
         * 
         * {
         *   "timestamp": "2024-01-15T10:30:00",
         *   "status": 401,
         *   "error": "Unauthorized",
         *   "message": "Token de autenticación inválido o no proporcionado. Debe incluir un token válido en el header Authorization: Bearer <token>",
         *   "path": "/api/products"
         * }
         * 
         * IMPORTANTE:
         * - Después de esto, la request se termina
         * - El controlador NUNCA se ejecuta
         * - No hay más filtros que procesen esta request
         */
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
```

### ¿Cómo funciona JwtAuthenticationEntryPoint?

Este componente es el **MANEJADOR DE ERRORES** de autenticación. Se ejecuta cuando Spring Security detecta falta de autenticación válida.

**Flujo completo de un error de autenticación**:
```
Cliente envía: GET /api/products
Header: Authorization: Bearer token_invalido
        ↓
┌─────────────────────────────────────────┐
│ JwtAuthenticationFilter                 │
│ 1. Extrae token                         │
│ 2. validateToken() = false              │
│ 3. NO establece SecurityContext         │
│ 4. Continúa sin autenticación           │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ Spring Security                         │
│ 1. Verifica SecurityContext → VACÍO    │
│ 2. Endpoint requiere autenticación     │
│ 3. Detecta AuthenticationException      │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│ JwtAuthenticationEntryPoint ← AQUÍ     │
│ 1. commence() se ejecuta                │
│ 2. Crea ErrorResponse 401               │
│ 3. Serializa a JSON                     │
│ 4. Escribe en response                  │
└─────────────────────────────────────────┘
        ↓
 Response 401 Unauthorized
{
  "status": 401,
  "message": "Token inválido...",
  "path": "/api/products"
}

¡El controlador NUNCA se ejecuta!
```

**Escenarios de error de autenticación**:

| Escenario | Token en Header | Validación | SecurityContext | commence() ejecutado |
|-----------|-----------------|------------|-----------------|----------------------|
| **Sin token** |  No | N/A | Vacío |  Sí → 401 |
| **Token expirado** |  Sí |  Expirado | Vacío |  Sí → 401 |
| **Token modificado** |  Sí |  Firma inválida | Vacío |  Sí → 401 |
| **Token malformado** |  Sí |  Formato incorrecto | Vacío |  Sí → 401 |
| **Token válido** |  Sí |  Válido | Usuario autenticado |  No |
| **Endpoint público** |  No | N/A | Vacío |  No (público) |

**Diferencia con @RestControllerAdvice**:

```
CADENA DE EJECUCIÓN:

Request
  ↓
┌─────────────────────────────────────────────────────┐
│ FILTROS DE SEGURIDAD                                │
│                                                     │
│ JwtAuthenticationFilter                             │
│ Spring Security Filters                             │
│ ↓                                                   │
│ ¿Autenticación exitosa?                            │
│   NO → AuthenticationEntryPoint ← AQUÍ             │
│         └─> 401 Unauthorized                        │
│         └─> Request se termina                      │
│                                                     │
│   SÍ → Continúa                                     │
└─────────────────────────────────────────────────────┘
  ↓
┌─────────────────────────────────────────────────────┐
│ CONTROLADOR                                         │
│                                                     │
│ @GetMapping("/products")                            │
│ public List<Product> getProducts() {                │
│   ↓                                                 │
│   Si lanza excepción (ej: ProductNotFoundException)│
│   ↓                                                 │
│   @RestControllerAdvice ← AQUÍ                      │
│   └─> 404 Not Found                                 │
│ }                                                   │
└─────────────────────────────────────────────────────┘
  ↓
Response
```

**Tabla comparativa**:

| Característica | AuthenticationEntryPoint | @RestControllerAdvice |
|----------------|-------------------------|----------------------|
| **Cuándo se ejecuta** | ANTES del controlador | DENTRO del controlador |
| **Tipo de error** | Autenticación (401) | Lógica de negocio (400, 404, 500) |
| **Ubicación en flujo** | Cadena de filtros | Después de DispatcherServlet |
| **Acceso a SecurityContext** | Puede estar vacío | Ya autenticado (si llega aquí) |
| **Formato de respuesta** | Manual (ObjectMapper) | Automático (@RestController) |
| **Ejemplo de uso** | Token inválido, sin token | ProductNotFoundException |

**¿Por qué usar ErrorResponse?**

```java
// ANTES (inconsistente):
// JwtAuthenticationEntryPoint retorna:
{
  "error": "Unauthorized",
  "message": "Token inválido"
}

// GlobalExceptionHandler retorna:
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Producto no encontrado",
  "path": "/api/products/999"
}

//  Formatos diferentes → Cliente debe manejar 2 estructuras


// DESPUÉS (consistente):
// TODOS los errores retornan ErrorResponse:
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token inválido...",
  "path": "/api/products"
}

//  Formato único → Cliente maneja una sola estructura
```

**Ejemplo de respuesta real**:

```http
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "Laptop",
  "price": 999.99
}

// SIN TOKEN:
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "timestamp": "2024-01-15T10:30:00.123",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token de autenticación inválido o no proporcionado. Debe incluir un token válido en el header Authorization: Bearer <token>",
  "path": "/api/products"
}
```

**Configuración en SecurityConfig**:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ... otras configuraciones
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtAuthenticationEntryPoint)  // ← AQUÍ se configura
        );
    return http.build();
}
```

**Ventajas de este diseño**:
1. **Consistencia**: Mismo formato que otros errores (ErrorResponse)
2. **Claridad**: Mensaje descriptivo explica cómo solucionar el problema
3. **Logging**: Todos los intentos de acceso no autorizado se registran
4. **Mantenibilidad**: Separación de responsabilidades (filtro valida, entrypoint formatea)
5. **Estándar**: Implementa AuthenticationEntryPoint de Spring Security

**Mejoras opcionales**:

```java
// MEJORA 1: Diferentes mensajes según tipo de error
if (authException instanceof BadCredentialsException) {
    message = "Credenciales inválidas";
} else if (authException instanceof InsufficientAuthenticationException) {
    message = "Token de autenticación requerido";
}

// MEJORA 2: Rate limiting en logs (evitar spam de logs)
// Implementar contador de intentos fallidos por IP

// MEJORA 3: Auditoría avanzada
// Registrar IP, user agent, timestamp en BD para análisis de seguridad
```

### Aspectos clave del JwtAuthenticationEntryPoint*

* **Usa `ErrorResponse` existente**: Mantiene consistencia con el formato de errores de toda la aplicación
* **NO usa `@RestControllerAdvice`**: Este filtro se ejecuta antes de llegar a los controladores
* **ObjectMapper**: Serializa el error a JSON usando Jackson
* **HTTP 401 Unauthorized**: Status code apropiado para errores de autenticación
* **Primera línea de defensa**: Captura errores de autenticación antes que cualquier otro handler

## 7.3. Configuración de Jackson para serialización de fechas

**¿Por qué es necesario?**

El `JwtAuthenticationEntryPoint` usa `ObjectMapper` para convertir `ErrorResponse` a JSON. Como `ErrorResponse` contiene un campo `timestamp` de tipo `LocalDateTime`, necesitamos configurar Jackson para que pueda serializar correctamente las fechas de Java 8+.

**Sin esta configuración obtendrías error**:
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: 
Java 8 date/time type `java.time.LocalDateTime` not supported by default
```

Archivo: `config/JacksonConfig.java`

```java
// imports packages y clases....

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    /**
     * Configura ObjectMapper global para toda la aplicación
     * 
     * @Primary: Marca este bean como el ObjectMapper principal
     * Se usa automáticamente en:
     * - @RestController para serializar respuestas
     * - JwtAuthenticationEntryPoint para serializar errores
     * - Cualquier componente que inyecte ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ============== CONFIGURACIÓN CRÍTICA ==============
        
        // Registrar módulo para manejo de fechas Java 8+
        // Permite serializar: LocalDateTime, LocalDate, LocalTime, Instant, etc.
        mapper.registerModule(new JavaTimeModule());

        // Serializar fechas como ISO-8601 ("2024-01-26T10:30:00")
        // En lugar de timestamp numérico (1706268600000)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ============== CONFIGURACIONES OPCIONALES ==============
        
        // No fallar si un bean está vacío (sin propiedades)
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // Indentar JSON para mejor legibilidad (opcional, desactivar en producción)
        // mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }
}
```

### ¿Qué hace cada configuración?

| Configuración | Propósito | Ejemplo |
|---------------|-----------|----------|
| **JavaTimeModule** | Soporte para tipos Java 8+ | `LocalDateTime` → `"2024-01-26T10:30:00"` |
| **WRITE_DATES_AS_TIMESTAMPS = false** | Formato ISO-8601 legible | `"2024-01-26"` en lugar de `1706268600000` |
| **FAIL_ON_EMPTY_BEANS = false** | Permite serializar beans vacíos | Evita errores con POJOs sin getters |
| **@Primary** | ObjectMapper por defecto | Se usa en toda la aplicación |

### Dependencia requerida

Esta configuración requiere la dependencia ya agregada en `build.gradle.kts`:

```kotlin
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
```

### ¿Dónde se usa este ObjectMapper?

1. **JwtAuthenticationEntryPoint**: Serializa `ErrorResponse` con `timestamp`
2. **@RestController**: Serializa automáticamente todas las respuestas
3. **GlobalExceptionHandler**: Serializa errores con campos de fecha
4. **Cualquier lugar** que inyecte `ObjectMapper`


# 8. Configuración de Spring Security

Archivo: `security/config/SecurityConfig.java`

```java
// imports packages y clases....

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationEntryPoint unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          JwtAuthenticationEntryPoint unauthorizedHandler,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider: Proveedor de autenticación que conecta:
     * - UserDetailsService: Carga información del usuario desde BD
     * - PasswordEncoder: Valida la contraseña hasheada
     * 
     * Spring Security usa este provider para autenticar credenciales.
     * El constructor acepta directamente el UserDetailsService en Spring Boot 3.x/4.x
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF (no necesario para APIs REST con JWT)
            .csrf(AbstractHttpConfigurer::disable)

            // Configurar manejo de excepciones de autenticación
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedHandler)
            )

            // Configurar sesiones como stateless (no usar sesiones HTTP)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configurar autorización de requests
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos (sin autenticación)
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/status/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // Todos los demás endpoints requieren autenticación
                .anyRequest().authenticated()
            );

        // Agregar proveedor de autenticación
        http.authenticationProvider(authenticationProvider());

        // Agregar filtro JWT antes del filtro de autenticación estándar
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

## 8.1 **¿Cómo funciona la configuración de seguridad?**

**SecurityConfig es el CENTRO de control** de toda la seguridad de la aplicación.

### 8.1.1 **Componentes principales**

**1. PasswordEncoder (BCrypt)**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**¿Qué hace BCrypt?**
- **NO almacena contraseñas en texto plano**: Irreversible
- **Hashing con salt**: Misma contraseña → hashes diferentes
- **Trabajo computacional**: Lento intencionalmente para prevenir fuerza bruta

**Ejemplo**:
```java
String password = "Secure123";
String hash = passwordEncoder.encode(password);
// Primera ejecución:  $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
// Segunda ejecución:  $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi
//                     ↑ Diferente cada vez (salt aleatorio)

// Validación:
passwordEncoder.matches("Secure123", hash);  // true
passwordEncoder.matches("Wrong123", hash);   // false
```

**2. DaoAuthenticationProvider**
```java
@Bean
public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}
```

**¿Qué hace?**
Conecta:
- **UserDetailsService**: Carga usuario desde BD
- **PasswordEncoder**: Valida contraseña hasheada

**Flujo de autenticación**:
```
Usuario ingresa credenciales
        ↓
AuthenticationManager
        ↓
DaoAuthenticationProvider
        ↓
┌───────────────────┬───────────────────┐
│                   │                   │
│ UserDetailsService│  PasswordEncoder  │
│ .loadUserByUsername│  .matches()       │
│                   │                   │
│ SELECT * FROM     │  Valida hash      │
│ users WHERE       │  BCrypt           │
│ email = ?         │                   │
│                   │                   │
│ Retorna UserEntity│  true/false       │
└───────────────────┴───────────────────┘
        ↓                   ↓
        └─────────┬─────────┘
                  ↓
     Autenticación exitosa → Genera JWT
     Fallo → 401 Unauthorized
```

**3. SecurityFilterChain (reglas de seguridad)**

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)  // 1. CSRF
        .exceptionHandling(...)                 // 2. Manejo errores
        .sessionManagement(...)                 // 3. Sin sesiones
        .authorizeHttpRequests(...)             // 4. Reglas de acceso
        .authenticationProvider(...)            // 5. Proveedor
        .addFilterBefore(...);                  // 6. Filtro JWT
    
    return http.build();
}
```

**Explicación de cada configuración**:

**a) CSRF deshabilitado**
```java
.csrf(AbstractHttpConfigurer::disable)
```

**¿Qué es CSRF?**
- **Cross-Site Request Forgery**: Ataque donde un sitio malicioso hace peticiones a tu API en nombre del usuario
- **Ejemplo**: Usuario logueado en banco.com → Visita sitio-malicioso.com → Sitio malicioso hace `POST /transferir` a banco.com

**¿Por qué deshabilitarlo?**
- **JWT es stateless**: No usa cookies (principal vector de CSRF)
- **APIs REST**: Tokens en headers (no en cookies)
- **Same-Origin Policy**: Navegadores modernos protegen contra CSRF

**Comparación**:
```
┌──────────────────┬────────────────────┬──────────────────┐
│                  │  Sesiones (Cookies)│  JWT (Headers)   │
├──────────────────┼────────────────────┼──────────────────┤
│ Vulnerable CSRF? │  SÍ (usar tokens)│  NO            │
│ Token en cookie? │  Automático      │  Manual        │
│ Necesita CSRF?   │  SÍ              │  NO            │
└──────────────────┴────────────────────┴──────────────────┘
```

**b) Manejo de excepciones**
```java
.exceptionHandling(exception -> exception
    .authenticationEntryPoint(unauthorizedHandler)
)
```
- Cuando falla autenticación → Llama a `JwtAuthenticationEntryPoint`
- Retorna JSON con error 401

**c) Sesiones stateless**
```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

**¿Qué significa STATELESS?**
```
 Con sesiones (STATEFUL):
Cliente → Login → Servidor crea sesión en memoria → JSESSIONID en cookie
Cliente → Request → Servidor busca sesión en memoria → Valida
(Problema: No escala, memoria del servidor, sincronización entre servidores)

 Sin sesiones (STATELESS):
Cliente → Login → Servidor genera JWT → Cliente guarda token
Cliente → Request con JWT → Servidor valida firma → No busca en BD/memoria
(Ventaja: Escala infinitamente, sin estado en servidor)
```

**d) Reglas de acceso**
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll()      // Público
    .requestMatchers("/status/**").permitAll()    // Público
    .requestMatchers("/actuator/**").permitAll()  // Público
    .anyRequest().authenticated()                 // Requiere login
)
```

**Tabla de protección**:
```
┌──────────────────────┬───────────────┬─────────────────────────┐
│ Endpoint             │ Protección    │ Razón                   │
├──────────────────────┼───────────────┼─────────────────────────┤
│ POST /auth/login     │  Público     │ Necesario para login    │
│ POST /auth/register  │  Público     │ Crear cuenta nueva      │
│ GET /status/health   │  Público     │ Monitoreo externo       │
│ GET /api/products    │  Protegido   │ Requiere autenticación  │
│ POST /api/products   │  Protegido   │ Requiere autenticación  │
│ DELETE /api/products │   Roles     │ Solo propietario/ADMIN  │
└──────────────────────┴───────────────┴─────────────────────────┘
```

**e) Filtro JWT personalizado**
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

**Orden de filtros**:
```
1. JwtAuthenticationFilter  ← AQUÍ validamos JWT
   ↓
2. UsernamePasswordAuthenticationFilter (estándar de Spring)
   ↓
3. Otros filtros de seguridad
   ↓
4. Llega al controlador
```

**¿Por qué `addFilterBefore`?**
- Necesitamos validar JWT **ANTES** del filtro estándar de Spring
- Si JWT es válido → Establecemos autenticación → Siguiente filtro ve usuario autenticado

#### Flujo completo de seguridad

```
Petición: GET /api/products
Header: Authorization: Bearer eyJhbGci...
        ↓
┌─────────────────────────────────────────┐
│  1. JwtAuthenticationFilter             │
│     - Extrae token del header           │
│     - Valida con JwtUtil                │
│     - Carga UserDetails                 │
│     - Establece SecurityContext         │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  2. Spring Security verifica            │
│     .authorizeHttpRequests()            │
│     ¿Endpoint protegido?                │
│     ¿Usuario autenticado?               │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  3. Controlador                         │
│     @PreAuthorize("isAuthenticated()") │
│     Accede a @AuthenticationPrincipal   │
└─────────────────────────────────────────┘
        ↓
 Response 200 OK con datos

 Si falla:
   - Token inválido → JwtAuthenticationEntryPoint
   - Sin permisos → AccessDeniedException → 403
```

### Aspectos clave de SecurityConfig*

* **CSRF deshabilitado**: No necesario para APIs REST stateless
* **SessionCreationPolicy.STATELESS**: No usar sesiones HTTP
* **Endpoints públicos**: `/auth/**`, `/status/**`, `/actuator/**`
* **Resto protegido**: Todos los demás requieren autenticación
* **@EnableMethodSecurity**: Permite usar anotaciones `@PreAuthorize`
* **BCrypt**: Hashing seguro de contraseñas con salt
* **Filtros ordenados**: JWT validation → Spring Security → Controllers

# 9. Servicios de Autenticación

## 9.1. AuthService

Archivo: `security/services/AuthService.java`

```java
// imports packages y clases....
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    // Dependencias inyectadas para login y registro
    private final AuthenticationManager authenticationManager; // Valida credenciales
    private final UserRepository userRepository;               // Acceso a BD
    private final RoleRepository roleRepository;               // Gestión de roles
    private final PasswordEncoder passwordEncoder;             // Hash de passwords
    private final JwtUtil jwtUtil;                            // Generación de tokens

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Login: Valida credenciales y retorna JWT
     */
    @Transactional(readOnly = true) // Solo lectura, no modifica BD
    public AuthResponseDto login(LoginRequestDto loginRequest) {
        
        // 1. Validar email y password con Spring Security
        // authenticationManager usa UserDetailsService internamente
        // Si falla: lanza BadCredentialsException → 401
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
            )
        );

        // 2. Establecer usuario autenticado en contexto de seguridad
        // Permite acceso a usuario actual en servicios
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Generar JWT con datos del usuario
        String jwt = jwtUtil.generateToken(authentication);

        // 4. Extraer información del usuario autenticado
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Convertir authorities a Set<String> para la respuesta
        Set<String> roles = userDetails.getAuthorities().stream()
            .map(item -> item.getAuthority()) // "ROLE_USER", "ROLE_ADMIN"
            .collect(Collectors.toSet());

        // 5. Retornar JWT + datos del usuario
        return new AuthResponseDto(
            jwt,                      // Token para autenticación
            userDetails.getId(),      // ID del usuario
            userDetails.getName(),    // Nombre completo
            userDetails.getEmail(),   // Email
            roles                     // Roles asignados
        );
    }

    /**
     * Registro: Crea nuevo usuario y retorna JWT automáticamente
     */
    @Transactional // Requiere transacción para INSERT
    public AuthResponseDto register(RegisterRequestDto registerRequest) {
        
        // 1. Validar que email no exista
        // Si existe: lanza ConflictException → 409
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new ConflictException("El email ya está registrado");
        }

        // 2. Crear nueva entidad de usuario
        UserEntity user = new UserEntity();
        user.setName(registerRequest.getName());
        user.setEmail(registerRequest.getEmail());
        // Hash del password con BCrypt (nunca almacenar en texto plano)
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // 3. Asignar rol por defecto ROLE_USER
        // Si no existe: lanza BadRequestException → 400
        RoleEntity userRole = roleRepository.findByName(RoleName.ROLE_USER)
            .orElseThrow(() -> new BadRequestException("Rol por defecto no encontrado"));

        Set<RoleEntity> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        // 4. Guardar en BD (INSERT)
        user = userRepository.save(user);

        // 5. Generar JWT automáticamente para login directo
        // No requiere que el usuario haga login después de registrarse
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String jwt = jwtUtil.generateTokenFromUserDetails(userDetails);

        // Convertir roles a nombres de string
        Set<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName().name()) // RoleName.ROLE_USER → "ROLE_USER"
            .collect(Collectors.toSet());

        // 6. Retornar JWT + datos del usuario registrado
        return new AuthResponseDto(
            jwt,
            user.getId(),
            user.getName(),
            user.getEmail(),
            roleNames
        );
    }
}
```

### ¿Cómo funciona AuthService?

Este servicio maneja las operaciones de autenticación y registro de usuarios.

**Flujo de Login**:
```
Cliente → POST /auth/login {email, password}
  ↓
AuthService.login()
  ↓
1. AuthenticationManager valida credenciales
   - Llama a UserDetailsService.loadUserByUsername()
   - Compara password con BCrypt
   - Si falla → BadCredentialsException → 401
  ↓
2. SecurityContext almacena usuario autenticado
  ↓
3. JwtUtil genera token JWT
  ↓
4. Extrae datos del usuario (id, email, roles)
  ↓
5. Retorna AuthResponseDto con JWT
  ↓
Cliente recibe: {token: "eyJhbGci...", id: 1, email: "...", roles: [...]}
```

**Flujo de Registro**:
```
Cliente → POST /auth/register {name, email, password}
  ↓
AuthService.register()
  ↓
1. Valida que email no exista
   - Si existe → ConflictException → 409
  ↓
2. Crea UserEntity
   - Hash password con BCrypt
  ↓
3. Asigna ROLE_USER por defecto
  ↓
4. Guarda en BD (INSERT)
  ↓
5. Genera JWT automáticamente
   - Usuario queda logueado sin hacer login
  ↓
6. Retorna AuthResponseDto con JWT
  ↓
Cliente recibe: {token: "eyJhbGci...", id: 2, email: "...", roles: ["ROLE_USER"]}
```

**Diferencias clave entre Login y Registro**:

| Aspecto | Login | Registro |
|---------|-------|----------|
| **Validación** | Credenciales con AuthenticationManager | Email no duplicado |
| **Transacción** | readOnly = true (solo lectura) | Modificación (INSERT) |
| **Password** | Valida con BCrypt | Hashea con BCrypt |
| **Roles** | Lee desde BD | Asigna ROLE_USER por defecto |
| **SecurityContext** | Establece manualmente | No necesario |
| **JWT** | Genera desde Authentication | Genera desde UserDetails |

**Manejo de errores**:

```java
// Login con credenciales inválidas:
POST /auth/login
{
  "email": "user@example.com",
  "password": "wrong_password"
}
→ authenticationManager.authenticate() lanza BadCredentialsException
→ GlobalExceptionHandler captura y retorna 401 Unauthorized

// Registro con email duplicado:
POST /auth/register
{
  "email": "existing@example.com",
  "name": "Test User",
  "password": "password123"
}
→ existsByEmail() retorna true
→ Lanza ConflictException
→ GlobalExceptionHandler captura y retorna 409 Conflict

// Registro sin rol ROLE_USER en BD:
→ findByName(RoleName.ROLE_USER) retorna Optional.empty()
→ Lanza BadRequestException
→ GlobalExceptionHandler captura y retorna 400 Bad Request
```

**Ventajas de este diseño**:

1. **Registro con auto-login**: Usuario queda autenticado automáticamente al registrarse
2. **Seguridad de passwords**: BCrypt con salt automático
3. **Transacciones apropiadas**: readOnly para login, modificación para registro
4. **Validaciones centralizadas**: Duplicados, roles faltantes, etc.
5. **Respuesta consistente**: Mismo DTO para login y registro
6. **Manejo de errores**: Excepciones específicas capturadas por GlobalExceptionHandler



# 10. Controlador de Autenticación

Archivo: `security/controllers/AuthController.java`

```java
// imports packages y clases....

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth") // Prefijo para todos los endpoints de autenticación
public class AuthController {

    private final AuthService authService; // Servicio de lógica de autenticación

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login - Endpoint público (configurado en SecurityConfig)
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        // @Valid valida anotaciones en LoginRequestDto (email, password requeridos)
        AuthResponseDto response = authService.login(loginRequest);
        return ResponseEntity.ok(response); // 200 OK con JWT
    }

    /**
     * Registro - Endpoint público (configurado en SecurityConfig)
     * POST /auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto registerRequest) {
        // @Valid valida anotaciones en RegisterRequestDto
        AuthResponseDto response = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); // 201 Created con JWT
    }
}
```

### ¿Cómo funciona AuthController?

Este controlador expone los endpoints REST para autenticación. Es simple porque toda la lógica está en AuthService.

**Características**:

- **Endpoints públicos**: No requieren JWT (configurado en SecurityConfig)
- **Validación automática**: @Valid valida DTOs antes de ejecutar el método
- **Códigos HTTP apropiados**: 200 OK para login, 201 Created para registro
- **Delega lógica**: Toda la lógica está en AuthService (separación de responsabilidades)

**Ejemplos de uso**:

```http
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

Respuesta 200 OK:
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "id": 1,
  "name": "Usuario Test",
  "email": "user@example.com",
  "roles": ["ROLE_USER"]
}
```

```http
POST http://localhost:8080/auth/register
Content-Type: application/json

{
  "name": "Nuevo Usuario",
  "email": "nuevo@example.com",
  "password": "securePassword123"
}

Respuesta 201 Created:
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "id": 2,
  "name": "Nuevo Usuario",
  "email": "nuevo@example.com",
  "roles": ["ROLE_USER"]
}
```

**Manejo de errores**:

Los errores son manejados automáticamente por GlobalExceptionHandler:

```http
POST /auth/login con password incorrecto:
→ AuthService lanza BadCredentialsException
→ 401 Unauthorized

POST /auth/register con email duplicado:
→ AuthService lanza ConflictException
→ 409 Conflict

POST /auth/login sin @Valid (email vacío):
→ MethodArgumentNotValidException
→ 400 Bad Request con detalles de validación
```

**Configuración de acceso público**:

En SecurityConfig estos endpoints están configurados como públicos:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll() // /auth/login y /auth/register son públicos
    .anyRequest().authenticated()             // Resto requiere autenticación
)
```



# 11. Repositorios de Seguridad

## 11.1. RoleRepository

Archivo: `security/repository/RoleRepository.java`

```java

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    // Buscar rol por nombre (ROLE_USER, ROLE_ADMIN, etc.)
    Optional<RoleEntity> findByName(RoleName name);
    
    // Verificar si existe un rol específico
    boolean existsByName(RoleName name);
}
```

### ¿Cómo funciona RoleRepository?

Repositorio para gestión de roles del sistema.

**Métodos**:

- **findByName()**: Busca un rol por su enum (ROLE_USER, ROLE_ADMIN)
  - Retorna Optional para manejo seguro de ausencia
  - Usado en registro para asignar ROLE_USER por defecto

- **existsByName()**: Verifica si un rol existe sin cargarlo
  - Más eficiente que findByName() cuando solo necesitas verificar existencia
  - Útil para validaciones o inicialización de datos

**Uso en AuthService**:la 

```java
// Registro de usuario - asignar rol por defecto
RoleEntity userRole = roleRepository.findByName(RoleName.ROLE_USER)
    .orElseThrow(() -> new BadRequestException("Rol por defecto no encontrado"));
```

## 11.2. Actualizar UserRepository

Archivo: `users/repository/UserRepository.java`

```java
package ec.edu.ups.icc.fundamentos01.users.repository;

import ec.edu.ups.icc.fundamentos01.users.models.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // ============== MÉTODOS EXISTENTES ==============
    
    Optional<UserEntity> findById(Long id);
    
    // ============== NUEVOS MÉTODOS PARA SEGURIDAD ==============
    
    // Buscar usuario por email (usado en login)
    Optional<UserEntity> findByEmailAndDeletedFalse(String email);
    
    // Verificar si email ya está registrado (usado en registro)
    boolean existsByEmail(String email);
}
```

Para login se usará:

```java
findByEmailAndDeletedFalse
```

así un usuario eliminado lógicamente no podrá autenticarse.


### ¿Cómo funciona UserRepository actualizado?

Se agregaron dos métodos para soportar autenticación.

**Métodos nuevos**:

- **findByEmail()**: Busca usuario por email
  - Usado en UserDetailsService.loadUserByUsername()
  - Retorna Optional para manejo seguro
  - Spring Data genera query: `SELECT * FROM users WHERE email = ?`

- **existsByEmail()**: Verifica si email existe
  - Usado en registro para evitar duplicados
  - Más eficiente que findByEmail() cuando solo necesitas verificar
  - Spring Data genera query: `SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)`

**Uso en seguridad**:

```java
// En UserDetailsServiceImpl (login)
UserEntity user = userRepository.findByEmail(email)
    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

// En AuthService (registro)
if (userRepository.existsByEmail(registerRequest.getEmail())) {
    throw new ConflictException("El email ya está registrado");
}
```

**Comparación de queries generadas**:

| Método | Query SQL | Cuándo usar |
|--------|-----------|-------------|
| findByEmail() | `SELECT * FROM users WHERE email = ?` | Cuando necesitas cargar el usuario completo |
| existsByEmail() | `SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)` | Cuando solo necesitas verificar existencia (más eficiente) |

---



## 11.3. Inicialización de roles

Antes de registrar usuarios, deben existir los roles base.

Archivo:

```txt
security/config/SecurityDataInitializer.java
```

Código:

```java


/*
 * Inicializa roles base del sistema.
 *
 * Se ejecuta al iniciar la aplicación.
 */
@Component
public class SecurityDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public SecurityDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {

        createRoleIfNotExists(
                RoleName.ROLE_USER,
                "Usuario estándar del sistema"
        );

        createRoleIfNotExists(
                RoleName.ROLE_ADMIN,
                "Administrador del sistema"
        );
    }

    private void createRoleIfNotExists(RoleName roleName, String description) {

        if (!roleRepository.existsByName(roleName)) {
            RoleEntity role = new RoleEntity(roleName, description);
            roleRepository.save(role);
        }
    }
}
```

Sin esta inicialización, el registro fallará porque `AuthService` no encontrará `ROLE_USER`.

---

![alt text](assets/11-autrepsonse.png)



# 12. Actividad práctica

Se debe implementar autenticación JWT en el proyecto.

- Crear estructura de seguridad
- Crear roles
- Actualizar UserEntity
- Crear DTOs de autenticación
- Implementar JWT
- Configurar Spring Security
- Crear AuthService y AuthController

## Entregables

## Captura de registro exitoso

Endpoint:

```txt
POST /api/auth/register
```

Debe evidenciar:

```txt
201 Created
token generado
ROLE_USER asignado
```

---

## Captura de login exitoso

Endpoint:

```txt
POST /api/auth/login
```

Debe evidenciar:

```txt
200 OK
token generado
roles devueltos
```

---

## Captura de endpoint protegido sin token

Ejemplo:

```txt
GET /api/products/page?page=0&size=5
```

Debe evidenciar:

```txt
401 Unauthorized
```

---

## Captura de endpoint protegido con token

Ejemplo:

```txt
GET /api/products/page?page=0&size=5
```

Debe incluir:

```http
Authorization: Bearer <token>
```

y evidenciar:

```txt
200 OK
```

---



# Próximos Pasos

Se ha completado la Práctica 11 sobre **Autenticación con JWT**. 

- Configurar Spring Security con JWT
- Crear filtros personalizados de autenticación
- Implementar login y registro con tokens
- Gestionar roles y permisos de usuarios
- Proteger la API con autenticación stateless

**Continúa con las siguientes prácticas**:

## Práctica 12: Roles y @PreAuthorize

Se realizara:
- Proteger endpoints específicos con roles
- Usar @PreAuthorize para control de acceso
- Inyectar usuario actual con @AuthenticationPrincipal
- Diferenciar entre endpoints públicos y protegidos

📄 Ver [12_roles_preauthorize.md](12_roles_preauthorize.md)

## Práctica 13: Validación de Ownership

Se realizara:
- Validar propiedad de recursos (ownership)
- Implementar validateOwnership() en servicios
- Permitir bypass de ADMIN
- Manejar AccessDeniedException correctamente

📄 Ver [13_ownership_validacion.md](13_ownership_validacion.md)

---





