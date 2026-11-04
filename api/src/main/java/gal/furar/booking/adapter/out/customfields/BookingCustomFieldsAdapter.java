// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.customfields;

import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.domain.BookingId;
import gal.furar.customfields.CustomFieldProjector;
import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.customfields.ValidationContext;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Campos propios da reserva pola API pública de {@code customfields} (A19). Quen reserva escribe
 * como titular; nas lecturas, a audiencia vén do caso de uso (as reservas propias, titular; as do
 * concello, persoal). Unha páxina de reservas custa dúas consultas, nunca unha por reserva.
 */
@Component
class BookingCustomFieldsAdapter implements BookingCustomFields {

  private final CustomFieldValidator validator;
  private final CustomFieldProjector projector;
  private final CustomFieldValueStore store;

  BookingCustomFieldsAdapter(
      CustomFieldValidator validator, CustomFieldProjector projector, CustomFieldValueStore store) {
    this.validator = validator;
    this.projector = projector;
    this.store = store;
  }

  @Override
  public CustomFieldValues validateNew(Map<String, ?> raw) {
    return validator.validate(
        CustomFieldTarget.BOOKING,
        raw == null ? Map.of() : raw,
        CustomFieldValues.empty(),
        ValidationContext.of(Audience.OWNER));
  }

  @Override
  public void replace(BookingId id, CustomFieldValues values) {
    store.replace(CustomFieldTarget.BOOKING, id.value(), values);
  }

  @Override
  public Map<UUID, CustomFieldValues> forHolder(Collection<UUID> bookingIds) {
    return visible(bookingIds, Audience.OWNER);
  }

  @Override
  public Map<UUID, CustomFieldValues> forStaff(Collection<UUID> bookingIds) {
    return visible(bookingIds, Audience.STAFF);
  }

  @Override
  public void erasePersonalData(BookingId id) {
    store.erasePersonalData(CustomFieldTarget.BOOKING, id.value());
  }

  private Map<UUID, CustomFieldValues> visible(Collection<UUID> bookingIds, Audience viewer) {
    if (bookingIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, CustomFieldValues> stored = store.loadAll(CustomFieldTarget.BOOKING, bookingIds);
    if (stored.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = new ArrayList<>(stored.keySet());
    List<CustomFieldValues> projected =
        projector.projectAll(
            CustomFieldTarget.BOOKING, ids.stream().map(stored::get).toList(), viewer);
    Map<UUID, CustomFieldValues> result = new HashMap<>();
    for (int i = 0; i < ids.size(); i++) {
      result.put(ids.get(i), projected.get(i));
    }
    return result;
  }
}
