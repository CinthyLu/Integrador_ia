# Guía Paso a Paso para Levantar y Ejecutar StreamML

Esta guía proporciona instrucciones detalladas paso a paso para configurar tu entorno e iniciar **StreamML** en diferentes modalidades (desarrollo local rápido, desarrollo nativo y despliegue de producción), además de configurar el **Conector Local de OBS**.

---

## Índice
1. [Requisitos Previos](#1-requisitos-previos)
2. [Entorno de Desarrollo Local](#2-entorno-de-desarrollo-local)
   - [Opción A: Script de Inicio Rápido Todo en Uno (Recomendado en Windows)](#opción-a-script-de-inicio-rápido-todo-en-uno-reconocido-en-windows)
   - [Opción B: Flujo de Desarrollo Nativo Paso a Paso (Para Desarrolladores)](#opción-b-flujo-de-desarrollo-nativo-paso-a-paso-para-desarrolladores)
   - [Opción C: Ejecución Rápida en Docker (Para Demos/QA)](#opción-c-ejecución-rápida-en-docker-para-demosqa)
3. [Despliegue en Entorno de Producción](#3-despliegue-en-entorno-de-producción)
   - [Opción A: Usando el Asistente Gráfico (GUI) (Recomendado en Windows)](#opción-a-usando-el-asistente-gráfico-gui-reconocido-en-windows)
   - [Opción B: Despliegue Manual por Línea de Comandos (CLI)](#opción-b-despliegue-manual-por-línea-de-comandos-cli)
4. [Configuración del Conector Local de OBS](#4-configuración-del-conector-local-de-obs)
   - [Paso 1: Configurar OBS Studio](#paso-1-configurar-obs-studio)
   - [Paso 2: Ejecutar y Vincular el Conector](#paso-2-ejecutar-y-vincular-el-conector)
5. [Resolución de Problemas Frecuentes](#5-resolución-de-problemas-frecuentes)

---

## 1. Requisitos Previos

Antes de comenzar, asegúrate de tener instaladas las siguientes herramientas según tu sistema operativo:

*   **Docker Desktop** (con WSL2 habilitado en Windows) - [Descargar](https://www.docker.com/products/docker-desktop)
*   **Python 3.11 o superior** (asegúrate de marcar *"Add Python to PATH"* durante la instalación) - [Descargar](https://www.python.org/downloads/)
*   **Node.js (versión 22 o superior)** - [Descargar](https://nodejs.org/)
*   **Git** (para control de versiones) - [Descargar](https://git-scm.com/downloads)
*   **OBS Studio** (instalado localmente en la PC desde la que vas a transmitir) - [Descargar](https://obsproject.com/)

---

## 2. Entorno de Desarrollo Local

Elige una de las siguientes opciones para ejecutar el proyecto en tu entorno local de desarrollo.

### Opción A: Script de Inicio Rápido Todo en Uno (Recomendado en Windows)

El proyecto incluye un script de PowerShell que automatiza la compilación e inicio de todos los componentes locales de forma simultánea.

1. Abre **PowerShell** en el directorio raíz del proyecto:
   ```powershell
   cd "c:\Users\MSI\Desktop\IA\Proyecto final\StreamML"
   ```
2. Asegúrate de tener instalado el frontend ejecutando por primera vez:
   ```powershell
   cd apps/frontend
   npm install
   cd ../..
   ```
3. Si no tienes un archivo `.env` local, cópialo desde la plantilla:
   ```powershell
   cp .env.example .env
   ```
4. Ejecuta el script de inicio:
   ```powershell
   .\Run-Servers.ps1
   ```
   *Este script se encargará de levantar automáticamente:*
   * Los contenedores de Docker locales (`mediamtx` y `nginx`).
   * El Backend de FastAPI en segundo plano (`http://localhost:8000`).
   * El Frontend de React/Vite en una consola (`http://localhost:5173`).

---

### Opción B: Flujo de Desarrollo Nativo Paso a Paso (Para Desarrolladores)

Si deseas mayor control o estás editando código y necesitas *Hot-Reload* nativo en cada componente, sigue estos pasos:

#### 1. Iniciar la Infraestructura de Medios (Docker)
Levanta MediaMTX en segundo plano para manejar los flujos RTMP y WebRTC:
```bash
docker compose -f infrastructure/docker/docker-compose.local.yml up mediamtx media-init -d
```

#### 2. Configurar e Iniciar la API Backend (FastAPI)
Abre una terminal en la raíz del proyecto y ejecuta:
```bash
# Crear entorno virtual de Python
python -m venv .venv

# Activar entorno virtual
# En Windows (PowerShell):
.venv\Scripts\Activate.ps1
# En Linux/macOS (Bash):
source .venv/bin/activate

# Instalar dependencias del Backend
pip install -r requirements.txt

# Configurar variables de entorno
cp .env.example .env

# Iniciar servidor de desarrollo
python -m uvicorn apps.api.main:app --host 0.0.0.0 --port 8000 --reload
```
La API estará accesible en `http://localhost:8000`.

#### 3. Configurar e Iniciar el Frontend (React + Vite)
Abre una nueva terminal en la raíz del proyecto y ejecuta:
```bash
# Ir al directorio del frontend
cd apps/frontend

# Instalar dependencias de Node.js
npm install

# Configurar variables para Vite
# En Windows (PowerShell):
$env:VITE_API_BASE_URL="http://localhost:8000/api/v1"
$env:VITE_WS_BASE_URL="ws://localhost:8000/ws"
# En Linux/macOS (Bash):
export VITE_API_BASE_URL="http://localhost:8000/api/v1"
export VITE_WS_BASE_URL="ws://localhost:8000/ws"

# Iniciar el servidor local del frontend
npm run dev
```
Abre en tu navegador la dirección indicada por la consola (usualmente `http://localhost:5173`).

---

### Opción C: Ejecución Rápida en Docker (Para Demos/QA)

Si no deseas realizar configuraciones de lenguajes locales y prefieres levantar el sistema completo en contenedores aislados:

1. Asegúrate de tener abierto **Docker Desktop**.
2. En la terminal de la raíz del proyecto, ejecuta:
   ```bash
   docker compose -f infrastructure/docker/docker-compose.local.yml up --build
   ```
3. Espera a que termine la construcción de las imágenes y visita **`http://localhost`** en tu navegador.
4. Inicia sesión con las credenciales de prueba preconfiguradas:
   * **Correo:** `admin@localhost`
   * **Contraseña:** `password123456`

Para detener el sistema completo en Docker, ejecuta:
```bash
docker compose -f infrastructure/docker/docker-compose.local.yml down
```

---

## 3. Despliegue en Entorno de Producción

Para entornos listos para producción que requieran un dominio público real y certificados SSL para transmisiones WebRTC seguras:

### Opción A: Usando el Asistente Gráfico (GUI) (Recomendado en Windows)

El proyecto incluye una interfaz gráfica local para facilitar el despliegue y la configuración de secretos:

1. Abre con doble clic el script local:
   [Abrir-Configuracion-StreamML.cmd](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/scripts/Abrir-Configuracion-StreamML.cmd)
2. El instalador configurará un entorno aislado y abrirá la URL del asistente en tu navegador: `http://127.0.0.1:8765/`.
3. Ve a la pestaña **Servidor Docker**.
4. Completa la información solicitada:
   * Dominio público HTTPS.
   * Rutas locales de los certificados SSL (`fullchain.pem` y `privkey.pem`).
   * Correo y contraseña inicial del administrador.
5. Haz clic en **Validar Docker Compose** y después en **Iniciar o actualizar servicios**.

---

### Opción B: Despliegue Manual por Línea de Comandos (CLI)

Si despliegas en un servidor remoto (por ejemplo, Linux):

1. **Generar variables de entorno protegidas:**
   Copia la plantilla y restringe los permisos de archivo en Linux:
   ```bash
   install -m 600 /dev/null .env
   cp .env.example .env
   ```
2. **Ejecutar el asistente de configuración interactivo por consola:**
   * **En Windows (PowerShell):**
     ```powershell
     .\setup.ps1
     ```
   * **En Linux/macOS (Bash):**
     ```bash
     bash setup.sh
     ```
   Ingresa el dominio, credenciales del administrador y las rutas absolutas a los certificados SSL cuando te lo solicite. El script autogenerará secretos criptográficos aleatorios seguros.

3. **Validar la configuración de Compose:**
   ```bash
   docker compose --env-file .env -f infrastructure/docker/docker-compose.yml config
   ```
4. **Construir y levantar el contenedor en producción:**
   ```bash
   docker compose --env-file .env -f infrastructure/docker/docker-compose.yml up -d --build
   ```
5. **Verificar el estado de salud interno del backend:**
   ```bash
   docker compose --env-file .env -f infrastructure/docker/docker-compose.yml exec api python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health/ready', timeout=3).read().decode())"
   ```

---

## 4. Configuración del Conector Local de OBS

> [!IMPORTANT]
> **Separación de roles:** El conector de OBS (`apps/connector`) **NO** debe correr dentro de Docker en el servidor de producción. Debe ejecutarse **localmente en la misma computadora donde esté instalado OBS Studio**, ya que requiere acceso a la API WebSocket local de OBS (`127.0.0.1:4455`).

### Paso 1: Configurar OBS Studio
1. Abre **OBS Studio**.
2. Ve al menú superior: **Herramientas -> Ajustes de WebSocket (WebSockets Server Settings)**.
3. Activa la opción **Habilitar el servidor WebSocket (Enable WebSocket Server)**.
4. Conserva el host local `127.0.0.1` y define un puerto (por defecto `4455`).
5. Configura una contraseña segura y anótala.
6. En OBS, crea exactamente dos escenas con los siguientes nombres:
   * **`StreamML Live`** (tu escena principal con el contenido en vivo).
   * **`StreamML Backup`** (una escena de respaldo con un video o imagen estática en caso de pérdida de conexión).

### Paso 2: Ejecutar y Vincular el Conector

#### Vía Asistente Gráfico (GUI - Recomendado en Windows)
1. Ejecuta el asistente gráfico mediante el archivo [Abrir-Configuracion-StreamML.cmd](file:///c:/Users/MSI/Desktop/IA/Proyecto%20final/StreamML/scripts/Abrir-Configuracion-StreamML.cmd).
2. En la pestaña del **Conector local**, ingresa la URL de la API (por ejemplo `http://localhost:8000` para desarrollo local, o `https://tu-dominio.com` en producción).
3. Escribe el puerto de OBS (normalmente `4455`) y la contraseña configurada en el paso anterior.
4. Abre el panel web de StreamML en tu navegador, inicia sesión, crea una nueva transmisión y ve al paso final **Comprobación** para generar un código temporal.
5. Copia el código temporal, pégalo en el asistente gráfico y presiona **Guardar y vincular**.
6. Haz clic en **Comprobar conexión** para validar que todo esté verde.
7. Haz clic en **Iniciar monitorización** para comenzar a transmitir telemetría y activar el agente inteligente.

#### Vía Consola de Comandos (CLI)
Si prefieres utilizar una terminal:
1. Crea un entorno virtual de Python e instala el conector en modo editable:
   ```powershell
   python -m venv .venv-connector
   .venv-connector\Scripts\python -m pip install -e apps/connector
   ```
2. Establece las variables de entorno de conexión:
   ```powershell
   $env:STREAMML_API_URL = "http://localhost:8000" # o tu URL de producción
   $env:OBS_WEBSOCKET_HOST = "127.0.0.1"
   $env:OBS_WEBSOCKET_PORT = "4455"
   ```
3. Ejecuta el conector en modo vinculación:
   ```powershell
   .venv-connector\Scripts\streamml-connector --pair
   ```
   *Ingresa el código temporal de vinculación obtenido en el Panel Web y la contraseña de OBS WebSocket cuando la terminal te lo solicite.*
4. En ejecuciones posteriores, simplemente corre el conector sin la bandera `--pair`:
   ```powershell
   .venv-connector\Scripts\streamml-connector
   ```

---

## 5. Resolución de Problemas Frecuentes

| Problema | Causa Probable | Solución Recomendada |
| :--- | :--- | :--- |
| **Error: "port is already allocated"** al iniciar Docker | Otro servicio local está ocupando los puertos `80`, `8000`, `1935` o `8889`. | Detén otros contenedores con `docker stop $(docker ps -q)`. Si es un servicio nativo, localiza el PID usando `netstat -ano \| findstr :80` en Windows y detén el servicio (ej. Skype, IIS, Apache). |
| **API no disponible** en el conector | El backend de FastAPI no se está ejecutando o la URL es incorrecta. | Comprueba que iniciaste la API en el puerto `8000`. Si estás en producción, verifica los logs de Docker Compose. |
| **OBS no conecta** | El puerto WebSocket es incorrecto o la contraseña está mal escrita. | Abre los Ajustes de WebSocket en OBS Studio. Comprueba que el servidor está habilitado y usa la misma contraseña en el asistente local. |
| **Faltan escenas en OBS** | Los nombres de escena en OBS no coinciden exactamente con los esperados. | Crea las escenas `StreamML Live` y `StreamML Backup` en OBS con esos nombres exactos (respetando mayúsculas y espacios), o configura los nombres personalizados en las opciones del conector. |
| **No se muestra la telemetría en vivo** | El token de vinculación del conector expiró o la transmisión no está activa. | Genera un nuevo código de vinculación en el Panel Web de StreamML y vuelve a guardar y vincular en el asistente. |
| **La transmisión por WebRTC no carga** | Las políticas de seguridad del navegador bloquean WebRTC en conexiones HTTP no seguras. | Si no estás usando certificados SSL/HTTPS, el reproductor web cambiará automáticamente a HLS (añadiendo unos 10 segundos de latencia). Para WebRTC sin latencia, es obligatorio configurar HTTPS con certificados SSL válidos. |
