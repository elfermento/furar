-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- O rol da API (furar_app) só le o historial de Flyway (SEC2-03, ADR-0015). Os privilexios por
-- defecto de 05-furar.sh dan DML en toda táboa nova de furar_owner, tamén nesta; sen este REVOKE,
-- unha inxección SQL como furar_app podería borrar ou falsear o historial das migracións.
-- O rol vén de spring.datasource.username (placeholder approle). Non se aplica se non existe ou se
-- coincide co dono (instalación cun só rol): Flyway segue precisando escribir no seu historial.

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke insert, update, delete, truncate on %I from %I',
                   '${flyway:table}', '${approle}');
  end if;
end
$$;
