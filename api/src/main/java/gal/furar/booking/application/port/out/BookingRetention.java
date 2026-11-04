// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.BookingId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Conservación das reservas (ADR-0017 punto 5): as que xa pasaron o prazo e a súa anonimización.
 * Sempre no concello actual e dentro dunha transacción (ADR-0006).
 */
public interface BookingRetention {

  /** Quen actúa nas filas da historia que fixo a persoa, despois de anonimizar. */
  String ANONYMISED_ACTOR = "furar:anonymized";

  /**
   * As reservas aínda sen anonimizar cuxa franxa rematou e que non cambiaron (cobro, devolución)
   * desde antes de {@code cutoff}, da máis vella á máis nova.
   *
   * @param paid as de pago ({@code price_kind = PAID}) ou as demais
   * @param limit como moito cantas
   */
  List<BookingId> expired(boolean paid, Instant cutoff, int limit);

  /**
   * Anonimiza esas reservas: sen conta, nome nin correo, coa data; quen cancelou e quen actuou na
   * historia, se era a propia persoa, pasa a {@link #ANONYMISED_ACTOR}. As xa anonimizadas non
   * cambian.
   *
   * @return cantas anonimizou
   */
  int anonymise(Collection<BookingId> ids, Instant now);
}
