// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.domain.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Rexistro de actividade (ENS op.exp.8, ADR-0020): quen fixo que, en que concello, con que
 * resultado. O adaptador engade a persoa ({@code sub}), a hora e o {@code X-Request-Id}.
 *
 * <p>O éxito dunha escritura gárdase na mesma transacción (só queda se se confirma); o fallo e o
 * que non vai nunha transacción de escritura, ao momento e fóra dela, así que queda tamén cando o
 * caso de uso falla ou é de só lectura. Se non se pode escribir, lanza: o caso de uso auditado non
 * continúa sen rastro.
 */
public interface AuditLog {

  void record(AuditEvent event);

  /**
   * Rexistra o fallo dunha escritura co código do erro ({@code common.internal} se non é de
   * dominio) e os detalles dados. Se o rexistro non se pode escribir, o seu erro vai suprimido no
   * orixinal: quen chama segue lanzando o erro do caso de uso.
   */
  default void recordFailure(
      RuntimeException failure,
      String action,
      @Nullable UUID municipalityId,
      @Nullable String municipalitySlug,
      Map<String, Object> details) {
    Map<String, Object> withCode = new LinkedHashMap<>(details);
    withCode.put(
        "code", failure instanceof DomainException d ? d.errorCode().code() : "common.internal");
    try {
      record(new AuditEvent(action, Outcome.FAILURE, municipalityId, municipalitySlug, withCode));
    } catch (RuntimeException auditFailure) {
      failure.addSuppressed(auditFailure);
    }
  }
}
