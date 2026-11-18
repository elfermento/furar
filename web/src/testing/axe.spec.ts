// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import axe from 'axe-core';
import { expectNoAxeViolations } from './axe';

describe('expectNoAxeViolations', () => {
  it('detecta de verdade as violacións en jsdom (non é unha comprobación baleira)', async () => {
    const host = document.createElement('div');
    host.innerHTML = '<img src="x.png"><button type="button"></button><input id="sen-nome">';
    document.body.append(host);
    const { violations } = await axe.run(host, { runOnly: { type: 'tag', values: ['wcag2a'] } });
    expect(violations.map((v) => v.id)).toEqual(expect.arrayContaining(['image-alt', 'button-name', 'label']));
    await expect(expectNoAxeViolations(host)).rejects.toThrow();
    host.remove();
  });
});
