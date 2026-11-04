// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a5.adapter.in.web;

import archviolations.a5.domain.Thing;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DomainController {
  @GetMapping("/things")
  public List<Thing> things() {
    return List.of();
  }
}
