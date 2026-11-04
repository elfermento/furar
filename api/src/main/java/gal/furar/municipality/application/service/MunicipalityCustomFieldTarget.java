// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldTargetProvider;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** O concello admite campos personalizados (ADR-0011, ADR-0005 ARQ3-2). */
@Component
class MunicipalityCustomFieldTarget implements CustomFieldTargetProvider {

  @Override
  public CustomFieldTarget target() {
    return CustomFieldTarget.MUNICIPALITY;
  }

  @Override
  public Set<String> coreFields() {
    return Set.of("slug", "name", "languages", "default_language", "theme", "legal");
  }

  /** O concello só garda valores de si mesmo. */
  @Override
  public boolean ownedByCurrentMunicipality(UUID entityId) {
    return TenantContext.current().map(TenantRef::id).filter(entityId::equals).isPresent();
  }
}
