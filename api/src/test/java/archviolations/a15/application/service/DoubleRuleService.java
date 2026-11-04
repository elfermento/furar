// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a15.application.service;

import archviolations.a15.application.port.in.DoUseCase;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;

/** Dúas regras de acceso á vez: non se sabe cal manda. */
public class DoubleRuleService implements DoUseCase {
  @Override
  @PersonUseCase
  @PublicUseCase
  public void doIt() {}
}
