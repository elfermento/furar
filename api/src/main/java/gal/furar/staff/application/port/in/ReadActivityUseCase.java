// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Ler o rexistro de actividade do concello (plan do dominio 4, §1.2.12): todo o que pasou nel,
 * tamén o que fixo a plataforma, con nome e correo do persoal.
 */
public interface ReadActivityUseCase {

  ActivityPage read(ActivityQuery query);
}
