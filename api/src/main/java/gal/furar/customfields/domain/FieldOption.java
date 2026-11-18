// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.LocalizedText;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opción dun campo de selección. O código é inmutable. Desactivada, non se pode escoller nun valor
 * novo, pero os gardados conservan a súa etiqueta; sen valores, pódese eliminar (ADR-0011, revisión
 * do 2026-11-04).
 */
public record FieldOption(String code, LocalizedText label, boolean inactive) {

  static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9_]{0,39}$");

  public FieldOption {
    Objects.requireNonNull(label, "label");
    if (code == null || !CODE.matcher(code).matches()) {
      throw new DomainException(
          CustomFieldError.INVALID_DEFINITION, Map.of("reason", "option-code-invalid"));
    }
  }

  public FieldOption deactivate() {
    return new FieldOption(code, label, true);
  }
}
