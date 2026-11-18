// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * «Buscar xente para xogar» do concello (Concello › Xeral, ADR-0040): apagado por defecto; aceso,
 * unha partida sen o mínimo cancélase {@code cancelHours} horas antes do inicio da reserva.
 *
 * @param since desde cando está aceso; nulo = apagado
 * @param cancelHours «Cancelar se non hai mínimo», horas antes (de 1 a 72)
 * @param changedAt o último cambio do interruptor ou das horas: a tarefa do prazo só mira as
 *     partidas cuxo prazo cae despois (acender ou subir as horas nunca cancela en bloque)
 */
public record OpenMatchPolicy(
    @Nullable Instant since, int cancelHours, @Nullable Instant changedAt) {

  public static final int HOURS_MIN = 1;
  public static final int HOURS_MAX = 72;

  /** A da alta: apagado, 12 horas. */
  public static final OpenMatchPolicy OFF = new OpenMatchPolicy(null, 12, null);

  public OpenMatchPolicy {
    if (cancelHours < HOURS_MIN || cancelHours > HOURS_MAX) {
      throw new DomainException(
          MunicipalityError.OPEN_MATCH_POLICY_INVALID, Map.of("min", HOURS_MIN, "max", HOURS_MAX));
    }
  }

  public boolean enabled() {
    return since != null;
  }

  /**
   * O interruptor e as horas novos. Acender garda o momento e, se xa estaba aceso, queda o de
   * antes; calquera cambio do interruptor ou das horas pon {@code changedAt} a {@code now}.
   */
  public OpenMatchPolicy changed(boolean enabled, int newCancelHours, Instant now) {
    Objects.requireNonNull(now, "now");
    Instant newSince = !enabled ? null : (since != null ? since : now);
    boolean changed = enabled != enabled() || newCancelHours != cancelHours;
    return new OpenMatchPolicy(newSince, newCancelHours, changed ? now : changedAt);
  }
}
