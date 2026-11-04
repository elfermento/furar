// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityJpaRepository extends JpaRepository<MunicipalityEntity, UUID> {

  boolean existsBySlug(String slug);
}
