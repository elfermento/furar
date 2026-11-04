// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import axe from 'axe-core';

/**
 * axe (WCAG 2.1 A e AA) sobre un fragmento renderizado en jsdom. O contraste de cor non se pode
 * medir sen motor de pintado: está probado en theme-tokens.spec.ts e no e2e con Chromium.
 */
export async function expectNoAxeViolations(element: Element): Promise<void> {
  const { violations } = await axe.run(element, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
  });
  const summary = violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(summary).toEqual([]);
}
