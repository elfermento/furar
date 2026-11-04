// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.out;

import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;

/** Porto de saída. Sempre dentro do concello actual: o adaptador non recibe o concello. */
public interface @Name@Repository {

  Optional<@Name@> findById(@Name@Id id);

  List<@Name@> findAllById(List<@Name@Id> ids);

  PageResult<@Name@> findPage(int page, int size);

  /** Todos os do concello, por data de alta (portabilidade, ADR-0018). */
  List<@Name@> findAll();

  @Name@ insert(@Name@ @name@);

  /** Garda os cambios se a versión coincide coa gardada; se non, {@code common.stale-version}. */
  @Name@ update(@Name@ @name@);
}
