// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Ligazóns que admite o editor de texto: só https e correo, as mesmas que deixa o saneador do
 * servidor. Fóra de rich-text-setup.ts para que validar non cargue TipTap.
 */
export const RICH_TEXT_LINK = /^(https:\/\/\S+|mailto:[^@\s]+@[^@\s]+\.[^@\s]+)$/i;
