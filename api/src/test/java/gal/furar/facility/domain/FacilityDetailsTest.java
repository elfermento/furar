// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tarxeta Datos, descrición e deportes: invariantes sen Spring nin base. */
class FacilityDetailsTest {

  static FacilityDetails details(
      List<SportRef> sports, String phone, String email, boolean online, boolean slots) {
    return new FacilityDetails(sports, null, null, phone, email, online, slots, null);
  }

  static List<String> fields(Runnable call, FacilityError code) {
    DomainException e = (DomainException) catchThrowable(call);
    assertThat(e.errorCode()).isEqualTo(code);
    return e.violations().stream().map(FieldViolation::field).toList();
  }

  static Throwable catchThrowable(Runnable call) {
    try {
      call.run();
    } catch (RuntimeException e) {
      return e;
    }
    throw new AssertionError("no exception");
  }

  @Test
  void emptyIsBookableOnlineWithoutFreeSlots() {
    assertThat(FacilityDetails.EMPTY.onlineBooking()).isTrue();
    assertThat(FacilityDetails.EMPTY.showFreeSlots()).isFalse();
    assertThat(FacilityDetails.EMPTY.covered()).isNull();
    assertThat(FacilityDetails.EMPTY.sports()).isEmpty();
  }

  @Test
  void onlineBookingAndFreeSlotsNeverGoTogether() {
    assertThatThrownBy(() -> details(List.of(), null, null, true, true))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FacilityError.BOOKING_MODE_INVALID));
    assertThat(details(List.of(), null, null, false, true).showFreeSlots()).isTrue();
    assertThat(details(List.of(), null, null, false, false).onlineBooking()).isFalse();
  }

  @Test
  void blankTextsAreNoDataAndBadOnesAreReportedByField() {
    FacilityDetails blank = details(List.of(), "  ", " ", true, false);
    assertThat(blank.phone()).isNull();
    assertThat(blank.email()).isNull();
    assertThat(details(List.of(), " +34 981 38 21 00 ", "a@b.gal", true, false).phone())
        .isEqualTo("+34 981 38 21 00");
    assertThat(
            fields(
                () ->
                    new FacilityDetails(
                        Collections.nCopies(31, SportRef.catalog("football")),
                        null,
                        "x".repeat(31),
                        "98-1",
                        "sen-arroba",
                        true,
                        false,
                        null),
                FacilityError.DETAILS_INVALID))
        .containsExactly("details.sports", "details.ridegaCode", "details.phone", "details.email");
  }

  @Test
  void aSportIsFromTheCatalogOrFromTheMunicipalityNeverBoth() {
    assertThat(SportRef.catalog("padel").isCustom()).isFalse();
    assertThat(SportRef.custom(UUID.randomUUID()).isCustom()).isTrue();
    assertThatThrownBy(() -> new SportRef(null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SportRef("padel", CustomSportId.newId()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void descriptionIsInBothLanguagesOrNone() {
    assertThat(FacilityDescription.of(null, " ")).isNull();
    assertThat(FacilityDescription.of(" Ola ", "Hola"))
        .isEqualTo(new FacilityDescription("Ola", "Hola"));
    assertThat(
            fields(() -> FacilityDescription.of("Só gl", null), FacilityError.DESCRIPTION_INVALID))
        .containsExactly("description.es");
    assertThat(
            fields(
                () -> FacilityDescription.of("x".repeat(2001), "y"),
                FacilityError.DESCRIPTION_INVALID))
        .containsExactly("description.gl");
  }

  @Test
  void customSportNeedsBothNames() {
    CustomSport sport =
        CustomSport.create(CustomSportId.newId(), " Pickleball ", "Pickleball", FacilityTest.NOW);
    assertThat(sport.label().gl()).isEqualTo("Pickleball");
    assertThat(sport.sport().ref().isCustom()).isTrue();
    assertThat(
            fields(
                () ->
                    CustomSport.create(CustomSportId.newId(), "", "x".repeat(61), FacilityTest.NOW),
                FacilityError.SPORT_NAME_INVALID))
        .containsExactly("label.gl", "label.es");
  }

  @Test
  void sectionsKeepTheRestOfTheFacility() {
    Facility facility =
        Facility.create(FacilityId.newId(), "Campo", FacilityTest.address(), FacilityTest.NOW);
    Facility equipped =
        facility.equip(List.of(Amenity.SHOWERS, Amenity.CAFE, Amenity.SHOWERS), FacilityTest.NOW);
    assertThat(equipped.amenities()).containsExactly(Amenity.SHOWERS, Amenity.CAFE);
    Facility described =
        equipped.describe(new FacilityDescription("Ola", "Hola"), FacilityTest.NOW);
    Facility detailed =
        described.updateDetails(
            details(List.of(SportRef.catalog("football")), null, null, true, false),
            FacilityTest.NOW);
    assertThat(detailed.amenities()).containsExactly(Amenity.SHOWERS, Amenity.CAFE);
    assertThat(detailed.description()).isNotNull();
    assertThat(detailed.details().sports()).containsExactly(SportRef.catalog("football"));
    assertThat(detailed.name()).isEqualTo("Campo");
  }
}
