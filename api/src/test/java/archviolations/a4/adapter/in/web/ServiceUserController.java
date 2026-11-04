// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a4.adapter.in.web;

import archviolations.a4.application.service.SomeService;

public class ServiceUserController {
  public String call(SomeService service) {
    return service.run();
  }
}
