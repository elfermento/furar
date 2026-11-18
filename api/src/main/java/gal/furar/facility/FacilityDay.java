// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un día do calendario dunha instalación (ADR-0039, punto 8; {@link FacilityQueries#calendar}).
 *
 * @param holiday se é festivo, teña ou non horario propio
 * @param open os tramos abertos dese día, sen cortar polos bloqueos
 * @param blocks os bloqueos que pisan ese día
 * @param exclusiveUseId o uso vivo que bloquea os demais ese día, se o hai
 */
public record FacilityDay(
    LocalDate date,
    boolean holiday,
    List<OpeningInterval> open,
    List<BlockSpan> blocks,
    @Nullable UUID exclusiveUseId) {

  public FacilityDay {
    Objects.requireNonNull(date, "date");
    open = List.copyOf(open);
    blocks = List.copyOf(blocks);
  }

  /** Un bloqueo co seu motivo (pode faltar). */
  public record BlockSpan(Instant start, Instant end, @Nullable String reason) {

    public BlockSpan {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }
}
