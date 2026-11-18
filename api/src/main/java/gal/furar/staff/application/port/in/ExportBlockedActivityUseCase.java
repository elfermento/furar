// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Exportar os eventos bloqueados do rexistro do concello (LOPDGDD art. 32; ADR-0020, revisión do
 * 2026-10-29): os que xa non se ven na web e aínda se gardan, só para un requirimento ou un
 * expediente, cun motivo e unha referencia que quedan no rexistro. Só o xestor do concello; a
 * plataforma non.
 */
public interface ExportBlockedActivityUseCase {

  /** Os eventos bloqueados do período, do máis vello ao máis novo, coa identidade do persoal. */
  BlockedActivityExport export(ExportBlockedActivityCommand command);
}
