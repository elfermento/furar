// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a15.application.service;

import archviolations.a15.application.port.in.DoUseCase;
import gal.furar.shared.security.PersonUseCase;

/** Correcto: un caso de uso da persoa (ADR-0025) declara @PersonUseCase. */
public class PersonService implements DoUseCase {
  @Override
  @PersonUseCase
  public void doIt() {}
}
