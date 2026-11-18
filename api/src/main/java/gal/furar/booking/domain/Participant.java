// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha persoa que ocupa praza nunha reserva dun uso por prazas (ADR-0031, punto 5). O titular non
 * leva nome (é o da reserva); a persoa a cargo copia o nome e o ano da súa ficha e queda ligada a
 * ela; o acompañante leva o que escribiu o persoal. Unha fila anonimizada (conservación) queda sen
 * nome nin ficha.
 *
 * @param dependentId a ficha, só en {@link ParticipantKind#DEPENDENT} sen anonimizar
 * @param name nome e apelidos; nulo no titular e nas anonimizadas
 * @param birthYear ano de nacemento; nulo no titular e nas anonimizadas
 */
public record Participant(
    ParticipantKind kind,
    @Nullable UUID dependentId,
    @Nullable String name,
    @Nullable Integer birthYear) {

  public static final int NAME_MAX = 200;

  public Participant {
    Objects.requireNonNull(kind, "kind");
    if (kind != ParticipantKind.DEPENDENT && dependentId != null) {
      throw new IllegalArgumentException("Only a dependent is linked to a record");
    }
    if ((name == null) != (birthYear == null)) {
      throw new IllegalArgumentException("Name and birth year go together");
    }
    if (kind == ParticipantKind.HOLDER && name != null) {
      throw new IllegalArgumentException("The holder's name is the booking's");
    }
    if (name != null && (name.isEmpty() || name.length() > NAME_MAX)) {
      throw new IllegalArgumentException("name length out of range");
    }
  }

  /** Quen ten a reserva, ocupando praza. */
  public static Participant holder() {
    return new Participant(ParticipantKind.HOLDER, null, null, null);
  }

  /** Unha persoa a cargo, coa copia do nome e do ano da súa ficha. */
  public static Participant dependent(UUID dependentId, String name, int birthYear) {
    return new Participant(
        ParticipantKind.DEPENDENT,
        Objects.requireNonNull(dependentId, "dependentId"),
        name,
        birthYear);
  }

  /** Un acompañante escrito no mostrador. */
  public static Participant companion(String name, int birthYear) {
    return new Participant(ParticipantKind.COMPANION, null, name.strip(), birthYear);
  }

  /**
   * A clave que identifica a persoa en «unha praza por persoa»: o titular, polo correo ({@code
   * e:<correo normalizado>}); a persoa a cargo, pola súa ficha ({@code d:<id>}). Sen correo e nos
   * acompañantes non hai clave estable: a persoa só se compara dentro da súa reserva.
   */
  public Optional<String> personKey(@Nullable String holderEmailKey) {
    return switch (kind) {
      case HOLDER -> Optional.ofNullable(holderEmailKey).map(k -> "e:" + k);
      case DEPENDENT -> Optional.ofNullable(dependentId).map(id -> "d:" + id);
      case COMPANION -> Optional.empty();
    };
  }

  /** O nome e o ano, para comparar dúas persoas da mesma reserva; baleiro no titular. */
  Optional<String> identity() {
    return name == null
        ? Optional.empty()
        : Optional.of(name.toLowerCase(Locale.ROOT) + "|" + birthYear);
  }
}
