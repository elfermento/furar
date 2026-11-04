// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a22;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLRestriction;

/** QA Q13: SQL en anotacións que @TenantId non filtra. */
@Entity
@SQLRestriction("archived = false")
public class FormulaEntity {
  @Id private UUID id;

  @Formula("(select count(*) from custom_field o where o.code = code)")
  private long sameCode;

  @ColumnTransformer(read = "upper(code)")
  private String code;
}
