#!/bin/sh
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Primeiro arranque de PostgreSQL (volume baleiro): roles de Furar sen superusuario (ADR-0015).
#   furar_owner  dono da base e do esquema; só o usa Flyway (DDL das migracións).
#   furar_app    o que usa a API: só SELECT/INSERT/UPDATE/DELETE nas táboas que crea furar_owner.
#                Sen DDL, sen acceso á base keycloak, sen COPY ... PROGRAM nin saltarse RLS.
# O superusuario (POSTGRES_USER) queda só para este script, copias e restauracións.
# Os mesmos roles úsanse nos IT (PostgresContainers copia este ficheiro ao contedor).
set -eu
: "${FURAR_DB_OWNER_PASSWORD:?FURAR_DB_OWNER_PASSWORD é obrigatoria}"
: "${FURAR_DB_PASSWORD:?FURAR_DB_PASSWORD é obrigatoria}"

psql -v ON_ERROR_STOP=1 -v owner_password="$FURAR_DB_OWNER_PASSWORD" \
  -v app_password="$FURAR_DB_PASSWORD" -v db="$POSTGRES_DB" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE furar_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD :'owner_password';
CREATE ROLE furar_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD :'app_password';
ALTER DATABASE :"db" OWNER TO furar_owner;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO furar_app;
-- PostgreSQL 15+: o esquema public é de pg_database_owner (agora furar_owner) e PUBLIC non crea.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO furar_app;
-- Toda táboa ou secuencia que cree Flyway (furar_owner) nace con permisos só de datos para a API.
ALTER DEFAULT PRIVILEGES FOR ROLE furar_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO furar_app;
ALTER DEFAULT PRIVILEGES FOR ROLE furar_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO furar_app;
EOSQL
