// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.application.port.in.ActivityEntry.ActorKind;
import gal.furar.staff.domain.ResendAvailability;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffMember;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha pertenza tal como a ve a xestión do persoal (plan do dominio 4, §2.3).
 *
 * @param member a pertenza
 * @param invitedBy quen a invitou
 * @param invitation a última invitación, só mentres a pertenza está invitada: di cando caduca e se
 *     o correo saíu
 * @param resend se se pode reenviar e desde cando, co mesmo criterio ca {@code invitation}
 * @param identityBlocked se o correo e o nome están bloqueados ({@link
 *     StaffMember#isIdentityBlocked}): a API non os devolve
 */
public record StaffMemberView(
    StaffMember member,
    Inviter invitedBy,
    @Nullable StaffInvitation invitation,
    @Nullable ResendAvailability resend,
    boolean identityBlocked) {

  public StaffMemberView {
    Objects.requireNonNull(invitedBy, "invitedBy");
  }

  /**
   * Quen invitou, co mesmo criterio ca o rexistro de actividade ({@link ActorKind}): con pertenza
   * no concello (tamén desactivada ou anonimizada), persoal ({@code MEMBER}); sen ela, a plataforma
   * ({@code PLATFORM}), porque só ela invita sen pertenza ({@code staff.invite} é de {@code
   * MANAGER} e {@code PLATFORM_ADMIN}). Da plataforma non sae ningún dato da conta.
   *
   * @param kind {@code MEMBER} ou {@code PLATFORM}
   * @param name o nome, se é persoal e o ten; nulo se non (a plataforma, unha persoa sen nome ou
   *     unha desactivación bloqueada)
   */
  public record Inviter(ActorKind kind, @Nullable String name) {

    public Inviter {
      Objects.requireNonNull(kind, "kind");
      if (kind != ActorKind.MEMBER && kind != ActorKind.PLATFORM) {
        throw new IllegalArgumentException("An inviter is a member or the platform: " + kind);
      }
      if (kind == ActorKind.PLATFORM && name != null) {
        throw new IllegalArgumentException("The platform inviter has no name");
      }
    }

    /**
     * Quen invitou, polas pertenzas no concello do seu {@code sub} (a máis nova primeiro). O nome
     * dunha desactivación bloqueada ({@link StaffMember#isIdentityBlocked}) non sae: segue sendo
     * persoal, sen nome.
     *
     * @param memberships as pertenzas do {@code sub} de quen invitou neste concello
     * @param visibleSince o primeiro instante que aínda se ve no rexistro
     */
    public static Inviter of(List<StaffMember> memberships, Instant visibleSince) {
      if (memberships.isEmpty()) {
        return new Inviter(ActorKind.PLATFORM, null);
      }
      String name =
          memberships.stream()
              .filter(m -> !m.isIdentityBlocked(visibleSince))
              .map(StaffMember::displayName)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      return new Inviter(ActorKind.MEMBER, name);
    }
  }
}
