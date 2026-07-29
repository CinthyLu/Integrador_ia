
# 26. Errores comunes

## 26.1. Swagger no abre

Revisar que exista la dependencia:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
```

También revisar la URL:

```txt
http://localhost:8080/api/swagger-ui/index.html
```

---

## 26.2. Swagger abre, pero no carga endpoints

Revisar:

```txt
/v3/api-docs
```

Si `/v3/api-docs` responde 401, entonces falta permitir:

```java
.requestMatchers("/v3/api-docs/**").permitAll()
```

---

## 26.3. El botón Authorize no aparece

Revisar que exista `OpenApiConfig` y que se haya definido:

```java
SecurityScheme bearerScheme = new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT");
```

---

## 26.4. El token no se envía

Revisar que el controlador tenga:

```java
@SecurityRequirement(name = "bearerAuth")
```

o que se haya configurado seguridad global en OpenAPI.

---

## 26.5. Se pegó mal el token

No pegar:

```txt
"token": "eyJhbGciOiJIUzI1NiJ9..."
```

Pegar solamente:

```txt
eyJhbGciOiJIUzI1NiJ9...
```

---