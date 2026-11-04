// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Nome e INE do concello por JDBC, para a busca de enderezos (ADR-0023). Sen caché: o INE cambia
 * cando o xestor edita os datos legais.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason = "municipality non leva municipality_id: a fila é o propio concello, buscada polo id")
class MunicipalityPlacesJdbc implements MunicipalityPlaces {

  private final JdbcClient jdbc;

  MunicipalityPlacesJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Place> place(UUID municipalityId) {
    return jdbc.sql("select name, ine_code from municipality where id = :id")
        .param("id", municipalityId)
        .query((rs, row) -> new Place(rs.getString("name"), rs.getString("ine_code")))
        .optional();
  }
}
