// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: o concello rematou a tempada dunha entidade desde {@code from} e cancelou {@code
 * cancelled} sesións (ADR-0039, punto 11); un só correo á entidade. Sen persoas, só ids.
 *
 * @param noticeId distingue esta publicación (rematala outra vez é outro correo)
 */
public record BookingSeriesEnded(
    UUID municipalityId, UUID seriesId, LocalDate from, int cancelled, UUID noticeId)
    implements TenantAware {

  public BookingSeriesEnded {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(seriesId, "seriesId");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(noticeId, "noticeId");
  }
}
