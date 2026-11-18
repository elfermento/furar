// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.people.DependentUsage;
import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Se unha persoa a cargo figura nalgunha reserva, de calquera concello e en calquera estado: chama
 * á función {@code dependent_has_bookings} (V202611101710), que se executa como o dono porque a RLS
 * de {@code booking_participant} só deixa ver o concello da transacción, e só devolve si ou non.
 * Sen táboas no SQL. É a SPI {@link DependentUsage} de {@code people} (ADR-0031, punto 3).
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "As persoas a cargo son da conta, común a todos os concellos; a función só di se a ficha"
            + " figura nalgunha reserva, sen ningún dato delas")
class DependentUsageSql implements DependentUsage {

  private final JdbcClient jdbc;

  DependentUsageSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isUsed(UUID dependentId) {
    return Boolean.TRUE.equals(
        jdbc.sql("select dependent_has_bookings(:dependent)")
            .param("dependent", dependentId)
            .query(Boolean.class)
            .single());
  }
}
