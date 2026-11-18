// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.ResidentBookings;
import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Se unha persoa reservou nalgún concello: chama á función {@code has_resident_bookings}
 * (V202610301001), que se executa como o dono porque a RLS de {@code booking} só deixa ver o
 * concello da transacción, e só devolve si ou non. Sen táboas no SQL.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "Unha conta persoal é persoal en todos os concellos; a función só di se a persoa ten"
            + " reservas, sen ningún dato delas")
class ResidentBookingsSql implements ResidentBookings {

  private final JdbcClient jdbc;

  ResidentBookingsSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean existAnywhere(String subject) {
    return Boolean.TRUE.equals(
        jdbc.sql("select has_resident_bookings(:subject)")
            .param("subject", subject)
            .query(Boolean.class)
            .single());
  }
}
