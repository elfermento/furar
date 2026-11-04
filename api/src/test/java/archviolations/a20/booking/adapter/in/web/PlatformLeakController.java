// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a20.booking.adapter.in.web;

import gal.furar.shared.adapter.out.persistence.DatabaseRoleCheck;
import gal.furar.shared.tenancy.TenantIdentifierResolver;
import gal.furar.shared.web.TenantFilter;

/** ARQ3-4: un controlador usa a infraestrutura de plataforma de shared. */
public class PlatformLeakController {
  public String leak(
      TenantFilter filter, DatabaseRoleCheck check, TenantIdentifierResolver resolver) {
    return filter.toString() + check + resolver;
  }
}
