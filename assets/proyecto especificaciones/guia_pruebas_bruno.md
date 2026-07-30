# Guía de Pruebas con Bruno - Módulo de Inscripciones (Paso 7)

Esta guía detalla cómo configurar y ejecutar las pruebas funcionales para el **Módulo de Inscripciones y Control Transaccional de Cupos (Paso 7)** utilizando **Bruno** (alternativa ligera y open-source a Postman).

---

## ⚙️ 1. Configuración Inicial en Bruno

### Paso 1: Crear la Colección y el Entorno
1. Abre Bruno y haz clic en **"Create Collection"**.
2. Nombra la colección como `Academic Events API`.
3. Crea un entorno (**Environment**) llamado `Local` (o `Production` si pruebas el despliegue).
4. Agrega las siguientes variables en el entorno:
   * `base_url`: `http://localhost:8080` (o la URL de tu backend desplegado).
   * `token_participant`: *(Se llenará automáticamente o manualmente tras iniciar sesión)*.
   * `token_organizer`: *(Se llenará automáticamente o manualmente tras iniciar sesión)*.
   * `token_admin`: *(Se llenará automáticamente o manualmente tras iniciar sesión)*.

### Paso 2: Configurar la Autenticación General en Bruno
Para evitar agregar la cabecera `Authorization` de forma manual en cada endpoint:
1. En la raíz de la colección, ve a la pestaña **Auth**.
2. Selecciona **Bearer Token**.
3. Usa la variable dinámica `{{token}}`. Luego, en cada petición, podrás elegir qué token de rol usar modificando la variable o seleccionándola en el entorno.

---

## 📂 2. Peticiones a Crear en Bruno

Estructura de la carpeta sugerida en Bruno:
```txt
Academic Events API/
├── 0. Auth/
│   ├── Login Participant
│   ├── Login Organizer
│   └── Login Admin
└── 7. Registrations/
    ├── Register to Event (Inscribirse)
    ├── Cancel Registration (Cancelar)
    └── Get Registrations (Listar)
```

---

## 🧪 3. Detalle de las Pruebas Paso a Paso

Sigue esta secuencia exacta para probar todos los flujos de negocio del Paso 7:

### 🔑 Bloque A: Autenticación y Obtención de Tokens

#### Petición 1: Login Participante
* **Nombre en Bruno:** `Login Participant`
* **Método:** `POST`
* **URL:** `{{base_url}}/api/auth/login`
* **Cuerpo (JSON):**
  ```json
  {
    "email": "student@ups.edu.ec",
    "password": "password123"
  }
  ```
* **Acción:** Copia el `accessToken` devuelto en la respuesta y asígnalo a la variable de entorno `token_participant` (puedes hacerlo en la pestaña **Tests** de Bruno agregando `bru.setEnvVar("token_participant", res.body.accessToken);`).

#### Petición 2: Login Organizador
* **Nombre en Bruno:** `Login Organizer`
* **Método:** `POST`
* **URL:** `{{base_url}}/api/auth/login`
* **Cuerpo (JSON):**
  ```json
  {
    "email": "organizer@ups.edu.ec",
    "password": "password123"
  }
  ```
* **Acción:** Copia el `accessToken` y asígnalo a `token_organizer`.

#### Petición 3: Login Administrador
* **Nombre en Bruno:** `Login Admin`
* **Método:** `POST`
* **URL:** `{{base_url}}/api/auth/login`
* **Cuerpo (JSON):**
  ```json
  {
    "email": "admin@ups.edu.ec",
    "password": "password123"
  }
  ```
* **Acción:** Copia el `accessToken` y asígnalo a `token_admin`.

---

### 📝 Bloque B: Escenarios de Inscripción (Flujos Críticos)

#### Prueba 1: Inscripción Exitosa (Participante)
* **Objetivo:** Registrar un usuario en un evento publicado y con asientos disponibles.
* **Petición:** `Register to Event`
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/10` *(Cambia '10' por el ID de un evento publicado y activo)*.
* **Resultado Esperado:** 
  * Código HTTP: `200 OK` o `201 Created`.
  * Respuesta JSON: Un objeto con el estado `CONFIRMED`.
  * *Verificación en BD:* Si consultas el evento, el campo `available_seats` debe haber disminuido en `1`.

#### Prueba 2: Inscripción en Evento NO Publicado
* **Objetivo:** Validar que no se permiten registros en eventos en borrador (`DRAFT`) o cancelados (`CANCELLED`).
* **Petición:** `Register to Event`
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/11` *(Usa el ID de un evento en estado DRAFT)*.
* **Resultado Esperado:** 
  * Código HTTP: `400 Bad Request` o `409 Conflict`.
  * Respuesta JSON: Código de excepción de negocio con el mensaje: `"No se permiten inscripciones en eventos que no estén publicados"`.

#### Prueba 3: Inscripción en Evento en Fecha Pasada
* **Objetivo:** Impedir el registro si el evento ya inició o terminó.
* **Petición:** `Register to Event`
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/12` *(Usa el ID de un evento cuya fecha de inicio ya pasó)*.
* **Resultado Esperado:** 
  * Código HTTP: `400 Bad Request`.
  * Respuesta JSON: Mensaje: `"No se permiten inscripciones en eventos que ya hayan iniciado o finalizado"`.

#### Prueba 4: Prevención de Doble Inscripción Activa
* **Objetivo:** Comprobar que no se puede registrar dos veces seguidas en el mismo evento al mismo participante.
* **Petición:** Vuelve a enviar la petición de la **Prueba 1** (mismo Event ID).
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/10`
* **Resultado Esperado:** 
  * Código HTTP: `400 Bad Request`.
  * Respuesta JSON: Mensaje: `"El usuario ya se encuentra inscrito de forma activa en este evento"`.

#### Prueba 5: Prevención de Inscripción Sin Cupos
* **Objetivo:** Verificar el control transaccional cuando `available_seats` es `0`.
* **Petición:** `Register to Event`
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/13` *(Usa el ID de un evento con available_seats = 0)*.
* **Resultado Esperado:** 
  * Código HTTP: `400 Bad Request`.
  * Respuesta JSON: Mensaje: `"No hay cupos disponibles"`.

---

### ❌ Bloque C: Cancelación y Reactivación de Cupos

#### Prueba 6: Cancelación Exitosa de la Inscripción propia
* **Objetivo:** Permitir al participante cancelar su propia inscripción liberando un cupo para el evento.
* **Petición:** `Cancel Registration`
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/100/cancel` *(Cambia '100' por el ID de la inscripción creada en la Prueba 1)*.
* **Resultado Esperado:** 
  * Código HTTP: `200 OK`.
  * Respuesta JSON: Estado cambia a `CANCELLED`.
  * *Verificación en BD:* El campo `available_seats` del evento debe haber aumentado en `1` (`+1`).

#### Prueba 7: Intento de Cancelación de Inscripción Ajena (Seguridad)
* **Objetivo:** Garantizar que un participante no pueda cancelar la inscripción de otro.
* **Petición:** `Cancel Registration`
* **Autenticación:** Configura el token del **Organizador** (`Bearer {{token_organizer}}`) o de otro participante no propietario.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/100/cancel` *(ID de la inscripción del participante 1)*.
* **Resultado Esperado:** 
  * Código HTTP: `403 Forbidden`.
  * Respuesta JSON: Mensaje: `"No posee permisos para cancelar esta inscripción"`.

#### Prueba 8: Reactivación de una Inscripción Cancelada
* **Objetivo:** Si un usuario canceló previamente, y vuelve a inscribirse al mismo evento, se debe actualizar y reactivar su registro existente a `CONFIRMED` en lugar de crear un registro duplicado (previniendo errores de restricción única en BD).
* **Petición:** `Register to Event` (vuelve a invocar el registro al evento cancelado en la Prueba 6).
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Método:** `POST`
* **URL:** `{{base_url}}/api/registrations/events/10`
* **Resultado Esperado:** 
  * Código HTTP: `200 OK`.
  * Respuesta JSON: Muestra el mismo ID de inscripción anterior pero con el estado actualizado a `CONFIRMED` y la fecha de inscripción al día de hoy. El cupo del evento vuelve a decrementarse en 1.

---

### 👁️ Bloque D: Listados de Inscripciones (Visibilidad y Roles)

#### Petición a Utilizar: `Get Registrations`
* **Método:** `GET`
* **URL:** `{{base_url}}/api/registrations?page=0&size=10`

#### Prueba 9: Consulta por Participante
* **Autenticación:** Usa `Bearer {{token_participant}}`.
* **Resultado Esperado:** `200 OK`. Retorna únicamente las inscripciones pertenecientes a dicho participante.

#### Prueba 10: Consulta por Organizador
* **Autenticación:** Usa `Bearer {{token_organizer}}`.
* **Resultado Esperado:** `200 OK`. Retorna únicamente las inscripciones a los eventos organizados por este organizador.

#### Prueba 11: Consulta por Administrador
* **Autenticación:** Usa `Bearer {{token_admin}}`.
* **Resultado Esperado:** `200 OK`. Retorna **todas** las inscripciones registradas en el sistema.
