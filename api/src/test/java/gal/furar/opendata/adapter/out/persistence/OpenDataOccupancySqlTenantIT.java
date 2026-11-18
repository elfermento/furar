// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.opendata.application.port.out.OpenDataStore.StoredDay;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): a ocupación publicada de {@link OpenDataOccupancySql} só se le e se borra no seu
 * concello, coa RLS da transacción e co concello como parámetro.
 */
class OpenDataOccupancySqlTenantIT extends AbstractTenantIT {

  static final LocalDate DAY = LocalDate.of(2026, 10, 15);

  @Autowired OpenDataOccupancySql subject;
  @Autowired TransactionTemplate tx;

  final List<UUID> seeded = Collections.synchronizedList(new ArrayList<>());

  /** Unha instalación nova cun día gardado; devolve a instalación. */
  UUID insertDay(UUID municipality) {
    UUID facility = UUID.randomUUID();
    jdbc.sql(
            """
            insert into facility
              (id, municipality_id, name, street, postal_code, locality, latitude,
               longitude, created_at, updated_at, version)
            values (:id, :m, :name, 'Rúa do Alto', '15570', 'Narón', 43.5, -8.2,
                    now(), now(), 0)
            """)
        .param("id", facility)
        .param("m", municipality)
        .param("name", "Pista " + facility)
        .update();
    List<Integer> quarters = new ArrayList<>(Collections.nCopies(96, null));
    List<Integer> uses = new ArrayList<>(Collections.nCopies(96, null));
    quarters.set(40, 100);
    uses.set(40, 1);
    TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.executeWithoutResult(
                s ->
                    subject.insert(
                        municipality,
                        new StoredDay(facility, DAY, quarters, List.of(UUID.randomUUID()), uses))));
    seeded.add(facility);
    return facility;
  }

  @Override
  protected Object seed(UUID municipality) {
    return insertDay(municipality);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status ->
                    subject.find(municipality, DAY, DAY, List.copyOf(seeded)).stream()
                        .map(d -> (Object) d.facilityId())
                        .toList()));
  }

  /** Co concello alleo como parámetro, nin se le nin se borra nada. */
  @Test
  void everyStatementSeesOnlyItsMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("odo-a")));
    UUID b = id(createMunicipality(uniqueSlug("odo-b")));
    UUID fa = insertDay(a);
    insertDay(b);

    List<Object> own = reads(a, a);
    assertThat(own).containsExactly(1, 1L, 1L);
    assertThat(reads(a, b)).containsExactly(0, 0L, 0L);

    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  subject.deleteExcept(b, List.of());
                  subject.deleteBetween(b, DAY, DAY);
                  subject.deleteBefore(b, DAY.plusDays(1));
                  subject.deleteAll(b);
                }));
    assertThat(reads(a, a)).containsExactly(1, 1L, 1L);
    TenantContext.runAs(
        TenantRef.of(a), () -> tx.executeWithoutResult(s -> subject.deleteExcept(a, List.of(fa))));
    assertThat(reads(a, a)).containsExactly(1, 1L, 1L);
    TenantContext.runAs(
        TenantRef.of(a), () -> tx.executeWithoutResult(s -> subject.deleteBetween(a, DAY, DAY)));
    assertThat(reads(a, a)).containsExactly(0, 0L, 0L);
  }

  List<Object> reads(UUID as, UUID param) {
    return TenantContext.runAs(
        TenantRef.of(as),
        () ->
            tx.execute(
                status ->
                    List.<Object>of(
                        subject.find(param, DAY, DAY, List.copyOf(seeded)).size(),
                        subject.count(param, DAY, DAY, List.copyOf(seeded)),
                        subject.quarters(param, DAY, DAY).quarters())));
  }
}
