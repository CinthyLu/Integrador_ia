# Pasos para Levantar el Proyecto StreamML

Esta guía describe detalladamente la secuencia de pasos para levantar y ejecutar el proyecto StreamML de forma local sin alterar los archivos de código preexistentes.

---

## Opción A: Levantar todo con Docker Compose (Recomendado y más rápido)

Este método levanta todos los componentes de la aplicación (API Backend, Frontend, MediaMTX, Base de Datos SQLite y Nginx) dentro de contenedores aislados.

### Paso 1: Iniciar Docker Desktop
Asegúrate de que la aplicación **Docker Desktop** esté abierta y ejecutándose en tu sistema. El indicador en la esquina inferior izquierda de Docker Desktop debe estar en color verde (running).

### Paso 2: Ejecutar Docker Compose
Abre una terminal (PowerShell o CMD en Windows) en la raíz del proyecto (`StreamML/`) y ejecuta el siguiente comando para compilar e iniciar los servicios en segundo plano:

```powershell
docker compose -f infrastructure/docker/docker-compose.local.yml up --build -d
```

*Nota: La bandera `--build` compila las imágenes locales para la API y el Frontend, y `-d` los ejecuta en segundo plano (detached mode).*

### Paso 3: Verificar el estado de los contenedores
Para confirmar que todos los servicios estén corriendo de forma correcta y saludable, ejecuta:

```powershell
docker compose -f infrastructure/docker/docker-compose.local.yml ps
```

Deberías ver que los contenedores `nginx`, `frontend`, `api`, `mediamtx` y `media-worker` están en estado `running` (o `healthy`).

### Paso 4: Acceder a la Aplicación Web
Una vez que los servicios estén activos, abre tu navegador web de preferencia e ingresa a la siguiente dirección:

* **URL del Frontend:** [http://localhost](http://localhost) (Puerto 80)
* **URL de la API (Documentación OpenAPI/Swagger):** [http://localhost/api/v1/docs](http://localhost/api/v1/docs)

### Paso 5: Iniciar Sesión en el Panel
Para ingresar al sistema, utiliza las siguientes credenciales predeterminadas para desarrollo local:

* **Correo electrónico:** `cramonm12@gmail.com`
* **Contraseña:** `password123456`

---

## Opción B: Flujo de Desarrollo Nativo (Backend y Frontend locales)

Usa esta opción si deseas correr la API y el Frontend directamente sobre tu sistema operativo (Hot-Reload) y únicamente levantar el servidor de medios (`MediaMTX`) en Docker.

### Paso 1: Levantar la Infraestructura Base (MediaMTX)
Asegúrate de tener Docker Desktop corriendo. En tu terminal, ejecuta:

```powershell
docker compose -f infrastructure/docker/docker-compose.local.yml up mediamtx media-init -d
```

### Paso 2: Configurar y Ejecutar la API Backend (Python)
1. Abre una nueva terminal en la raíz del proyecto.
2. Crea un entorno virtual de Python:
   ```powershell
   python -m venv .venv
   ```
3. Activa el entorno virtual:
   ```powershell
   .venv\Scripts\Activate.ps1
   ```
4. Instala las dependencias:
   ```powershell
   pip install -r requirements.txt
   ```
5. Copia el archivo de configuración del entorno (sin modificarlo):
   ```powershell
   cp .env.example .env
   ```
6. Inicia el servidor de desarrollo de la API:
   ```powershell
   python -m uvicorn apps.api.main:app --host 0.0.0.0 --port 8000 --reload
   ```

### Paso 3: Configurar y Ejecutar el Frontend (React / Vite)
1. Abre una nueva terminal en la raíz del proyecto.
2. Cambia al directorio del frontend:
   ```powershell
   cd apps/frontend
   ```
3. Instala las dependencias de Node.js:
   ```powershell
   npm install
   ```
4. Configura las variables de entorno temporales para Vite en tu sesión de terminal:
   ```powershell
   $env:VITE_API_BASE_URL="http://localhost:8000/api/v1"
   $env:VITE_WS_BASE_URL="ws://localhost:8000/ws"
   ```
5. Ejecuta el servidor de desarrollo del Frontend:
   ```powershell
   npm run dev
   ```
6. Abre tu navegador en la dirección que aparezca en consola (usualmente [http://localhost:5173](http://localhost:5173)).

---

## Detener el Proyecto

* Para detener los servicios de Docker:
  ```powershell
  docker compose -f infrastructure/docker/docker-compose.local.yml down
  ```
