// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

/**
 * Os correos das reservas (ADR-0025, punto 15): os catro da fase 1, o de cada falta (ADR-0032,
 * punto 10), os dous das esperas (ADR-0033), o recordatorio (ADR-0035) e os seis das partidas
 * abertas (ADR-0040).
 */
public enum MailKind {
  CONFIRMED,
  CHANGED,
  CANCELLED_BY_PERSON,
  CANCELLED_BY_MUNICIPALITY,
  /** A reserva quedou anotada como falta («non presentado»). */
  NO_SHOW,
  /**
   * «Quedou libre» (ADR-0033, punto 11): non é dunha reserva, senón dunha espera; vai polo seu
   * propio camiño ({@code SendWaitMail}).
   */
  SLOT_RELEASED,
  /** O recordatorio dunha reserva, 24 horas antes do prazo para cancelar de balde (ADR-0035). */
  REMINDER,
  /**
   * «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): de esperas, coma «Quedou
   * libre»; un correo por persoa con todas as súas esperas desa instalación.
   */
  WAIT_CANCELLED,
  /**
   * Partida aberta cancelada (ADR-0040): ás persoas dentro e, se non chegou ao mínimo, a quen
   * organiza. Os seis das partidas van polo seu propio camiño ({@code SendOpenMatchMail}).
   */
  MATCH_CANCELLED,
  /** A reserva da partida cambiou de día ou de hora: ás persoas dentro. */
  MATCH_CHANGED,
  /** Alguén pediu unirse: a quen organiza. */
  MATCH_REQUESTED,
  /** Quen organiza aceptou a solicitude: a quen a pediu. */
  MATCH_ACCEPTED,
  /** Quen organiza rexeitou a solicitude: a quen a pediu. */
  MATCH_REJECTED,
  /** A xestión ocultou a partida: a quen organiza. */
  MATCH_HIDDEN,
  /**
   * Tempada asignada (ou solicitude aprobada) á entidade, con todas as datas (ADR-0039). Os tres
   * das tempadas van polo seu propio camiño ({@code SendSeriesMail}).
   */
  SEASON_CREATED,
  /** O concello rematou a tempada: as sesións que quedaban, canceladas. */
  SEASON_ENDED,
  /** O concello non aprobou a solicitude de tempada, coa súa mensaxe. */
  SEASON_REQUEST_REJECTED;

  /** Se é o correo dunha reserva (nin das esperas, nin das partidas abertas, nin das tempadas). */
  public boolean ofBooking() {
    return !ofWait() && !ofOpenMatch() && !ofSeason();
  }

  /** Se é un correo dunha tempada ou solicitude de tempada (ADR-0039). */
  public boolean ofSeason() {
    return name().startsWith("SEASON_");
  }

  /** Se é un correo dunha partida aberta (ADR-0040). */
  public boolean ofOpenMatch() {
    return name().startsWith("MATCH_");
  }

  /** Se é o correo dunha espera («Quedou libre» ou «Lista de espera cancelada»). */
  public boolean ofWait() {
    return this == SLOT_RELEASED || this == WAIT_CANCELLED;
  }

  public boolean cancellation() {
    return this == CANCELLED_BY_PERSON || this == CANCELLED_BY_MUNICIPALITY;
  }
}
