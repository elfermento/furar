// SPDX-FileCopyrightText: Google LLC
// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: Apache-2.0
/**
 * Material Symbols (Apache-2.0) como SVG empaquetados (docs/deseno.md P1-10, P27 do plan do dominio
 * 1): a única familia de iconas de Furar. Só as que se usan, estilo «outlined» de peso 400, camiños
 * copiados sen cambios de `@material-symbols/svg-400` 0.47.6 (o nome orixinal vai no comentario de
 * cada unha; a clave é o nome que usa Furar). Debúxanse con recheo sobre `viewBox="0 -960 960 960"`.
 * Aquí só as que pinta o marco ao abrir calquera páxina (barra superior e barra inferior pública),
 * que van na carga inicial; o resto vive en `lazy-symbols.ts` (orzamento, D27).
 */
export const SYMBOLS = {
  /** grid_view */
  grid: 'M120-510v-330h330v330H120Zm0 390v-330h330v330H120Zm390-390v-330h330v330H510Zm0 390v-330h330v330H510ZM180-570h210v-210H180v210Zm390 0h210v-210H570v210Zm0 390h210v-210H570v210Zm-390 0h210v-210H180v210Zm390-390Zm0 180Zm-180 0Zm0-180Z',
  /** home */
  home: 'M220-180h150v-250h220v250h150v-390L480-765 220-570v390Zm-60 60v-480l320-240 320 240v480H530v-250H430v250H160Zm320-353Z',
  /** more_horiz */
  more: 'M207.86-432Q188-432 174-446.14t-14-34Q160-500 174.14-514t34-14Q228-528 242-513.86t14 34Q256-460 241.86-446t-34 14Zm272 0Q460-432 446-446.14t-14-34Q432-500 446.14-514t34-14Q500-528 514-513.86t14 34Q528-460 513.86-446t-34 14Zm272 0Q732-432 718-446.14t-14-34Q704-500 718.14-514t34-14Q772-528 786-513.86t14 34Q800-460 785.86-446t-34 14Z',
  /** verified_user */
  shield:
    'm436-347 228-228-42-41-183 183-101-101-44 44 142 143Zm44 266q-140-35-230-162.5T160-523v-238l320-120 320 120v238q0 152-90 279.5T480-81Zm0-62q115-38 187.5-143.5T740-523v-196l-260-98-260 98v196q0 131 72.5 236.5T480-143Zm0-337Z',
  /** person */
  user: 'M372-523q-42-42-42-108t42-108q42-42 108-42t108 42q42 42 42 108t-42 108q-42 42-108 42t-108-42ZM160-160v-94q0-38 19-65t49-41q67-30 128.5-45T480-420q62 0 123 15.5T731-360q31 14 50 41t19 65v94H160Zm60-60h520v-34q0-16-9.5-30.5T707-306q-64-31-117-42.5T480-360q-57 0-111 11.5T252-306q-14 7-23 21.5t-9 30.5v34Zm324.5-346.5Q570-592 570-631t-25.5-64.5Q519-721 480-721t-64.5 25.5Q390-670 390-631t25.5 64.5Q441-541 480-541t64.5-25.5ZM480-631Zm0 411Z',
} as const satisfies Record<string, string>;

export type SymbolName = keyof typeof SYMBOLS;
