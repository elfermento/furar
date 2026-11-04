// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orde de bloqueo (ADR-0022, punto 4): o cambio de estado toma o bloqueo da instalación, así que
 * espera a quen o ten (no dominio 2, {@code booking} con {@code lockFacility}) e só despois sae.
 */
class FacilityLockIT extends AbstractIT {

  @Autowired FacilityQueries queries;
  @Autowired TransactionTemplate tx;
  @Autowired DataSource dataSource;

  /**
   * Fío propio para cada tarefa: no pool común (un só fío nun runner de 2 CPU) quen ten o bloqueo
   * ocupaba o único fío e a segunda escritura nunca saía.
   */
  private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();

  @Test
  void statusChangeWaitsForTheFacilityLock() throws Exception {
    String slug = uniqueSlug("lock");
    UUID municipality = id(createMunicipality(slug));
    Api.Response created =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/facilities",
                Map.of("name", "Pista bloqueada", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    UUID facility = id(created.json());

    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Boolean> holder =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.runAs(
                    new TenantRef(municipality, slug),
                    () ->
                        tx.execute(
                            status -> {
                              boolean ok = queries.lockFacility(facility);
                              locked.countDown();
                              await(release);
                              return ok;
                            })),
            async);
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Api.Response> change =
        CompletableFuture.supplyAsync(
            () ->
                api()
                    .put(
                        "/api/v1/municipalities/" + slug + "/facilities/" + facility + "/status",
                        Map.of("status", "TEMPORARILY_CLOSED"))
                    .ifMatch(0)
                    .token(TestTokens.manager(slug))
                    .send(),
            async);
    // A segunda transacción queda á espera dun bloqueo de fila na base, non remata. Mírase co rol
    // da API: pg_stat_activity só lle ensina a cada rol as súas sesións.
    assertThat(waitingOnALock(Duration.ofSeconds(10))).isTrue();
    assertThat(change).isNotDone();

    release.countDown();
    assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
    Api.Response response = change.get(10, TimeUnit.SECONDS);
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(response.json().get("status").asString()).isEqualTo("TEMPORARILY_CLOSED");
  }

  @Test
  void lockFacilityNeedsTheCallersTransaction() {
    String slug = uniqueSlug("lock");
    UUID municipality = id(createMunicipality(slug));
    TenantContext.runAs(
        new TenantRef(municipality, slug),
        () -> {
          try {
            queries.lockFacility(UUID.randomUUID());
            throw new AssertionError("lockFacility ran without a transaction");
          } catch (IllegalTransactionStateException expected) {
            // MANDATORY: o bloqueo só ten sentido dentro da transacción de quen chama.
          }
          Boolean locked = tx.execute(s -> queries.lockFacility(UUID.randomUUID()));
          assertThat(locked).isFalse();
        });
  }

  /**
   * Máis escrituras á vez sobre a mesma instalación ca conexións ten o pool: todas saen. Antes,
   * quen tiña o bloqueo pedía unha segunda conexión para o rexistro de actividade mentres as outras
   * gardaban as súas á espera da fila, e o pool quedaba cheo ata o connectionTimeout.
   */
  @Test
  void moreConcurrentWritesThanPoolConnectionsAllSucceed() throws Exception {
    FacilityRef f = facility();
    int writes = 3 * poolSize() + 1;
    List<CompletableFuture<Api.Response>> responses = new ArrayList<>();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < writes; i++) {
        String date = LocalDate.of(2026, 11, 1).plusDays(i).toString();
        responses.add(
            CompletableFuture.supplyAsync(
                () ->
                    api()
                        .post(f.url() + "/blocks", FacilityBlockIT.block(date, 480, 720))
                        .token(TestTokens.manager(f.slug()))
                        .send(),
                pool));
      }
      for (CompletableFuture<Api.Response> response : responses) {
        Api.Response r = response.get(60, TimeUnit.SECONDS);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
      }
    }
    Integer audited =
        jdbc.sql(
                """
                select count(*) from audit_event
                where municipality_id = :m and action = 'facility.block-create'
                  and outcome = 'SUCCESS'
                """)
            .param("m", f.municipality())
            .query(Integer.class)
            .single();
    assertThat(audited).isEqualTo(writes);
  }

  /**
   * Quen agarda o bloqueo da instalación máis de {@code lock_timeout} recibe 503, non agarda
   * sempre.
   */
  @Test
  void aLockHeldTooLongGivesBusy() throws Exception {
    FacilityRef f = facility();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Boolean> holder =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.runAs(
                    new TenantRef(f.municipality(), f.slug()),
                    () ->
                        tx.execute(
                            status -> {
                              boolean ok = queries.lockFacility(f.id());
                              locked.countDown();
                              await(release);
                              return ok;
                            })),
            async);
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    try {
      Api.Response response =
          api()
              .post(f.url() + "/blocks", FacilityBlockIT.block("2026-11-03", 480, 720))
              .token(TestTokens.manager(f.slug()))
              .send();
      assertThat(response.status()).as(response.toString()).isEqualTo(503);
      assertThat(response.json().get("code").asString()).isEqualTo("common.busy");
    } finally {
      release.countDown();
    }
    assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
    Integer failures =
        jdbc.sql(
                """
                select count(*) from audit_event
                where municipality_id = :m and action = 'facility.block-create'
                  and outcome = 'FAILURE'
                """)
            .param("m", f.municipality())
            .query(Integer.class)
            .single();
    assertThat(failures).isEqualTo(1);
  }

  record FacilityRef(String slug, UUID municipality, UUID id, String url) {}

  private FacilityRef facility() {
    String slug = uniqueSlug("lock");
    UUID municipality = id(createMunicipality(slug));
    String base = "/api/v1/municipalities/" + slug + "/facilities";
    Api.Response created =
        api()
            .post(base, Map.of("name", "Pista concorrida", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new FacilityRef(slug, municipality, id, base + "/" + id);
  }

  private int poolSize() {
    return ((HikariDataSource) dataSource).getMaximumPoolSize();
  }

  private boolean waitingOnALock(Duration timeout) throws InterruptedException {
    long end = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < end) {
      Integer waiting =
          appJdbc
              .sql(
                  """
                  select count(*) from pg_stat_activity
                  where wait_event_type = 'Lock' and query like '%for no key update%'
                  """)
              .query(Integer.class)
              .single();
      if (waiting > 0) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("not released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
