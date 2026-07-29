-- Script 01: Esquema de Tablas e Inserción de Datos Iniciales
-- Base de datos: academic_events_db

-- Eliminar tablas si existen para garantizar limpieza de esquema
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS registrations CASCADE;
DROP TABLE IF EXISTS sessions CASCADE;
DROP TABLE IF EXISTS events CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. Tabla Roles
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- 2. Tabla Users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- 3. Tabla Intermedia User Roles
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 4. Tabla Categorías
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- 5. Tabla Eventos
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    modality VARCHAR(30) NOT NULL CHECK (modality IN ('ONLINE', 'PRESENTIAL', 'HYBRID')),
    location VARCHAR(200),
    capacity INT NOT NULL CHECK (capacity > 0),
    available_seats INT NOT NULL CHECK (available_seats >= 0),
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'FINISHED')),
    organizer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_dates CHECK (end_date > start_date),
    CONSTRAINT chk_seats CHECK (available_seats <= capacity)
);

-- 6. Tabla Sesiones
CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_session_times CHECK (end_time > start_time)
);

-- 7. Tabla Inscripciones
CREATE TABLE registrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    registration_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_user_event UNIQUE (user_id, event_id)
);

-- 8. Tabla Auditoría
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_name VARCHAR(100),
    resource_id VARCHAR(50),
    ip_address VARCHAR(45),
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indices de Rendimiento
CREATE INDEX idx_events_category ON events(category_id);
CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_dates ON events(start_date, end_date);
CREATE INDEX idx_registrations_user ON registrations(user_id);
CREATE INDEX idx_registrations_event ON registrations(event_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);

-- ========================================================
-- PRECARGA DE DATOS INICIALES
-- Contraseña por defecto para todos los usuarios: "password123"
-- Hash BCrypt: $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0Vgh5iV20H13n48N3jC
-- ========================================================

-- Insertar Roles
INSERT INTO roles (id, name) VALUES 
(1, 'ROLE_ADMIN'),
(2, 'ROLE_ORGANIZER'),
(3, 'ROLE_PARTICIPANT');

-- Insertar Usuarios Iniciales
INSERT INTO users (id, name, email, password, enabled, account_locked, created_at) VALUES 
(1, 'Administrador General', 'admin@ups.edu.ec', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0Vgh5iV20H13n48N3jC', true, false, CURRENT_TIMESTAMP),
(2, 'Dr. Juan Pérez (Organizador)', 'organizer@ups.edu.ec', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0Vgh5iV20H13n48N3jC', true, false, CURRENT_TIMESTAMP),
(3, 'Estudiante Carlos López', 'student@ups.edu.ec', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0Vgh5iV20H13n48N3jC', true, false, CURRENT_TIMESTAMP);

-- Asignar Roles a Usuarios
INSERT INTO user_roles (user_id, role_id) VALUES 
(1, 1), -- Admin
(2, 2), -- Organizer
(3, 3); -- Participant

-- Insertar Categorías
INSERT INTO categories (id, name, description, created_at) VALUES 
(1, 'Inteligencia Artificial y Ciencia de Datos', 'Conferencias, talleres y seminarios sobre IA, ML y análisis masivo de datos.', CURRENT_TIMESTAMP),
(2, 'Ciberseguridad y Redes', 'Eventos centrados en seguridad de la información, hacking ético y redes avanzadas.', CURRENT_TIMESTAMP),
(3, 'Desarrollo de Software y Arquitectura Web', 'Seminarios de buenas prácticas, microservicios y frameworks modernos.', CURRENT_TIMESTAMP);

-- Insertar Evento de Ejemplo
INSERT INTO events (id, title, description, modality, location, capacity, available_seats, start_date, end_date, status, organizer_id, category_id, created_at) VALUES 
(1, 'Congreso Internacional de IA 2026', 'Magno evento sobre los últimos avances en Inteligencia Artificial y Deep Learning.', 'HYBRID', 'Auditorio Leónidas Proaño / Zoom', 100, 99, '2026-09-10 09:00:00+00', '2026-09-12 18:00:00+00', 'PUBLISHED', 2, 1, CURRENT_TIMESTAMP);

-- Insertar Sesión de Ejemplo
INSERT INTO sessions (id, title, description, start_time, end_time, room, event_id, created_at) VALUES 
(1, 'Keynote: Redes Neuronales Transformer', 'Presentación magistral sobre transformers en visión y PNL.', '2026-09-10 10:00:00+00', '2026-09-10 12:00:00+00', 'Aula Magna 1', 1, CURRENT_TIMESTAMP);

-- Insertar Inscripción de Ejemplo
INSERT INTO registrations (id, user_id, event_id, registration_date, status, created_at) VALUES 
(1, 3, 1, CURRENT_TIMESTAMP, 'CONFIRMED', CURRENT_TIMESTAMP);

-- Reset de Secuencias Post-Insert
SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));
SELECT setval('events_id_seq', (SELECT MAX(id) FROM events));
SELECT setval('sessions_id_seq', (SELECT MAX(id) FROM sessions));
SELECT setval('registrations_id_seq', (SELECT MAX(id) FROM registrations));
