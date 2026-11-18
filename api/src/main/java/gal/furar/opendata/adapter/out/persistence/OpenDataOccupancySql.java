// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.out.persistence;

import gal.furar.opendata.application.port.out.OpenDataStore.QuarterCount;
import gal.furar.opendata.application.port.out.OpenDataStore.StoredDay;
import gal.furar.shared.persistence.NativeSql;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A ocupación publicada en SQL (ADR-0034, punto 3): un día por fila, cun vector de tramos de 15
 * minutos. Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * OpenDataOccupancySqlTenantIT}). Sen persoa, reserva nin texto.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class OpenDataOccupancySql {

  /** Ningún valor real: o recheo dunha lista baleira. */
  private static final UUID NONE = new UUID(0, 0);

  private final JdbcClient jdbc;

  OpenDataOccupancySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void deleteAll(UUID municipality) {
    jdbc.sql(
            """
            delete from open_data_occupancy
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .update();
  }

  void deleteExcept(UUID municipality, Collection<UUID> facilityIds) {
    jdbc.sql(
            """
            delete from open_data_occupancy
            where facility_id not in (:facilities)
              and municipality_id = :tenant
            """)
        .param("facilities", facilityIds.isEmpty() ? List.of(NONE) : List.copyOf(facilityIds))
        .param("tenant", municipality)
        .update();
  }

  void deleteBetween(UUID municipality, LocalDate from, LocalDate to) {
    jdbc.sql(
            """
            delete from open_data_occupancy
            where day >= :since and day <= :until
              and municipality_id = :tenant
            """)
        .param("since", from)
        .param("until", to)
        .param("tenant", municipality)
        .update();
  }

  void deleteBefore(UUID municipality, LocalDate day) {
    jdbc.sql(
            """
            delete from open_data_occupancy
            where day < :since
              and municipality_id = :tenant
            """)
        .param("since", day)
        .param("tenant", municipality)
        .update();
  }

  void insert(UUID municipality, StoredDay day) {
    jdbc.sql(
            """
            insert into open_data_occupancy
              (municipality_id, facility_id, day, quarters, uses, quarter_uses)
            values (:tenant, :facility, :day, :quarters, :uses, :quarterUses)
            """)
        .param("tenant", municipality)
        .param("facility", day.facilityId())
        .param("day", day.day())
        .param("quarters", shorts(day.quarters()))
        .param("uses", day.uses().toArray(UUID[]::new))
        .param("quarterUses", shorts(day.quarterUses()))
        .update();
  }

  List<StoredDay> find(
      UUID municipality, LocalDate from, LocalDate to, Collection<UUID> facilityIds) {
    return jdbc.sql(
            """
            select facility_id, day, quarters, uses, quarter_uses
            from open_data_occupancy
            where day >= :since and day <= :until
              and facility_id in (:facilities)
              and municipality_id = :tenant
            order by facility_id, day
            """)
        .param("since", from)
        .param("until", to)
        .param("facilities", facilityIds.isEmpty() ? List.of(NONE) : List.copyOf(facilityIds))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new StoredDay(
                    rs.getObject("facility_id", UUID.class),
                    rs.getObject("day", LocalDate.class),
                    integers(rs, "quarters"),
                    uuids(rs, "uses"),
                    integers(rs, "quarter_uses")))
        .list();
  }

  long count(UUID municipality, LocalDate from, LocalDate to, Collection<UUID> facilityIds) {
    return jdbc.sql(
            """
            select count(*)
            from open_data_occupancy
            where day >= :since and day <= :until
              and facility_id in (:facilities)
              and municipality_id = :tenant
            """)
        .param("since", from)
        .param("until", to)
        .param("facilities", facilityIds.isEmpty() ? List.of(NONE) : List.copyOf(facilityIds))
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  QuarterCount quarters(UUID municipality, LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            select coalesce(sum(cardinality(array_remove(quarters, null))), 0) as quarters,
                   count(distinct day) as days
            from open_data_occupancy
            where day >= :since and day <= :until
              and municipality_id = :tenant
            """)
        .param("since", from)
        .param("until", to)
        .param("tenant", municipality)
        .query((rs, n) -> new QuarterCount(rs.getLong("quarters"), rs.getLong("days")))
        .single();
  }

  private static Short[] shorts(List<Integer> values) {
    return values.stream().map(v -> v == null ? null : v.shortValue()).toArray(Short[]::new);
  }

  private static List<Integer> integers(ResultSet rs, String column) throws SQLException {
    Array array = rs.getArray(column);
    Object[] values = (Object[]) array.getArray();
    List<@Nullable Integer> result = new ArrayList<>(values.length);
    for (Object v : values) {
      result.add(v == null ? null : ((Number) v).intValue());
    }
    return result;
  }

  private static List<UUID> uuids(ResultSet rs, String column) throws SQLException {
    Array array = rs.getArray(column);
    return Arrays.stream((Object[]) array.getArray()).map(v -> (UUID) v).toList();
  }
}
