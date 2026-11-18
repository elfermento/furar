// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha partida aberta (ADR-0040): unha reserva confirmada dunha pista enteira ou por partes que
 * quen a ten abre a outras persoas. Inmutable: cada transición devolve unha copia coa mesma
 * versión, que sobe ao gardala. As prazas cóntanse baixo o bloqueo da súa fila: aquí, as contas con
 * {@code confirmed} (as persoas que se uniron e están dentro).
 *
 * <ul>
 *   <li>{@code total = knownCount + confirmed}; «faltan» = {@code minimum − total} se é positivo;
 *       «quedan» = {@code maximum − total}; completa se {@code total = maximum}.
 *   <li>O prazo é o inicio da reserva menos as horas do concello: non se garda.
 * </ul>
 *
 * @param organizerName o nome do token de quen organiza; publícase e nunca é un correo
 * @param knownCount «Xa somos», con quen organiza
 * @param level nulo = «Sen indicar»
 * @param deadlineCheckedAt o prazo pasou co mínimo cuberto: a tarefa xa non a mira
 * @param hiddenBy o {@code sub} de quen a ocultou
 */
public record OpenMatch(
    UUID id,
    UUID bookingId,
    String organizerSubject,
    String organizerName,
    int knownCount,
    int minimum,
    int maximum,
    @Nullable MatchLevel level,
    @Nullable String title,
    @Nullable String description,
    boolean approval,
    OpenMatchStatus status,
    @Nullable OpenMatchCancelReason cancelReason,
    @Nullable Instant deadlineCheckedAt,
    @Nullable Instant hiddenAt,
    @Nullable String hiddenBy,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  /** Persoas como moito nunha partida. */
  public static final int MAX_PEOPLE = 40;

  /** Mínimo como pouco: dúas persoas. */
  public static final int MIN_PEOPLE = 2;

  public static final int NAME_MAX = 200;
  public static final int TITLE_MAX = 80;
  public static final int DESCRIPTION_MAX = 1000;

  public OpenMatch {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(organizerSubject, "organizerSubject");
    Objects.requireNonNull(organizerName, "organizerName");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if ((status == OpenMatchStatus.CANCELLED) != (cancelReason != null)) {
      throw new IllegalStateException("Cancel reason does not match " + status);
    }
    if ((hiddenAt == null) != (hiddenBy == null)) {
      throw new IllegalStateException("Hidden without who or when");
    }
    if (organizerName.contains("@")) {
      throw new IllegalStateException("An e-mail address is never published as a name");
    }
  }

  /**
   * O que escribe quen organiza no diálogo, xa comprobado: os números e os textos (en branco = sen
   * texto).
   *
   * @throws DomainException {@code booking.match-counts-invalid} se os números son imposibles
   */
  public record Terms(
      int knownCount,
      int minimum,
      int maximum,
      @Nullable MatchLevel level,
      @Nullable String title,
      @Nullable String description,
      boolean approval) {

    public Terms {
      if (minimum < MIN_PEOPLE
          || minimum > MAX_PEOPLE
          || maximum < minimum
          || maximum > MAX_PEOPLE
          || knownCount < 1
          || knownCount >= maximum) {
        throw new DomainException(BookingError.MATCH_COUNTS_INVALID, Map.of("max", MAX_PEOPLE));
      }
      title = text(title, TITLE_MAX);
      description = text(description, DESCRIPTION_MAX);
    }

    private static @Nullable String text(@Nullable String value, int max) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String stripped = value.strip();
      if (stripped.length() > max) {
        throw new IllegalArgumentException("Text longer than " + max);
      }
      return stripped;
    }
  }

  /**
   * O nome de quen organiza ou se une, do token: sen nome (ou cun correo como nome), {@code
   * booking.match-name-required}. Nunca se publica un correo.
   */
  public static String publicName(@Nullable String name) {
    if (name == null || name.isBlank() || name.contains("@")) {
      throw new DomainException(BookingError.MATCH_NAME_REQUIRED);
    }
    String stripped = name.strip();
    return stripped.length() > NAME_MAX ? stripped.substring(0, NAME_MAX).strip() : stripped;
  }

  /** O prazo do concello: o inicio da reserva menos as horas («Cancelar se non hai mínimo»). */
  public static Instant deadline(Instant bookingStart, int cancelHours) {
    return bookingStart.minus(Duration.ofHours(cancelHours));
  }

  /**
   * Se aínda se pode abrir ou mover a partida (ADR-0040, punto 4): a reserva aínda se cancela de
   * balde e o prazo do concello non pasou.
   *
   * @throws DomainException {@code booking.match-too-late} co primeiro dos dous prazos
   */
  public static void checkInTime(
      Instant cancelUntil, Instant bookingStart, int cancelHours, Instant now) {
    Instant deadline = deadline(bookingStart, cancelHours);
    if (!now.isBefore(cancelUntil) || !now.isBefore(deadline)) {
      Instant first = cancelUntil.isBefore(deadline) ? cancelUntil : deadline;
      throw new DomainException(BookingError.MATCH_TOO_LATE, Map.of("deadline", first.toString()));
    }
  }

  /** Partida nova, aberta, sobre unha reserva da persoa. */
  public static OpenMatch open(
      UUID id, UUID bookingId, String subject, String name, Terms terms, Instant now) {
    return new OpenMatch(
        id,
        bookingId,
        subject,
        publicName(name),
        terms.knownCount(),
        terms.minimum(),
        terms.maximum(),
        terms.level(),
        terms.title(),
        terms.description(),
        terms.approval(),
        OpenMatchStatus.OPEN,
        null,
        null,
        null,
        null,
        now,
        now,
        0);
  }

  /**
   * Os números e os textos novos; unha pechada volve abrirse. O nome de quen organiza é o do token
   * de agora.
   *
   * @param confirmed as persoas que se uniron e están dentro
   * @throws DomainException {@code booking.match-closed} se está cancelada; {@code
   *     booking.match-maximum-below-count} se o máximo novo queda por debaixo das que xa van
   */
  public OpenMatch edit(Terms terms, String name, int confirmed, Instant now) {
    requireNotCancelled();
    int count = terms.knownCount() + confirmed;
    if (terms.maximum() < count) {
      throw new DomainException(BookingError.MATCH_MAXIMUM_BELOW_COUNT, Map.of("count", count));
    }
    return new OpenMatch(
        id,
        bookingId,
        organizerSubject,
        publicName(name),
        terms.knownCount(),
        terms.minimum(),
        terms.maximum(),
        terms.level(),
        terms.title(),
        terms.description(),
        terms.approval(),
        OpenMatchStatus.OPEN,
        null,
        deadlineCheckedAt,
        hiddenAt,
        hiddenBy,
        createdAt,
        now,
        version);
  }

  /** «Deixar de buscar» (ou un cambio que a pecha): a reserva e as persoas seguen. */
  public OpenMatch close(Instant now) {
    requireNotCancelled();
    return withStatus(OpenMatchStatus.CLOSED, null, deadlineCheckedAt, now);
  }

  /** Volve buscar xente, cos mesmos números. */
  public OpenMatch reopen(int confirmed, Instant now) {
    requireNotCancelled();
    if (maximum < knownCount + confirmed) {
      throw new DomainException(
          BookingError.MATCH_MAXIMUM_BELOW_COUNT, Map.of("count", knownCount + confirmed));
    }
    return withStatus(OpenMatchStatus.OPEN, null, deadlineCheckedAt, now);
  }

  /** Cancelada coa súa reserva ou por non chegar ao mínimo. Non se reabre. */
  public OpenMatch cancel(OpenMatchCancelReason reason, Instant now) {
    Objects.requireNonNull(reason, "reason");
    return withStatus(OpenMatchStatus.CANCELLED, reason, deadlineCheckedAt, now);
  }

  /** O prazo pasou co mínimo cuberto: xógase. */
  public OpenMatch deadlineMet(Instant now) {
    return withStatus(status, cancelReason, now, now);
  }

  /** A reserva cambiou de hora: o prazo novo mírase outra vez. */
  public OpenMatch moved(Instant now) {
    return withStatus(status, cancelReason, null, now);
  }

  /** A xestión ocúltaa (ou volve mostrala). Repetir non cambia nada. */
  public OpenMatch hidden(boolean hide, String subject, Instant now) {
    if (hide == hidden()) {
      return this;
    }
    return new OpenMatch(
        id,
        bookingId,
        organizerSubject,
        organizerName,
        knownCount,
        minimum,
        maximum,
        level,
        title,
        description,
        approval,
        status,
        cancelReason,
        deadlineCheckedAt,
        hide ? now : null,
        hide ? Objects.requireNonNull(subject, "subject") : null,
        createdAt,
        now,
        version);
  }

  public boolean hidden() {
    return hiddenAt != null;
  }

  /** As persoas que van: «Xa somos» e as que se uniron. */
  public int total(int confirmed) {
    return knownCount + confirmed;
  }

  /** Cantas faltan para o mínimo (0 se xa se chegou). */
  public int missing(int confirmed) {
    return Math.max(0, minimum - total(confirmed));
  }

  /** Cantas caben aínda. */
  public int left(int confirmed) {
    return Math.max(0, maximum - total(confirmed));
  }

  public boolean full(int confirmed) {
    return total(confirmed) >= maximum;
  }

  /** Se chegou ao mínimo. */
  public boolean filled(int confirmed) {
    return total(confirmed) >= minimum;
  }

  private void requireNotCancelled() {
    if (status == OpenMatchStatus.CANCELLED) {
      throw new DomainException(BookingError.MATCH_CLOSED);
    }
  }

  private OpenMatch withStatus(
      OpenMatchStatus newStatus,
      @Nullable OpenMatchCancelReason reason,
      @Nullable Instant checkedAt,
      Instant now) {
    return new OpenMatch(
        id,
        bookingId,
        organizerSubject,
        organizerName,
        knownCount,
        minimum,
        maximum,
        level,
        title,
        description,
        approval,
        newStatus,
        reason,
        checkedAt,
        hiddenAt,
        hiddenBy,
        createdAt,
        now,
        version);
  }
}
