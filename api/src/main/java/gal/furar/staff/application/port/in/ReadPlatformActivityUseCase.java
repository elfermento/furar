// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Ler o rexistro de actividade da plataforma (plan do dominio 4, §1.2.12, P4): só as accións feitas
 * co rol {@code PLATFORM_ADMIN} e as que non teñen concello, de todos os concellos.
 */
public interface ReadPlatformActivityUseCase {

  ActivityPage readPlatform(ActivityQuery query);
}
