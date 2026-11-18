// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.opendata.application.port.out.OpenDataStore.DatasetState;
import gal.furar.opendata.domain.Dataset;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o estado dos conxuntos de {@link OpenDataDatasetSql} só se le, garda e borra no
 * seu concello.
 */
class OpenDataDatasetSqlTenantIT extends AbstractTenantIT {

  @Autowired OpenDataDatasetSql subject;
  @Autowired TransactionTemplate tx;

  @Override
  protected Object seed(UUID municipality) {
    String checksum = "sum-" + municipality;
    TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.executeWithoutResult(
                s ->
                    subject.save(
                        municipality,
                        Dataset.FACILITIES,
                        new DatasetState(checksum, LocalDate.of(2026, 11, 11), null))));
    return checksum;
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return TenantContext.runAs(
        TenantRef.of(municipality),
        () ->
            tx.execute(
                status ->
                    subject.all(municipality).values().stream()
                        .map(d -> (Object) d.checksum())
                        .toList()));
  }

  /** Gardar outra vez cambia a fila; borrar co concello alleo non toca nada. */
  @Test
  void savesUpdatesAndDeletesOnlyItsMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("odd-a")));
    UUID b = id(createMunicipality(uniqueSlug("odd-b")));
    seed(a);
    seed(b);
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                s -> {
                  subject.save(
                      a,
                      Dataset.FACILITIES,
                      new DatasetState("new", LocalDate.of(2026, 11, 12), null));
                  subject.deleteAll(b);
                }));
    assertThat(visibleTo(a)).containsExactly("new");
    assertThat(visibleTo(b)).containsExactly("sum-" + b);
  }
}
