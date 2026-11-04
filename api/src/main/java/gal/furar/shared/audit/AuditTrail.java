// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Lectura do rexistro de actividade (ADR-0020, ADR-0027; plan do dominio 4, §1.2.12 e §2.2).
 * Páxinas de {@value #PAGE_SIZE} eventos, do máis novo ao máis vello, por {@code (occurred_at,
 * id)}. Non comproba permisos: os casos de uso que a chaman (en {@code staff}) xa o fixeron.
 */
public interface AuditTrail {

  int PAGE_SIZE = 50;

  /**
   * O rexistro dun concello: todo o que pasou nel, tamén o que fixo a plataforma.
   *
   * @param after o cursor da páxina anterior; nulo = a primeira
   */
  AuditPage page(UUID municipalityId, AuditFilter filter, @Nullable AuditCursor after);

  /**
   * O rexistro da plataforma: os eventos con {@code PLATFORM_ADMIN} entre os roles de quen actuou e
   * os que non teñen concello, de todos os concellos.
   *
   * @param after o cursor da páxina anterior; nulo = a primeira
   */
  AuditPage platformPage(AuditFilter filter, @Nullable AuditCursor after);

  /**
   * Todos os eventos dun concello nun período, do máis vello ao máis novo, para unha exportación.
   * Non mira se están bloqueados: o período xa o recorta quen chama.
   *
   * @param from comezo, incluído
   * @param to fin, excluído
   * @param limit cantos como moito; se hai máis, devolve {@code limit + 1} para que quen chama o
   *     saiba
   */
  List<AuditRecord> all(UUID municipalityId, Instant from, Instant to, int limit);
}
