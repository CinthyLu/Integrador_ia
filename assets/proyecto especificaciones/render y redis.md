Viewed guia_despliegue_produccion.md:1-305
Listed directory academic-events-api
Viewed guia_paso_a_paso.md:1-200
Viewed Dockerfile:1-30
Viewed docker-compose.yml:1-39
Searched for "spring:"
Viewed application.yml:1-59
Searched for "SecurityConfig"
Viewed SecurityConfig.java:1-55
Viewed 00_create_database.sql:1-6
Viewed 01_schema_and_data.sql:1-100

Aquí tienes una guía paso a paso, detallada y adaptada a tu situación actual para realizar el despliegue del servidor (Base de Datos PostgreSQL, Redis y la API de Spring Boot) en **Render**, dejando el código listo para que tus compañeros implementen los pasos 10 y 11, y finalmente tú completes la protección de Swagger (Paso 12).

---

## 📋 Resumen del Plan de Despliegue
Dado que tus compañeros trabajarán en los pasos 10 y 11 sobre el código antes de terminar el Paso 12, el despliegue inicial se dividirá en:
1. **Crear y configurar PostgreSQL en Render** (y cargar el script de datos iniciales).
2. **Crear y configurar Redis en Render**.
3. **Crear el Web Service de la API de Spring Boot en Render** usando el [Dockerfile](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/Dockerfile) actual.
4. **Configurar las variables de entorno de producción** para enlazar los servicios.

---

### 🌐 REGLA DE ORO DE RENDER: La Región
> [!IMPORTANT]
> Para que la API, PostgreSQL y Redis se comuniquen de forma interna y gratuita, **todos los servicios deben crearse en la misma región** (por ejemplo, `Oregon (US West)` o la que elijas). Si los creas en regiones distintas, no podrán comunicarse a través de la red interna de Render.

---

## 🛠️ Paso a Paso para el Despliegue

### 1️⃣ Crear la Base de Datos PostgreSQL en Render
1. En tu panel de Render, haz clic en **New +** y selecciona **PostgreSQL**.
2. Completa los campos basándote en la captura de pantalla que compartiste:
   * **Name:** `academic-events-db`
   * **Database:** `academic_events_db` (Recomendado para coincidir con tus configuraciones y scripts).
   * **User:** `postgres` o `postgres_user`.
   * **Region:** Elige una (ej. `Oregon (US West)`).
   * **PostgreSQL Version:** Puedes dejar el valor por defecto (ej. `15` o `16` para mayor compatibilidad con tu entorno local).
3. Selecciona el Plan **Free** al final de la página.
4. Haz clic en **Create Database**.

Una vez creada, Render te mostrará la información de conexión. Toma nota de los siguientes valores:
* **Internal Database URL** (para uso interno de la API).
* **External Database URL** (para que te conectes desde tu máquina con DBeaver/PgAdmin).
* Las credenciales individuales: **Host**, **Database**, **Username**, **Password** y **Port**.

---

### 2️⃣ Conectar y Cargar los Datos en PostgreSQL
Como Render crea la base de datos automáticamente, **no necesitas ejecutar** [00_create_database.sql](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/00_create_database.sql). Solo debes cargar las tablas y los datos.

1. Abre tu gestor de base de datos preferido (DBeaver, PgAdmin, etc.).
2. Crea una nueva conexión de tipo **PostgreSQL** utilizando la **External Database URL** que te dio Render (o copia los datos individuales de Host externo, usuario y contraseña).
3. Una vez conectado a la base de datos de Render, abre un editor SQL y ejecuta el contenido completo del archivo [01_schema_and_data.sql](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/01_schema_and_data.sql).
4. Verifica que las tablas (`users`, `roles`, `events`, etc.) se hayan creado correctamente y contengan los datos iniciales.

---

### 3️⃣ Crear el Servicio Redis en Render
1. En el panel de Render, haz clic en **New +** y selecciona **Redis**.
2. Configura los campos básicos:
   * **Name:** `academic-events-redis`
   * **Region:** **Debe ser la misma** que elegiste para PostgreSQL (ej. `Oregon (US West)`).
   * **Plan:** Free.
3. Haz clic en **Create Redis**.
4. Cuando esté listo, busca la sección de conexiones y copia la **Internal Connection URI** (se ve como `redis://red-xxxxxxxxxx:6379`). 
   * *El host interno de tu Redis será la parte del subdominio de esa URL (ej. `red-xxxxxxxxxx`). El puerto por defecto es `6379`.*

---

### 4️⃣ Desplegar la API de Spring Boot en Render
Como tu [Dockerfile](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/Dockerfile) ya está creado en la raíz del proyecto, Render compilará tu aplicación automáticamente usando Docker.

1. En el panel de Render, haz clic en **New +** y selecciona **Web Service**.
2. Conecta tu repositorio de GitHub donde se encuentra el proyecto.
3. Configura el servicio web:
   * **Name:** `academic-events-api`
   * **Region:** **Debe ser la misma** que los otros servicios (ej. `Oregon (US West)`).
   * **Branch:** Tu rama principal (ej. `main` o `master`).
   * **Runtime:** Selecciona **Docker** (Render detectará el `Dockerfile` de tu raíz de forma automática).
   * **Plan:** Free.
4. Despliega la pestaña **Advanced** para agregar las **Environment Variables (Variables de Entorno)**.

---

### 🔑 Variables de Entorno a Configurar (Paso Crítico)
Configura las siguientes variables clave en la sección de Variables de Entorno de tu Web Service:

| Variable | Valor / Origen | Explicación |
| :--- | :--- | :--- |
| `DB_HOST` | *(Host **interno** de PostgreSQL en Render)* | Evita usar el host con `.render.com`, usa el nombre de host interno (ej. `dpg-xxxxxxxxx-a`). |
| `DB_PORT` | `5432` | Puerto por defecto de PostgreSQL. |
| `DB_NAME` | `academic_events_db` | Nombre de la base de datos que definiste. |
| `DB_USER` | *(Tu usuario de BD en Render)* | Generalmente `postgres` o el que hayas asignado. |
| `DB_PASSWORD` | *(Tu contraseña de BD en Render)* | Contraseña autogenerada por Render. |
| `REDIS_HOST` | *(Host **interno** de Redis en Render)* | Extraído del Internal Connection URI (ej. `red-xxxxxxxxxx`). |
| `REDIS_PORT` | `6379` | Puerto por defecto de Redis. |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | URL del frontend (puedes agregar más separadas por coma). |
| `JAVA_TOOL_OPTIONS`| `-XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Guayaquil` | **Esencial:** Limita el uso de memoria RAM para que no tumbe el contenedor gratuito de Render y define la zona horaria ecuatoriana en la JVM. |

5. Haz clic en **Create Web Service**.

---

## 🔮 ¿Qué tendrás que hacer cuando tus compañeros terminen los Pasos 10 y 11?

Cuando tus compañeros terminen sus respectivas partes y se fusionen los cambios al repositorio, tendrás que actualizar la infraestructura y el código para **finalizar el Paso 12**:

1. **Modificar el código de seguridad:** Deberás actualizar tu [SecurityConfig.java](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/java/ec/edu/ups/icc/events/core/security/SecurityConfig.java) y [application.yml](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/src/main/resources/application.yml) para aplicar **Basic Auth** sobre las rutas de Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) siguiendo la sección **Paso 1** de la [guía de despliegue](file:///c:/Users/MSI/Desktop/PPW/Backend/SPRINGBOOT/academic-events-api/guia_despliegue_produccion.md).
2. **Agregar nuevas variables de entorno en Render:** Una vez subido ese cambio a producción, tendrás que ingresar a la configuración de tu Web Service en Render y agregar:
   * `SWAGGER_USER` (ej. `evaluador`)
   * `SWAGGER_PASSWORD` (ej. `SeguraClaveDocente2026`)
3. **Subir los cambios a GitHub:** Render detectará el commit automáticamente, reconstruirá la imagen Docker y aplicará la seguridad a Swagger de inmediato.


postgresql://postgres_user:IYgXb0rt1SvZ3mvA32jZQ3QRNgmHf0Ed@dpg-d9kb8cijobas738dqhqg-a.oregon-postgres.render.com
academic_events_db_vi7p  

postgresql://postgres_user:IYgXb0rt1SvZ3mvA32jZQ3QRNgmHf0Ed@dpg-d9kb8cijobas738dqhqg-a.oregon-postgres.render.com/academic_events_db_vi7p