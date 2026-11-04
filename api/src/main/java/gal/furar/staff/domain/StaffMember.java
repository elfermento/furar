// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Pertenza dunha persoa ao persoal dun concello (agregado; ADR-0027, plan do dominio 4, §1.2.8).
 * Inmutable: cada transición é un método con nome de negocio que devolve unha copia (A13).
 *
 * <ul>
 *   <li>{@code INVITED → ACTIVE} ao aceptar, co correo verificado igual ao invitado;
 *   <li>{@code INVITED|ACTIVE → DEACTIVATED} ao desactivar: a fila queda e o acceso retírase na
 *       seguinte petición;
 *   <li>{@code DEACTIVATED → ACTIVE} ao reactivar, só se aceptou algunha vez e non está
 *       anonimizada: o mesmo rol, sen invitación nova (ADR-0027, revisión do 2026-10-30). Unha
 *       invitada desactivada invítase de novo (outra fila);
 *   <li>o rol cámbiase en {@code INVITED} ou {@code ACTIVE};
 *   <li>nunca queda un concello sen xestor activo: o caso de uso conta as pertenzas {@code MANAGER}
 *       activas baixo {@code select … for update} e pásallelas a {@link #changeRole} e {@link
 *       #deactivate}.
 * </ul>
 *
 * As horas van en microsegundos, a precisión da base.
 */
public final class StaffMember {

  /** Máximo do nome que vén do token ao aceptar (o mesmo ca a táboa). */
  public static final int DISPLAY_NAME_MAX = 200;

  private final StaffMemberId id;
  private final EmailAddress email;
  private final @Nullable String displayName;
  private final @Nullable String subject;
  private final StaffRole role;
  private final StaffStatus status;
  private final Language language;
  private final String invitedBy;
  private final Instant invitedAt;
  private final @Nullable Instant acceptedAt;
  private final @Nullable String deactivatedBy;
  private final @Nullable Instant deactivatedAt;
  private final @Nullable Instant lastSignInAt;
  private final @Nullable Instant previousSignInAt;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  @SuppressWarnings("java:S107") // unha fila enteira: o construtor privado só o usan as fábricas
  private StaffMember(
      StaffMemberId id,
      EmailAddress email,
      @Nullable String displayName,
      @Nullable String subject,
      StaffRole role,
      StaffStatus status,
      Language language,
      String invitedBy,
      Instant invitedAt,
      @Nullable Instant acceptedAt,
      @Nullable String deactivatedBy,
      @Nullable Instant deactivatedAt,
      @Nullable Instant lastSignInAt,
      @Nullable Instant previousSignInAt,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.email = Objects.requireNonNull(email, "email");
    this.displayName = displayName;
    this.subject = subject;
    this.role = Objects.requireNonNull(role, "role");
    this.status = Objects.requireNonNull(status, "status");
    this.language = Objects.requireNonNull(language, "language");
    this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy");
    this.invitedAt = Objects.requireNonNull(invitedAt, "invitedAt");
    this.acceptedAt = acceptedAt;
    this.deactivatedBy = deactivatedBy;
    this.deactivatedAt = deactivatedAt;
    this.lastSignInAt = lastSignInAt;
    this.previousSignInAt = previousSignInAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    // As mesmas garantías ca a táboa staff_member: un fallo aquí é un erro de programación.
    boolean accepted = acceptedAt != null;
    if (accepted != (subject != null)) {
      throw new IllegalStateException("Acceptance data is incomplete");
    }
    if (status == StaffStatus.ACTIVE && !accepted) {
      throw new IllegalStateException("An active membership was accepted");
    }
    if (status == StaffStatus.INVITED && accepted) {
      throw new IllegalStateException("An invited membership is not accepted yet");
    }
    if ((status == StaffStatus.DEACTIVATED) != (deactivatedAt != null)
        || (deactivatedAt == null) != (deactivatedBy == null)) {
      throw new IllegalStateException("Deactivation data does not match " + status);
    }
    if (previousSignInAt != null
        && (lastSignInAt == null || previousSignInAt.isAfter(lastSignInAt))) {
      throw new IllegalStateException("previousSignInAt after lastSignInAt");
    }
    if (invitedBy.isBlank()) {
      throw new IllegalStateException("invitedBy is blank");
    }
  }

  /** Pertenza nova, invitada: aínda sen persoa nin aceptación. */
  public static StaffMember invite(
      StaffMemberId id,
      EmailAddress email,
      StaffRole role,
      Language language,
      String invitedBy,
      Instant now) {
    Instant at = micros(now);
    return new StaffMember(
        id,
        email,
        null,
        null,
        role,
        StaffStatus.INVITED,
        language,
        invitedBy,
        at,
        null,
        null,
        null,
        null,
        null,
        null,
        at,
        at);
  }

  /** Desde a base, tal como se gardou. */
  @SuppressWarnings("java:S107")
  public static StaffMember rehydrate(
      StaffMemberId id,
      EmailAddress email,
      @Nullable String displayName,
      @Nullable String subject,
      StaffRole role,
      StaffStatus status,
      Language language,
      String invitedBy,
      Instant invitedAt,
      @Nullable Instant acceptedAt,
      @Nullable String deactivatedBy,
      @Nullable Instant deactivatedAt,
      @Nullable Instant lastSignInAt,
      @Nullable Instant previousSignInAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new StaffMember(
        id,
        email,
        displayName,
        subject,
        role,
        status,
        language,
        invitedBy,
        invitedAt,
        acceptedAt,
        deactivatedBy,
        deactivatedAt,
        lastSignInAt,
        previousSignInAt,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * A persoa acepta a invitación (§1.2.6): a pertenza pasa a activa co seu {@code sub} e o nome do
   * token. Que a invitación vale e que a persoa non é xa activa no concello compróbao antes o caso
   * de uso.
   *
   * @param verifiedEmail o correo verificado da sesión
   * @throws DomainException {@code staff.invitation-invalid} se a pertenza xa non está invitada ou
   *     {@code staff.invitation-email-mismatch} se o correo non é o invitado
   */
  public StaffMember accept(
      @Nullable String verifiedEmail, String subject, @Nullable String displayName, Instant now) {
    if (status != StaffStatus.INVITED) {
      throw new DomainException(StaffError.INVITATION_INVALID);
    }
    if (!email.matches(verifiedEmail)) {
      throw new DomainException(StaffError.INVITATION_EMAIL_MISMATCH);
    }
    if (subject.isBlank()) {
      throw new IllegalArgumentException("subject is blank");
    }
    Instant at = micros(now);
    return new StaffMember(
        id,
        email,
        cleanName(displayName),
        subject,
        role,
        StaffStatus.ACTIVE,
        language,
        invitedBy,
        invitedAt,
        at.isBefore(invitedAt) ? invitedAt : at,
        null,
        null,
        lastSignInAt,
        previousSignInAt,
        version,
        createdAt,
        at);
  }

  /**
   * Cambia o rol dunha pertenza invitada ou activa. O mesmo rol non cambia nada.
   *
   * @param activeManagers pertenzas {@code MANAGER} activas do concello, contadas baixo bloqueo
   * @throws DomainException {@code staff.transition-invalid} se está desactivada ou {@code
   *     staff.last-manager} se é a última persoa xestora activa e pasa a persoal
   */
  public StaffMember changeRole(StaffRole newRole, int activeManagers, Instant now) {
    if (status == StaffStatus.DEACTIVATED) {
      throw new DomainException(StaffError.TRANSITION_INVALID);
    }
    if (newRole == role) {
      return this;
    }
    requireAnotherManager(activeManagers);
    return new StaffMember(
        id,
        email,
        displayName,
        subject,
        newRole,
        status,
        language,
        invitedBy,
        invitedAt,
        acceptedAt,
        deactivatedBy,
        deactivatedAt,
        lastSignInAt,
        previousSignInAt,
        version,
        createdAt,
        micros(now));
  }

  /**
   * Desactivar unha pertenza invitada ou activa. Non se borra nada: a fila queda (o rexistro de
   * actividade segue sabendo quen era) e a invitación aberta substitúea o caso de uso. A
   * conservación conta desde agora ({@code deactivatedAt}).
   *
   * @param by {@code sub} de quen desactiva
   * @param activeManagers pertenzas {@code MANAGER} activas do concello, contadas baixo bloqueo
   * @throws DomainException {@code staff.transition-invalid} se xa está desactivada ou {@code
   *     staff.last-manager} se é a última persoa xestora activa
   */
  public StaffMember deactivate(String by, int activeManagers, Instant now) {
    if (status == StaffStatus.DEACTIVATED) {
      throw new DomainException(StaffError.TRANSITION_INVALID);
    }
    if (by.isBlank()) {
      throw new IllegalArgumentException("by is blank");
    }
    requireAnotherManager(activeManagers);
    Instant at = micros(now);
    return new StaffMember(
        id,
        email,
        displayName,
        subject,
        role,
        StaffStatus.DEACTIVATED,
        language,
        invitedBy,
        invitedAt,
        acceptedAt,
        by,
        at,
        lastSignInAt,
        previousSignInAt,
        version,
        createdAt,
        at);
  }

  /**
   * Reactivar unha pertenza desactivada que aceptou (ADR-0027, revisión do 2026-10-30): volve a
   * activa co mesmo rol e a mesma persoa, sen invitación nova; o segundo factor pídese igual ca
   * sempre. Quita a data da desactivación: se se volve desactivar, a conservación conta desde esa
   * nova data. Unha bloqueada (desactivada hai máis de {@code FURAR_AUDIT_VISIBLE}) tamén se
   * reactiva: o bloqueo só esconde o seu correo e o seu nome na xestión mentres está desactivada.
   * Que non haxa outra pertenza viva co mesmo correo ou a mesma persoa no concello garántena os
   * índices únicos.
   *
   * @throws DomainException {@code staff.transition-invalid} se non está desactivada ou nunca
   *     aceptou, ou {@code staff.anonymised} se xa se anonimizou
   */
  public StaffMember reactivate(Instant now) {
    if (status != StaffStatus.DEACTIVATED || acceptedAt == null) {
      throw new DomainException(StaffError.TRANSITION_INVALID);
    }
    if (email.isAnonymised()) {
      throw new DomainException(StaffError.ANONYMISED);
    }
    return new StaffMember(
        id,
        email,
        displayName,
        subject,
        role,
        StaffStatus.ACTIVE,
        language,
        invitedBy,
        invitedAt,
        acceptedAt,
        null,
        null,
        lastSignInAt,
        previousSignInAt,
        version,
        createdAt,
        micros(now));
  }

  /** Activa e xestora: conta para a regra do último xestor. */
  public boolean isActiveManager() {
    return status == StaffStatus.ACTIVE && role == StaffRole.MANAGER;
  }

  /**
   * Se un acceso con este {@code auth_time} é novo (§1.2.5): só cando é posterior ao último
   * rexistrado. A base fai o mesmo nun {@code update} condicional, así que só unha petición o
   * rexistra.
   */
  public boolean isNewSignIn(Instant authTime) {
    return status == StaffStatus.ACTIVE
        && (lastSignInAt == null || lastSignInAt.isBefore(micros(authTime)));
  }

  /** O rol que dá esta pertenza no concello, se está activa. */
  public @Nullable StaffRole activeRole() {
    return status == StaffStatus.ACTIVE ? role : null;
  }

  private void requireAnotherManager(int activeManagers) {
    if (isActiveManager() && activeManagers <= 1) {
      throw new DomainException(StaffError.LAST_MANAGER);
    }
  }

  private static @Nullable String cleanName(@Nullable String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String stripped = name.strip();
    return stripped.length() <= DISPLAY_NAME_MAX
        ? stripped
        : stripped.substring(0, DISPLAY_NAME_MAX).strip();
  }

  private static Instant micros(Instant instant) {
    return instant.truncatedTo(ChronoUnit.MICROS);
  }

  public StaffMemberId id() {
    return id;
  }

  public EmailAddress email() {
    return email;
  }

  public @Nullable String displayName() {
    return displayName;
  }

  public @Nullable String subject() {
    return subject;
  }

  public StaffRole role() {
    return role;
  }

  public StaffStatus status() {
    return status;
  }

  public Language language() {
    return language;
  }

  public String invitedBy() {
    return invitedBy;
  }

  public Instant invitedAt() {
    return invitedAt;
  }

  public @Nullable Instant acceptedAt() {
    return acceptedAt;
  }

  public @Nullable String deactivatedBy() {
    return deactivatedBy;
  }

  public @Nullable Instant deactivatedAt() {
    return deactivatedAt;
  }

  public @Nullable Instant lastSignInAt() {
    return lastSignInAt;
  }

  /**
   * Se o correo e o nome están bloqueados (LOPDGDD art. 32; ADR-0020, revisión do 2026-10-29):
   * desactivada antes de {@code visibleSince} (a desactivación xa non se ve no rexistro) e aínda
   * sen anonimizar. A xestión vea como «Persoa desactivada», sen correo nin nome; só a exportación
   * dos eventos bloqueados os mostra.
   *
   * @param visibleSince o primeiro instante que aínda se ve no rexistro
   */
  public boolean isIdentityBlocked(Instant visibleSince) {
    return status == StaffStatus.DEACTIVATED
        && deactivatedAt != null
        && deactivatedAt.isBefore(visibleSince)
        && !email.isAnonymised();
  }

  public @Nullable Instant previousSignInAt() {
    return previousSignInAt;
  }

  /** Versión gardada; {@code null} nunha pertenza nova que aínda non se gardou. */
  public @Nullable Long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
