// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.testsupport.AbstractIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RLS polo Spring real (ADR-0006, ARQ3-1, ARQ4-1, T18), como {@code furar_app}: o repositorio JPA e
 * o {@code JdbcClient} da aplicación, co {@link TenantJpaTransactionManager}. Casos:
 *
 * <ol type="a">
 *   <li>cada transacción ve só o seu concello, tamén por SQL nativo sen filtro;
 *   <li>a mesma conexión (mesmo {@code pg_backend_pid}), tras unha transacción de A, non ve A nunha
 *       transacción sen concello nin nunha de B; sen transacción tampouco ve nada;
 *   <li>un adaptador do concello sen transacción lanza {@link MissingTenantException};
 *   <li>{@code runAs(B)} dentro dunha transacción de A falla; con {@code REQUIRES_NEW} despois de
 *       {@code runAs}, si se pode.
 * </ol>
 */
class TenantRlsSpringIT extends AbstractIT {

  private static final String ROWS = "select municipality_id from custom_field";
  private static final String SETTING =
      "select coalesce(current_setting('furar.municipality_id', true), '')";
  private static final String PID = "select pg_backend_pid()";

  @Autowired CustomFieldDefinitionRepository repository;
  @Autowired PlatformTransactionManager transactions;
  @Autowired TransactionTemplate tx;

  UUID a;
  UUID b;
  UUID fieldA;
  UUID fieldB;

  @BeforeEach
  void setUp() {
    a = id(createMunicipality(uniqueSlug("rls-a")));
    b = id(createMunicipality(uniqueSlug("rls-b")));
    fieldA = insertField(a, "rls_field", "PUBLIC");
    fieldB = insertField(b, "rls_field", "PUBLIC");
  }

  @Test
  void transactionSeesOnlyItsMunicipality() {
    assertThat(transactions).isInstanceOf(TenantJpaTransactionManager.class);
    TenantContext.runAs(
        TenantRef.of(a),
        () ->
            tx.executeWithoutResult(
                status -> {
                  assertThat(appJdbc.sql(SETTING).query(String.class).single())
                      .isEqualTo(a.toString());
                  assertThat(appJdbc.sql(ROWS).query(UUID.class).list()).containsOnly(a);
                  assertThat(
                          appJdbc
                              .sql(ROWS + " where municipality_id = :b")
                              .param("b", b)
                              .query(UUID.class)
                              .list())
                      .as("native SQL asking for B explicitly")
                      .isEmpty();
                  assertThat(repository.findAll())
                      .extracting(d -> d.id().value())
                      .contains(fieldA)
                      .doesNotContain(fieldB);
                }));
  }

  @Test
  void reusedConnectionNeverCarriesThePreviousMunicipality() {
    int pidA =
        TenantContext.runAs(
            TenantRef.of(a),
            () -> tx.execute(status -> appJdbc.sql(PID).query(Integer.class).single()));

    record Seen(int pid, String setting, List<UUID> rows) {}
    Seen none =
        tx.execute(
            status ->
                new Seen(
                    appJdbc.sql(PID).query(Integer.class).single(),
                    appJdbc.sql(SETTING).query(String.class).single(),
                    appJdbc.sql(ROWS).query(UUID.class).list()));
    assertThat(none.pid())
        .as("Hikari hands the same connection back to the thread")
        .isEqualTo(pidA);
    assertThat(none.setting()).isEmpty();
    assertThat(none.rows()).isEmpty();

    Seen inB =
        TenantContext.runAs(
            TenantRef.of(b),
            () ->
                tx.execute(
                    status ->
                        new Seen(
                            appJdbc.sql(PID).query(Integer.class).single(),
                            appJdbc.sql(SETTING).query(String.class).single(),
                            appJdbc.sql(ROWS).query(UUID.class).list())));
    assertThat(inB.pid()).isEqualTo(pidA);
    assertThat(inB.setting()).isEqualTo(b.toString());
    assertThat(inB.rows()).containsOnly(b);

    // Sen transacción (auto-commit), nin sequera dentro do concello: a RLS non ten concello.
    List<UUID> autoCommit =
        TenantContext.runAs(TenantRef.of(a), () -> appJdbc.sql(ROWS).query(UUID.class).list());
    assertThat(autoCommit).isEmpty();
  }

  @Test
  void adapterWithoutTransactionFailsClearly() {
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    TenantRef.of(a), () -> repository.findAll().stream().map(this::code).toList()))
        .isInstanceOf(MissingTenantException.class)
        .hasMessageContaining("No transaction");
  }

  @Test
  void switchingMunicipalityInsideATransactionFails() {
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    TenantRef.of(a),
                    () ->
                        tx.executeWithoutResult(
                            status ->
                                TenantContext.runAs(
                                    TenantRef.of(b), () -> appJdbc.sql(ROWS).query(UUID.class)))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot switch municipality inside a transaction");
    assertThat(TenantContext.current()).isEmpty();

    // O camiño correcto: primeiro o concello, despois unha transacción nova.
    TransactionTemplate requiresNew = new TransactionTemplate(transactions);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    List<UUID> rowsB =
        TenantContext.runAs(
            TenantRef.of(b),
            () -> requiresNew.execute(status -> appJdbc.sql(ROWS).query(UUID.class).list()));
    assertThat(rowsB).containsOnly(b);
  }

  private String code(CustomFieldDefinition definition) {
    return definition.code();
  }
}
