// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a22;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.Repository;

/** QN2: @NativeQuery de Spring Data JPA 4 leva SQL que A8b non le. */
public interface NativeQueryRepository extends Repository<FormulaEntity, UUID> {
  @NativeQuery("select code from custom_field")
  List<String> allCodes();
}
