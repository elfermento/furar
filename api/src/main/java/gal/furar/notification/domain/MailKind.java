// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

/** Os catro correos dunha reserva (plan do dominio 2, §1, «Correo fixo gl/es»). */
public enum MailKind {
  CONFIRMED,
  CHANGED,
  CANCELLED_BY_PERSON,
  CANCELLED_BY_MUNICIPALITY;

  public boolean cancellation() {
    return this == CANCELLED_BY_PERSON || this == CANCELLED_BY_MUNICIPALITY;
  }
}
