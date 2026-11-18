// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import gal.furar.shared.domain.LocalizedText;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Os campos personalizados da instalación que ve calquera persoa (ADR-0011; ADR-0034, punto 16): só
 * os do destino {@code FACILITY} con visibilidade {@code PUBLIC} e activos. Nunca os do persoal nin
 * un dato persoal (a base impide que un dato persoal sexa público).
 *
 * @param definitions as definicións, por orde de código
 * @param values por instalación, os valores en forma JSON (texto, número, booleano, data {@code
 *     AAAA-MM-DD}, código da opción ou lista de códigos); unha instalación sen valores non aparece
 */
public record PublicCustomFields(
    List<Definition> definitions, Map<UUID, Map<String, Object>> values) {

  public static final PublicCustomFields NONE = new PublicCustomFields(List.of(), Map.of());

  public PublicCustomFields {
    definitions = List.copyOf(definitions);
    values = Map.copyOf(values);
  }

  /** Os valores dunha instalación (baleiro se non ten). */
  public Map<String, Object> of(UUID facilityId) {
    return values.getOrDefault(facilityId, Map.of());
  }

  /**
   * Un campo público.
   *
   * @param type {@code TEXT}, {@code LONG_TEXT}, {@code NUMBER}, {@code DATE}, {@code BOOLEAN},
   *     {@code SELECT} ou {@code MULTI_SELECT}
   * @param options as opcións, na orde da definición (baleira se o tipo non as ten)
   */
  public record Definition(String code, LocalizedText label, String type, List<Option> options) {

    public Definition {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(type, "type");
      options = List.copyOf(options);
    }
  }

  /** Unha opción dun campo de selección. */
  public record Option(String code, LocalizedText label) {}
}
