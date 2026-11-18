-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os deportes de cada uso (ADR-0025, revisión do 2026-11-18): un subconxunto dos da súa
-- instalación, como claves (o código do catálogo ou o id do deporte do concello, as mesmas que
-- `SportResponse`). Na mesma fila do uso: non hai FK, porque a instalación pode quitar un deporte
-- e o uso segue gardándoo; ao ler, só contan os que aínda ten a instalación, e se non queda
-- ningún (ou a lista está baleira) o uso ten todos os dela.
--
-- Os usos que xa hai quedan con todos os deportes da súa instalación, na súa orde: nada cambia
-- ata que alguén os edite.

alter table facility_use add column sports text[] not null default '{}';

update facility_use u
   set sports = coalesce(
       (select array_agg(coalesce(s.sport_code, s.custom_sport_id::text) order by s.position)
          from facility_sport s
         where s.municipality_id = u.municipality_id
           and s.facility_id = u.facility_id),
       '{}');
