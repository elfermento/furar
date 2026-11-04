// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.domain.InvitationDelivery;
import gal.furar.staff.domain.InvitationToken;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffInvitationId;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.TokenHash;
import gal.furar.testsupport.AbstractTenantIT;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link StaffInvitationSql} só le e cambia as invitacións do seu
 * concello, coa RLS da transacción e co concello como parámetro: un token doutro concello non se
 * atopa. Tamén as garantías da táboa (plan do dominio 4, §2.1): hash de 32 bytes e único, 7 días,
 * unha aberta por pertenza e nunca usada e substituída á vez.
 */
class StaffInvitationSqlTenantIT extends AbstractTenantIT {

  static final SecureRandom RANDOM = new SecureRandom();
  static final Instant AT = Instant.parse("2026-11-05T09:00:00Z");

  @Autowired StaffInvitationSql subject;
  @Autowired TransactionTemplate tx;

  UUID member(UUID municipality) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, role, status, language, invited_by, invited_at,
               created_at, updated_at, version)
            values (:id, :m, :email, 'STAFF', 'INVITED', 'gl', 'sub-xestora', now(), now(),
                    now(), 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", id + "@example.org")
        .update();
    return id;
  }

  /** Unha invitación por JDBC, sen filtro (o dono da base non ten RLS). */
  UUID invitation(UUID municipality, UUID member, TokenHash hash, Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_invitation
              (id, municipality_id, member_id, token_hash, requested_by, created_at, expires_at,
               delivery)
            values (:id, :m, :member, :hash, 'sub-xestora', cast(:at as timestamptz),
                    cast(:at as timestamptz) + interval '168 hours',
                    'SENT')
            """)
        .param("id", id)
        .param("m", municipality)
        .param("member", member)
        .param("hash", hash.bytes())
        .param("at", createdAt.atOffset(ZoneOffset.UTC))
        .update();
    return id;
  }

  @Override
  protected Object seed(UUID municipality) {
    return invitation(municipality, member(municipality), newHash(), AT);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            new ArrayList<Object>(
                subject.latestPerMember(municipality).stream().map(i -> i.id().value()).toList()));
  }

  @Test
  void everyMethodSeesAndChangesOnlyItsOwnMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("convite-a")));
    UUID b = id(createMunicipality(uniqueSlug("convite-b")));
    UUID memberA = member(a);
    UUID memberB = member(b);
    TokenHash hashA = newHash();
    TokenHash hashB = newHash();
    UUID invitationA = invitation(a, memberA, hashA, AT);
    UUID invitationB = invitation(b, memberB, hashB, AT);

    as(
        a,
        () -> {
          // O token de B, buscado en A, non existe.
          assertThat(subject.findByHash(a, hashB)).isEmpty();
          assertThat(subject.findByHash(a, hashA).orElseThrow().id().value())
              .isEqualTo(invitationA);
          assertThat(subject.latestOf(a, memberB)).isEmpty();
          assertThat(subject.sendsOf(a, memberB)).isEmpty();
          assertThat(subject.sendCountPerMember(a)).containsExactly(Map.entry(memberA, 1));
          assertThat(subject.sendsSince(a, AT.minusSeconds(1), 30)).containsExactly(AT);
          assertThat(subject.supersedeOpen(a, memberB, AT.plusSeconds(1))).isZero();
          assertThat(subject.markUsed(a, invitationB, AT.plusSeconds(1))).isFalse();
          assertThat(subject.recordDelivery(a, invitationB, InvitationDelivery.FAILED)).isFalse();
          return null;
        });

    assertThat(column(invitationB, "superseded_at")).isNull();
    assertThat(column(invitationB, "used_at")).isNull();
    assertThat(column(invitationB, "delivery")).isEqualTo("SENT");
  }

  @Test
  void writesAndReadsEveryColumnAndTheLifecycle() {
    UUID a = id(createMunicipality(uniqueSlug("convite-ida")));
    UUID member = member(a);
    InvitationToken token = InvitationToken.generate(RANDOM);
    StaffInvitation first =
        StaffInvitation.issue(
            StaffInvitationId.newId(),
            new StaffMemberId(member),
            token.hash(),
            "sub-xestora",
            AT.plusNanos(987_654_321));

    as(
        a,
        () -> {
          subject.insert(a, first);
          StaffInvitation read = subject.findByHash(a, token.hash()).orElseThrow();
          assertThat(read).usingRecursiveComparison().isEqualTo(first);
          assertThat(read.delivery()).isEqualTo(InvitationDelivery.PENDING);
          assertThat(subject.recordDelivery(a, first.id().value(), InvitationDelivery.SENT))
              .isTrue();
          // Xa ten resultado: non se cambia.
          assertThat(subject.recordDelivery(a, first.id().value(), InvitationDelivery.FAILED))
              .isFalse();
          assertThat(subject.latestOf(a, member).orElseThrow().delivery())
              .isEqualTo(InvitationDelivery.SENT);
          return null;
        });

    // Reenviar: substitúe a aberta e garda outra.
    StaffInvitation second =
        StaffInvitation.issue(
            StaffInvitationId.newId(),
            new StaffMemberId(member),
            newHash(),
            "sub-xestora",
            AT.plus(Duration.ofMinutes(15)));
    as(
        a,
        () -> {
          assertThat(subject.supersedeOpen(a, member, second.createdAt())).isEqualTo(1);
          subject.insert(a, second);
          assertThat(subject.findByHash(a, token.hash()).orElseThrow().supersededAt())
              .isEqualTo(second.createdAt());
          assertThat(subject.latestOf(a, member).orElseThrow().id()).isEqualTo(second.id());
          assertThat(subject.latestPerMember(a))
              .extracting(StaffInvitation::id)
              .containsExactly(second.id());
          assertThat(subject.sendsOf(a, member))
              .containsExactly(second.createdAt(), first.createdAt());
          assertThat(subject.sendCountPerMember(a)).containsExactly(Map.entry(member, 2));
          assertThat(subject.sendsSince(a, AT.plusSeconds(1), 30))
              .containsExactly(second.createdAt());
          assertThat(subject.sendsSince(a, AT.minusSeconds(1), 1))
              .containsExactly(second.createdAt());
          // A substituída xa non se usa; a nova, unha vez e antes de caducar.
          assertThat(subject.markUsed(a, first.id().value(), second.createdAt())).isFalse();
          assertThat(subject.markUsed(a, second.id().value(), second.expiresAt())).isFalse();
          assertThat(subject.markUsed(a, second.id().value(), second.createdAt().plusSeconds(5)))
              .isTrue();
          assertThat(subject.markUsed(a, second.id().value(), second.createdAt().plusSeconds(6)))
              .isFalse();
          assertThat(subject.supersedeOpen(a, member, second.createdAt().plusSeconds(7))).isZero();
          return null;
        });
  }

  /**
   * A conservación (ADR-0027, punto 14) só ve as invitacións do seu concello: as que borra
   * devólveas (para esquecer os seus envíos, A2) e as dunha pertenza doutro concello non saen.
   */
  @Test
  void retentionListsAndDeletesOnlyItsOwnInvitations() {
    UUID a = id(createMunicipality(uniqueSlug("convite-conserva-a")));
    UUID b = id(createMunicipality(uniqueSlug("convite-conserva-b")));
    UUID memberA = member(a);
    UUID memberB = member(b);
    UUID staleA = invitation(a, memberA, newHash(), AT);
    UUID staleB = invitation(b, memberB, newHash(), AT);
    Instant cutoff = AT.plus(Duration.ofDays(30));

    as(
        a,
        () -> {
          assertThat(subject.idsOf(a, List.of(memberA, memberB))).containsExactly(staleA);
          assertThat(subject.deleteUnusedExpiredBefore(a, cutoff)).containsExactly(staleA);
          assertThat(subject.deleteUnusedExpiredBefore(a, cutoff)).isEmpty();
          return null;
        });

    assertThat(
            jdbc.sql("select id from staff_invitation where id in (:ids)")
                .param("ids", List.of(staleA, staleB))
                .query(UUID.class)
                .list())
        .containsExactly(staleB);
  }

  @Test
  void theTableKeepsItsGuarantees() {
    UUID a = id(createMunicipality(uniqueSlug("convite-ck")));
    UUID b = id(createMunicipality(uniqueSlug("convite-ck-b")));
    UUID member = member(a);
    TokenHash hash = newHash();
    UUID open = invitation(a, member, hash, AT);

    // Unha aberta por pertenza.
    assertThatThrownBy(() -> invitation(a, member, newHash(), AT.plusSeconds(1)))
        .isInstanceOf(DataIntegrityViolationException.class);
    // O mesmo hash, nin sequera noutro concello.
    assertThatThrownBy(() -> invitation(b, member(b), hash, AT))
        .isInstanceOf(DataIntegrityViolationException.class);
    // A pertenza é do mesmo concello (FK composta).
    assertThatThrownBy(() -> invitation(b, member, newHash(), AT))
        .isInstanceOf(DataIntegrityViolationException.class);
    List<String> broken =
        List.of(
            "update staff_invitation set token_hash = '\\x00'::bytea where id = :id",
            "update staff_invitation set expires_at = created_at + interval '8 days' where id = :id",
            "update staff_invitation set used_at = created_at, superseded_at = created_at"
                + " where id = :id",
            "update staff_invitation set used_at = expires_at where id = :id",
            "update staff_invitation set used_at = created_at - interval '1 second' where id = :id",
            "update staff_invitation set superseded_at = created_at - interval '1 second'"
                + " where id = :id",
            "update staff_invitation set delivery = 'BOUNCED' where id = :id");
    for (String sql : broken) {
      assertThatThrownBy(() -> jdbc.sql(sql).param("id", open).update())
          .as(sql)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  private Object column(UUID id, String column) {
    return jdbc.sql("select " + column + " from staff_invitation where id = :id")
        .param("id", id)
        .query((rs, n) -> java.util.Optional.ofNullable(rs.getObject(1)))
        .single()
        .orElse(null);
  }

  private static TokenHash newHash() {
    return InvitationToken.generate(RANDOM).hash();
  }
}
