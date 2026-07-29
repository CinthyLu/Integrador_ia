# Registro de Errores y Soluciones - StreamML

Este documento registra los errores encontrados durante el proceso de instalación y puesta en marcha del proyecto, junto con sus respectivas soluciones y las correcciones aplicadas.

---

## Índice
1. [Error 1: STREAMML_TOKEN_SECRET/STREAMML_MEDIA_AUTH_SECRET menor a 32 caracteres](#error-1-streamml_token_secretstreamml_media_auth_secret-menor-a-32-caracteres)
2. [Error 2: Entorno de ejecución de scikit-learn/pandas incompatible en contenedor Docker por caché de capas](#error-2-entorno-de-ejecución-de-scikit-learnpandas-incompatible-en-contenedor-docker-por-caché-de-capas)
3. [Error 3: Fallo en el script de arranque del asistente local por restricción estricta de Python 3.11](#error-3-fallo-en-el-script-de-arranque-del-asistente-local-por-restricción-estricta-de-python-311)

---

## Error 1: STREAMML_TOKEN_SECRET/STREAMML_MEDIA_AUTH_SECRET menor a 32 caracteres

### Descripción del Error
Al intentar levantar la API backend (tanto en Docker como localmente usando uvicorn), el inicio fallaba inmediatamente lanzando el siguiente error:
```text
RuntimeError: STREAMML_TOKEN_SECRET debe contener al menos 32 caracteres.
```
Esto ocurría porque el archivo `.env` local contenía las configuraciones iniciales heredadas de CI con valores como `dummy_token_secret_for_ci` (24 caracteres). La validación en `apps/api/config.py` requiere de forma estricta un mínimo de 32 caracteres para ambos secretos de token y de autenticación de medios.

### Solución
Se modificó el archivo `.env` local para adaptarlo al desarrollo en local:
*   Se cambió `STREAMML_ENVIRONMENT` a `development`.
*   Se actualizaron `STREAMML_TOKEN_SECRET` y `STREAMML_MEDIA_AUTH_SECRET` con cadenas dummy de 32 caracteres (`dummy_secret_for_local_use_only_32_chars` y `dummy_media_secret_for_local_use_32_chars`).
*   Se adaptaron los orígenes permitidos (`STREAMML_ALLOWED_ORIGINS`) para incluir `http://localhost:5173`.
*   Se deshabilitaron los controles estrictos de HTTPS en desarrollo local cambiando `STREAMML_COOKIE_SECURE` y `STREAMML_ENFORCE_HTTPS` a `false`.

---

## Error 2: Entorno de ejecución de scikit-learn/pandas incompatible en contenedor Docker por caché de capas

### Descripción del Error
Al iniciar la infraestructura de Docker Compose local con `docker compose -f infrastructure/docker/docker-compose.local.yml up mediamtx media-init -d`, la dependencia `api` fallaba con el siguiente error en sus logs:
```text
RuntimeError: Entorno de ejecución de scikit-learn incompatible: se esperaba 1.9.0, se encontró 1.7.2.
```
Esto ocurría porque Docker estaba utilizando una capa de compilación almacenada en caché (`CACHED`) para el paso de instalación de dependencias en el `api.Dockerfile`, que contenía versiones antiguas de las librerías preinstaladas (scikit-learn 1.7.2 y pandas 2.3.3) antes de que se actualizaran las definiciones oficiales del release de modelos a scikit-learn 1.9.0 y pandas 3.0.3 en `requirements.txt`.

### Solución
Se forzó la reconstrucción completa y limpia de la imagen de Docker para la API sin utilizar la caché de compilación mediante el siguiente comando:
```bash
docker compose -f infrastructure/docker/docker-compose.local.yml build --no-cache api
```
Esto obligó a `pip` a descargar e instalar correctamente las versiones actualizadas de `scikit-learn==1.9.0` y `pandas==3.0.3` dentro de la imagen de la API, superando la validación del cargador oficial del modelo.

---

## Error 3: Fallo en el script de arranque del asistente local por restricción estricta de Python 3.11

### Descripción del Error
Al ejecutar el script de arranque del asistente de configuración de StreamML (`scripts/Abrir-Configuracion-StreamML.ps1`), la terminal retornaba el siguiente error:
```text
No suitable Python runtime found
No se pudo crear el entorno aislado de StreamML.
```
Esto ocurría porque el script de PowerShell ejecutaba internamente la instrucción `py -3.11 -m venv $environmentPath`, requiriendo de forma estricta la instalación de la versión de Python 3.11. El entorno del desarrollador disponía únicamente de Python 3.12 y Python 3.14.

### Solución
Se editó la línea 32 de [Abrir-Configuracion-StreamML.ps1](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/scripts/Abrir-Configuracion-StreamML.ps1) para remover la restricción de versión explícita `-3.11`, permitiendo que el comando use la instalación de Python por defecto del lanzador (`py`):
```diff
-    & py -3.11 -m venv $environmentPath
+    & py -m venv $environmentPath
```
Con esta modificación, el asistente se inicializa exitosamente y corre sobre el entorno de Python 3.14 instalado en la máquina local.

---

## Verificación de Vinculación y Conector de OBS

El proceso de configuración de OBS WebSocket (puerto `4455`) y la vinculación con el conector local se ha completado de manera automatizada. No se detectaron errores de conexión con OBS, y las pruebas de envío de telemetría (`streamml-connector --once`) se completaron exitosamente con código de respuesta HTTP `200 OK`.

---

## Error 4: Restricción de guardado en pestaña "Servidor Docker" del Asistente por falta de Certificados TLS

### Descripción del Error
Al intentar registrar configuraciones de retransmisión (como claves de Twitch) en la pestaña **Servidor Docker** desde el asistente de configuración local (`http://127.0.0.1:8765/`), la interfaz arrojaba el siguiente error de validación e impedía el guardado:
```text
No se encontró el certificado TLS o su clave privada en las rutas indicadas.
```
Esto ocurría porque el formulario del Asistente requiere obligatoriamente rutas locales a certificados SSL válidos (`fullchain.pem` y `privkey.pem`) para poder guardar y validar cualquier campo de esa pestaña, lo cual no suele estar disponible en entornos de desarrollo local (`localhost`).

### Solución
Se creó un script de Python [generate_dev_certs.py](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/scripts/generate_dev_certs.py) que genera certificados SSL auto-firmados de desarrollo de manera automática usando la librería estándar:
1. Se instaló la dependencia `cryptography` en el entorno `.venv-setup`.
2. Se ejecutó el script generador, creando los archivos `fullchain.pem` y `privkey.pem` en la carpeta `./certs/`.
3. Al existir ya archivos PEM válidos de certificados en las rutas preestablecidas, el asistente de configuración permite guardar con éxito las claves de retransmisión y destinos JSON sin arrojar errores.

---

## Error 5: Fallo de autenticación en MediaMTX y falta de salida a Internet del Conector de Retransmisión (Twitch) en Local

### Descripción del Error
Durante las pruebas de transmisión a Twitch en el entorno de desarrollo local (Opción B), se identificaron tres problemas críticos que impedían la retransmisión:
1. **Conflicto de Base de Datos / API en Docker**: El contenedor de `mediamtx` en local estaba configurado por defecto para validar las transmisiones RTMP consultando al contenedor `api` interno de Docker (`http://api:8000`), el cual no compartía la base de datos local ni poseía los registros del token y sesión activos creados en la API nativa de desarrollo (puerto `8000` de la máquina host). Esto producía un error `HTTP 401: No autorizado` al intentar enviar stream desde OBS.
2. **Formato incorrecto de Credenciales en RTMP**: El script `restream_worker.py` del media-worker enviaba las credenciales en formato de URL clásica (`rtmp://user:pass@host/path`), pero el servidor de MediaMTX no las interpretaba correctamente para este protocolo, recibiendo parámetros de usuario y contraseña vacíos.
3. **Falta de salida a internet en Red Docker Local**: El contenedor de retransmisión `media-worker` estaba asignado en `docker-compose.local.yml` únicamente a la red interna aislada `backend` (`internal: true`), impidiéndole resolver el host `live.twitch.tv` o conectarse a Internet.

### Solución
Se aplicaron las siguientes correcciones:
1. **Redirección de la Validación a Host**: Se modificó `MTX_AUTHHTTPADDRESS` en [docker-compose.local.yml](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/infrastructure/docker/docker-compose.local.yml) para que las solicitudes de autenticación se enruten a `host.docker.internal:8000` (el backend nativo que corre en el host), y se eliminó la dependencia innecesaria del contenedor de API en Docker.
2. **Corrección de Formato de Credenciales RTMP**: Se modificaron las funciones `ffmpeg_command` y `source_url` de [restream_worker.py](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/apps/media/restream_worker.py) para que envíen el usuario y contraseña como parámetros de la query string (`?user=media-worker&pass=...`), formato oficial soportado por MediaMTX para RTMP.
3. **Acceso a Red Externa**: Se modificó la red del servicio `media-worker` en [docker-compose.local.yml](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/infrastructure/docker/docker-compose.local.yml) agregando la red `edge` (que provee salida a Internet).

Tras reconstruir el contenedor (`docker compose up media-worker -d --build --force-recreate`), el flujo de autenticación contra la base de datos de desarrollo y la conexión hacia Twitch se establecieron exitosamente.
