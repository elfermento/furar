// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a16;

import gal.furar.shared.domain.CustomFieldValues;
import java.util.Map;

public class Forger {
  public CustomFieldValues forge() {
    return CustomFieldValues.trusted(Map.of());
  }
}
