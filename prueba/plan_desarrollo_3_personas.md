# Plan de Desarrollo para 3 Personas

Este documento reorganiza el trabajo del proyecto integrador **API REST Segura para la Gestión de Eventos Académicos** para un equipo de **3 personas**, manteniendo el alcance técnico del proyecto y alineándolo con la rúbrica de la asignatura.

El objetivo es que cada integrante tenga responsabilidad clara sobre una parte del sistema, pueda explicar su código en la sustentación y mantenga un historial de Git suficiente para evaluación.

---

## 1. Reglas Para Cumplir la Rúbrica

Para evitar penalizaciones, el equipo debe cumplir estas reglas desde el inicio:

1. Cada integrante debe hacer **mínimo 5 commits funcionales**.
2. Los commits de cada persona deben distribuirse en **al menos 3 días diferentes**.
3. Los commits deben representar trabajo real: funcionalidad, pruebas, refactor o documentación útil.
4. No usar commits vacíos ni subir todo el proyecto al final.
5. Trabajar con ramas por funcionalidad, por ejemplo `feature/auth`, `feature/events`, `feature/deploy`.
6. Cada persona debe poder explicar su módulo principal y al menos una parte transversal del proyecto.
7. Las entregas parciales deben validar que el código sigue compilando y que la app arranca con la base de datos.

---

## 2. Distribución General Por Persona

### Persona 1: Arquitectura, Persistencia y Seguridad Base

Responsable principal de la base técnica del proyecto.

Debe encargarse de:

- Estructura modular por dominios.
- Entidades JPA base y relaciones principales.
- `BaseEntity`, auditoría temporal y campos comunes.
- Configuración inicial de Spring Security.
- Registro, carga de usuario y cifrado BCrypt.
- Configuración de OpenAPI / Swagger.

### Persona 2: Dominio Funcional y Reglas de Negocio

Responsable principal de los módulos que el usuario final consume.

Debe encargarse de:

- Categorías.
- Eventos.
- Sesiones.
- Paginación, filtros y ordenamiento.
- Validación de propiedad del recurso.
- DTOs de entrada y respuesta del dominio.

### Persona 3: Inscripciones, Infraestructura y Validaciones Transversales

Responsable principal de la parte operativa y de despliegue.

Debe encargarse de:

- Inscripciones y control transaccional de cupos.
- Excepciones globales y validación centralizada.
- Auditoría de acciones críticas.
- Redis, rate limiting y bloqueos temporales.
- Reportes PDF/Excel.
- Docker, pruebas y despliegue.

---

## 3. Reparto Detallado Del Trabajo

### Persona 1

#### Bloque 1: Base del proyecto

- Crear la estructura base de paquetes.
- Ajustar `application.yml` con variables de entorno.
- Preparar la configuración común del proyecto.

#### Bloque 2: Persistencia

- Crear `BaseEntity`.
- Mapear las entidades más transversales.
- Verificar compatibilidad entre JPA y PostgreSQL.

#### Bloque 3: Seguridad base

- Implementar `SecurityConfig`.
- Configurar `PasswordEncoder`.
- Crear el flujo de registro de usuarios.
- Integrar Swagger con seguridad permitida en rutas públicas.

### Persona 2

#### Bloque 1: Catálogos y eventos

- CRUD de categorías.
- CRUD de eventos.
- Reglas de negocio para eventos.

#### Bloque 2: Sesiones y consultas

- CRUD de sesiones.
- Búsqueda, filtros y paginación.
- Ordenamiento seguro con lista blanca de campos.

#### Bloque 3: Ownership

- Validar que un organizador solo modifique sus propios eventos.
- Preparar DTOs y mappers de dominio.
- Añadir pruebas unitarias del dominio funcional.

### Persona 3

#### Bloque 1: Inscripciones

- Implementar inscripción a eventos.
- Validar cupos disponibles.
- Controlar transacciones para evitar inconsistencias.

#### Bloque 2: Transversales

- `@RestControllerAdvice` global.
- Validación de DTOs con Bean Validation.
- Auditoría de acciones críticas.

#### Bloque 3: Infraestructura y salida

- Redis para rate limiting y bloqueos.
- Generación de reportes PDF y Excel.
- Dockerfile y `docker-compose.yml`.
- Ajustes de despliegue y pruebas finales.

---

## 4. Cronograma Sugerido De 7 Días

La idea es que el trabajo avance en paralelo, pero sin perder la responsabilidad principal de cada uno.

| Día | Persona 1 | Persona 2 | Persona 3 |
| --- | --- | --- | --- |
| Día 1 | Estructura del proyecto, paquetes, configuración base y Docker local | Revisar scripts SQL y modelo de datos | Crear `docker-compose.yml` local y validar PostgreSQL |
| Día 2 | `BaseEntity` y entidades comunes | Entidades de dominio (`Category`, `Event`, `Session`) | Repositorios iniciales y verificación de esquema |
| Día 3 | `SecurityConfig`, BCrypt y registro | DTOs y servicio de autenticación | Controlador de auth y pruebas básicas del flujo |
| Día 4 | JWT y filtro de autenticación | Refresh token, logout y Swagger | Ajustes de seguridad pública y validación |
| Día 5 | Apoyo en usuarios y roles | CRUD de categorías y eventos | Filtros, paginación y ordenamiento |
| Día 6 | Revisión de relaciones y ownership | CRUD de sesiones y mappers | Inscripciones transaccionales y validaciones de cupos |
| Día 7 | Apoyo en errores comunes y Redis | `@RestControllerAdvice`, auditoría y reportes | Docker final, pruebas unitarias y despliegue |

---

## 5. Tareas Mínimas Por Integrante Para Que El Reparto Sea Equilibrado

### Persona 1 debe entregar

- Base técnica del proyecto.
- Configuración de seguridad base.
- Flujo de registro autenticado.
- Documentación OpenAPI funcional.

### Persona 2 debe entregar

- Módulos de catálogo y eventos.
- Paginación y filtros.
- Reglas de ownership.
- Parte importante de los DTOs y mappers.

### Persona 3 debe entregar

- Inscripciones y transacciones.
- Manejo centralizado de errores.
- Redis y rate limiting.
- Reportes y despliegue.

---

## 6. Estrategia De Git Para La Sustentación

Para que el historial de Git sea defendible ante la rúbrica:

1. Crear ramas por módulo y no por archivo.
2. Hacer commits pequeños y funcionales.
3. Alternar días de trabajo para que cada integrante tenga actividad en al menos 3 días distintos.
4. Mantener mensajes claros como `auth: add register endpoint` o `events: add pagination filter`.
5. Evitar commits de formato sin valor funcional.
6. Fusionar solo cuando el módulo pase pruebas o al menos compile.

Ejemplo de reparto mínimo sugerido por persona:

- Persona 1: 5 a 7 commits.
- Persona 2: 5 a 7 commits.
- Persona 3: 5 a 7 commits.

---

## 7. Checklist Final De Rubrica

- [ ] Cada integrante tiene al menos 5 commits funcionales.
- [ ] Cada integrante aparece en al menos 3 días diferentes del historial.
- [ ] Existe separación por módulos y responsabilidades.
- [ ] La aplicación compila y arranca con PostgreSQL.
- [ ] Swagger funciona y los endpoints protegidos están configurados.
- [ ] JWT, refresh token y logout están implementados.
- [ ] Eventos, categorías, sesiones e inscripciones funcionan.
- [ ] Excepciones y validación global están centralizadas.
- [ ] Redis protege contra abuso de solicitudes.
- [ ] Reportes PDF/Excel descargan correctamente.
- [ ] Docker y despliegue están documentados.

---

## 8. Recomendación Práctica

Si quieren trabajar con menos riesgo, usen esta regla:

- Persona 1 resuelve la base y la seguridad.
- Persona 2 resuelve el dominio del negocio.
- Persona 3 resuelve las inscripciones, transversales e infraestructura.

Eso evita que una sola persona cargue con todo y ayuda a que cada integrante pueda explicar una parte real del proyecto durante la defensa.