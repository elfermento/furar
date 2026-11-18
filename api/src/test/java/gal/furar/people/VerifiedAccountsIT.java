// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.security.VerifiedAccounts;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * As contas coñecidas polo seu correo verificado (ADR-0031, punto 4): unha escritura por sesión, só
 * co correo verificado e normalizado; o cambio de correo; un correo que pasa a outra conta; e dúas
 * sesións á vez co mesmo correo, que non rompen ningunha petición.
 */
class VerifiedAccountsIT extends AbstractIT {

  @Autowired VerifiedAccounts accounts;

  String email(String subject) {
    return jdbc.sql("select email from account_email where account_subject = :s")
        .param("s", subject)
        .query(String.class)
        .single();
  }

  Instant seenAt(String subject) {
    return Instant.ofEpochSecond(
        jdbc.sql(
                "select extract(epoch from seen_at)::bigint from account_email"
                    + " where account_subject = :s")
            .param("s", subject)
            .query(Long.class)
            .single());
  }

  int count(String subject) {
    return jdbc.sql("select count(*) from account_email where account_subject = :s")
        .param("s", subject)
        .query(Integer.class)
        .single();
  }

  @Test
  void aRequestWithAVerifiedEmailRecordsTheAccountOncePerSession() {
    String sub = "conta-" + UUID.randomUUID();
    Instant signedIn = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(120);
    String token =
        TestTokens.withoutRolesClaim(
            sub, Map.of("auth_time", signedIn.getEpochSecond(), "email", sub + "@Example.ORG"));

    assertThat(api().get("/api/v1/me/dependents").token(token).send().status()).isEqualTo(200);

    assertThat(email(sub)).as("normalizado").isEqualTo(sub + "@example.org");
    assertThat(seenAt(sub)).isEqualTo(signedIn);

    // Outra petición da mesma sesión non escribe; unha sesión máis nova, si.
    jdbc.sql("update account_email set email = 'marca@example.org' where account_subject = :s")
        .param("s", sub)
        .update();
    accounts.seen(sub, "marca@example.org", signedIn);
    assertThat(seenAt(sub)).isEqualTo(signedIn);
    Instant later = signedIn.plusSeconds(60);
    accounts.seen(sub, " Nova@Example.org ", later);
    assertThat(email(sub)).as("cambio de correo").isEqualTo("nova@example.org");
    assertThat(seenAt(sub)).isEqualTo(later);
  }

  @Test
  void withoutVerifiedEmailOrSessionTimeNothingIsRecorded() {
    String unverified = "sen-verificar-" + UUID.randomUUID();
    api().get("/api/v1/me/dependents").token(TestTokens.person(unverified, false)).send();
    assertThat(count(unverified)).isZero();

    String noAuthTime = "sen-hora-" + UUID.randomUUID();
    accounts.seen(noAuthTime, noAuthTime + "@example.org", null);
    assertThat(count(noAuthTime)).isZero();
  }

  @Test
  void anEmailThatMovesToAnotherAccountLeavesTheOldOne() {
    String first = "primeira-" + UUID.randomUUID();
    String second = "segunda-" + UUID.randomUUID();
    String email = "compartido-" + UUID.randomUUID() + "@example.org";
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    accounts.seen(first, email, now.minusSeconds(60));
    accounts.seen(second, email, now);

    assertThat(count(first)).as("o correo é dunha soa conta: a última que entrou con el").isZero();
    assertThat(email(second)).isEqualTo(email);
  }

  @Test
  void twoSessionsAtOnceWithTheSameEmailNeverFail() throws Exception {
    String email = "carreira-" + UUID.randomUUID() + "@example.org";
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    List<String> subjects = new ArrayList<>();
    List<Callable<Void>> calls = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      String sub = "carreira-" + i + "-" + UUID.randomUUID();
      subjects.add(sub);
      calls.add(
          () -> {
            accounts.seen(sub, email, now);
            return null;
          });
    }
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    try {
      for (var done : pool.invokeAll(calls)) {
        done.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbc.sql("select count(*) from account_email where email = :e")
                .param("e", email)
                .query(Integer.class)
                .single())
        .as("como moito unha conta por correo; ningunha petición falla")
        .isLessThanOrEqualTo(1);
  }
}
