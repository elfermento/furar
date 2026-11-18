// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** Acender ou apagar o aviso para instalar no móbil (Concello › Xeral). */
public record UpdateInstallHintCommand(long expectedVersion, boolean enabled) {}
