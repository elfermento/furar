// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.service;

import gal.furar.@module@.@Name@Queries;
import gal.furar.@module@.@Name@Summary;
import gal.furar.@module@.application.port.out.@Name@Repository;
import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Id;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link @Name@Queries}) para outros módulos. Sempre no concello
 * actual (o repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class @Name@QueryService implements @Name@Queries {

  private final @Name@Repository repository;

  @Name@QueryService(@Name@Repository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<@Name@Summary> find(UUID id) {
    return repository.findById(new @Name@Id(id)).map(@Name@QueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<@Name@Summary> findAll(List<UUID> ids) {
    return repository.findAllById(ids.stream().map(@Name@Id::new).toList()).stream()
        .map(@Name@QueryService::summary)
        .toList();
  }

  private static @Name@Summary summary(@Name@ @name@) {
    return new @Name@Summary(@name@.id().value(), @name@.name());
  }
}
