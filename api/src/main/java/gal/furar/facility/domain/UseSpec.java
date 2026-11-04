// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que escribe o xestor nun uso, sen comprobar: {@link FacilityUse} valídao ao crear ou editar. As
 * partes van enteiras e na súa orde; as que xa existen, co seu id. Días da semana ({@link
 * Weekdays}), prezo e normas do dominio 2: sen eles, todos os días, sen prezo e sen normas.
 */
public record UseSpec(
    String name,
    RentalMode rentalMode,
    @Nullable Integer capacity,
    int slotMinutes,
    UseAvailability availability,
    boolean blocksOthers,
    List<PartSpec> parts,
    List<UsePeriod> periods,
    int weekdays,
    UsePrice price,
    UseRules rules) {

  public UseSpec {
    parts = parts == null ? List.of() : List.copyOf(parts);
    periods = periods == null ? List.of() : List.copyOf(periods);
    price = price == null ? UsePrice.NONE : price;
    rules = rules == null ? UseRules.NONE : rules;
  }

  /** Uso de todos os días, sen prezo nin normas. */
  public UseSpec(
      String name,
      RentalMode rentalMode,
      @Nullable Integer capacity,
      int slotMinutes,
      UseAvailability availability,
      boolean blocksOthers,
      List<PartSpec> parts,
      List<UsePeriod> periods) {
    this(
        name,
        rentalMode,
        capacity,
        slotMinutes,
        availability,
        blocksOthers,
        parts,
        periods,
        Weekdays.ALL,
        UsePrice.NONE,
        UseRules.NONE);
  }

  /** Unha parte: sen id é nova; con id, unha das que o uso xa ten. */
  public record PartSpec(@Nullable UUID id, String name) {}
}
