-- Script 00: Creación de la base de datos para eventos académicos
-- Ejecutar como superusuario (postgres) si es necesario

SELECT 'CREATE DATABASE academic_events_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'academic_events_db')\gexec
