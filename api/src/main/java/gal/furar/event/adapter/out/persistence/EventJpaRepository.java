// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {

  List<EventEntity> findByPublishedTrueAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAscIdAsc(
      Instant now);
}
