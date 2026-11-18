// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.shared.audit.AuditError;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Que eventos bloqueados exportar e por que.
 *
 * @param from comezo do período, incluído
 * @param to fin do período, excluído
 * @param reason quen o pide (pecho: o bloqueo só se levanta para estes, LOPDGDD art. 32.2)
 * @param reference o número do expediente, das dilixencias ou do requirimento, tal como chega: o
 *     caso de uso compróbao ({@link #checkedReference}) despois do permiso
 */
public record ExportBlockedActivityCommand(
    Instant from, Instant to, DisclosureReason reason, String reference) {

  /** Máximo da referencia: un número de expediente, non un texto libre. */
  public static final int REFERENCE_MAX = 80;

  private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

  public ExportBlockedActivityCommand {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(reason, "reason");
    reference = reference == null ? "" : reference;
  }

  /**
   * A referencia sen espazos nos extremos.
   *
   * @throws DomainException {@code audit.export-reference-invalid} se queda baleira, pasa de
   *     {@value #REFERENCE_MAX} caracteres ou ten caracteres de control
   */
  public String checkedReference() {
    String stripped = reference.strip();
    if (stripped.isEmpty()
        || stripped.length() > REFERENCE_MAX
        || CONTROL.matcher(stripped).find()) {
      throw new DomainException(AuditError.EXPORT_REFERENCE_INVALID, Map.of("max", REFERENCE_MAX));
    }
    return stripped;
  }

  /** Para que se levanta o bloqueo (LOPDGDD art. 32.2). */
  public enum DisclosureReason {
    /** Requirimento dun xulgado ou tribunal. */
    COURT_ORDER,
    /** Ministerio Fiscal. */
    PUBLIC_PROSECUTOR,
    /** Autoridade de protección de datos. */
    DATA_PROTECTION_AUTHORITY,
    /** Expediente aberto polo concello (tamén disciplinario). */
    OPEN_PROCEEDINGS
  }
}
