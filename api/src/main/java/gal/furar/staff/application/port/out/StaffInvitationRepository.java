// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import gal.furar.staff.domain.InvitationDelivery;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffInvitationId;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.TokenHash;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As invitacións do concello actual (ADR-0027, puntos 6 e 7): só o hash do token. Sempre dentro
 * dunha transacción e con concello.
 */
public interface StaffInvitationRepository {

  void insert(StaffInvitation invitation);

  /**
   * A invitación deste hash no concello actual, estea como estea (usada, substituída ou caducada):
   * decide o dominio. A dun token doutro concello non se atopa.
   */
  Optional<StaffInvitation> findByHash(TokenHash hash);

  /**
   * Marca a invitación como usada se aínda vale (aberta e sen caducar en {@code now}). É a garda
   * ante dúas aceptacións, un reenvío ou unha desactivación ao mesmo tempo.
   *
   * @return falso se outra escritura chegou antes ou se caducou
   */
  boolean markUsed(StaffInvitationId id, Instant now);

  /** A última invitación de cada pertenza. */
  List<StaffInvitation> latestPerMember();

  Optional<StaffInvitation> latestOf(StaffMemberId member);

  /** Cantos envíos leva cada pertenza con algunha invitación (as que non teñen, non saen). */
  Map<StaffMemberId, Integer> sendCountPerMember();

  /** Horas de todos os envíos dunha pertenza. */
  List<Instant> sendsOf(StaffMemberId member);

  /** Horas dos envíos do concello despois de {@code since}, como moito {@code limit}. */
  List<Instant> sendsSince(Instant since, int limit);

  /** Substitúe a invitación aberta da pertenza, se a ten. */
  void supersedeOpen(StaffMemberId member, Instant now);

  /**
   * Garda o resultado do correo dunha invitación pendente.
   *
   * @return falso se xa tiña resultado
   */
  boolean recordDelivery(StaffInvitationId id, InvitationDelivery delivery);

  /**
   * Conservación (§1.2.14): borra as invitacións non aceptadas (abertas ou substituídas) que
   * caducaron antes de {@code cutoff}. As usadas quedan.
   *
   * @return as que se borraron
   */
  List<StaffInvitationId> deleteUnusedExpiredBefore(Instant cutoff);

  /** As invitacións que quedan destas pertenzas (as aceptadas), para esquecer os seus envíos. */
  List<StaffInvitationId> idsOf(Collection<StaffMemberId> members);
}
