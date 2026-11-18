// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.Requirement;
import gal.furar.facility.domain.Sport;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * O que devolven os casos de uso: o agregado, o que lle falta para deixar de estar en borrador
 * (calculado, ADR-0022 punto 3, na orde do {@code enum}) e os seus deportes co nome (na orde da
 * tarxeta Datos; baleiro no listado, que non os pinta), as imaxes e os campos propios que pode ver
 * quen le (os dous só na ficha; sen imaxes, {@code gallery} é nulo). {@code booked}: tivo reservas
 * algunha vez (só na ficha; entón non se pode eliminar, só dar de baixa). Nunca se garda unha View.
 */
public record FacilityView(
    Facility value,
    Set<Requirement> missing,
    List<Sport> sports,
    @Nullable FacilityGallery gallery,
    CustomFieldValues customFields,
    boolean booked) {

  public FacilityView {
    EnumSet<Requirement> ordered = EnumSet.noneOf(Requirement.class);
    ordered.addAll(missing);
    missing = Collections.unmodifiableSet(ordered);
    sports = List.copyOf(sports);
  }

  /** Sen saber se tivo reservas (exportación, listado). */
  public FacilityView(
      Facility value,
      Set<Requirement> missing,
      List<Sport> sports,
      @Nullable FacilityGallery gallery,
      CustomFieldValues customFields) {
    this(value, missing, sports, gallery, customFields, false);
  }

  /** Sen imaxes nin campos propios. */
  public FacilityView(Facility value, Set<Requirement> missing, List<Sport> sports) {
    this(value, missing, sports, null, CustomFieldValues.empty());
  }

  /** Sen os nomes dos deportes (listado). */
  public FacilityView(Facility value, Set<Requirement> missing) {
    this(value, missing, List.of(), null, CustomFieldValues.empty());
  }
}
