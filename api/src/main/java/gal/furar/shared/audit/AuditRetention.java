// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Prazos do rexistro de actividade (ADR-0020, revisión do 2026-10-29; LOPDGDD art. 32). Cada evento
 * pasa por tres tramos, contados desde que pasou:
 *
 * <ol>
 *   <li><b>Visible</b> ({@code visible}, P1Y por defecto): a xestión leo na web.
 *   <li><b>Bloqueado</b> (de {@code visible} a {@code retention}, P3Y por defecto): ningunha lista
 *       o devolve; só o xestor o exporta, cun motivo (xulgados, Ministerio Fiscal, autoridade de
 *       protección de datos ou expediente aberto), e a exportación queda no rexistro.
 *   <li><b>Borrado</b> (desde {@code retention}): a tarefa diaria bórrao fisicamente.
 * </ol>
 *
 * <p>A identidade do persoal desactivado segue os mesmos tramos desde a última desactivación:
 * visible ata {@code visible}, bloqueada ata {@code retention} (cando xa non quedan eventos seus) e
 * despois anonimizada. Reactivar reinicia o contador.
 *
 * @param visible canto se ve cada evento na web ({@code FURAR_AUDIT_VISIBLE})
 * @param retention canto se garda cada evento ({@code FURAR_AUDIT_RETENTION}); máis ca {@code
 *     visible} e polo menos {@value #MINIMUM_RETENTION}
 */
public record AuditRetention(Period visible, Period retention) {

  /** O mínimo de {@code retention}: a función da base tampouco borra nada máis novo. */
  public static final String MINIMUM_RETENTION = "P1Y";

  /** Data fixa para comparar períodos (un mes ou un ano non teñen sempre os mesmos días). */
  private static final LocalDate REFERENCE = LocalDate.of(2000, 1, 1);

  /** Os valores por defecto: un ano á vista e tres gardado. */
  public static final AuditRetention DEFAULT =
      new AuditRetention(Period.ofYears(1), Period.ofYears(3));

  public AuditRetention {
    Objects.requireNonNull(visible, "visible");
    Objects.requireNonNull(retention, "retention");
    if (!after(visible, Period.ZERO)) {
      throw new IllegalStateException("furar.audit.visible must be positive: " + visible);
    }
    if (after(Period.parse(MINIMUM_RETENTION), retention)) {
      throw new IllegalStateException(
          "furar.audit.retention must be at least " + MINIMUM_RETENTION + ": " + retention);
    }
    if (!after(retention, visible)) {
      throw new IllegalStateException(
          "furar.audit.retention ("
              + retention
              + ") must be longer than furar.audit.visible ("
              + visible
              + ")");
    }
  }

  /** O primeiro instante que aínda se ve na web: o anterior está bloqueado ou borrado. */
  public Instant visibleSince(Instant now) {
    return minus(now, visible);
  }

  /** O primeiro instante que se garda: o anterior bórrase. */
  public Instant keptSince(Instant now) {
    return minus(now, retention);
  }

  /** Se un evento dese instante está bloqueado agora (xa non se ve, aínda se garda). */
  public boolean isBlocked(Instant at, Instant now) {
    return at.isBefore(visibleSince(now)) && !at.isBefore(keptSince(now));
  }

  private static Instant minus(Instant now, Period period) {
    return now.atOffset(ZoneOffset.UTC).minus(period).toInstant();
  }

  /** {@code a} dura máis ca {@code b}, contados desde a mesma data. */
  private static boolean after(Period a, Period b) {
    return REFERENCE.plus(a).isAfter(REFERENCE.plus(b));
  }
}
