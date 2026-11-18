// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a13.domain;

public class Mutable {
  private String name;

  public void setName(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }
}
