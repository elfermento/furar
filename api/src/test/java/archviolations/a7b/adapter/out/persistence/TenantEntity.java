// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a7b.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
public class TenantEntity {
  @Id private Long id;
  @TenantId private UUID municipalityId;
}
