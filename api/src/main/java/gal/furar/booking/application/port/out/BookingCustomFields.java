// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.application.port.in.BookingFieldColumn;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porto de saída: os campos propios da reserva (destino {@code BOOKING}), que valida e garda {@code
 * customfields} (ADR-0011). Sempre no concello actual.
 */
public interface BookingCustomFields {

  /**
   * Valida os valores que escribe quen reserva (titular): os campos de «só o persoal» non os ve nin
   * os escribe.
   *
   * @throws gal.furar.shared.domain.DomainException {@code validation.failed} con {@code errors[]}
   *     en {@code customFields.<código>}, ou {@code customfield.limit-exceeded}
   */
  CustomFieldValues validateNew(Map<String, ?> raw);

  /**
   * Valida os valores que escribe o persoal ao reservar no nome dunha persoa ou ao cambiar a
   * reserva: todos os campos, tamén os de «só o persoal».
   *
   * @param previous os valores gardados ata agora ({@link CustomFieldValues#empty()} ao crear)
   * @throws gal.furar.shared.domain.DomainException {@code validation.failed} con {@code errors[]}
   *     en {@code customFields.<código>}, ou {@code customfield.limit-exceeded}
   */
  CustomFieldValues validateByStaff(Map<String, ?> raw, CustomFieldValues previous);

  /** Os valores gardados da reserva, todos (para validar un cambio sobre eles). */
  CustomFieldValues stored(BookingId id);

  /** Garda os valores; na mesma transacción en que se garda a reserva. */
  void replace(BookingId id, CustomFieldValues values);

  /** Os valores que ve quen fixo as reservas (titular): sen os de «só o persoal». */
  Map<UUID, CustomFieldValues> forHolder(Collection<UUID> bookingIds);

  /** Os valores que ve o persoal: todos. */
  Map<UUID, CustomFieldValues> forStaff(Collection<UUID> bookingIds);

  /**
   * Os campos que ve o persoal, como columnas da descarga: os mesmos e na orde de {@link
   * #forStaff}.
   */
  List<BookingFieldColumn> staffColumns();

  /**
   * Borra os valores dos campos marcados como dato persoal (RGPD, anonimización): na mesma
   * transacción en que se anonimiza a reserva.
   */
  void erasePersonalData(BookingId id);
}
