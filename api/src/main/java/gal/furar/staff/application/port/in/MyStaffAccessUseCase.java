// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Que pode facer na xestión a persoa da sesión, no concello do URL (plan do dominio 4, §2.3): a web
 * decide con isto as entradas do menú e as gardas.
 */
public interface MyStaffAccessUseCase {

  MyStaffAccess mine();
}
