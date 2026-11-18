// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.List;

/**
 * Porto para percorrer todos os concellos sen depender do módulo {@code municipality}: as tarefas
 * da plataforma ({@link PlatformTask}) traballan despois en cada un co seu {@link
 * TenantContext#runAs} e a súa RLS. Implementao o módulo {@code municipality}.
 */
public interface MunicipalityDirectory {

  /** Todos os concellos, en orde estable (polo id). */
  List<TenantRef> all();
}
