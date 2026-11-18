// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * O 25 de outubro de 2026 ás 19:30 en Nova York, que en Madrid xa é o 26 ás 00:30: o día do
 * navegador e o de Madrid non coinciden.
 */
export const BROWSER_DAY = '2026-10-25';
export const MADRID_DAY = '2026-10-26';

/**
 * Pon o navegador en Nova York (a zona de Node, `TZ`) e o reloxo nese instante; só se finxe
 * `Date`, os temporizadores seguen reais. Devolve a función que o desfai (para `afterEach`).
 */
export function fakeBrowserInNewYork(): () => void {
  const previous = process.env['TZ'];
  process.env['TZ'] = 'America/New_York';
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-25T23:30:00Z'));
  return () => {
    vi.useRealTimers();
    if (previous === undefined) delete process.env['TZ'];
    else process.env['TZ'] = previous;
  };
}
