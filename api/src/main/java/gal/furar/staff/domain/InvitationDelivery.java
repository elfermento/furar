// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

/**
 * Resultado do envío do correo dunha invitación (plan do dominio 4, §1.2.6): pendente mentres se
 * envía, despois do commit e fóra da transacción; enviado ou fallido despois.
 */
public enum InvitationDelivery {
  PENDING,
  SENT,
  FAILED
}
