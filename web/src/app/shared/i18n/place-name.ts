// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Artigos dos topónimos galegos e a súa contracción con «de» (os plurais primeiro). */
const GALICIAN_ARTICLES: readonly (readonly [string, string])[] = [
  ['As ', 'das '],
  ['Os ', 'dos '],
  ['A ', 'da '],
  ['O ', 'do '],
];

/** Os mesmos artigos contraídos con «en». */
const GALICIAN_ARTICLES_IN: readonly (readonly [string, string])[] = [
  ['As ', 'nas '],
  ['Os ', 'nos '],
  ['A ', 'na '],
  ['O ', 'no '],
];

/** «Concello de …», «Ayuntamiento da …»: a institución, a preposición e o resto. */
const INSTITUTION = /^(?:concello|ayuntamiento)\s+(de|da|do|das|dos)\s+(\S.*)$/iu;

/** O artigo que leva o topónimo segundo a preposición contraída. */
const ARTICLE: Readonly<Record<string, string>> = { de: '', da: 'A ', do: 'O ', das: 'As ', dos: 'Os ' };

/**
 * O topónimo: «Concello da Coruña» → «A Coruña»; «Narón» → «Narón». O nome do concello é o
 * topónimo e os textos engaden «Concello»; se alguén escribiu a denominación, úsase o topónimo, para
 * non dicir «Concello de Concello de Narón». A mesma regra ca a API (`shared.domain.PlaceName`).
 */
function toponym(name: string): string {
  const trimmed = name.trim();
  const institution = INSTITUTION.exec(trimmed);
  if (!institution) return trimmed;
  return (ARTICLE[institution[1].toLowerCase()] ?? '') + institution[2];
}

function contract(name: string, lang: string, articles: typeof GALICIAN_ARTICLES, plain: string): string {
  const trimmed = toponym(name);
  if (lang === 'gl') {
    for (const [article, contracted] of articles) {
      if (trimmed.startsWith(article) && trimmed.length > article.length) {
        return contracted + trimmed.slice(article.length);
      }
    }
  }
  return `${plain} ${trimmed}`;
}

/**
 * «de» + o nome dun lugar, como se escribe en cada idioma: en galego a preposición contrae co
 * artigo («da Coruña», «do Porriño», «das Pontes», «dos Blancos»; o resto, «de Narón»); en
 * castelán, sempre «de» («de A Coruña»). A mesma regra ca a API (`shared.domain.PlaceName`).
 */
export function placeOf(name: string, lang: string): string {
  return contract(name, lang, GALICIAN_ARTICLES, 'de');
}

/** «en» + o nome dun lugar: en galego contrae co artigo («na Coruña», «nas Pontes»; «en Narón»). */
export function placeIn(name: string, lang: string): string {
  return contract(name, lang, GALICIAN_ARTICLES_IN, 'en');
}

/**
 * Parámetros dos textos co nome do concello: `name` («Narón») e `ofName` («de Narón», «da Coruña»).
 * Os textos que dirían «de {{name}}» levan `{{ofName}}` («Concello {{ofName}}»).
 */
export function placeParams(name: string, lang: string): { name: string; ofName: string } {
  return { name, ofName: placeOf(name, lang) };
}
