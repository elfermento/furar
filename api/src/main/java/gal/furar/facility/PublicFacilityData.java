// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As instalacións que ve a veciñanza co que xa mostra a súa ficha pública, para os datos abertos
 * (ADR-0034, puntos 7 e 16; A19: só tipos da raíz e de Java). Nunca o código RIDEGA, o tipo, as
 * fotos nin os campos do persoal.
 *
 * @param facilities por nome
 * @param customFields as definicións dos campos personalizados públicos e activos
 */
public record PublicFacilityData(
    List<Item> facilities, List<PublicCustomFields.Definition> customFields) {

  public PublicFacilityData {
    facilities = List.copyOf(facilities);
    customFields = List.copyOf(customFields);
  }

  /**
   * Unha instalación.
   *
   * @param sports na orde da ficha; {@code code} nulo nos deportes propios do concello
   * @param covered nulo = sen dato
   * @param amenities códigos dos servizos ({@code CHANGING_ROOMS}…)
   * @param week a semana da tempada vixente hoxe: do 1 (luns) ao 7 (domingo) e o 8 (festivos); nulo
   *     sen tempada vixente
   * @param customFields valores dos campos públicos, en forma JSON
   * @param uses os usos que mostra a ficha, por nome
   */
  public record Item(
      UUID id,
      String name,
      @Nullable Text description,
      String street,
      @Nullable String streetNumber,
      String postalCode,
      String locality,
      @Nullable BigDecimal latitude,
      @Nullable BigDecimal longitude,
      @Nullable String phone,
      @Nullable String email,
      List<SportItem> sports,
      @Nullable Boolean covered,
      List<String> amenities,
      boolean temporarilyClosed,
      boolean onlineBooking,
      boolean showFreeSlots,
      @Nullable Map<Integer, List<Hours>> week,
      @Nullable Text houseRules,
      Map<String, Object> customFields,
      List<Use> uses) {

    public Item {
      Objects.requireNonNull(id, "id");
      sports = List.copyOf(sports);
      amenities = List.copyOf(amenities);
      customFields = Map.copyOf(customFields);
      uses = List.copyOf(uses);
    }
  }

  /** Un texto en galego e castelán. */
  public record Text(String gl, String es) {}

  /** Un deporte: o código do catálogo (nulo se é propio do concello) e o nome. */
  public record SportItem(@Nullable String code, Text label) {}

  /** Un tramo do horario en minutos de reloxo (0–1440). */
  public record Hours(int startMinute, int endMinute) {}

  /**
   * Un uso.
   *
   * @param rentalMode {@code WHOLE}, {@code PARTS} ou {@code SEATS}
   * @param parts os nomes das partes vivas, na súa orde (só {@code PARTS})
   * @param capacity o aforo por franxa (só {@code SEATS})
   * @param weekdays máscara dos días da semana (luns = 1 … domingo = 64)
   * @param periods os períodos dun uso de só unhas datas; baleira nos de sempre
   * @param priceKind {@code PAID}, {@code FREE} ou {@code NONE}
   * @param capPeriod {@code DAY}, {@code WEEK} ou {@code MONTH}
   */
  public record Use(
      UUID id,
      String name,
      String rentalMode,
      int slotMinutes,
      List<String> parts,
      @Nullable Integer capacity,
      int weekdays,
      List<Period> periods,
      String priceKind,
      @Nullable Integer priceCents,
      @Nullable Integer minAdvanceHours,
      @Nullable Integer maxAdvanceDays,
      @Nullable Integer maxSlots,
      @Nullable Integer capCount,
      @Nullable String capPeriod,
      @Nullable Integer cancelHours) {

    public Use {
      parts = List.copyOf(parts);
      periods = List.copyOf(periods);
    }
  }

  /** Un período de datas, os dous extremos incluídos. */
  public record Period(LocalDate from, LocalDate to) {}
}
