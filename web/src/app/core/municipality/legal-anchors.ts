// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/* Anclas fixas dos textos legais de Furar, nun módulo propio: o pé (shell) usa unha e así non leva á carga inicial as pezas dos textos (`legal-html-parts.ts`). */

/**
 * Ancla da sección «Persoal do concello» en Privacidade: a ela leva o correo da invitación
 * (`StaffInvitationMailComposer.STAFF_SECTION`, RGPD art. 14). Fixa: cambiala rompe os correos xa
 * enviados.
 */
export const STAFF_PRIVACY_ANCHOR = 'persoal';

/** Ancla da sección «Software» do aviso legal: a ela leva a marca do pé. */
export const SOFTWARE_LEGAL_ANCHOR = 'software';

/** Ancla da sección «Cookies» de Privacidade: a ela leva a ligazón «Cookies» do pé. */
export const COOKIES_PRIVACY_ANCHOR = 'cookies';

/** Ancla das condicións de uso dentro do aviso legal. */
export const CONDITIONS_LEGAL_ANCHOR = 'condicions';

/** As anclas fixas dos textos de Furar (as únicas `id` que pinta `[furarProseHtml]`). */
export const LEGAL_ANCHORS: readonly string[] = [
  STAFF_PRIVACY_ANCHOR,
  SOFTWARE_LEGAL_ANCHOR,
  COOKIES_PRIVACY_ANCHOR,
  CONDITIONS_LEGAL_ANCHOR,
];
