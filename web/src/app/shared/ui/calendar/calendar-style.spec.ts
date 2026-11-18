// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Aspecto dos días que non se poden escoller (docs/deseno.md §3.1 e §8, decidido o 2026-10-26): apagados
 * en gris claro, sen riscar nin bordo descontinuo; con cores forzadas, `GrayText`. O estado vai en
 * `aria-disabled` (calendar.component.spec.ts).
 */
const CSS = readFileSync(resolve(process.cwd(), 'src/styles/components/calendar.css'), 'utf8').replace(
  /\/\*[\s\S]*?\*\//g,
  '',
);

function rule(selector: string, from = 0): string {
  const start = CSS.indexOf(`${selector} {`, from);
  expect(start, selector).toBeGreaterThanOrEqual(0);
  return CSS.slice(start, CSS.indexOf('}', start));
}

describe('calendario: días non seleccionables', () => {
  it('vense apagados, sen riscar nin bordo descontinuo', () => {
    const disabled = rule(".f-calendar__day[aria-disabled='true']");
    expect(disabled).toContain('color: color-mix(in srgb, var(--f-ink-soft) 50%, var(--f-surface))');
    expect(disabled).not.toContain('line-through');
    expect(disabled).not.toContain('dashed');
    expect(disabled).not.toContain('border');
  });

  it('con cores forzadas, GrayText', () => {
    const forced = CSS.indexOf('@media (forced-colors: active)');
    expect(rule(".f-calendar__day[aria-disabled='true']", forced)).toContain('color: GrayText');
  });
});
