# StreamML: Guión para Video de Presentación (2 Minutos 20 Segundos) y Resumen del Proyecto

Este documento consolida el resumen de todo lo desarrollado en el proyecto **StreamML** y proporciona un guión técnico estructurado de **2 minutos y 20 segundos** para un video de presentación, dividido en la parte funcional e ingeniería/ML.

---

## Resumen de lo Desarrollado en el Proyecto

### 1. Modelos de Machine Learning (El "Cerebro")
El núcleo inteligente se desarrolló en **Jupyter Notebooks** en una fase experimental offline:
* **Notebook 1 (`01_data_preparation.ipynb`)**: Auditoría y limpieza de datasets reales de telemetría de red. Normalización y partición de datos.
* **Notebook 2 (`02_model_training.ipynb`)**: Entrenamiento de clasificadores supervisados:
  * **Modelo Reactivo (`DecisionTreeClassifier`)**: Clasifica en milisegundos la calidad instantánea en `low`, `medium` o `high` a partir de `upload_mbps`, `download_mbps` y `latency_ms`. Alcanzó **99.91% Macro F1**.
  * **Modelo Predictivo (`LogisticRegression`)**: Anticipa si en los próximos 10 minutos se necesitará reducir la calidad (`downgrade_needed` o `maintain`) basándose en una ventana de 600 segundos (19 variables de tendencia). Alcanzó **100.00% Macro F1**.
* **Notebook 3 (`03_model_inference.ipynb`)**: Pruebas de velocidad de inferencia y verificación de modelos serializados (`.joblib`).
* **Notebook 4 (`04_entrenamiento_y_creacion_del_agente.ipynb`)**: Evaluación del comportamiento e integración con políticas de control deterministas.

### 2. Infraestructura y Arquitectura de Microservicios (El "Cuerpo")
Una vez entrenados, los modelos se integraron en una arquitectura dockerizada:
* **Backend API (FastAPI)**: Orquesta la lógica, expone endpoints REST y WebSockets de alta frecuencia para recibir telemetría y ejecutar inferencias.
* **Agente Nexa (`policy.py`)**: Filtro de seguridad determinista que recibe las recomendaciones del ML y aplica políticas como *cooldowns* (evita cambios de calidad repetitivos), histéresis y control de escenas.
* **Frontend Dashboard (React + TypeScript)**: Panel en tiempo real que muestra gráficos de red, logs, la mascota de Nexa y sus estados de ánimo (neutral, pensando, trabajando, éxito, error).
* **Conector Local de OBS (Python)**: Script que corre en la máquina local del streamer y ejecuta las órdenes del backend (ajuste de bitrate o conmutación de escenas) a través de OBS WebSockets.
* **Servidor de Medios (MediaMTX + FFmpeg)**: Administra la señal de video (WebRTC/RTMP/HLS) e inyecta un video en bucle de soporte técnico si hay pérdida total de señal.
* **Proxy y Seguridad (Nginx + SSL)**: Protege la API y encamina de forma segura las señales WebRTC del vivo.

---

## Guión Técnico del Video (140 Segundos)

El video tiene una duración exacta de **2 minutos y 20 segundos (140 segundos)** y se divide en dos secciones principales:
1. **Parte Funcional y de Interfaz (0:00 - 1:00)**
2. **Parte de Ingeniería, Machine Learning y Seguridad (1:00 - 2:20)**

### Estructura de Escenas y Tiempos

| Escena | Tiempo | Segmento | Visual (Qué se muestra en pantalla) | Audio / Locución |
| :---: | :---: | :---: | :--- | :--- |
| **1** | 0:00 - 0:15 | Introducción | Animación fluida de la arquitectura StreamML. Un streamer transmitiendo con fluctuaciones de red (pantalla pixelada o con lag). | ¿Imaginas que tu transmisión en vivo se caiga o se pixelee por problemas de internet? Bienvenidos a **StreamML**, un sistema de streaming adaptativo inteligente que optimiza la transmisión en tiempo real usando Inteligencia Artificial. |
| **2** | 0:15 - 0:35 | Interfaz y Vínculo | Grabación de pantalla del Dashboard en React. El usuario ingresa a `LiveMonitorPage`. Inicia la transmisión vinculando el origen WebRTC de VDO.Ninja y encendiendo el Conector Local de OBS (el indicador pasa a verde). | Nuestra interfaz web monitorea segundo a segundo el estado de la red. Al iniciar la transmisión, el frontend recibe la telemetría WebRTC y la envía al backend. Si hay una fluctuación de red, nuestro Agente Nexa entra en acción. |
| **3** | 0:35 - 0:50 | Adaptación en Vivo | Se emula una caída de red (bajada de ancho de banda). La mascota de Nexa cambia de **Neutral (💻)** a **Pensando (🤔)** y **Trabajando (⚡)**. Al lado, la ventana de OBS Studio muestra cómo el bitrate baja suave y automáticamente de 5000 a 2500 kbps. | Nexa analiza los datos y ordena al conector de OBS reducir el bitrate de forma suave de 5000 a 2500 kbps para evitar cortes en la transmisión, garantizando la mejor calidad de experiencia para el usuario. |
| **4** | 0:50 - 1:00 | Caída y Recuperación | Se apaga la conexión del teléfono simulando un corte total. Nexa pasa a **Error (❌)**. OBS cambia instantáneamente a la escena de respaldo "Problemas Técnicos" reproduciendo un loop de video local de MediaMTX. Al restablecer la red, vuelve a Live automáticamente. | Si la red cae por completo, Nexa cambia a una escena de respaldo con un video en bucle en MediaMTX, restaurando la transmisión en vivo de forma automática e inmediata una vez que la red se estabiliza. |
| **5** | 1:00 - 1:25 | Cuadernos y Datos | Grabación rápida de los cuadernos de Jupyter. Vista rápida de `01_data_preparation.ipynb` procesando telemetría WebRTC y de `02_model_training.ipynb` con las curvas de entrenamiento de los dos modelos (Macro F1: 99.9% y 100%). | ¿Cómo logramos esto? En la fase de ciencia de datos, preparamos la telemetría y entrenamos dos clasificadores en Jupyter. El reactivo es un árbol de decisión con noventa y nueve punto nueve por ciento de precisión. El predictivo es una regresión logística que analiza una ventana de seiscientos segundos con diecinueve estadísticas de tendencia como pendientes y percentiles de latencia. |
| **6** | 1:25 - 1:45 | Políticas y Decisiones | Código de inferencia en `03_model_inference.ipynb` y las políticas deterministas de Nexa en `src/streamml/agent/policy.py`. Se hace zoom a las constantes de `cooldown` (30s) y al cálculo del margen de seguridad de capacidad. | En el backend, integramos los modelos con políticas deterministas en Python. Nexa aplica cooldowns de treinta segundos e histéresis de mejora para evitar cambios bruscos y repetitivos de calidad, garantizando decisiones explicables y seguras. |
| **7** | 1:45 - 2:05 | Ciberseguridad y Resiliencia | Zoom al código de `src/streamml/security/` y `apps/connector/` donde se ven funciones de firma de comandos HMAC, tokens SHA-256 e interpolación lineal de datos. | Para blindar la seguridad, el conector local valida comandos firmados con HMAC y almacena las contraseñas de OBS en el sistema operativo local. Además, los datos se interpolan linealmente si hay microcortes, descartando muestras antiguas de red. |
| **8** | 2:05 - 2:20 | Arquitectura Dockerizada y Cierre | Terminal ejecutando `docker compose up`. Se muestran en cascada los contenedores de FastAPI, React, Nginx, FFmpeg y MediaMTX. El video finaliza con un plano general del dashboard estable y un mensaje final. | Toda esta arquitectura corre sobre contenedores dockerizados con FastAPI, React, Nginx, FFmpeg y MediaMTX bajo sistemas de archivos de solo lectura. StreamML demuestra cómo el Machine Learning y la ingeniería de software se unen para una transmisión perfecta. |

---

## Texto de Corrido para Leer (Locución Continua)

*Este es el texto completo que debes leer de manera pausada y fluida durante la grabación. La duración de lectura estimada a velocidad normal es de aproximadamente **140 segundos (2 minutos y 20 segundos)**.*

> "¿Imaginas que tu transmisión en vivo se caiga o se pixelee por problemas de internet? Bienvenidos a **StreamML**, un sistema de streaming adaptativo inteligente que optimiza la transmisión en tiempo real usando Inteligencia Artificial.
> 
> Nuestra interfaz web monitorea segundo a segundo el estado de la red. Al iniciar la transmisión, el frontend recibe la telemetría WebRTC y la envía al backend. Si hay una fluctuación de red, nuestro Agente Nexa entra en acción.
> 
> Nexa analiza los datos y ordena al conector de OBS reducir el bitrate de forma suave de 5000 a 2500 kbps para evitar cortes en la transmisión, garantizando la mejor calidad de experiencia para el usuario.
> 
> Si la red cae por completo, Nexa cambia a una escena de respaldo con un video en bucle en MediaMTX, restaurando la transmisión en vivo de forma automática e inmediata una vez que la red se estabiliza.
> 
> ¿Cómo logramos esto? En la fase de ciencia de datos, preparamos la telemetría y entrenamos dos clasificadores en Jupyter. El modelo reactivo es un árbol de decisión con 99.9% de precisión. El modelo predictivo es una regresión logística que analiza una ventana de 10 minutos con diecinueve estadísticas de tendencia como pendientes y percentiles de latencia.
> 
> En el backend, integramos los modelos con políticas deterministas en Python. Nexa aplica cooldowns de treinta segundos e histéresis de mejora para evitar cambios bruscos y repetitivos de calidad, garantizando decisiones explicables y seguras.
> 
> Para blindar la seguridad, el conector local valida comandos firmados con HMAC y almacena las contraseñas de OBS en el sistema operativo local. Además, los datos se interpolan linealmente si hay microcortes, descartando muestras antiguas de red.
> 
> Toda esta arquitectura corre sobre contenedores dockerizados con FastAPI, React, Nginx, FFmpeg y MediaMTX bajo sistemas de archivos de solo lectura. StreamML demuestra cómo el Machine Learning y la ingeniería de software se unen para garantizar una transmisión perfecta."
