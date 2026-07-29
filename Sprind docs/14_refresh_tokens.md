s# Programación y Plataformas Web

# Spring Boot – Refresh Token con JWT

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="95">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="95">
</div>

---

# Práctica 16 (Spring Boot): Renovación de Access Token con Refresh Token

### Autores

**Pablo Torres**

[ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# 1. Introducción

En las prácticas anteriores se implementó seguridad usando:

* Spring Security
* JWT
* `JwtAuthenticationFilter`
* `JwtAuthenticationEntryPoint`
* `UserDetailsImpl`
* `UserDetailsServiceImpl`
* `AuthService`
* `AuthController`
* roles con `ROLE_USER` y `ROLE_ADMIN`
* autorización por roles con `@PreAuthorize`
* validación de ownership para productos

Hasta este punto, el sistema ya puede:

```txt
registrar usuarios
iniciar sesión
generar access token
proteger endpoints con JWT
validar roles
validar ownership
```

Sin embargo, todavía existe una limitación importante.

El access token tiene un tiempo de vida corto:

```yaml
jwt:
  expiration: 1800000
```

Esto equivale aproximadamente a:

```txt
30 minutos
```

Cuando el access token expira, el usuario ya no puede consumir endpoints protegidos y tendría que iniciar sesión nuevamente.

En aplicaciones reales, esto no es cómodo para el usuario.

Para resolver este problema se usa un segundo token:

```txt
refresh token
```

---

# 2. Problema actual

Actualmente, cuando el usuario inicia sesión, la API devuelve un token JWT.

Ejemplo:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "userId": 1,
  "name": "Usuario A",
  "email": "usera@ups.edu.ec",
  "roles": [
    "ROLE_USER"
  ]
}
```

Ese token se usa en cada petición:

```http
Authorization: Bearer <token>
```

El problema es que ese token expira.

Cuando expira, ocurre esto:

```txt
GET /api/products/page?page=0&size=5
Authorization: Bearer <access-token-expirado>
```

Resultado:

```txt
401 Unauthorized
```

Esto es correcto desde el punto de vista de seguridad, pero obliga al usuario a volver a iniciar sesión.

La solución es implementar un flujo de renovación.

---

# 3. Objetivo de la práctica

El objetivo de esta práctica es implementar un mecanismo de refresh token.

Al finalizar, la API podrá:

```txt
generar access token
generar refresh token
guardar refresh token en base de datos
renovar access token usando refresh token
rotar refresh token
revocar refresh token al cerrar sesión
evitar que un refresh token sea usado como access token
```

---

# 4. Access Token vs Refresh Token

## 4.1. Access Token

El access token se usa para consumir endpoints protegidos.

Ejemplo:

```http
Authorization: Bearer <access-token>
```

Características:

| Aspecto | Access Token |
| ------- | ------------ |
| Uso | Acceder a endpoints protegidos |
| Duración | Corta |
| Viaja en header | Sí |
| Se usa en cada request | Sí |
| Ejemplo | `GET /api/products/page` |

---

## 4.2. Refresh Token

El refresh token se usa para renovar el access token.

No debe usarse para consumir endpoints protegidos.

Ejemplo:

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<refresh-token>"
}
```

Características:

| Aspecto | Refresh Token |
| ------- | ------------- |
| Uso | Renovar tokens |
| Duración | Más larga |
| Viaja en body | Sí |
| Se usa en cada request | No |
| Ejemplo | `POST /api/auth/refresh` |

---

# 5. Flujo general

```txt
Cliente
  ↓
POST /api/auth/login
  ↓
Servidor valida credenciales
  ↓
Servidor genera:
  - access token
  - refresh token
  ↓
Cliente guarda ambos tokens
  ↓
Cliente consume endpoints con access token
  ↓
Access token expira
  ↓
Cliente envía refresh token a /api/auth/refresh
  ↓
Servidor valida refresh token
  ↓
Servidor revoca refresh token anterior
  ↓
Servidor genera nuevos tokens
  ↓
Cliente continúa usando la aplicación
```

---

# 6. Decisión de diseño de esta práctica

En esta práctica se usará una estrategia segura y didáctica:

```txt
Access token: JWT de corta duración
Refresh token: JWT de larga duración guardado en base de datos
```

Además, se aplicará rotación:

```txt
Cada vez que se usa un refresh token,
ese refresh token se revoca
y se genera uno nuevo.
```

Esto evita que el mismo refresh token se reutilice indefinidamente.

---

# 7. Configuración en application.yml

En prácticas anteriores ya se agregó la configuración:

Archivo:

```txt
src/main/resources/application.yml
```

Código:

```yaml
jwt:
    secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}

    # Access token: 30 minutos
    expiration: 1800000

    # Refresh token: 7 días
    refresh-expiration: 604800000

    issuer: fundamentos01-api
    header: Authorization
    prefix: "Bearer "
```

En esta práctica se usará realmente:

```yaml
refresh-expiration: 604800000
```

Antes existía como configuración, pero no se estaba usando en el flujo de autenticación.

---

# 8. Actualización de estructura de paquetes

Se mantendrá la estructura de seguridad existente:

```txt
security/
├── config
├── controllers
├── dtos
├── entities
├── enums
├── filters
├── repositories
├── services
└── utils
```

Se agregarán los siguientes archivos:

```txt
security/
├── dtos
│   └── RefreshTokenRequestDto.java
├── entities
│   └── RefreshTokenEntity.java
├── repositories
│   └── RefreshTokenRepository.java
└── services
    └── RefreshTokenService.java
```

---

# 9. Entidad RefreshTokenEntity

Archivo:

```txt
security/entities/RefreshTokenEntity.java
```

Código:

```java
/*
 * Entidad JPA que representa un refresh token emitido por el sistema.
 *
 * El refresh token se guarda en base de datos para poder:
 * - validar que existe
 * - verificar si está revocado
 * - verificar si expiró
 * - invalidarlo durante logout
 * - rotarlo durante /auth/refresh
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity extends BaseEntity {

    /*
     * Usuario dueño del refresh token.
     *
     * Un usuario puede tener uno o varios refresh tokens,
     * dependiendo de la estrategia de sesión.
     *
     * En esta práctica se manejará una sesión activa por usuario,
     * revocando tokens anteriores al hacer login.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /*
     * Valor del refresh token.
     *
     * En esta práctica se guarda el token completo para facilitar
     * el aprendizaje.
     *
     * En producción se recomienda guardar un hash del token,
     * no el token en texto plano.
     */
    @Column(nullable = false, unique = true, length = 1000)
    private String token;

    /*
     * Fecha y hora de expiración del refresh token.
     *
     * Aunque el JWT ya contiene expiración interna,
     * se guarda también en base de datos para facilitar consultas
     * y control del ciclo de vida del token.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /*
     * Indica si el refresh token fue revocado.
     *
     * Un refresh token revocado ya no puede usarse para renovar sesión.
     */
    @Column(nullable = false)
    private boolean revoked = false;

    public RefreshTokenEntity() {
    }

    public RefreshTokenEntity(
            UserEntity user,
            String token,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    /*
     * Verifica si el refresh token ya expiró según la fecha guardada
     * en la base de datos.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    // Getters y setters
}
```

Imports principales:

```java
import ec.edu.ups.icc.fundamentos01.core.entities.BaseEntity;
import ec.edu.ups.icc.fundamentos01.users.entities.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
```

---

# 10. RefreshTokenRepository

Archivo:

```txt
security/repositories/RefreshTokenRepository.java
```

Código:

```java
/*
 * Repositorio encargado de gestionar refresh tokens.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /*
     * Busca un refresh token activo por su valor.
     *
     * Se usa durante /auth/refresh y /auth/logout.
     */
    Optional<RefreshTokenEntity> findByTokenAndRevokedFalse(String token);

    /*
     * Busca todos los refresh tokens activos de un usuario.
     *
     * Se usa para revocar tokens anteriores cuando el usuario inicia sesión.
     */
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(Long userId);
}
```

Imports:

```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
```

---

# 11. DTO RefreshTokenRequestDto

Archivo:

```txt
security/dtos/RefreshTokenRequestDto.java
```

Código:

```java
/*
 * DTO usado para recibir un refresh token desde el cliente.
 *
 * Se usa en:
 * POST /api/auth/refresh
 * POST /api/auth/logout
 */
public class RefreshTokenRequestDto {

    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;

    public RefreshTokenRequestDto() {
    }

    public RefreshTokenRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // Getters y setters
}
```

Import:

```java
import jakarta.validation.constraints.NotBlank;
```

---

# 12. Actualización de AuthResponseDto

Actualmente `AuthResponseDto` devuelve un solo token.

Ahora debe devolver:

```txt
token
refreshToken
type
userId
name
email
roles
```

Archivo:

```txt
security/dtos/AuthResponseDto.java
```

Código actualizado:

```java
/*
 * DTO de respuesta para login, register y refresh.
 *
 * token:
 * - representa el access token
 * - se usa en Authorization: Bearer <token>
 *
 * refreshToken:
 * - se usa solo en /auth/refresh
 * - no debe usarse para consumir endpoints protegidos
 */
public class AuthResponseDto {

    // Otros campos existentes 

    private String refreshToken;

    // Incluir en l constructor 
    // Getters y setters para el nuevo campo 
}
```


---

# 13. Actualización de JwtUtil

El punto más importante de esta práctica es diferenciar entre:

```txt
access token
refresh token
```

Si no se diferencian, un refresh token podría ser enviado en el header:

```http
Authorization: Bearer <refresh-token>
```

y el backend podría aceptarlo como si fuera access token.

Eso sería un error grave.

Por eso, cada token tendrá un claim llamado:

```txt
type
```

Con estos valores:

```txt
access
refresh
```

---

## 13.1. JwtUtil actualizado

Archivo:

```txt
security/utils/JwtUtil.java
```

Código:

```java
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

// adicionar los tppo 
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

// resto de la calses
```


Remplazarrr los metodos de generación de token por los siguientes, que centralizan la creación de tokens y diferencian entre access y refresh: 


```java


    /*
     * Genera un access token desde Authentication.
     *
     * Este método se usa en login.
     */
    public String generateAccessToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return buildToken(
                userPrincipal,
                jwtProperties.getExpiration(),
                ACCESS_TOKEN_TYPE
        );
    }

    /*
     * Genera un access token desde UserDetailsImpl.
     *
     * Este método se usa en register y refresh.
     */
    public String generateAccessTokenFromUserDetails(UserDetailsImpl userDetails) {
        return buildToken(
                userDetails,
                jwtProperties.getExpiration(),
                ACCESS_TOKEN_TYPE
        );
    }

    /*
     * Genera un refresh token.
     *
     * Este token dura más tiempo y solo debe usarse en:
     * POST /api/auth/refresh
     */
    public String generateRefreshToken(UserDetailsImpl userDetails) {
        return buildToken(
                userDetails,
                jwtProperties.getRefreshExpiration(),
                REFRESH_TOKEN_TYPE
        );
    }

    /*
     * Método centralizado para construir tokens JWT.
     *
     * tokenType puede ser:
     * - access
     * - refresh
     */
    private String buildToken(
            UserDetailsImpl userDetails,
            Long expirationMs,
            String tokenType
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String roles = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(String.valueOf(userDetails.getId()))
                .claim("email", userDetails.getEmail())
                .claim("name", userDetails.getName())
                .claim("roles", roles)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /*
     * Mantiene compatibilidad con el nombre anterior.
     *
     * Si en tu AuthService todavía se llama generateToken(),
     * este método seguirá funcionando como access token.
     */
    public String generateToken(Authentication authentication) {
        return generateAccessToken(authentication);
    }

    /*
     * Mantiene compatibilidad con el nombre anterior.
     */
    public String generateTokenFromUserDetails(UserDetailsImpl userDetails) {
        return generateAccessTokenFromUserDetails(userDetails);
    }

    /*
     * Extrae el email desde cualquier token válido.
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("email", String.class);
    }

    /*
     * Extrae el id del usuario desde el subject.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /*
     * Extrae el tipo del token.
     *
     * Valores esperados:
     * - access
     * - refresh
     */
    public String getTokenType(String token) {
        Claims claims = getClaims(token);
        return claims.get(TOKEN_TYPE_CLAIM, String.class);
    }

    /*
     * Valida firma, formato y expiración del token.
     *
     * No valida si es access o refresh.
     * Solo valida que el JWT sea técnicamente correcto.
     */
    public boolean validateToken(String authToken) {
        try {
            getClaims(authToken);
            return true;

        } catch (SignatureException ex) {
            logger.error("Firma JWT inválida: {}", ex.getMessage());

        } catch (MalformedJwtException ex) {
            logger.error("Token JWT malformado: {}", ex.getMessage());

        } catch (ExpiredJwtException ex) {
            logger.error("Token JWT expirado: {}", ex.getMessage());

        } catch (UnsupportedJwtException ex) {
            logger.error("Token JWT no soportado: {}", ex.getMessage());

        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string está vacío: {}", ex.getMessage());
        }

        return false;
    }

    /*
     * Valida que el token sea un access token.
     *
     * Este método debe usarse en JwtAuthenticationFilter.
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token) &&
                ACCESS_TOKEN_TYPE.equals(getTokenType(token));
    }

    /*
     * Valida que el token sea un refresh token.
     *
     * Este método debe usarse en /auth/refresh.
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token) &&
                REFRESH_TOKEN_TYPE.equals(getTokenType(token));
    }

    /*
     * Obtiene los claims del token.
     *
     * Si el token es inválido, este método lanza excepción.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

---

# 14. Actualización de JwtAuthenticationFilter

El filtro de autenticación no debe aceptar cualquier token válido.

Debe aceptar únicamente:

```txt
access token
```

Si un cliente intenta usar un refresh token en:

```http
Authorization: Bearer <refresh-token>
```

el backend debe rechazarlo.

---

## 14.1. Cambio en JwtAuthenticationFilter

Archivo:

```txt
security/filters/JwtAuthenticationFilter.java
```

Buscar esta condición:

```java
if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
```

Reemplazar por:

```java
if (StringUtils.hasText(jwt) && jwtUtil.validateAccessToken(jwt)) {
```


Después de este cambio, los tokens anteriores generados antes de la práctica 16 pueden dejar de funcionar porque no tienen el claim:

```txt
type
```

Solución:

```txt
volver a iniciar sesión
```

---

# 15. RefreshTokenService

Archivo:

```txt
security/services/RefreshTokenService.java
```

Código:

```java
/*
 * Servicio encargado de crear, validar, rotar y revocar refresh tokens.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtUtil jwtUtil;

    private final JwtProperties jwtProperties;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtUtil jwtUtil,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
    }

    /*
     * Crea un refresh token para un usuario.
     *
     * El token se firma como JWT y también se guarda en base de datos.
     */
    @Transactional
    public RefreshTokenEntity createRefreshToken(
            UserEntity user,
            UserDetailsImpl userDetails
    ) {
        String token = jwtUtil.generateRefreshToken(userDetails);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshExpiration()));

        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                user,
                token,
                expiresAt
        );

        return refreshTokenRepository.save(refreshToken);
    }

    /*
     * Valida un refresh token recibido desde el cliente.
     *
     * Validaciones:
     * 1. El JWT debe tener firma válida.
     * 2. El JWT debe ser de tipo refresh.
     * 3. El token debe existir en base de datos.
     * 4. El token no debe estar revocado.
     * 5. El token no debe estar expirado.
     * 6. El usuario dueño del token debe seguir activo.
     */
    @Transactional
    public RefreshTokenEntity validateAndGetActiveToken(String token) {

        if (!jwtUtil.validateRefreshToken(token)) {
            throw new BadRequestException("Refresh token inválido");
        }

        RefreshTokenEntity refreshToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new BadRequestException("Refresh token no encontrado o revocado"));

        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);

            throw new BadRequestException("Refresh token expirado");
        }

        if (refreshToken.getUser() == null || refreshToken.getUser().isDeleted()) {
            throw new BadRequestException("Usuario no válido para este refresh token");
        }

        return refreshToken;
    }

    /*
     * Revoca un refresh token específico.
     *
     * Se usa en:
     * - refresh, para rotar tokens
     * - logout, para cerrar sesión
     */
    @Transactional
    public void revoke(RefreshTokenEntity refreshToken) {
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    /*
     * Revoca todos los refresh tokens activos de un usuario.
     *
     * En esta práctica se usa durante login para dejar
     * una sola sesión activa por usuario.
     *
     * Si se quisiera permitir varias sesiones o varios dispositivos,
     * se podría no llamar a este método durante login.
     */
    @Transactional
    public void revokeAllByUser(UserEntity user) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository
                .findByUserIdAndRevokedFalse(user.getId());

        tokens.forEach(token -> token.setRevoked(true));

        refreshTokenRepository.saveAll(tokens);
    }
}
```

Imports principales:

```java
import ec.edu.ups.icc.fundamentos01.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.fundamentos01.security.config.JwtProperties;
import ec.edu.ups.icc.fundamentos01.security.entities.RefreshTokenEntity;
import ec.edu.ups.icc.fundamentos01.security.repositories.RefreshTokenRepository;
import ec.edu.ups.icc.fundamentos01.security.utils.JwtUtil;
import ec.edu.ups.icc.fundamentos01.users.entities.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

```

---



# 16. Actualización de UserDetailsServiceImpl

Es recomendable que el login no permita autenticar usuarios eliminados lógicamente.

Archivo:

```txt
security/services/UserDetailsServiceImpl.java
```

Código actualizado:

```java
        UserEntity user = userRepository.findByEmailAndDeletedFalse(email);
        
```

---

# 17. Actualización de AuthService

Archivo:

```txt
security/services/AuthService.java
```

Se actualizará para:

```txt
generar access token
generar refresh token
guardar refresh token
renovar tokens
cerrar sesión
```

Código:

```java
@Service
public class AuthService {

    //addicionar la dependencia

    private final RefreshTokenService refreshTokenService;

    public AuthService(
        // ....
            RefreshTokenService refreshTokenService
    ) {
        // ....
        this.refreshTokenService = refreshTokenService;
    }

    /*
     * Login:
     *
     * 1. Valida credenciales.
     * 2. Genera access token.
     * 3. Revoca refresh tokens anteriores.
     * 4. Genera refresh token nuevo.
     * 5. Devuelve ambos tokens al cliente.
     */
    @Transactional
    public AuthResponseDto login(LoginRequestDto loginRequest) {

            // Conservar metodos de 
            // 1. Validar email y password con Spring Security
            // 2. Establecer usuario autenticado en contexto de seguridad

        // actualizar 3. Generar JWT con datos del usuario
        // Cambiart la generacion del token nornal por la generacion del access token
           String accessToken = jwtUtil.generateAccessToken(authentication);

 
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();


        UserEntity user = findActiveUserById(userDetails.getId()); // se crea mas abajo

     
        /*
         * En esta práctica se deja una sola sesión activa por usuario.
         * Por eso se revocan refresh tokens anteriores.
         */
        refreshTokenService.revokeAllByUser(user);

        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(
                user,
                userDetails
        );

        return buildAuthResponse( // se crea mas abajo
                accessToken,
                refreshToken.getToken(),
                user
        );
    }

    /*
     * Registro:
     *
     * 1. Crea el usuario.
     * 2. Asigna ROLE_USER.
     * 3. Genera access token.
     * 4. Genera refresh token.
     */
    @Transactional
    public AuthResponseDto register(RegisterRequestDto registerRequest) {

        // Persistencia del usuario permace igual


    // se actuliza paso 5 la generacion del token 
        String accessToken = jwtUtil.generateAccessTokenFromUserDetails(userDetails);

        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(
                user,
                userDetails
        );

  // 6. Retornar JWT (acces y refesh) + datos del usuario registrado
        return buildAuthResponse(
                accessToken,
                refreshToken.getToken(),
                savedUser
        );
    }

    /*
     * Refresh:
     *
     * 1. Valida el refresh token recibido.
     * 2. Revoca el refresh token usado.
     * 3. Genera nuevo access token.
     * 4. Genera nuevo refresh token.
     *
     * Esto se llama rotación de refresh token.
     */
    @Transactional
    public AuthResponseDto refresh(RefreshTokenRequestDto request) {

        RefreshTokenEntity currentRefreshToken =
                refreshTokenService.validateAndGetActiveToken(request.getRefreshToken());

        UserEntity user = currentRefreshToken.getUser();

        refreshTokenService.revoke(currentRefreshToken);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);

        String newAccessToken = jwtUtil.generateAccessTokenFromUserDetails(userDetails);

        RefreshTokenEntity newRefreshToken = refreshTokenService.createRefreshToken(
                user,
                userDetails
        );

        return buildAuthResponse(
                newAccessToken,
                newRefreshToken.getToken(),
                user
        );
    }

    /*
     * Logout:
     *
     * Revoca el refresh token enviado.
     *
     * Después de esto, ese refresh token ya no podrá usarse
     * para renovar sesión.
     */
    @Transactional
    public void logout(RefreshTokenRequestDto request) {

        RefreshTokenEntity refreshToken =
                refreshTokenService.validateAndGetActiveToken(request.getRefreshToken());

        refreshTokenService.revoke(refreshToken);
    }

    /*
     * Busca un usuario activo por id.
     */
    private UserEntity findActiveUserById(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("Usuario no válido"));
    }

    /*
     * Construye la respuesta de autenticación.
     */
    private AuthResponseDto buildAuthResponse(
            String accessToken,
            String refreshToken,
            UserEntity user
    ) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        return new AuthResponseDto(
                accessToken,
                refreshToken,
                user.getId(),
                user.getName(),
                user.getEmail(),
                roles
        );
    }
}
```


---

# 19. Actualización de AuthController

Archivo:

```txt
security/controllers/AuthController.java
```

Se agregarán dos endpoints:

```txt
POST /api/auth/refresh
POST /api/auth/logout
```

Código actualizado:

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

  
    /*
     * Refresh.
     *
     * Recibe un refresh token válido y devuelve nuevos tokens.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        AuthResponseDto response = authService.refresh(request);

        return ResponseEntity.ok(response);
    }

    /*
     * Logout.
     *
     * Revoca el refresh token recibido.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        authService.logout(request);
    }
}


---

# 20. SecurityConfig

Si tu `SecurityConfig` ya tiene:

```java
.requestMatchers("/auth/**").permitAll()
```

no se requiere ningún cambio adicional.

Esto permite acceder públicamente a:

```txt
POST /api/auth/login
POST /api/auth/register
POST /api/auth/refresh
POST /api/auth/logout
```

¿Por qué `/auth/refresh` es público?

Porque no se valida con access token.

Se valida con refresh token.

La validación ocurre en:

```java
refreshTokenService.validateAndGetActiveToken(...)
```

Configuración esperada:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**").permitAll()
        .requestMatchers("/status/**").permitAll()
        .requestMatchers("/actuator/**").permitAll()
        .anyRequest().authenticated()
)
```

---

# 21. Tabla esperada en base de datos

Con `ddl-auto: update`, Hibernate puede crear la tabla:

```txt
refresh_tokens
```

Estructura conceptual:

```sql
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(1000) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted BOOLEAN,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);
```

---

# 22. Consultas SQL de apoyo

## 22.1. Ver refresh tokens

```sql
SELECT 
    rt.id,
    rt.user_id,
    u.email,
    rt.revoked,
    rt.expires_at,
    rt.created_at
FROM refresh_tokens rt
INNER JOIN users u ON u.id = rt.user_id
ORDER BY rt.id DESC;
```

---

## 22.2. Ver refresh tokens activos

```sql
SELECT 
    rt.id,
    rt.user_id,
    u.email,
    rt.revoked,
    rt.expires_at
FROM refresh_tokens rt
INNER JOIN users u ON u.id = rt.user_id
WHERE rt.revoked = false
ORDER BY rt.id DESC;
```

---

## 22.3. Revocar manualmente refresh tokens de un usuario

```sql
UPDATE refresh_tokens
SET revoked = true
WHERE user_id = 1;
```

---

# 23. Pruebas sugeridas en Bruno o Postman

## 23.1. Login

Request:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "usera@ups.edu.ec",
  "password": "Password123"
}
```

Resultado esperado:

```txt
200 OK
```

Respuesta esperada:

```json
{
  "token": "<access-token>",
  "refreshToken": "<refresh-token>",
  "type": "Bearer",
  "userId": 1,
  "name": "Usuario A",
  "email": "usera@ups.edu.ec",
  "roles": [
    "ROLE_USER"
  ]
}
```

---

## 23.2. Consumir endpoint protegido con access token

Request:

```http
GET /api/products/page?page=0&size=5
Authorization: Bearer <access-token>
```

Resultado esperado:

```txt
200 OK
```

---

## 23.3. Intentar consumir endpoint protegido con refresh token

Request:

```http
GET /api/products/page?page=0&size=5
Authorization: Bearer <refresh-token>
```

Resultado esperado:

```txt
401 Unauthorized
```

Esto demuestra que el refresh token no puede usarse como access token.

---

## 23.4. Renovar tokens

Request:

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<refresh-token>"
}
```

Resultado esperado:

```txt
200 OK
```

Respuesta esperada:

```json
{
  "token": "<new-access-token>",
  "refreshToken": "<new-refresh-token>",
  "type": "Bearer",
  "userId": 1,
  "name": "Usuario A",
  "email": "usera@ups.edu.ec",
  "roles": [
    "ROLE_USER"
  ]
}
```

---

## 23.5. Intentar reutilizar refresh token anterior

Después de renovar tokens, el refresh token anterior queda revocado.

Request:

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<refresh-token-anterior>"
}
```

Resultado esperado:

```txt
400 Bad Request
```

Mensaje esperado:

```txt
Refresh token no encontrado o revocado
```

---

## 23.6. Consumir endpoint con nuevo access token

Request:

```http
GET /api/products/page?page=0&size=5
Authorization: Bearer <new-access-token>
```

Resultado esperado:

```txt
200 OK
```

---

## 23.7. Logout

Request:

```http
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "<new-refresh-token>"
}
```

Resultado esperado:

```txt
204 No Content
```

---

## 23.8. Intentar refresh después de logout

Request:

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<new-refresh-token>"
}
```

Resultado esperado:

```txt
400 Bad Request
```

---

# 24. Flujo de rotación de refresh token

```txt
Login
  ↓
accessToken A
refreshToken A
  ↓
Refresh
  ↓
refreshToken A se revoca
  ↓
accessToken B
refreshToken B
  ↓
Refresh
  ↓
refreshToken B se revoca
  ↓
accessToken C
refreshToken C
```

Esto evita reutilización permanente del mismo refresh token.

---

# 25. Diferencia entre expiración y revocación

| Concepto | Significado |
| -------- | ----------- |
| Expiración | El token dejó de ser válido por tiempo |
| Revocación | El servidor lo invalidó antes de que expire |

Ejemplo de expiración:

```txt
refresh token creado hoy
expira en 7 días
después de 7 días ya no sirve
```

Ejemplo de revocación:

```txt
usuario hace logout
el refresh token se marca como revoked = true
aunque no haya expirado, ya no sirve
```

---

# 26. Consideraciones de seguridad

## 26.1. El refresh token no debe usarse como Bearer token

Incorrecto:

```http
GET /api/products/page
Authorization: Bearer <refresh-token>
```

Correcto:

```http
POST /api/auth/refresh

{
  "refreshToken": "<refresh-token>"
}
```

---

## 26.2. El refresh token debe durar más que el access token

Ejemplo:

```txt
Access token: 30 minutos
Refresh token: 7 días
```

---

## 26.3. El refresh token debe poder revocarse

Por eso se guarda en base de datos.

Si no se guarda, no se puede cerrar sesión de forma controlada.

---

## 26.4. En producción se recomienda guardar hash del refresh token

En esta práctica se guarda el token completo para facilitar el aprendizaje.

En producción, una opción más segura es guardar:

```txt
hash(refreshToken)
```

y comparar hashes.

---

# 27. Actividad práctica

Se debe implementar refresh token en el backend.

---

##  Actualizar JwtUtil

Agregar:

```java
generateAccessToken(...)
generateAccessTokenFromUserDetails(...)
generateRefreshToken(...)
validateAccessToken(...)
validateRefreshToken(...)
getTokenType(...)
```

---

## 27.6. Actualizar JwtAuthenticationFilter

Cambiar:

```java
jwtUtil.validateToken(jwt)
```

por:

```java
jwtUtil.validateAccessToken(jwt)
```

---

## Crear RefreshTokenService

Crear:

```txt
security/services/RefreshTokenService.java
```

Debe implementar:

```java
createRefreshToken(...)
validateAndGetActiveToken(...)
revoke(...)
revokeAllByUser(...)
```



## Actualizar AuthService

Actualizar:

```java
login(...)
register(...)
```

Crear:

```java
refresh(...)
logout(...)
```

---

## 27.9. Actualizar AuthController

Agregar endpoints:

```txt
POST /api/auth/refresh
POST /api/auth/logout
```

---

# 28. Resultados y evidencias

En la nueva entrada del README, se debe agregar:

---

## Captura de login con refresh token

Endpoint:

```txt
POST /api/auth/login
```

Debe evidenciar:

```txt
token
refreshToken
roles
```

---

## Captura de refresh exitoso

Endpoint:

```txt
POST /api/auth/refresh
```

Debe evidenciar:

```txt
200 OK
nuevo token
nuevo refreshToken
```

---



## Captura de logout

Endpoint:

```txt
POST /api/auth/logout
```

Debe evidenciar:

```txt
204 No Content
```

---

## Captura de refresh después de logout

Endpoint:

```txt
POST /api/auth/refresh
```

Debe evidenciar:

```txt
400 Bad Request
refresh token revocado
```

---

## Explicación breve

Debe responder:

```txt
¿Cuál es la diferencia entre access token y refresh token?
```

También debe responder:

```txt
¿Por qué el refresh token no debe usarse en Authorization: Bearer?
```

Y:

```txt
¿Qué significa rotar un refresh token?
```

---

