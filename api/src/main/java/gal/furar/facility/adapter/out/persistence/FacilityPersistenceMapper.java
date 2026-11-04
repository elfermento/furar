// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDescription;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityPaymentNotes;
import gal.furar.facility.domain.HouseRules;
import gal.furar.facility.domain.SportRef;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.LocalizedText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Dominio ↔ entidades JPA, a man. */
final class FacilityPersistenceMapper {

  private FacilityPersistenceMapper() {}

  static FacilityEntity newEntity(Facility facility) {
    var entity = new FacilityEntity(facility.id().value(), facility.createdAt());
    apply(entity, facility);
    return entity;
  }

  static void apply(FacilityEntity entity, Facility facility) {
    entity.apply(facility.name(), facility.status(), facility.published(), facility.updatedAt());
    FacilityDetails d = facility.details();
    FacilityDescription description = facility.description();
    entity.applyDetails(
        d.type(),
        d.ridegaCode(),
        d.phone(),
        d.email(),
        d.onlineBooking(),
        d.showFreeSlots(),
        d.covered(),
        description == null ? null : text(description.gl(), description.es()));
    HouseRules houseRules = facility.houseRules();
    entity.applyHouseRules(houseRules == null ? null : text(houseRules.gl(), houseRules.es()));
    FacilityPaymentNotes notes = facility.paymentNotes();
    entity.applyPaymentNotes(note(notes.payment()), note(notes.refund()));
    Address a = facility.address();
    if (a == null) {
      entity.applyAddress(null, null, null, null, null, null, null);
    } else {
      entity.applyAddress(
          a.street(),
          a.streetNumber(),
          a.postalCode(),
          a.locality(),
          a.ineCode(),
          a.latitude(),
          a.longitude());
    }
  }

  /** Filas novas dos deportes, coa súa posición na tarxeta. */
  static List<FacilitySportEntity> sports(Facility facility) {
    UUID facilityId = facility.id().value();
    List<SportRef> refs = facility.details().sports();
    List<FacilitySportEntity> rows = new ArrayList<>();
    for (int i = 0; i < refs.size(); i++) {
      SportRef ref = refs.get(i);
      rows.add(
          new FacilitySportEntity(
              Ids.newV7(),
              facilityId,
              ref.code(),
              ref.customSportId() == null ? null : ref.customSportId().value(),
              i));
    }
    return rows;
  }

  static List<FacilityAmenityEntity> amenities(Facility facility) {
    return facility.amenities().stream()
        .map(a -> new FacilityAmenityEntity(facility.id().value(), a.name()))
        .toList();
  }

  static Facility toDomain(
      FacilityEntity e, List<FacilitySportEntity> sports, List<FacilityAmenityEntity> amenities) {
    Map<String, String> description = e.description();
    Map<String, String> houseRules = e.houseRules();
    return Facility.rehydrate(
        new FacilityId(e.getId()),
        e.name(),
        e.status(),
        address(e),
        e.published(),
        new FacilityDetails(
            sports.stream().map(FacilityPersistenceMapper::ref).toList(),
            e.type(),
            e.ridegaCode(),
            e.phone(),
            e.email(),
            e.onlineBooking(),
            e.showFreeSlots(),
            e.covered()),
        description == null
            ? null
            : new FacilityDescription(description.get("gl"), description.get("es")),
        houseRules == null ? null : new HouseRules(houseRules.get("gl"), houseRules.get("es")),
        new FacilityPaymentNotes(note(e.paymentNote()), note(e.refundNote())),
        amenities.stream().map(a -> Amenity.valueOf(a.code())).toList(),
        e.version() == null ? 0 : e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  /** Sen vía non hai enderezo (a base garante que vai enteiro). */
  private static @Nullable Address address(FacilityEntity e) {
    String street = e.street();
    String postalCode = e.postalCode();
    String locality = e.locality();
    String ineCode = e.ineCode();
    if (street == null || postalCode == null || locality == null || ineCode == null) {
      return null;
    }
    return new Address(
        street, e.streetNumber(), postalCode, locality, ineCode, e.latitude(), e.longitude());
  }

  private static @Nullable Map<String, String> note(@Nullable LocalizedText t) {
    return t == null ? null : text(t.gl(), t.es());
  }

  private static @Nullable LocalizedText note(@Nullable Map<String, String> m) {
    return m == null ? null : new LocalizedText(m.get("gl"), m.get("es"));
  }

  static SportRef ref(FacilitySportEntity s) {
    UUID custom = s.customSportId();
    String code = s.sportCode();
    return custom != null ? SportRef.custom(custom) : SportRef.catalog(code);
  }

  static Map<String, String> text(String gl, String es) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("gl", gl);
    map.put("es", es);
    return map;
  }

  static LocalizedText text(Map<String, String> m) {
    return new LocalizedText(m.get("gl"), m.get("es"));
  }
}
