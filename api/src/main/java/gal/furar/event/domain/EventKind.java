// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

/**
 * Que tipo de evento é (ADR-0041): dun día ({@code ONE_DAY}, ADR-0038: unha data e unha hora) ou
 * con horario ({@code SCHEDULED}, unha actividade: uns días da semana a unha hora, entre dúas
 * datas, cunha sesión por día).
 */
public enum EventKind {
  ONE_DAY,
  SCHEDULED
}
