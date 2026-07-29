# Programación y Plataformas Web

# **Spring Boot – Despliegue en Producción**

<div align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="80">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="80">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/nginx/nginx-original.svg" width="80">
</div>

## **Práctica 14 (Spring Boot): Preparación y Despliegue de APIs**

### **Autores**

**Pablo Torres**

 [ptorresp@ups.edu.ec](mailto:ptorresp@ups.edu.ec)

GitHub: PabloT18

---

# **Introducción**

Después de implementar autenticación, autorización y validación de ownership en las prácticas anteriores, es momento de **desplegar tu API Spring Boot en producción**.

En esta práctica aprenderás:
- **Arquitectura de Spring Boot**: Cómo funciona Tomcat embebido
- **Build para producción**: Crear JAR ejecutable optimizado
- **Despliegue nativo**: Configurar systemd + Nginx
- **Despliegue con Docker**: Dockerfile multi-stage optimizado
- **PaaS**: Desplegar en Render, Railway, Heroku
- **Health checks**: Usar Spring Boot Actuator
- **Configuración por ambiente**: Profiles y variables de entorno

**Prerequisitos**:
- Proyecto funcional de las prácticas 1-13
- Conocimientos básicos de Linux y terminal
- Docker instalado (para sección Docker)
- Cuenta en servicio PaaS (opcional para despliegue cloud)

---

# **1. Arquitectura de Spring Boot en Ejecución**

## **1.1. ¿Qué es Tomcat Embebido?**

Cuando ejecutas una aplicación Spring Boot con `./gradlew bootRun` o `java -jar app.jar`, **Spring Boot inicia automáticamente un servidor Tomcat embebido**.

### **¿Qué es Tomcat?**

**Apache Tomcat** es un **contenedor de servlets** (servidor de aplicaciones Java) que:
- Recibe peticiones HTTP en un puerto (por defecto 8080)
- Las pasa a tu aplicación Spring Boot (controladores)
- Devuelve las respuestas HTTP al cliente

### **Tomcat "Embebido" vs Tomcat Externo**

| Aspecto | Tomcat Embebido (Spring Boot) | Tomcat Externo (Tradicional) |
|---------|-------------------------------|------------------------------|
| **Instalación** | Incluido en el JAR | Instalar por separado |
| **Configuración** | application.properties | server.xml, context.xml |
| **Despliegue** | `java -jar app.jar` | Copiar WAR a `/webapps` |
| **Portabilidad** | Alta (todo en un JAR) | Baja (requiere servidor) |
| **Actualizaciones** | Actualizar dependencia | Actualizar servidor |
| **Recomendado para** | Microservicios, APIs, cloud | Aplicaciones legacy |

### **¿Hay Nginx Embebido en Spring Boot?**

**NO**. Spring Boot **solo incluye Tomcat embebido** (o Jetty/Undertow si lo configuras).

- **Tomcat embebido**: Maneja peticiones HTTP de tu aplicación Java
- **Nginx**: Es un servidor web externo que se usa como **reverse proxy** (opcional, se configura por separado)

**Flujo típico en producción**:
```
Internet → Nginx (puerto 80/443) → Tomcat embebido (puerto 8080)
```

Nginx se instala **aparte**, no viene con Spring Boot.

## **1.2. Flujo de Ejecución de Spring Boot**

### **En Desarrollo Local**

```bash
./gradlew bootRun
```

**¿Qué sucede internamente?**

```
1. Gradle compila el código (.java → .class)
        ↓
2. Spring Boot inicia Tomcat embebido
        ↓
3. Tomcat abre puerto 8080 (configurable en application.properties)
        ↓
4. Spring Boot escanea @Controllers, @Services, @Repositories
        ↓
5. Inicializa beans (inyección de dependencias)
        ↓
6. Conecta a base de datos (si está configurada)
        ↓
7. Aplicación lista para recibir peticiones
        ↓
8. LOG: "Started Application in X seconds"
```

**Acceso directo**:
```
http://localhost:8080/api/products
↓ (Tomcat embebido maneja directamente)
Controlador → Servicio → Repositorio → BD
```

### **En Build (JAR)**

```bash
./gradlew build
java -jar build/libs/mi-api.jar
```

**¿Qué sucede?**

```
1. Gradle compila y empaqueta TODO en un JAR:
   - Clases compiladas
   - Dependencias (Spring, Tomcat, PostgreSQL driver)
   - application.properties
   - Tomcat embebido
        ↓
2. java -jar ejecuta el JAR
        ↓
3. Tomcat embebido se inicia (igual que en desarrollo)
        ↓
4. Aplicación escucha en puerto configurado
```

**Ventaja**: Un solo archivo ejecutable, sin instalar nada más (solo necesitas Java).

### **En Producción con Nginx**

```bash
java -jar mi-api.jar
# Tomcat escucha en localhost:8080 (interno)
```

```nginx
# Nginx configurado como reverse proxy
server {
    listen 80;
    location / {
        proxy_pass http://localhost:8080;  ← Redirige a Tomcat
    }
}
```

**Flujo completo**:
```
Cliente → Internet → Nginx (80/443) → Tomcat embebido (8080) → Tu API
                           ↓
                    (SSL, rate limiting,
                     load balancing)
```

**Ventajas de Nginx como proxy**:
- Maneja SSL/HTTPS
- Sirve archivos estáticos sin pasar por Java
- Load balancing entre múltiples instancias de Spring Boot
- Rate limiting y seguridad
- Compresión gzip

## **1.3. JAR Ejecutable vs WAR**

### **JAR (Java ARchive) - Recomendado**

**Características**:
- Archivo `.jar` ejecutable standalone
- Incluye Tomcat embebido
- Se ejecuta con `java -jar app.jar`
- Ideal para microservicios y cloud

**Cuándo usar**:
- APIs REST modernas
- Despliegue en Docker
- PaaS (Heroku, Railway, Render)
- Microservicios

### **WAR (Web ARchive) - Legacy**

**Características**:
- Archivo `.war` para desplegar en servidor Tomcat externo
- No incluye Tomcat (usa el del servidor)
- Se copia a carpeta `/webapps` de Tomcat
- Ideal para aplicaciones tradicionales

**Cuándo usar**:
- Empresa con servidores Tomcat ya instalados
- Múltiples aplicaciones en el mismo servidor
- Requisitos legacy

**En esta práctica usaremos JAR** (enfoque moderno).

---

# **2. Configuración por Ambiente**

## **2.1. Profiles de Spring Boot**

Spring Boot permite configuraciones diferentes para cada ambiente usando **profiles**.

### **Estructura de Archivos**

```
src/main/resources/
├── application.yml              ← Configuración común
├── application-dev.yml          ← Desarrollo local
├── application-prod.yml         ← Producción
└── application-test.yml         ← Tests
```

### **application.yml (Base)**

Configuración compartida por todos los ambientes:

```yaml
spring:
  application:
    name: mi-api
  
  # Configuración común de JPA
  jpa:
    hibernate:
      ddl-auto: validate  # En producción NUNCA usar create/update
    properties:
      hibernate:
        format_sql: true

# Configuración común del servidor
server:
  error:
    include-message: always
    include-binding-errors: always

# Actuator (health checks)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### **application-dev.yml (Desarrollo)**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mi_api_db
    username: postgres
    password: postgres
  
  jpa:
    show-sql: true  # Mostrar queries en consola
    hibernate:
      ddl-auto: update  # Auto-crear tablas (solo en dev)

# Puerto de desarrollo
server:
  port: 8080

# Logs detallados
logging:
  level:
    root: INFO
    ec.edu.ups.pwp67: DEBUG  # Tu paquete base
    org.hibernate.SQL: DEBUG
    org.springframework.security: DEBUG

# JWT (valores de desarrollo)
jwt:
  secret: dev-secret-key-no-usar-en-produccion
  expiration: 3600000  # 1 hora
```

### **application-prod.yml (Producción)**

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}  # Variable de entorno
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 20000
  
  jpa:
    show-sql: false  # No mostrar queries
    hibernate:
      ddl-auto: validate  # Solo validar, no modificar BD

# Puerto de producción
server:
  port: ${PORT:8080}

# Logs solo errores
logging:
  level:
    root: WARN
    ec.edu.ups.pwp67: INFO
  file:
    name: /var/log/mi-api/application.log
    max-size: 10MB
    max-history: 30

# JWT (valores desde entorno)
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}

# Seguridad adicional
server:
  error:
    include-stacktrace: never  # No exponer stack traces
```

## **2.2. Activar Profile**

### **Opción 1: Variable de entorno**

```bash
# Desarrollo
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun

# Producción
export SPRING_PROFILES_ACTIVE=prod
java -jar build/libs/mi-api.jar
```

### **Opción 2: Argumento de línea de comandos**

```bash
java -jar build/libs/mi-api.jar --spring.profiles.active=prod
```

### **Opción 3: En application.yml**

```yaml
spring:
  profiles:
    active: dev  # Profile por defecto
```

### **Opción 4: En IDE (IntelliJ/Eclipse)**

**IntelliJ IDEA**:
```
Run → Edit Configurations → Active Profiles: dev
```

## **2.3. Variables de Entorno en Producción**

### **Crear archivo .env (NO versionar en Git)**

```bash
# .env.production
DATABASE_URL=jdbc:postgresql://db.miservidor.com:5432/prod_db
DB_USERNAME=prod_user
DB_PASSWORD=super-secure-password-123
JWT_SECRET=c2VjcmV0LWtleS1mb3ItcHJvZHVjdGlvbi1kb250LWV4cG9zZQ==
JWT_EXPIRATION=3600000
PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### **Cargar variables al ejecutar**

```bash
# Opción 1: Source del archivo
source .env.production
java -jar build/libs/mi-api.jar

# Opción 2: Inline
DATABASE_URL=jdbc:postgresql://... DB_USERNAME=... java -jar build/libs/mi-api.jar
```

### **En systemd (Linux)**

```ini
[Service]
EnvironmentFile=/opt/mi-api/.env.production
ExecStart=/usr/bin/java -jar /opt/mi-api/mi-api.jar
```

---

# **3. Build para Producción**

## **3.1. Configurar build.gradle.kts**

### **Verificar configuración del plugin**

```java
plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
}

group = "ec.edu.ups.pwp67"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

springBoot {
    mainClass.set("ec.edu.ups.pwp67.Application")
}

tasks.bootJar {
    archiveFileName.set("mi-api.jar")
}
```

### **Dependencias opcionales para producción**

```java
dependencies {
    // Spring Boot Actuator para health checks
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Micrometer para métricas (opcional)
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Resto de dependencias...
}
```

## **3.2. Compilar JAR Ejecutable**

### **Paso 1: Limpiar builds anteriores**

```bash
./gradlew clean
```

### **Paso 2: Compilar y empaquetar**

```bash
./gradlew build -x test
# -x test para omitir tests (ejecutar tests antes en otro paso)
```

**Salida**:
```
BUILD SUCCESSFUL
Generated JAR: build/libs/mi-api.jar
```

### **Paso 3: Verificar JAR**

```bash
ls -lh build/libs/
# Deberías ver: mi-api.jar (aproximadamente 50-100 MB)

# Ver contenido del JAR
jar -tf build/libs/mi-api.jar | head -20
```

### **Paso 4: Probar JAR localmente**

```bash
java -jar build/libs/mi-api.jar --spring.profiles.active=dev

# Ver logs de inicio
# Deberías ver: "Started Application in X seconds"
```

### **Paso 5: Verificar que funciona**

```bash
# En otra terminal
curl http://localhost:8080/api/products

# O health check
curl http://localhost:8080/actuator/health
```

## **3.3. Optimizar Tamaño del JAR**

### **Excluir dependencias no usadas**

```kotlin
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // Resto de dependencias...
}
```

### **Usar build optimizado**

```bash
# Crear JAR optimizado
./gradlew bootJar --stacktrace

# Tamaño típico:
# Sin optimización: 80-100 MB
# Con optimización: 50-70 MB
```

---

# **4. Despliegue Nativo (Sin Docker)**

## **4.1. Preparar Servidor Linux (Ubuntu/Debian)**

### **Paso 1: Instalar Java**

```bash
# Actualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Java 17 (o versión que uses en tu proyecto)
sudo apt install -y openjdk-17-jdk

# Verificar instalación
java -version
# Debería mostrar: openjdk version "17.X.X"
```

### **Paso 2: Crear usuario para la aplicación**

```bash
# Crear usuario sin shell (seguridad)
sudo useradd -r -s /bin/false miapi

# Crear directorio de la aplicación
sudo mkdir -p /opt/mi-api
sudo chown miapi:miapi /opt/mi-api
```

### **Paso 3: Subir JAR al servidor**

```bash
# Desde tu máquina local
scp build/libs/mi-api.jar usuario@servidor:/opt/mi-api/

# O clonar repositorio y hacer build en servidor
cd /opt/mi-api
git clone https://github.com/tu-usuario/tu-api.git .
./gradlew build -x test
```

## **4.2. Configurar Variables de Entorno**

### **Crear archivo de configuración**

```bash
sudo nano /opt/mi-api/.env
```

```bash
# Configuración de producción
DATABASE_URL=jdbc:postgresql://localhost:5432/prod_db
DB_USERNAME=miapi_user
DB_PASSWORD=secure-password-here
JWT_SECRET=your-super-secret-jwt-key-here-min-256-bits
JWT_EXPIRATION=3600000
SPRING_PROFILES_ACTIVE=prod
```

```bash
# Proteger archivo (solo lectura para el usuario de la app)
sudo chown miapi:miapi /opt/mi-api/.env
sudo chmod 600 /opt/mi-api/.env
```

## **4.3. Configurar systemd Service**

### **Crear archivo de servicio**

```bash
sudo nano /etc/systemd/system/mi-api.service
```

```ini
[Unit]
Description=Spring Boot Mi API
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=miapi
Group=miapi
WorkingDirectory=/opt/mi-api

# Cargar variables de entorno
EnvironmentFile=/opt/mi-api/.env

# Comando para ejecutar
ExecStart=/usr/bin/java \
    -Xms256m \
    -Xmx512m \
    -jar /opt/mi-api/mi-api.jar \
    --spring.profiles.active=prod

# Reiniciar automáticamente si falla
Restart=always
RestartSec=10

# Logs
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mi-api

# Seguridad adicional
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### **Explicación de parámetros JVM**

| Parámetro | Significado | Valor recomendado |
|-----------|-------------|-------------------|
| `-Xms` | Memoria inicial heap | 256m (ajustar según servidor) |
| `-Xmx` | Memoria máxima heap | 512m-1g (ajustar según carga) |
| `-jar` | Ejecutar JAR | Ruta al JAR |

**Para servidores con más recursos**:
```
-Xms512m -Xmx2g
```

### **Activar y gestionar el servicio**

```bash
# Recargar systemd
sudo systemctl daemon-reload

# Habilitar inicio automático
sudo systemctl enable mi-api

# Iniciar servicio
sudo systemctl start mi-api

# Ver estado
sudo systemctl status mi-api

# Ver logs en tiempo real
sudo journalctl -u mi-api -f

# Reiniciar
sudo systemctl restart mi-api

# Detener
sudo systemctl stop mi-api
```

### **Verificar que funciona**

```bash
# Esperar unos segundos para que inicie
sleep 10

# Probar endpoint
curl http://localhost:8080/api/products

# Health check
curl http://localhost:8080/actuator/health
```

## **4.4. Configurar Nginx como Reverse Proxy**

### **Instalar Nginx**

```bash
sudo apt install -y nginx
```

### **Crear configuración del sitio**

```bash
sudo nano /etc/nginx/sites-available/mi-api
```

```nginx
# Upstream (backend Spring Boot)
upstream spring_backend {
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s;
}

# HTTP (redirigir a HTTPS)
server {
    listen 80;
    server_name api.midominio.com;
    
    return 301 https://$server_name$request_uri;
}

# HTTPS
server {
    listen 443 ssl http2;
    server_name api.midominio.com;

    # Certificados SSL (Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/api.midominio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.midominio.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Logs
    access_log /var/log/nginx/mi-api-access.log;
    error_log /var/log/nginx/mi-api-error.log;

    # Tamaño máximo de request
    client_max_body_size 10M;

    # Health check (sin logs)
    location /actuator/health {
        proxy_pass http://spring_backend;
        access_log off;
    }

    # API endpoints
    location /api {
        proxy_pass http://spring_backend;
        proxy_http_version 1.1;
        
        # Headers importantes
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts para operaciones largas
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # WebSocket support (si usas)
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Servir documentación estática (si tienes)
    location /docs {
        alias /opt/mi-api/docs;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

### **Activar configuración**

```bash
# Crear enlace simbólico
sudo ln -s /etc/nginx/sites-available/mi-api /etc/nginx/sites-enabled/

# Probar configuración
sudo nginx -t

# Recargar Nginx
sudo systemctl reload nginx
```

### **Configurar SSL con Let's Encrypt**

```bash
# Instalar Certbot
sudo apt install -y certbot python3-certbot-nginx

# Obtener certificado (Nginx debe estar corriendo)
sudo certbot --nginx -d api.midominio.com

# Renovación automática ya está configurada
sudo systemctl status certbot.timer
```

# **5. Despliegue con Docker**

## **5.1. Dockerfile Multi-Stage con Spring Boot por capas**

### **Objetivo**

Construir una imagen Docker para una aplicación Spring Boot utilizando dos etapas:

1. Una etapa de compilación con JDK 21 y Gradle Wrapper.
2. Una etapa de ejecución con JRE 21.

En lugar de copiar el JAR completo a la imagen final, el artefacto se descomprime y se separa en:

* Dependencias externas.
* Metadatos.
* Clases y recursos de la aplicación.

Esta organización permite que Docker reutilice la capa de dependencias cuando únicamente cambia el código fuente.

### **Configuración del JAR**

En `build.gradle.kts`, definir un nombre fijo para el JAR ejecutable:

```kotlin
tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}
```

Gradle generará:

```text
build/libs/app.jar
```

### **Dockerfile**

Crear un archivo denominado `Dockerfile` en la raíz del proyecto:

```dockerfile
# syntax=docker/dockerfile:1.7

# ============================================
# ETAPA 1: BUILD
# ============================================
# Imagen con JDK 21 para compilar la aplicación
FROM eclipse-temurin:21-jdk AS builder

# Directorio de trabajo
WORKDIR /workspace/app

# Copiar Gradle Wrapper y archivos de configuración
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Dar permiso de ejecución al Wrapper
RUN chmod +x gradlew

# Copiar el código fuente
COPY src ./src

# Compilar la aplicación utilizando caché para Gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# Crear el directorio donde se descomprimirá el JAR
RUN mkdir -p build/dependency \
    && cd build/dependency \
    && jar -xf ../libs/app.jar


# ============================================
# ETAPA 2: RUNTIME
# ============================================
# Imagen con JRE 21 para ejecutar la aplicación
FROM eclipse-temurin:21-jre AS runtime

# Directorio de ejecución
WORKDIR /app

# Crear usuario y grupo sin privilegios
RUN groupadd --system spring \
    && useradd --system --gid spring spring

# Ruta de los archivos extraídos en la etapa anterior
ARG DEPENDENCY=/workspace/app/build/dependency

# Copiar las dependencias externas
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/lib /app/lib

# Copiar metadatos del JAR
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/META-INF /app/META-INF

# Copiar clases y recursos de la aplicación
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/classes /app

# Ejecutar como usuario no-root
USER spring:spring

# Documentar el puerto de Spring Boot
EXPOSE 8080

# Configurar zona horaria de la JVM
ENV TZ=America/Guayaquil

# Ejecutar la clase principal utilizando el classpath generado
ENTRYPOINT ["java", \
    "-Xms256m", \
    "-Xmx512m", \
    "-cp", \
    "/app:/app/lib/*", \
    "ec.edu.ups.icc.fundamentos01.Fundamentos01Application"]
```

Se debe verificar que la clase principal definida en `build.gradle.kts` coincide con la clase especificada en el `ENTRYPOINT` del Dockerfile:

```text
ec.edu.ups.icc.fundamentos01.Fundamentos01Application
```

---

### Explicación del Dockerfile
#### **Explicación del Build Stage**

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
```

Esta etapa utiliza una imagen con JDK 21. El JDK incluye las herramientas necesarias para compilar el proyecto y manipular el archivo JAR.

El nombre `builder` permite referenciar esta etapa posteriormente:

```dockerfile
COPY --from=builder ...
```

#### **Directorio de trabajo**

```dockerfile
WORKDIR /workspace/app
```

Define el directorio interno donde se copiará y compilará el proyecto.

Las instrucciones posteriores se ejecutan desde:

```text
/workspace/app
```

#### **Gradle Wrapper**

```dockerfile
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
```

Se copia el Gradle Wrapper y la configuración del proyecto.

El Wrapper utiliza la versión declarada en:

```text
gradle/wrapper/gradle-wrapper.properties
```

Esto evita depender de una instalación global de Gradle dentro de la imagen.

#### **Permiso de ejecución**

```dockerfile
RUN chmod +x gradlew
```

Permite ejecutar el archivo `gradlew` en Linux.

#### **Copiar el código**

```dockerfile
COPY src ./src
```

Copia el código fuente y los recursos del proyecto.

#### **Compilación con caché de BuildKit**

```dockerfile
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon
```

Esta instrucción:

* Utiliza el Gradle Wrapper.
* Ejecuta `bootJar`.
* Omite los tests mediante `-x test`.
* Evita iniciar el daemon de Gradle.
* Conserva las dependencias descargadas en una caché de BuildKit.

La caché:

```text
/root/.gradle
```

reduce el tiempo de compilaciones posteriores.

La opción `--mount=type=cache` requiere Docker BuildKit, disponible por defecto en versiones actuales de Docker.

#### **Extracción del JAR**

```dockerfile
RUN mkdir -p build/dependency \
    && cd build/dependency \
    && jar -xf ../libs/app.jar
```

El JAR ejecutable de Spring Boot se descomprime en:

```text
build/dependency/
```

La estructura resultante contiene:

```text
build/dependency/
├── BOOT-INF/
│   ├── classes/
│   └── lib/
├── META-INF/
└── org/
```

Los directorios utilizados en la imagen final son:

```text
BOOT-INF/lib
BOOT-INF/classes
META-INF
```

---

#### **Explicación del Runtime Stage**

```dockerfile
FROM eclipse-temurin:21-jre AS runtime
```

Esta etapa incluye únicamente el entorno necesario para ejecutar Java 21.

No contiene:

* Gradle.
* Compilador Java.
* Código fuente.
* Archivos temporales de construcción.

#### **Usuario no-root**

```dockerfile
RUN groupadd --system spring \
    && useradd --system --gid spring spring
```

Crea:

* Un grupo del sistema denominado `spring`.
* Un usuario del sistema denominado `spring`.

La aplicación no se ejecutará como `root`.

#### **Separación por capas**

```dockerfile
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/lib /app/lib
```

Copia las dependencias externas de la aplicación:

```text
Spring Framework
Hibernate
Jackson
PostgreSQL Driver
Spring Security
```

Estas dependencias cambian con menor frecuencia que el código fuente.

```dockerfile
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/META-INF /app/META-INF
```

Copia los metadatos del artefacto.

```dockerfile
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/classes /app
```

Copia:

* Clases compiladas.
* Archivos `application.properties`.
* Recursos estáticos.
* Plantillas.
* Configuraciones de la aplicación.

Cuando únicamente cambia el código, Docker puede reutilizar la capa de dependencias y reconstruir principalmente la capa de clases.

#### **Permisos del archivo**

La opción:

```dockerfile
--chown=spring:spring
```

asigna directamente los archivos al usuario y grupo `spring`.

Esto evita una instrucción adicional como:

```dockerfile
RUN chown -R spring:spring /app
```

#### **Ejecución como usuario limitado**

```dockerfile
USER spring:spring
```

Todas las instrucciones de ejecución posteriores utilizan el usuario `spring`.

#### **Puerto**

```dockerfile
EXPOSE 8080
```

Documenta que la aplicación escucha en el puerto 8080.

No publica automáticamente el puerto. Para acceder desde el host se requiere:

```bash
-p 8080:8080
```

#### **Zona horaria**

```dockerfile
ENV TZ=America/Guayaquil
```

Define la zona horaria utilizada por la aplicación.

También puede configurarse directamente en la JVM:

```dockerfile
"-Duser.timezone=America/Guayaquil"
```

#### **Ejecución mediante classpath**

```dockerfile
ENTRYPOINT ["java", \
    "-Xms256m", \
    "-Xmx512m", \
    "-cp", \
    "/app:/app/lib/*", \
    "ec.edu.ups.mob_parking.Main"]
```

El parámetro:

```text
-cp
```

define el classpath de Java.

El classpath incluye:

```text
/app
```

para las clases de la aplicación, y:

```text
/app/lib/*
```

para las dependencias externas.

Los parámetros de memoria son:

```text
-Xms256m
```

Memoria inicial del heap.

```text
-Xmx512m
```

Memoria máxima del heap.

La última posición corresponde a la clase que contiene el método:

```java
public static void main(String[] args)
```

---

#### **Construir la imagen**

Desde la raíz del proyecto:

```bash
docker build --pull -t mi-api:1.0 .
```

Para visualizar detalladamente la compilación:

```bash
docker build \
  --pull \
  --progress=plain \
  -t mi-api:1.0 \
  .
```

Verificar la imagen:

```bash
docker images mi-api
```

### **Ejecutar el contenedor**

```bash
docker run -d \
  --name mi-api \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/prod_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e JWT_SECRET=my-secret-key \
  mi-api:1.0
```

### **Verificar el contenedor**

```bash
docker ps
```

### **Consultar logs**

```bash
docker logs -f mi-api
```

### **Probar la aplicación**

Desde Ubuntu Server:

```bash
curl http://localhost:8080
```

Desde la máquina anfitriona:

```bash
curl http://192.168.56.2:8080
```

### **Detener y eliminar el contenedor**

```bash
docker stop mi-api
docker rm mi-api
```

---

## **5.2. Archivo `.dockerignore`**

### **Objetivo**

El archivo `.dockerignore` evita que Docker envíe archivos innecesarios al contexto de construcción.

Crear el archivo en la raíz del proyecto:

```text
.dockerignore
```

### **Contenido recomendado**

```dockerignore
# Gradle
.gradle/
build/

# Git
.git/
.gitignore

# IDE
.idea/
.vscode/
*.iml

# Sistema operativo
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Variables de entorno
.env
.env.*

# Archivos temporales
tmp/
temp/
*.tmp
*.swp

# Documentación
README.md

# Configuración local de Docker Compose
compose.override.yaml
docker-compose.override.yml
```

### **Archivos que deben permanecer disponibles**

El Dockerfile requiere:

```text
gradlew
gradle/
build.gradle.kts
settings.gradle.kts
gradle.properties
src/
```

Estos archivos no deben incluirse en `.dockerignore`.

### **Verificación**

```bash
docker build \
  --progress=plain \
  -t mi-api:1.0 \
  .
```

La salida mostrará el tamaño del contexto enviado a Docker.

---

## **5.3. Levantar Nginx en Docker**

### **Arquitectura**

Nginx funcionará como reverse proxy:

```text
Cliente
   |
   | Puerto 80
   v
Nginx
   |
   | Puerto 8080
   v
Spring Boot
```

Spring Boot no necesita publicar el puerto 8080 directamente en el host. Nginx se comunicará con la aplicación mediante una red Docker privada.

### **Crear una red Docker**

```bash
docker network create app-network
```

Verificar:

```bash
docker network ls
```

### **Levantar Spring Boot**

```bash
docker run -d \
  --name mi-api \
  --network app-network \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/prod_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e JWT_SECRET=my-secret-key \
  mi-api:1.0
```

No se publica el puerto:

```bash
-p 8080:8080
```

porque Nginx accederá a `mi-api:8080` desde la red interna.

### **Estructura de Nginx**

Crear:

```text
nginx/
└── default.conf
```

### **Configuración de Nginx**

Archivo `nginx/default.conf`:

```nginx
upstream spring_backend {
    server mi-api:8080;
}

server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://spring_backend;

        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### **Backend**

```nginx
upstream spring_backend {
    server mi-api:8080;
}
```

`mi-api` es el nombre del contenedor Spring Boot.

Docker resuelve ese nombre mediante DNS interno porque ambos contenedores pertenecen a:

```text
app-network
```

No debe configurarse:

```nginx
server localhost:8080;
```

Dentro del contenedor Nginx, `localhost` representa al propio contenedor Nginx.

### **Levantar Nginx**

```bash
docker run -d \
  --name nginx \
  --network app-network \
  -p 80:80 \
  -v "$(pwd)/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:alpine
```

### **Verificar la configuración**

```bash
docker exec nginx nginx -t
```

Salida esperada:

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

### **Verificar contenedores**

```bash
docker ps
```

Ejemplo:

```text
CONTAINER ID   IMAGE          NAME      PORTS
abc123         nginx:alpine   nginx     0.0.0.0:80->80/tcp
def456         mi-api:1.0     mi-api    8080/tcp
```

### **Probar desde Ubuntu Server**

```bash
curl http://localhost
```

### **Probar desde la Mac**

```bash
curl http://192.168.56.2
```

También puede accederse desde un navegador:

```text
http://192.168.56.2
```

### **Flujo de la solicitud**

```text
Mac
 |
 | HTTP puerto 80
 v
Ubuntu Server
 |
 v
Contenedor Nginx
 |
 | http://mi-api:8080
 v
Contenedor Spring Boot
```

### **Consultar logs**

Nginx:

```bash
docker logs -f nginx
```

Spring Boot:

```bash
docker logs -f mi-api
```

### **Inspeccionar la red**

```bash
docker network inspect app-network
```

La salida debe incluir los contenedores:

```text
mi-api
nginx
```

### **Detener los servicios**

```bash
docker stop nginx mi-api
```

### **Eliminar contenedores**

```bash
docker rm nginx mi-api
```

### **Eliminar la red**

```bash
docker network rm app-network
```

### **Error `502 Bad Gateway`**

Revisar el contenedor Spring Boot:

```bash
docker ps
docker logs mi-api
```

Comprobar que ambos contenedores estén conectados a la misma red:

```bash
docker network inspect app-network
```

Comprobar resolución DNS desde Nginx:

```bash
docker exec nginx getent hosts mi-api
```

### **La aplicación no inicia**

Consultar:

```bash
docker logs mi-api
```

Causas frecuentes:

* Variables de entorno incorrectas.
* Base de datos no disponible.
* Clase principal incorrecta en `ENTRYPOINT`.
* Perfil `prod` sin configuración.
* Puerto diferente de `8080`.
* Error durante la compilación del proyecto.

### **No se puede acceder desde el host**

Verificar la publicación del puerto de Nginx:

```bash
docker port nginx
```

Salida esperada:

```text
80/tcp -> 0.0.0.0:80
```

Verificar la IP del servidor:

```bash
ip a
```

Probar desde la Mac:

```bash
curl http://192.168.56.2
```





# **5.B Docker Compose Completo**

### **docker-compose.yml**

```yaml
version: '3.8'

services:
  # ============================================
  # POSTGRESQL
  # ============================================
  postgres:
    image: postgres:15-alpine
    container_name: mi-api-postgres
    restart: always
    environment:
      POSTGRES_DB: mi_api_db
      POSTGRES_USER: api_user
      POSTGRES_PASSWORD: secure-password-123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U api_user -d mi_api_db"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app-network

  # ============================================
  # SPRING BOOT API
  # ============================================
  api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: mi-api
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/mi_api_db
      DB_USERNAME: api_user
      DB_PASSWORD: secure-password-123
      JWT_SECRET: your-super-secret-jwt-key-change-this-in-production
      JWT_EXPIRATION: 3600000
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - app-network

  # ============================================
  # NGINX REVERSE PROXY
  # ============================================
  nginx:
    image: nginx:alpine
    container_name: mi-api-nginx
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - nginx_logs:/var/log/nginx
    depends_on:
      - api
    networks:
      - app-network

# ============================================
# VOLUMES
# ============================================
volumes:
  postgres_data:
    driver: local
  nginx_logs:
    driver: local

# ============================================
# NETWORKS
# ============================================
networks:
  app-network:
    driver: bridge
```

### **nginx/nginx.conf**

```nginx
user nginx;
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;
    error_log /var/log/nginx/error.log warn;

    sendfile on;
    keepalive_timeout 65;
    gzip on;

    upstream spring_backend {
        server api:8080 max_fails=3 fail_timeout=30s;
    }

    server {
        listen 80;
        server_name localhost;

        client_max_body_size 10M;

        location /actuator/health {
            proxy_pass http://spring_backend;
            access_log off;
        }

        location / {
            proxy_pass http://spring_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
        }
    }
}
```

### **Comandos Docker Compose**

```bash
# Construir y levantar todos los servicios
docker-compose up -d --build

# Ver logs de todos los servicios
docker-compose logs -f

# Ver logs solo de API
docker-compose logs -f api

# Ver estado
docker-compose ps

# Reiniciar solo API
docker-compose restart api

# Detener todo
docker-compose down

# Detener y eliminar volúmenes (CUIDADO: borra BD)
docker-compose down -v

# Ejecutar comando en contenedor
docker-compose exec api bash
docker-compose exec postgres psql -U api_user -d mi_api_db
```

### **Verificar que funciona**

```bash
# Esperar a que inicien todos los servicios
sleep 30

# Health check
curl http://localhost/actuator/health

# API endpoint
curl http://localhost/api/products

# Ver logs de inicio
docker-compose logs api | grep "Started"
```


# **6. Despliegue en PaaS (Platform as a Service)**

## **6.1. Render**

### **Ventajas**:
- Free tier generoso (750 horas/mes)
- PostgreSQL incluido
- SSL automático
- Deploy desde Git
- Health checks automáticos

### **Pasos de despliegue**:

#### **1. Crear `render.yaml` en raíz del proyecto**

```yaml
services:
  # Base de datos PostgreSQL
  - type: pserv
    name: mi-api-db
    env: docker
    plan: free
    databases:
      - name: mi_api_db
        user: api_user

  # Spring Boot API
  - type: web
    name: mi-api
    env: java
    plan: free
    buildCommand: ./gradlew build -x test
    startCommand: java -jar build/libs/mi-api.jar
    healthCheckPath: /actuator/health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        fromDatabase:
          name: mi-api-db
          property: connectionString
      - key: JWT_SECRET
        generateValue: true
      - key: JWT_EXPIRATION
        value: 3600000
```

#### **2. Conectar repositorio Git**

1. Hacer commit y push a GitHub/GitLab
2. Ir a [Render Dashboard](https://dashboard.render.com)
3. "New +" → "Blueprint"
4. Conectar repositorio
5. Render detecta `render.yaml` automáticamente
6. Deploy

#### **3. Variables de entorno adicionales**

En Dashboard de Render:
- Settings → Environment
- Agregar variables no versionadas:
  - `JWT_SECRET`: (generar secreto seguro)
  - `SPRING_DATASOURCE_URL`: (autocompletado desde BD)

#### **4. Ver logs**

```
Render Dashboard → Tu servicio → Logs
```

## **6.2. Railway**

### **Ventajas**:
- $5 gratis/mes
- Muy fácil configuración
- PostgreSQL con un click
- Deploy automático desde Git

### **Pasos**:

#### **1. Crear proyecto en Railway**

1. Ir a [Railway](https://railway.app)
2. "New Project"
3. "Deploy from GitHub repo"
4. Seleccionar tu repositorio

#### **2. Agregar PostgreSQL**

1. En proyecto → "New" → "Database" → "PostgreSQL"
2. Railway genera automáticamente:
   - `DATABASE_URL`
   - `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`

#### **3. Configurar variables**

En tu servicio → Variables:

```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=${{Postgres.DATABASE_URL}}
JWT_SECRET=your-secret-here
JWT_EXPIRATION=3600000
```

#### **4. Configurar start command**

Settings → Deploy → Start Command:

```bash
java -Xms256m -Xmx512m -jar build/libs/*.jar
```

#### **5. Deploy automático**

Railway detecta push a GitHub y despliega automáticamente.

## **6.3. Heroku (Clásico)**

### **Ventajas**:
- Plataforma madura
- Muchos add-ons
- Documentación extensa

### **Nota**: Heroku eliminó free tier en 2022, ahora mínimo $7/mes.

### **Pasos**:

#### **1. Crear `Procfile` en raíz**

```
web: java -Xmx512m -jar build/libs/mi-api.jar --server.port=$PORT
```

#### **2. Crear `system.properties`**

```properties
java.runtime.version=17
```

#### **3. Deploy con Heroku CLI**

```bash
# Login
heroku login

# Crear app
heroku create mi-api

# Agregar PostgreSQL
heroku addons:create heroku-postgresql:mini

# Configurar variables
heroku config:set SPRING_PROFILES_ACTIVE=prod
heroku config:set JWT_SECRET=your-secret

# Deploy (Heroku detecta Gradle automáticamente)
git push heroku main

# Ver logs
heroku logs --tail

# Abrir app
heroku open
```

## **6.4. Comparación de PaaS**

| PaaS | Free Tier | PostgreSQL | SSL | Deploy desde Git | Mejor para |
|------|-----------|------------|-----|------------------|------------|
| **Render** | 750h/mes | Incluido | Auto | Sí | Portafolio, demos |
| **Railway** | $5 crédito | 1 click | Auto | Sí | Proyectos personales |
| **Heroku** | ❌ Desde $7/mes | Add-on | Auto | Sí | Producción seria |
| **Fly.io** | Limitado | Sí | Auto | Sí | Apps globales |

---

# **7. Spring Boot Actuator (Health Checks)**

## **7.1. ¿Qué es Actuator?**

**Spring Boot Actuator** proporciona endpoints de monitoreo y gestión listos para producción:
- `/actuator/health`: Estado de salud de la aplicación
- `/actuator/info`: Información de la aplicación
- `/actuator/metrics`: Métricas (memoria, threads, requests)
- `/actuator/env`: Variables de entorno (cuidado en producción)

## **7.2. Configuración**

### **1. Agregar dependencia**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### **2. Configurar en application-prod.yml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics  # Solo exponer endpoints necesarios
      base-path: /actuator
  
  endpoint:
    health:
      show-details: when-authorized  # Detalles solo para usuarios autenticados
      probes:
        enabled: true  # Para Kubernetes liveness/readiness
  
  health:
    db:
      enabled: true  # Verificar conexión a BD
    defaults:
      enabled: true

# Información de la app
info:
  app:
    name: Mi API
    description: API de gestión de productos
    version: '@project.version@'
    encoding: '@project.build.sourceEncoding@'
    java:
      version: '@java.version@'
```

### **3. Proteger endpoints con Security**

```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ... configuración existente
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()  // Health público
                .requestMatchers("/actuator/**").hasRole("ADMIN")  // Resto solo ADMIN
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## **7.3. Endpoints Importantes**

### **/actuator/health (Público)**

```bash
curl http://localhost:8080/actuator/health
```

**Respuesta cuando todo está bien**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Respuesta cuando BD está caída**:
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "Cannot create PoolableConnectionFactory"
      }
    }
  }
}
```

### **/actuator/info**

```bash
curl http://localhost:8080/actuator/info
```

```json
{
  "app": {
    "name": "Mi API",
    "description": "API de gestión de productos",
    "version": "1.0.0",
    "java": {
      "version": "17"
    }
  }
}
```

### **/actuator/metrics**

```bash
# Lista de métricas disponibles
curl http://localhost:8080/actuator/metrics

# Métrica específica
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

```json
{
  "name": "jvm.memory.used",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 234567890
    }
  ]
}
```

## **7.4. Health Check Personalizado**

### **Crear HealthIndicator personalizado**

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public Health health() {
        try {
            // Verificar que podemos consultar la BD
            long count = productRepository.count();
            
            return Health.up()
                    .withDetail("products", count)
                    .withDetail("message", "Database connection OK")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

**Resultado**:
```json
{
  "status": "UP",
  "components": {
    "custom": {
      "status": "UP",
      "details": {
        "products": 42,
        "message": "Database connection OK"
      }
    }
  }
}
```

---

# **8. Aplicación Práctica: Continuando tu Proyecto**

## **8.1. Preparar tu Proyecto Actual**

### **Paso 1: Configurar Profiles y build.gradle.kts**

**Crear estructura**:
```
src/main/resources/
├── application.yml              ← Base
├── application-dev.yml          ← Desarrollo
└── application-prod.yml         ← Producción
```

**Configurar build.gradle.kts**:
```kotlin
plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
}

group = "ec.edu.ups.pwp67"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

springBoot {
    mainClass.set("ec.edu.ups.pwp67.Application")
}

tasks.bootJar {
    archiveFileName.set("mi-api.jar")
}
```

**application.yml**:
```yaml
spring:
  application:
    name: pwp67-api
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true

server:
  error:
    include-message: always

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

**application-dev.yml**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pwp67_dev
    username: postgres
    password: postgres
  
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update

server:
  port: 8080

logging:
  level:
    ec.edu.ups.pwp67: DEBUG

jwt:
  secret: dev-secret-not-for-production
  expiration: 3600000
```

**application-prod.yml**:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
  
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

server:
  port: ${PORT:8080}
  error:
    include-stacktrace: never

logging:
  level:
    root: WARN
    ec.edu.ups.pwp67: INFO
  file:
    name: /var/log/pwp67-api/application.log

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}
```

### **Paso 2: Actuator ya está incluido**

Ya está incluido en el `build.gradle.kts` anterior:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### **Paso 3: Proteger Actuator**

**SecurityConfig.java**:
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                
                // Solo ADMIN
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Resto autenticado
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### **Paso 4: Crear Dockerfile**

```dockerfile
# ETAPA 1: BUILD
FROM gradle:8.5-eclipse-temurin-17 AS builder
WORKDIR /build
COPY build.gradle.kts gradle.properties settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon
COPY src ./src
RUN gradle build -x test --no-daemon

# ETAPA 2: RUNTIME
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Xms256m", \
    "-Xmx512m", \
    "-jar", \
    "app.jar"]
```

### **Paso 5: Crear docker-compose.yml**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    restart: always
    environment:
      POSTGRES_DB: pwp67_db
      POSTGRES_USER: pwp67_user
      POSTGRES_PASSWORD: pwp67_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pwp67_user -d pwp67_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  api:
    build: .
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/pwp67_db
      DB_USERNAME: pwp67_user
      DB_PASSWORD: pwp67_password
      JWT_SECRET: your-jwt-secret-change-this
      JWT_EXPIRATION: 3600000
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

### **Paso 6: Crear .dockerignore**

```
build/
.gradle/
.git/
.idea/
*.iml
README.md
.env
.gradle/
gradle-app.setting
.gradletasknamecache
```

## **8.2. Probar Localmente**

### **Con Gradle (desarrollo)**

```bash
# Compilar
./gradlew build -x test

# Ejecutar
./gradlew bootRun --args='--spring.profiles.active=dev'

# O directamente el JAR
java -jar build/libs/mi-api.jar --spring.profiles.active=dev
```

### **Con Docker Compose**

```bash
# Build y levantar
docker-compose up -d --build

# Ver logs
docker-compose logs -f api

# Esperar inicio (ver "Started Application")

# Probar health check
curl http://localhost:8080/actuator/health

# Probar login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ups.edu.ec","password":"admin123"}'

# Ver productos
curl http://localhost:8080/api/products

# Detener
docker-compose down
```

## **8.3. Desplegar en Render**

### **Paso 1: Preparar repositorio**

```bash
# Asegúrate de tener todos los archivos (incluido build.gradle.kts)
git add .
git commit -m "feat(spring-boot): configurar despliegue producción con Gradle"
git push origin main
```

### **Paso 2: Crear render.yaml**

```yaml
services:
  - type: pserv
    name: pwp67-db
    env: docker
    plan: free
    databases:
      - name: pwp67_db
        user: pwp67_user

  - type: web
    name: pwp67-api
    env: java
    plan: free
    buildCommand: ./gradlew build -x test
    startCommand: java -Xms256m -Xmx512m -jar build/libs/pwp67-api.jar
    healthCheckPath: /actuator/health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        fromDatabase:
          name: pwp67-db
          property: connectionString
      - key: JWT_SECRET
        generateValue: true
      - key: JWT_EXPIRATION
        value: 3600000
```

### **Paso 3: Deploy en Render**

1. Ir a [Render Dashboard](https://dashboard.render.com)
2. New → Blueprint
3. Conectar GitHub repo
4. Render detecta `render.yaml` (basado en Gradle)
5. Deploy automático con `./gradlew build -x test`

### **Paso 4: Probar en producción**

```bash
# URL de Render (ejemplo)
API_URL=https://pwp67-api.onrender.com

# Health check
curl $API_URL/actuator/health

# Login
curl -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ups.edu.ec","password":"admin123"}'

# Usar token en requests
TOKEN="<token-obtenido>"
curl $API_URL/api/products \
  -H "Authorization: Bearer $TOKEN"
```

---

# **9. Troubleshooting Común**

## **9.1. Problemas de Build**

### **Error: "Failed to execute goal on project"**

```bash
# Limpiar cache de Gradle
./gradlew clean
rm -rf ~/.gradle/caches

# Volver a build
./gradlew build -x test
```

### **Error: "No main manifest attribute"**

**Solución**: Verificar configuración en `build.gradle.kts`:

```kotlin
springBoot {
    mainClass.set("ec.edu.ups.pwp67.Application")
}

tasks.bootJar {
    archiveFileName.set("mi-api.jar")
}
```

## **9.2. Problemas de Ejecución**

### **Error: "Port 8080 already in use"**

```bash
# Ver qué usa el puerto
lsof -i :8080

# Matar proceso
kill -9 <PID>

# O cambiar puerto
java -jar app.jar --server.port=8081
```

### **Error: "Cannot create PoolableConnectionFactory"**

**Causas**:
- PostgreSQL no está corriendo
- Credenciales incorrectas
- Host/puerto incorrecto

**Solución**:
```bash
# Verificar PostgreSQL
systemctl status postgresql

# Verificar conexión
psql -U usuario -h host -d base_datos
```

## **9.3. Problemas de Docker**

### **Error: "Cannot connect to Docker daemon"**

```bash
# Iniciar Docker
sudo systemctl start docker

# Verificar
docker ps
```

### **Error: "Health check unhealthy"**

```bash
# Ver logs del contenedor
docker logs <container-id>

# Entrar al contenedor
docker exec -it <container-id> sh

# Probar health check manualmente
wget -O- http://localhost:8080/actuator/health
```

## **9.4. Comandos de Diagnóstico**

```bash
# Ver procesos Java corriendo
ps aux | grep java

# Ver memoria de JVM
jstat -gc <pid>

# Ver threads
jstack <pid>

# Ver logs de systemd
journalctl -u mi-api -f

# Verificar variables de entorno
docker exec mi-api env | grep DATABASE

# Test de conectividad a BD
telnet postgres-host 5432
```

---

# **10. Best Practices de Producción**

## **10.1. Configuración**

**Usar profiles** (`dev`, `prod`)  
**Variables de entorno** para secretos  
**No versionar** `.env` en Git  
**Validar configuración** antes de deploy  
**Logs estructurados** (JSON en producción)

## **10.2. Seguridad**

**No exponer** stack traces  
**Rate limiting** en Nginx  
**HTTPS obligatorio**  
**Actualizar dependencias** regularmente  
**Escanear vulnerabilidades** (usar herramientas como OWASP Dependency-Check)

## **10.3. Performance**

**Pool de conexiones** optimizado  
**Índices** en BD  
**Caché** para queries frecuentes  
**Compresión gzip**  
**Limitar memoria JVM** (`-Xmx`)

## **10.4. Monitoreo**

**Health checks** con Actuator  
**Logs centralizados**  
**Alertas** para errores 5xx  
**Métricas** de rendimiento  
**Backups automáticos** de BD

---

# **11. Conclusiones**

Has aprendido a:

**Entender** arquitectura de Spring Boot (Tomcat embebido)  
**Configurar** profiles y variables de entorno  
**Compilar** JAR ejecutable optimizado  
**Desplegar nativamente** con systemd + Nginx  
**Dockerizar** con multi-stage build  
**Usar Docker Compose** para stack completo  
**Desplegar en PaaS** (Render, Railway, Heroku)  
**Monitorear** con Spring Boot Actuator  
**Aplicar best practices** de producción

**Próximos pasos**:
- Implementar CI/CD con GitHub Actions
- Configurar monitoreo avanzado (Prometheus/Grafana)
- Implementar caché con Redis
- Optimizar queries con JPA
- Deploy en Kubernetes (avanzado)

---

# **12. Recursos Adicionales**

**Documentación oficial**:
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Nginx Documentation](https://nginx.org/en/docs/)

**Tutoriales**:
- [Deploying Spring Boot Apps](https://spring.io/guides/gs/spring-boot-docker/)
- [Spring Boot with Docker](https://spring.io/guides/topicals/spring-boot-docker/)

**Herramientas**:
- [Render](https://render.com/)
- [Railway](https://railway.app/)
- [Docker Hub](https://hub.docker.com/)

---

# **Resumen Final**

| Opción | Configuración | Portabilidad | Escalabilidad | Mejor para |
|--------|---------------|--------------|---------------|------------|
| **JAR + systemd** | Media | Baja | Media | Aprendizaje, VPS |
| **Docker Compose** | Media | Alta | Media | Producción simple |
| **PaaS (Render/Railway)** | Baja | Alta | Alta | Portafolio, MVP |
| **Kubernetes** | Muy alta | Máxima | Máxima | Empresarial |

