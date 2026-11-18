// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Para quen é unha reserva dun uso por prazas, tal como se pide (ADR-0031, puntos 5 e 9): quen ten
 * a reserva, persoas a cargo gardadas na súa conta e, só desde o mostrador, acompañantes escritos
 * para esa reserva. Cada persoa ocupa unha praza.
 *
 * @param self se quen ten a reserva ocupa praza
 * @param dependentIds fichas da conta da reserva
 * @param companions acompañantes, só nas reservas que fai o persoal
 */
public record ParticipantSelection(
    boolean self, List<UUID> dependentIds, List<Companion> companions) {

  /** Sen dicir nada: unha praza para quen reserva (o que fai un cliente antigo). */
  public static final ParticipantSelection SELF =
      new ParticipantSelection(true, List.of(), List.of());

  public ParticipantSelection {
    dependentIds = dependentIds == null ? List.of() : List.copyOf(dependentIds);
    companions = companions == null ? List.of() : List.copyOf(companions);
  }

  /** Se é a de por defecto: só quen reserva. */
  public boolean isDefault() {
    return self && dependentIds.isEmpty() && companions.isEmpty();
  }

  /** Texto estable para a pegada da clave de reintento: as fichas, en orde. */
  public String canonical() {
    return self
        + ";"
        + dependentIds.stream().map(UUID::toString).sorted().toList()
        + ";"
        + companions.stream().map(c -> c.name() + "/" + c.birthYear()).toList();
  }

  /**
   * Un acompañante escrito no mostrador.
   *
   * @param name nome e apelidos
   * @param birthYear ano de nacemento
   */
  public record Companion(String name, int birthYear) {

    public Companion {
      Objects.requireNonNull(name, "name");
    }
  }
}
