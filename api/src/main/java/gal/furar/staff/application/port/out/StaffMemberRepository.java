// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * As pertenzas do concello actual (ADR-0027). Sempre dentro dunha transacción e con concello: sen
 * elas, a RLS non devolve nada.
 */
public interface StaffMemberRepository {

  Optional<StaffMember> find(StaffMemberId id);

  /** A pertenza bloqueada ata o fin da transacción, para cambiala. */
  Optional<StaffMember> findForUpdate(StaffMemberId id);

  /** Todas ou só as dun estado, das máis novas ás máis vellas. */
  List<StaffMember> list(@Nullable StaffStatus status);

  /** As pertenzas destas persoas ({@code sub}), tamén as desactivadas. */
  List<StaffMember> findBySubjects(Collection<String> subjects);

  /** Estas pertenzas, tamén as desactivadas; as que non existen no concello non saen. */
  List<StaffMember> findByIds(Collection<StaffMemberId> ids);

  /**
   * Bloquea as pertenzas xestoras activas ata o fin da transacción e devolve cantas son (regra do
   * último xestor).
   */
  int lockActiveManagers();

  /**
   * Garda unha pertenza nova coa versión 0.
   *
   * @return falso se xa hai outra non desactivada co mesmo correo
   */
  boolean insert(StaffMember member);

  /**
   * Garda os cambios e sobe a versión.
   *
   * @return falso se a versión xa non é {@code expectedVersion}
   */
  boolean update(StaffMember member, long expectedVersion);

  /**
   * Conservación (§1.2.14): anonimiza o correo ({@link EmailAddress#anonymised}) e borra o nome das
   * pertenzas desactivadas antes de {@code cutoff}; a fila, o {@code sub} e as datas quedan. As xa
   * anonimizadas non se tocan.
   *
   * @return as que se anonimizaron agora
   */
  List<RetainedMember> anonymiseDeactivatedBefore(Instant cutoff, Instant now);

  /**
   * Conservación (§1.2.14; RGPD art. 5.1.c): as pertenzas invitadas antes de {@code invitedBefore}
   * que nunca aceptaron e xa non teñen ningunha invitación pasan a desactivadas por {@code by} e
   * anonimízanse ({@link EmailAddress#anonymised}, sen nome). Idempotente.
   *
   * @return as que se pecharon agora
   */
  List<RetainedMember> deactivateUnacceptedWithoutInvitation(
      Instant invitedBefore, String by, Instant now);
}
