// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** QN2: nin dentro de @NativeSql vale @Query(nativeQuery = true): A8b non le anotacións. */
@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "fixture")
public interface NativeQueryInNativeSql extends Repository<Object, UUID> {
  @Query(value = "select code from custom_field", nativeQuery = true)
  List<String> allCodes();
}
