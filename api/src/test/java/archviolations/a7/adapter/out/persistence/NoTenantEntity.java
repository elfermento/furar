// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a7.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class NoTenantEntity {
  @Id private Long id;
}
