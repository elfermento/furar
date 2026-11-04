// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldTargetProvider;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A instalación admite campos personalizados (ADR-0011, ADR-0005 ARQ3-2): destino {@code FACILITY},
 * na tarxeta Datos. Un campo propio non pode levar o código dun campo núcleo da ficha.
 */
@Component
class FacilityCustomFieldTarget implements CustomFieldTargetProvider {

  private final FacilityRepository facilities;

  FacilityCustomFieldTarget(FacilityRepository facilities) {
    this.facilities = facilities;
  }

  @Override
  public CustomFieldTarget target() {
    return CustomFieldTarget.FACILITY;
  }

  @Override
  public Set<String> coreFields() {
    return Set.of(
        "name",
        "type",
        "description",
        "address",
        "street",
        "street_number",
        "postal_code",
        "locality",
        "ine_code",
        "latitude",
        "longitude",
        "sports",
        "ridega_code",
        "phone",
        "email",
        "online_booking",
        "show_free_slots",
        "covered",
        "amenities",
        "photos",
        "images",
        "uses",
        "schedule",
        "missing");
  }

  /** Só se é unha instalación do concello actual (o repositorio le dentro del). */
  @Override
  public boolean ownedByCurrentMunicipality(UUID entityId) {
    return facilities.findById(new FacilityId(entityId)).isPresent();
  }
}
