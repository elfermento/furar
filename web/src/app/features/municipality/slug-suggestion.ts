// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Palabras que a API non deixa como identificador (ADR-0006, `Slug.RESERVED`). */
export const RESERVED_SLUGS: ReadonlySet<string> = new Set([
  'api',
  'auth',
  'assets',
  'plataforma',
  'xestion',
  'admin',
  'healthz',
  'actuator',
  'codigo-fonte',
  'erro',
  'tiles',
  'en',
  'es',
  'gl',
]);

export const SLUG_MAX = 40;

/**
 * Identificador suxerido para o nome dun concello: minúsculas, sen tiles nin diacríticos (ñ → n,
 * ç → c), cada tramo que non sexa letra ou díxito → un guión, sen guións nos extremos e ata 40
 * caracteres («A Coruña» → `a-coruna`). Baleiro se o resultado non valería (menos de 2 caracteres
 * ou unha palabra reservada): mellor nada ca un identificador que a API vai rexeitar.
 */
export function suggestSlug(name: string): string {
  const slug = name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, SLUG_MAX)
    .replace(/-+$/, '');
  return slug.length < 2 || RESERVED_SLUGS.has(slug) ? '' : slug;
}
