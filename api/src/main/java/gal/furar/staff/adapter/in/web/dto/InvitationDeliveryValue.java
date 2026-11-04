// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * Como foi o correo da invitación: enviado, fallido («Reenviar») ou pendente (aínda non se sabe:
 * non se puido gardar o resultado).
 */
public enum InvitationDeliveryValue {
  PENDING,
  SENT,
  FAILED
}
