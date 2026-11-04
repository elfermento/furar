// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a22;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.Subselect;

/** QA Q13: unha entidade sobre un SELECT arbitrario. */
@Entity
@Subselect("select id from custom_field")
public class SubselectView {
  @Id private UUID id;
}
