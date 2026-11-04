// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a17;

import gal.furar.shared.tenancy.TenantContext;

public class Sneaky {
  public int all() {
    return TenantContext.runAsPlatform(() -> 1);
  }
}
