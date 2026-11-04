#!/bin/sh
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Primeiro arranque de PostgreSQL (volume baleiro): base e rol propios para Keycloak.
# A base "furar" créaa a imaxe oficial con POSTGRES_DB/POSTGRES_USER. ADR-0015.
set -eu
: "${KC_DB_PASSWORD:?KC_DB_PASSWORD é obrigatoria}"

psql -v ON_ERROR_STOP=1 -v kc_password="$KC_DB_PASSWORD" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE keycloak LOGIN PASSWORD :'kc_password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE ALL ON DATABASE furar FROM PUBLIC;
EOSQL
