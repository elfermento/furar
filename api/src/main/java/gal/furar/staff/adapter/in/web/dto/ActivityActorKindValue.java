// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

/**
 * Que tipo de conta actuou: persoal do concello (co seu nome e correo), a plataforma
 * («Administración da plataforma»), unha tarefa de Furar («Furar (tarefa automática)») ou outra
 * («Conta sen rol»).
 */
public enum ActivityActorKindValue {
  MEMBER,
  PLATFORM,
  SYSTEM,
  OTHER
}
