// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.MissingTenantException;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.testsupport.AbstractIT;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D14 (ADR-0006): sen concello no contexto, (1) o adaptador lanza {@link MissingTenantException};
 * (2) unha sesión aberta igualmente le 0 filas e non pode escribir (centinela + FK, e RLS); (3)
 * {@code runAsPlatform} só funciona desde unha clase {@link PlatformTask} e, coa RLS, non le as
 * táboas do concello.
 */
class MissingTenantIT extends AbstractIT {

  @Autowired CustomFieldDefinitionRepository repository;
  @Autowired CustomFieldJpaRepository jpa;
  @Autowired TransactionTemplate tx;

  @BeforeEach
  void setUp() {
    String slug = uniqueSlug("missing");
    // Por JDBC: se o resolver non filtrase, caería a aserción de 0 filas, non o setUp.
    insertField(id(createMunicipality(slug)), "some_field", "PUBLIC");
  }

  @Test
  void adapterFailsClearlyWithoutTenant() {
    assertThat(TenantContext.current()).isEmpty();
    assertThatThrownBy(() -> repository.findByTarget(CustomFieldTarget.MUNICIPALITY))
        .isInstanceOf(MissingTenantException.class);
    assertThatThrownBy(() -> repository.insert(definition()))
        .isInstanceOf(MissingTenantException.class);
  }

  @Test
  void sessionWithoutTenantReadsNothingAndCannotWrite() {
    Integer rows = jdbc.sql("select count(*) from custom_field").query(Integer.class).single();
    assertThat(rows).isPositive();
    long visible = tx.execute(status -> jpa.count());
    assertThat(visible).isZero();
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status ->
                        jpa.saveAndFlush(CustomFieldPersistenceMapper.newEntity(definition()))))
        // RLS (with check, 42501) ou, co rol furar_app_bypass, a FK cara ao centinela (23503).
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void runAsPlatformNeedsPlatformTask() {
    assertThatThrownBy(() -> TenantContext.runAsPlatform(() -> 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("@PlatformTask");
    long all = new AllMunicipalitiesProbe().countAll(tx, jpa);
    Integer rows = jdbc.sql("select count(*) from custom_field").query(Integer.class).single();
    // Hibernate non filtra como plataforma, pero a RLS si: as táboas do concello só se len
    // concello a concello (runAs + transacción), nunca todas á vez (ADR-0006).
    assertThat(all).isEqualTo(rowLevelSecurityEnforced() ? 0L : rows.longValue());
  }

  @PlatformTask
  static final class AllMunicipalitiesProbe {
    long countAll(TransactionTemplate tx, CustomFieldJpaRepository jpa) {
      return TenantContext.runAsPlatform(() -> tx.execute(status -> jpa.count()));
    }
  }

  private static CustomFieldDefinition definition() {
    return CustomFieldDefinition.create(
        CustomFieldDefinitionId.newId(),
        "orphan_field",
        FieldType.TEXT,
        new DefinitionSpec(
            new LocalizedText("Orfo", "Huérfano"),
            null,
            null,
            List.of(),
            false,
            List.of(new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.PUBLIC))),
        Instant.now());
  }
}
