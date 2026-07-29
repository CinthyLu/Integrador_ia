# PrograHOSTión y Plataformas Web

# **Práctica 16: Despliegue portable de Spring Boot con Docker y Nginx en Ubuntu Server**

## **Objetivo**

Preparar una API Spring Boot para que la misma imagen Docker pueda ejecutarse en desarrollo, en un Ubuntu Server y posteriormente en una plataforma como Render, sin modificar el código ni reconstruir una imagen por cada ambiente.

La configuración específica de cada entorno se suministrará mediante:

- Profiles de Spring Boot.
- Variables de entorno.
- Archivos `.env` que no se versionan.
- Parámetros entregados al ejecutar el contenedor.

La práctica no utiliza Docker Compose. Cada imagen, red y contenedor se administrará mediante comandos Docker.

## **Arquitectura de la práctica**

```text
HOST anfitrión
├── Código fuente e IDE
├── PostgreSQL de desarrollo: 192.168.56.1:5432
└── Red Host-Only de VirtualBox
             │
             │ 192.168.56.0/24
             ▼
Ubuntu Server: 192.168.56.2
├── Docker Engine
├── Contenedor Spring Boot
└── Contenedor Nginx
```

Las IPs podrian cambiar según la configuración de VirtualBox. La práctica asume que la HOST anfitriona tiene PostgreSQL y que el Ubuntu Server es un invitado con Docker.

Flujo HTTP final:

```text
HOST → http://192.168.56.2/api/... → Nginx:80 → fundamentos-api:8080 → PostgreSQL
```


![alt text](assets/16-n1.png)
---

# **Preparación y despliegue portable con Docker**

## **1. Configuración por ambientes**

La configuración se dividirá en tres archivos:

```text
src/main/resources/
├── application.yml
├── application-dev.yml
└── application-prod.yml
```

`application.yml` contiene la configuración común. `application-dev.yml` permite valores predeterminados para desarrollo. `application-prod.yml` exige que los valores propios del entorno se entreguen mediante variables de entorno.



### **1.1. Desarrollo: `application-dev.yml`**

Crear:

```text
src/main/resources/application-dev.yml
```

Contenido:

```yaml
server:
  port: ${PORT:8080}
  servlet:
    context-path: /api

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/devdb}
    username: ${DB_USERNAME:ups}
    password: ${DB_PASSWORD:ups123}

  jpa:
    show-sql: ${JPA_SHOW_SQL:true}
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:update}

logging:
  level:
    root: INFO
    ec.edu.ups.icc.fundamentos01: DEBUG
    org.hibernate.SQL: DEBUG

jwt:
  secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}
  expiration: ${JWT_EXPIRATION:1800000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}
  issuer: fundamentos01-api
  header: Authorization
  prefix: "Bearer "
```

La sintaxis:

```yaml
${VARIABLE:valor-predeterminado}
```

indica que Spring Boot utiliza la variable de entorno cuando existe. Si no existe, utiliza el valor colocado después de los dos puntos.

Ejemplo:

```yaml
url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/devdb}
```

Comportamiento:

```text
DATABASE_URL definida     → usa el valor de la variable
DATABASE_URL no definida  → usa jdbc:postgresql://localhost:5432/devdb
```

### **1.3. Producción: `application-prod.yml`**

Crear:

```text
src/main/resources/application-prod.yml
```

Contenido:

```yaml
server:
    port: ${PORT}
    servlet:
        context-path: /api

spring:
    datasource:
        url: ${DATABASE_URL}
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
        hikari:
            maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
            minimum-idle: ${DB_POOL_MIN_IDLE:2}
            connection-timeout: ${DB_CONNECTION_TIMEOUT:20000}

    jpa:
        show-sql: ${JPA_SHOW_SQL}
        hibernate:
            ddl-auto: ${JPA_DDL_AUTO}

logging:
    level:
        root: ${ROOT_LOG_LEVEL:WARN}
        ec.edu.ups.icc.fundamentos01: ${APP_LOG_LEVEL:INFO}

jwt:
    secret: ${JWT_SECRET}
    expiration: ${JWT_EXPIRATION}
    refresh-expiration: ${JWT_REFRESH_EXPIRATION}
    issuer: ${JWT_ISSUER}
    header: Authorization
    prefix: "Bearer "

```

En producción no se incluyen valores predeterminados para:

- URL de PostgreSQL.
- Usuario y contraseña.
- Secreto JWT.
- Duración de los tokens.
- Profile activo.
- Puerto de ejecución.

Si una variable obligatoria no está definida, la aplicación debe fallar durante el arranque. Esto evita desplegar una instancia con credenciales de desarrollo.

### **1.4. Actuator y configuración del JAR**

En `build.gradle.kts`, verificar la dependencia:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

Configurar Java 21 y un nombre determinista para el JAR:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}
```

El artefacto generado será:

```text
build/libs/app.jar
```

Si Spring Security protege todos los endpoints, permitir públicamente el health check dentro de la configuración de seguridad:

```java
.requestMatchers("/actuator/health").permitAll()
```

El matcher se define sin `/api` porque `/api` es el context path del servlet.

---

## **2. Variables de entorno para desarrollo local**

### **2.1. Crear `.env.dev`**

En la raíz del proyecto, crear:

```text
.env.dev
```

Contenido:

```dotenv
SPRING_PROFILES_ACTIVE=dev
PORT=8080

DATABASE_URL=jdbc:postgresql://localhost:5432/devdb
DB_USERNAME=ups
DB_PASSWORD=ups123

JPA_DDL_AUTO=update
JPA_SHOW_SQL=true

JWT_SECRET=mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm
JWT_EXPIRATION=1800000
JWT_REFRESH_EXPIRATION=604800000
JWT_ISSUER=fundamentos01-api

ROOT_LOG_LEVEL=INFO
APP_LOG_LEVEL=DEBUG
```

El archivo `.env.dev` no debe subirse al repositorio.

Agregar a `.gitignore`:

```gitignore
.env
.env.*
!.env.example
```

Se puede versionar una plantilla sin secretos:

```text
.env.example
```

### **2.2. Importante: Spring Boot no carga `.env` automáticamente**

El archivo `.env.dev` es un archivo auxiliar. Antes de ejecutar Gradle o Java, sus valores deben cargarse en el proceso del sistema operativo, configurarse en el IDE o entregarse mediante `docker run --env-file`.

### **2.3. Ejecutar con Gradle desde terminal**

En HOSTOS o Linux:

```bash
set -a
source .env.dev
set +a

./gradlew bootRun
```

Verificar el profile activo en los logs:

```text
The following 1 profile is active: "dev"
```

También puede ejecutarse en una sola línea:

```bash
set -a && source .env.dev && set +a && ./gradlew bootRun
```

### **2.4. Construir y ejecutar el JAR con variables de entorno**

```bash
set -a
source .env.dev
set +a

./gradlew clean bootJar -x test
java -jar build/libs/app.jar
```

Las variables cargadas permanecen disponibles para el proceso `java`.

### **2.5. Script local de ejecución**

Crear:

```text
scripts/run-dev.sh
```

Contenido:

```bash
#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

set -a
source .env.dev
set +a

./gradlew bootRun
```

Dar permisos:

```bash
chmod +x scripts/run-dev.sh
```

Ejecutar:

```bash
./scripts/run-dev.sh
```

---

## **3. Configuración de variables en los IDE**

### **3.1. IntelliJ IDEA**

1. Abrir `Run`.
2. Seleccionar `Edit Configurations`.
3. Crear o seleccionar la configuración de Spring Boot.
4. Verificar la clase principal:

```text
ec.edu.ups.icc.fundamentos01.Fundamentos01Application
```

5. En `Environment variables`, agregar:

```text
SPRING_PROFILES_ACTIVE=dev
PORT=8080
DATABASE_URL=jdbc:postgresql://localhost:5432/devdb
DB_USERNAME=ups
DB_PASSWORD=ups123
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true
JWT_SECRET=mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm
JWT_EXPIRATION=1800000
JWT_REFRESH_EXPIRATION=604800000
JWT_ISSUER=fundamentos01-api
ROOT_LOG_LEVEL=INFO
APP_LOG_LEVEL=DEBUG
```

6. Guardar y ejecutar.

IntelliJ no necesita modificar `application.yml`. Las variables configuradas en la ejecución sobrescriben la configuración correspondiente.

### **3.2. Visual Studio Code**

Crear:

```text
.vscode/launch.json
```

Contenido:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Fundamentos01 - dev",
      "request": "launch",
      "mainClass": "ec.edu.ups.icc.fundamentos01.Fundamentos01Application",
      "projectName": "fundamentos01",
      "envFile": "${workspaceFolder}/.env.dev"
    }
  ]
}
```

Si la extensión Java instalada no reconoce `envFile`, ejecutar desde la terminal integrada:

```bash
set -a
source .env.dev
set +a
./gradlew bootRun
```

Verificar que VS Code herede las variables de la terminal integrada.

Tambien es posible generar desde VsCode `launch.json`, seleccionar la clase principal y agregar manualmente las variables de entorno en `env`:

```json
            "envFile": "${workspaceFolder}/.env.dev"
```

---

## **4. Verificación local antes de Docker**

### **4.1. Comprobar PostgreSQL**

```bash
psql -h localhost -p 5432 -U ups -d devdb
```

Dentro de `psql`:

```sql
SELECT current_database(), current_user;
```

### **4.2. Ejecutar la aplicación**

```bash
set -a
source .env.dev
set +a
./gradlew bootRun
```

### **4.3. Probar Actuator**

```bash
curl http://localhost:8080/api/actuator/health
```

Respuesta esperada:

```json
{"status":"UP"}
```

### **4.4. Probar un endpoint de la API**

Ejemplo:

```bash
curl http://localhost:8080/api/products
```

La ruta concreta depende de los controladores implementados en el proyecto.

---

## **5. Preparar el proyecto en Ubuntu Server**

Se puede realizar la configuración de Ubuntu Server en una máquina virtual, en un contenedor LXC o en un servidor físico. 

O conectarse al servidor remoto mediante SSH. Ejemplo: 

```bash
ssh pablo@192.168.56.2
```

Instalar Docker Engine y Docker CLI según la documentación oficial de Docker para Ubuntu Server.

```bash
sudo apt install -y docker.io
```

Configurar el usuario para ejecutar Docker sin `sudo`:

```bash
sudo usermod -aG docker $USER
```

### Crear Red virtual en Virtual Box

Para que la máquina virtual Ubuntu Server pueda conectarse a la base de datos PostgreSQL instalada en la HOST anfitriona, se debe crear una red Host-Only en VirtualBox. 

```bash
VBoxManage hostonlynet add \
  --name=HostOnly \
  --netmask=255.255.255.0 \
  --lower-ip=192.168.56.1 \
  --upper-ip=192.168.56.254 \
  --enable
```


La maquina virtual Ubuntu Server debe estar conectada a esta red Host-Only. Tendra 2 adaptadores de red: uno NAT para salir a Internet y otro Host-Only para conectarse a la HOST anfitriona.

En la configuración de red de la máquina virtual, debera seleccionarse el adaptador Host-Only.

![alt text](assets/16-n5.png)


### **5.1. Obtener el código fuente**

Opción recomendada:

```bash
git clone https://github.com/USUARIO/REPOSITORIO.git
cd REPOSITORIO
```

Si el repositorio ya existe:

```bash
cd REPOSITORIO
git pull
```


### **5.2. Crear variables para Ubuntu Server**

Crear en Ubuntu:

```bash
nano .env.ubuntu
```

Para conectarse al PostgreSQL instalado en la HOST anfitriona:

```dotenv
SPRING_PROFILES_ACTIVE=prod
PORT=8080

DATABASE_URL=jdbc:postgresql://192.168.56.1:5432/devdb
DB_USERNAME=ups
DB_PASSWORD=ups123

JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false

JWT_SECRET=ReplaceWithASecureRandomSecretOfAtLeast256BitsForTheLab
JWT_EXPIRATION=1800000
JWT_REFRESH_EXPIRATION=604800000
JWT_ISSUER=fundamentos01-api

DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT=20000

ROOT_LOG_LEVEL=WARN
APP_LOG_LEVEL=INFO
```

Proteger el archivo:

```bash
chmod 600 .env.ubuntu
```

No copiar `.env.ubuntu` dentro de la imagen y no subirlo a Git.

---

## **6. Dockerfile Multi-Stage con Spring Boot por capas**


El Dockerfile utilizará dos etapas:

1. `builder`: compila con JDK 21 y Gradle Wrapper.
2. `runtime`: ejecuta con JRE 21 y usuario no-root.

El JAR se descomprime para separar dependencias y clases. De esta forma, cuando cambia únicamente el código, Docker puede reutilizar la capa que contiene las dependencias.

Las credenciales no se declaran con `ENV` ni `ARG`. Se entregan al crear el contenedor mediante `--env-file`.

### **6.1. Crear `Dockerfile`**

```dockerfile
# syntax=docker/dockerfile:1.7

# ============================================
# ETAPA 1: BUILD
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace/app

# Copiar primero Gradle Wrapper y configuración
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew

# Copiar el código fuente
COPY src ./src

# Compilar usando caché persistente de BuildKit
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# Extraer el JAR para separar dependencias y clases
RUN mkdir -p build/dependency \
    && cd build/dependency \
    && jar -xf ../libs/app.jar


# ============================================
# ETAPA 2: RUNTIME
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# curl se utiliza exclusivamente para el health check
RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring

ARG DEPENDENCY=/workspace/app/build/dependency

# Dependencias externas: cambian con menor frecuencia
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/lib /app/lib

# Metadatos del JAR
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/META-INF /app/META-INF

# Clases y recursos de la aplicación
COPY --from=builder --chown=spring:spring \
    ${DEPENDENCY}/BOOT-INF/classes /app

USER spring:spring

EXPOSE 8080

# Configuración no sensible y reemplazable en runtime
ENV TZ=America/Guayaquil

HEALTHCHECK --interval=30s \
    --timeout=5s \
    --start-period=60s \
    --retries=3 \
    CMD curl --fail --silent --show-error \
    http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Xms256m", \
    "-Xmx512m", \
    "-cp", \
    "/app:/app/lib/*", \
    "ec.edu.ups.icc.fundamentos01.Fundamentos01Application"]
```

### **6.2. Variables de entorno y Dockerfile**

El Dockerfile no debe contener valores sensibles ni específicos de un ambiente. La configuración se entrega al ejecutar el contenedor mediante `--env-file`.

La imagen queda independiente del ambiente. La configuración se entrega al ejecutar:

```bash
docker run --env-file .env.ubuntu ...
```

La misma imagen puede utilizar:

```text
.env.dev       → desarrollo
.env.ubuntu    → Ubuntu Server
variables PaaS → Render
```

### **6.4. Verificar la clase principal**

Comprobar:

```text
src/main/java/ec/edu/ups/icc/fundamentos01/Fundamentos01Application.java
```

Si el paquete o la clase principal tienen otro nombre, actualizar la última línea del `ENTRYPOINT`.

---

## **7. Archivo `.dockerignore`**

Crear en la raíz:

```text
.dockerignore
```

Contenido:

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

# Variables y secretos
.env
.env.*

# Logs
*.log
logs/

# Temporales
*.tmp
*.swp
tmp/
temp/

# Documentación y archivos no requeridos en el build
README.md

# Configuraciones locales
compose.override.yaml
docker-compose.override.yml
```

No ignorar:

```text
gradlew
gradle/
build.gradle.kts
settings.gradle.kts
gradle.properties
src/
```

---

## **8. Construir y probar la imagen en Ubuntu Server**

Si se esta usando Ubuntu Server, primero actualizar e instalar docker buildx:

```bash
sudo apt update
sudo apt install -y docker-buildx
```


### **8.1. Construir**

```bash
docker buildx build \
  --pull \
  --progress=plain \
  --load \
  -t fundamentos-api:1.0 \
  .
```

Verificar:

```bash
docker images fundamentos-api
```

### **8.2. Crear la red privada**

```bash
docker network create app-network
```

Si ya existe, Docker mostrará un error de nombre duplicado. Puede verificarse con:

```bash
docker network ls
```

### **8.3. Primera ejecución con puerto directo**

Esta ejecución publica temporalmente el puerto 8080 para comprobar la API antes de agregar Nginx:

```bash
docker run -d \
  --name fundamentos-api \
  --network app-network \
  --env-file .env.ubuntu \
  -p 8080:8080 \
  fundamentos-api:1.0
```

Ver logs:

```bash
docker logs -f fundamentos-api
```

Verificar estado:

```bash
docker ps
```

Verificar health check:

```bash
docker inspect \
  --format='{{json .State.Health}}' \
  fundamentos-api
```

Probar desde Ubuntu:

```bash
curl http://localhost:8080/api/actuator/health
```

Probar desde la HOST:

```bash
curl http://192.168.56.2:8080/api/actuator/health
```

---

## **9. Conectar el contenedor a PostgreSQL**

### **9.1. Configuración de base de datos**

Si al problar la API desde Ubuntu Server se obtiene un error de conexión a PostgreSQL, es necesario configurar la base de datos para que el contenedor pueda conectarse.

La ruta principal utilizará PostgreSQL instalado en HOST anfitrion.

Ventajas qeu aprendemos :

- La base de datos está fuera del contenedor de la aplicación.
- Se demuestra que la imagen depende de una URL configurable y no de `localhost`.
- El mismo patrón se utiliza al conectarse a una base administrada en la nube.
- No se requiere reconstruir la imagen para cambiar de servidor PostgreSQL.

Con la red Host-Only configurada:

```text
HOST:           192.168.56.1
Ubuntu Server: 192.168.56.2
```

La URL utilizada por el contenedor será:

```text
jdbc:postgresql://192.168.56.1:5432/devdb
```

Si PostgreSQL ya está instalado, esta configuración suele requerir entre 10 y 20 minutos. El punto que normalmente requiere ajustes es permitir que PostgreSQL escuche en la interfaz Host-Only.

### **9.2. Configurar PostgreSQL en la HOST**

Desde la HOST, localizar los archivos de configuración:

```bash
psql -U ups -d devdb -c "SHOW config_file;"
psql -U ups -d devdb -c "SHOW hba_file;"
```

Editar `postgresql.conf` y configurar:

```conf
listen_addresses = 'localhost,192.168.56.1'
port = 5432
```

Editar `pg_hba.conf` y agregar:

```conf
host    devdb    ups    192.168.56.0/24    scram-sha-256
```

La regla permite al usuario `ups` conectarse a `devdb` desde la red privada Host-Only.



### **9.3. Probar desde Ubuntu Server**

Primero verificar el puerto:

```bash
nc -vz 192.168.56.1 5432
```

Si `nc` no está instalado:

```bash
sudo apt update
sudo apt install -y netcat-openbsd
```

Prueba completa con cliente PostgreSQL:

```bash
sudo apt install -y postgresql-client

PGPASSWORD=ups123 psql \
  -h 192.168.56.1 \
  -p 5432 \
  -U ups \
  -d devdb \
  -c 'SELECT current_database(), current_user;'
```

### **9.4. Probar desde un contenedor Docker**

La prueba desde Ubuntu no garantiza por sí sola que un contenedor tenga conectividad. Verificar desde Docker:

```bash
docker run --rm \
  --network app-network \
  -e PGPASSWORD=ups123 \
  postgres:17-alpine \
  psql \
    -h 192.168.56.1 \
    -p 5432 \
    -U ups \
    -d devdb \
    -c 'SELECT 1;'
```

Resultado esperado:

```text
 ?column?
----------
        1
```

### **9.5. Verificar la aplicación**

Si el contenedor de la API no arrancó antes de configurar PostgreSQL:

```bash
docker restart fundamentos-api
```

Consultar logs:

```bash
docker logs -f fundamentos-api
```

Errores frecuentes:

```text
Connection refused
```

PostgreSQL no escucha en `192.168.56.1:5432` o el servicio está detenido.

```text
no pg_hba.conf entry
```

Falta la regla para `192.168.56.0/24`.

```text
password authentication failed
```

Usuario o contraseña incorrectos.

```text
database "devdb" does not exist
```

La base indicada en `DATABASE_URL` no existe.

### **9.6. Alternativa de contingencia: PostgreSQL en Docker**

Si la configuración del PostgreSQL de la HOST consume demasiado tiempo, levantar PostgreSQL en Ubuntu Server sin Docker Compose:

```bash
docker volume create postgres-data
```

```bash
docker run -d \
  --name postgres \
  --network app-network \
  -e POSTGRES_DB=devdb \
  -e POSTGRES_USER=ups \
  -e POSTGRES_PASSWORD=ups123 \
  -v postgres-data:/var/lib/postgresql/data \
  postgres:17-alpine
```

Cambiar en `.env.ubuntu`:

```dotenv
DATABASE_URL=jdbc:postgresql://postgres:5432/devdb
```

Recrear la API para cargar el nuevo valor:

```bash
docker rm -f fundamentos-api

docker run -d \
  --name fundamentos-api \
  --network app-network \
  --env-file .env.ubuntu \
  -p 8080:8080 \
  fundamentos-api:1.0
```

En una red Docker creada por el usuario, `postgres` se resuelve mediante DNS interno al contenedor con ese nombre.

---

## **10. Levantar Nginx en Docker**

Nginx será el único servicio publicado en el puerto 80. La API permanecerá accesible únicamente dentro de `app-network`.

```text
Cliente → Ubuntu:80 → Nginx → fundamentos-api:8080
```

### **10.1. Descargar la imagen**

```bash
docker pull nginx:alpine
```

Verificar:

```bash
docker images nginx
```

### **10.3. Ejecutar Nginx inicialmente**

```bash
docker run -d \
  --name nginx \
  --network app-network \
  -p 80:80 \
  nginx:alpine
```

Comprobar:

```bash
curl http://localhost
```

O desde la HOST:

```bash
curl http://192.168.56.2
```
![alt text](assets/16-n10-3.png)

### **10.4. Inspeccionar el contenedor**

```bash
docker exec -it nginx sh
```

Dentro del contenedor:

```sh
cat /etc/nginx/conf.d/default.conf
nginx -v
exit
```

Modificar archivos directamente dentro del contenedor sirve para exploración, pero los cambios se pierden cuando el contenedor se elimina. La configuración definitiva se alHOSTenará en Ubuntu Server y se montará como solo lectura.

### **10.5. Crear configuración persistente en Ubuntu**

```bash
mkdir -p nginx
nano nginx/default.conf
```

Contenido:

```nginx
upstream spring_backend {
    server fundamentos-api:8080;
    keepalive 16;
}

server {
    listen 80;
    server_name _;

    client_max_body_size 10M;

    location = / {
        default_type text/plain;
        return 200 "Nginx activo\n";
    }

    location = /api/actuator/health {
        proxy_pass http://spring_backend;
        access_log off;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
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

`proxy_pass` no incluye una barra después del upstream:

```nginx
proxy_pass http://spring_backend;
```

De esta manera, la ruta original se conserva:

```text
/api/products → /api/products
```

### **10.6. Retirar la publicación directa de Spring Boot**

Eliminar la API creada con `-p 8080:8080`:

```bash
docker rm -f fundamentos-api
```

Crearla nuevamente sin publicar el puerto:

```bash
docker run -d \
  --name fundamentos-api \
  --network app-network \
  --env-file .env.ubuntu \
  fundamentos-api:1.0
```

El puerto 8080 permanece visible para los contenedores de `app-network`, pero no se publica en Ubuntu.

### **10.7. Recrear Nginx con la configuración persistente**

```bash
docker rm -f nginx
```

```bash
docker run -d \
  --name nginx \
  --network app-network \
  -p 80:80 \
  -v "$(pwd)/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:alpine
```

### **10.8. Validar Nginx**

```bash
docker exec nginx nginx -t
```

Salida esperada:

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

![alt text](assets/16-n10-8.png)

Recargar después de modificar `default.conf`:

```bash
docker exec nginx nginx -s reload
```

### **10.9. Verificar resolución DNS interna**

```bash
docker exec nginx getent hosts fundamentos-api
```

Inspeccionar la red:

```bash
docker network inspect app-network
```

Deben aparecer:

```text
fundamentos-api
nginx
```

![alt text](assets/16-n10-9.png)

---

## **11. Pruebas completas**

### **11.1. Desde Ubuntu Server**

Comprobar Nginx:

```bash
curl http://localhost
```

Comprobar Spring Boot a través de Nginx:

```bash
curl http://localhost/api/actuator/health
```

Comprobar un endpoint:

```bash
curl http://localhost/api/products
```

### **11.2. Desde la HOST anfitriona**

```bash
curl http://192.168.56.2
```

```bash
curl http://192.168.56.2/api/actuator/health
```

```bash
curl http://192.168.56.2/api/products
```

También puede utilizarse el navegador:

```text
http://192.168.56.2/api/actuator/health
```

### **11.3. Flujo validado**

```text
HOST
 │
 │ HTTP 192.168.56.2:80
 ▼
Ubuntu Server
 │
 ▼
Nginx
 │
 │ http://fundamentos-api:8080
 ▼
Spring Boot
 │
 │ jdbc:postgresql://192.168.56.1:5432/devdb
 ▼
PostgreSQL en la HOST
```

### **11.4. Ver logs**

Spring Boot:

```bash
docker logs -f fundamentos-api
```

Nginx:

```bash
docker logs -f nginx
```

Últimas 100 líneas:

```bash
docker logs --tail 100 fundamentos-api
docker logs --tail 100 nginx
```

### **11.5. Diagnóstico de `502 Bad Gateway`**

```bash
docker ps
```

```bash
docker logs fundamentos-api
```

```bash
docker network inspect app-network
```

```bash
docker exec nginx getent hosts fundamentos-api
```

```bash
docker exec nginx wget -qO- http://fundamentos-api:8080/api/actuator/health
```

Causas frecuentes:

- La API está detenida.
- La API no pertenece a `app-network`.
- El nombre configurado en Nginx no coincide con `--name fundamentos-api`.
- Spring Boot escucha en un puerto diferente de 8080.
- La aplicación no inició por un error de PostgreSQL.

---

## **12. Reconstrucción y actualización de la aplicación**

Después de modificar código:

```bash
git pull
```

Reconstruir:

```bash
docker build --pull -t fundamentos-api:1.1 .
```

Reemplazar únicamente la API:

```bash
docker rm -f fundamentos-api
```

```bash
docker run -d \
  --name fundamentos-api \
  --network app-network \
  --env-file .env.ubuntu \
  fundamentos-api:1.1
```

Nginx no necesita reconstruirse porque continúa resolviendo el mismo nombre de contenedor:

```text
fundamentos-api
```

Verificar:

```bash
curl http://localhost/api/actuator/health
```

---

## **13. Detener y limpiar el entorno**

Detener:

```bash
docker stop nginx fundamentos-api
```

Eliminar contenedores:

```bash
docker rm nginx fundamentos-api
```

Eliminar red:

```bash
docker network rm app-network
```

Eliminar imágenes de la aplicación:

```bash
docker image rm fundamentos-api:1.0 fundamentos-api:1.1
```

No eliminar el volumen `postgres-data` si se utilizó la alternativa de PostgreSQL en Docker y se desea conservar la inforHOSTión.

---

## **14. Llevar la misma imagen a Render**

### **14.1. Principio de portabilidad**

No es necesario crear otro Dockerfile para Render.

La misma imagen funciona porque:

- El profile se selecciona con `SPRING_PROFILES_ACTIVE`.
- La base se selecciona con `DATABASE_URL`.
- Las credenciales se reciben mediante variables.
- El puerto se recibe mediante `PORT`.
- El secreto JWT no está dentro de la imagen.

En Render no es necesario desplegar el contenedor Nginx para una API única. El servicio web de Render ya proporciona el punto de entrada HTTP/HTTPS, enrutamiento externo y terminación TLS. Nginx puede mantenerse en Ubuntu Server para la práctica local.

### **14.2. Preparar repositorio**

Verificar que estén versionados:

```text
Dockerfile
.dockerignore
build.gradle.kts
gradlew
gradle/
src/
```

Verificar que no estén versionados:

```text
.env.dev
.env.ubuntu
.env.production
```

Subir cambios:

```bash
git add .
git commit -m "feat: preparar despliegue Docker portable"
git push origin main
```

### **14.3. Crear servicio Docker en Render**

1. Crear un nuevo `Web Service`.
2. Conectar el repositorio.
3. Seleccionar runtime Docker.
4. Usar el `Dockerfile` ubicado en la raíz.
5. Configurar el health check:

```text
/api/actuator/health
```

6. Configurar variables:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://HOST_INTERNO:5432/NOMBRE_BD
DB_USERNAME=USUARIO_BD
DB_PASSWORD=CONTRASENA_BD
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false
JWT_SECRET=SECRETO_ALEATORIO_DE_AL_MENOS_256_BITS
JWT_EXPIRATION=1800000
JWT_REFRESH_EXPIRATION=604800000
JWT_ISSUER=fundamentos01-api
ROOT_LOG_LEVEL=WARN
APP_LOG_LEVEL=INFO
```

Render entrega la variable `PORT` al servicio. `application-prod.yml` la utiliza mediante:

```yaml
server:
  port: ${PORT}
```

### **14.4. PostgreSQL en Render**

Crear una instancia PostgreSQL y utilizar sus datos internos de conexión.

Spring JDBC espera una URL con formato:

```text
jdbc:postgresql://host:5432/database
```

Si Render muestra una URL con formato:

```text
postgresql://usuario:password@host:5432/database
```

no copiarla directamente como `DATABASE_URL` JDBC. Construir el valor:

```text
DATABASE_URL=jdbc:postgresql://host:5432/database
DB_USERNAME=usuario
DB_PASSWORD=password
```

Para servicios de Render en la misma región, utilizar el host interno de PostgreSQL.

### **14.5. Blueprint opcional: `render.yaml`**

La base de datos puede crearse manualmente y las credenciales pueden configurarse desde el Dashboard. Un Blueprint mínimo para la API es:

```yaml
services:
  - type: web
    name: fundamentos-api
    runtime: docker
    dockerfilePath: ./Dockerfile
    dockerContext: .
    healthCheckPath: /api/actuator/health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        sync: false
      - key: DB_USERNAME
        sync: false
      - key: DB_PASSWORD
        sync: false
      - key: JPA_DDL_AUTO
        value: validate
      - key: JPA_SHOW_SQL
        value: "false"
      - key: JWT_SECRET
        generateValue: true
      - key: JWT_EXPIRATION
        value: "1800000"
      - key: JWT_REFRESH_EXPIRATION
        value: "604800000"
      - key: JWT_ISSUER
        value: fundamentos01-api
      - key: ROOT_LOG_LEVEL
        value: WARN
      - key: APP_LOG_LEVEL
        value: INFO
```

Las variables con `sync: false` deben ingresarse en el Dashboard durante la creación inicial.

### **14.6. Escalamiento**

La imagen no cambia al aumentar el número de instancias. Para escalar correctamente:

- La API debe ser stateless.
- Los tokens JWT no deben depender de memoria local.
- Los archivos persistentes no deben guardarse dentro del contenedor.
- Todas las instancias deben utilizar la misma base PostgreSQL.
- Las migraciones de base deben administrarse de forma controlada.
- `ddl-auto=validate` debe reemplazar a `update` en un entorno real.

El flujo en Render será:

```text
Cliente
  │
  ▼
Proxy administrado de Render
  │
  ├── Instancia API 1
  ├── Instancia API 2
  └── Instancia API N
          │
          ▼
    PostgreSQL administrado
```

---

## **15. Entregables de la práctica**

Generar una nueva entrada en README

1. Captura de docker ps de Ubuntu Server mostrando ambos contenedores en ejecución.
2. Captura de curl de `/api/actuator/health` desde Ubuntu Server.
3. Captura de curl de `/api/actuator/health` desde la máquina anfitriona.
4. Explicación de la conexión a PostgreSQL externo o evidencia de fallback utilizado.
6. Captura consumo de login desde la máquina anfitriona con Bruno o Postman.



---

## **16. Resultado esperado**

Al finalizar:

```text
Nginx               → publicado en Ubuntu:80
Spring Boot          → privado en app-network:8080
PostgreSQL           → externo y definido por DATABASE_URL
Configuración        → variables de entorno
Secretos             → fuera del código y de la imagen
Imagen               → reutilizable en Ubuntu Server y Render
Docker Compose       → no utilizado
```
