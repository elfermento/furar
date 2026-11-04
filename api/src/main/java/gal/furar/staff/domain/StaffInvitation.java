// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un envío da invitación dunha pertenza (plan do dominio 4, §1.2.6). Inmutable: cada transición
 * devolve unha copia (A13). Garda só o hash do token. Vale mentres non estea usada, substituída
 * (por un reenvío ou unha desactivación) nin caducada; a ligazón ábrese sen gastala, e só aceptar a
 * usa.
 */
public final class StaffInvitation {

  private final StaffInvitationId id;
  private final StaffMemberId memberId;
  private final TokenHash tokenHash;
  private final String requestedBy;
  private final Instant createdAt;
  private final Instant expiresAt;
  private final @Nullable Instant usedAt;
  private final @Nullable Instant supersededAt;
  private final InvitationDelivery delivery;

  private StaffInvitation(
      StaffInvitationId id,
      StaffMemberId memberId,
      TokenHash tokenHash,
      String requestedBy,
      Instant createdAt,
      Instant expiresAt,
      @Nullable Instant usedAt,
      @Nullable Instant supersededAt,
      InvitationDelivery delivery) {
    this.id = Objects.requireNonNull(id, "id");
    this.memberId = Objects.requireNonNull(memberId, "memberId");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    this.usedAt = usedAt;
    this.supersededAt = supersededAt;
    this.delivery = Objects.requireNonNull(delivery, "delivery");
    // As mesmas garantías ca a táboa staff_invitation.
    if (!expiresAt.equals(InvitationToken.expiresAt(createdAt))) {
      throw new IllegalStateException("An invitation lasts " + InvitationToken.VALIDITY);
    }
    if (usedAt != null && supersededAt != null) {
      throw new IllegalStateException("An invitation is either used or superseded");
    }
    if (usedAt != null && (usedAt.isBefore(createdAt) || !usedAt.isBefore(expiresAt))) {
      throw new IllegalStateException("Used outside its validity");
    }
    if (requestedBy.isBlank()) {
      throw new IllegalStateException("requestedBy is blank");
    }
  }

  /**
   * Envío novo, pendente de correo, que caduca aos 7 días. A hora vai en microsegundos, a precisión
   * da base, para que a caducidade gardada sexa exactamente a calculada.
   */
  public static StaffInvitation issue(
      StaffInvitationId id,
      StaffMemberId memberId,
      TokenHash tokenHash,
      String requestedBy,
      Instant now) {
    Instant at = now.truncatedTo(ChronoUnit.MICROS);
    return new StaffInvitation(
        id,
        memberId,
        tokenHash,
        requestedBy,
        at,
        InvitationToken.expiresAt(at),
        null,
        null,
        InvitationDelivery.PENDING);
  }

  /** Desde a base, tal como se gardou. */
  public static StaffInvitation rehydrate(
      StaffInvitationId id,
      StaffMemberId memberId,
      TokenHash tokenHash,
      String requestedBy,
      Instant createdAt,
      Instant expiresAt,
      @Nullable Instant usedAt,
      @Nullable Instant supersededAt,
      InvitationDelivery delivery) {
    return new StaffInvitation(
        id, memberId, tokenHash, requestedBy, createdAt, expiresAt, usedAt, supersededAt, delivery);
  }

  /** Caducada desde o instante {@code expiresAt} (incluído). */
  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  /** Nin usada nin substituída (pode estar caducada). */
  public boolean isOpen() {
    return usedAt == null && supersededAt == null;
  }

  /**
   * Comproba que o token aínda serve para ver ou aceptar a invitación.
   *
   * @throws DomainException {@code staff.invitation-invalid} se está usada ou substituída (a mesma
   *     resposta ca un token descoñecido) ou {@code staff.invitation-expired} se caducou
   */
  public void requireUsable(Instant now) {
    if (!isOpen()) {
      throw new DomainException(StaffError.INVITATION_INVALID);
    }
    if (isExpired(now)) {
      throw new DomainException(StaffError.INVITATION_EXPIRED);
    }
  }

  /** Aceptada: queda usada e xa non vale outra vez. */
  public StaffInvitation use(Instant now) {
    requireUsable(now);
    return new StaffInvitation(
        id,
        memberId,
        tokenHash,
        requestedBy,
        createdAt,
        expiresAt,
        now.truncatedTo(ChronoUnit.MICROS),
        null,
        delivery);
  }

  /**
   * Substituída por un reenvío ou pola desactivación da pertenza. Se xa estaba pechada, queda
   * igual.
   */
  public StaffInvitation supersede(Instant now) {
    if (!isOpen()) {
      return this;
    }
    Instant at = now.truncatedTo(ChronoUnit.MICROS);
    return new StaffInvitation(
        id,
        memberId,
        tokenHash,
        requestedBy,
        createdAt,
        expiresAt,
        null,
        at.isBefore(createdAt) ? createdAt : at,
        delivery);
  }

  /** O resultado do correo, despois do commit (§1.2.6). */
  public StaffInvitation delivered(boolean sent) {
    return new StaffInvitation(
        id,
        memberId,
        tokenHash,
        requestedBy,
        createdAt,
        expiresAt,
        usedAt,
        supersededAt,
        sent ? InvitationDelivery.SENT : InvitationDelivery.FAILED);
  }

  public StaffInvitationId id() {
    return id;
  }

  public StaffMemberId memberId() {
    return memberId;
  }

  public TokenHash tokenHash() {
    return tokenHash;
  }

  public String requestedBy() {
    return requestedBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public @Nullable Instant usedAt() {
    return usedAt;
  }

  public @Nullable Instant supersededAt() {
    return supersededAt;
  }

  public InvitationDelivery delivery() {
    return delivery;
  }
}
