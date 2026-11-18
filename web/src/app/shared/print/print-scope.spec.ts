// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { printWithScope, setPrintScope } from './print-scope';

describe('alcance da impresión', () => {
  const root = document.documentElement;
  let card: HTMLElement;
  let other: HTMLElement;

  beforeEach(() => {
    card = document.body.appendChild(document.createElement('section'));
    other = document.body.appendChild(document.createElement('section'));
  });

  afterEach(() => {
    card.remove();
    other.remove();
    root.removeAttribute('data-print');
    vi.restoreAllMocks();
  });

  it('pon data-print en <html> e data-print-target na tarxeta; afterprint quítaos', () => {
    setPrintScope(document, 'card', card);
    expect(root.getAttribute('data-print')).toBe('card');
    expect(card.hasAttribute('data-print-target')).toBe(true);

    window.dispatchEvent(new Event('afterprint'));
    expect(root.hasAttribute('data-print')).toBe(false);
    expect(card.hasAttribute('data-print-target')).toBe(false);
  });

  it('un alcance novo substitúe o anterior sen deixar outro destino marcado', () => {
    setPrintScope(document, 'card', other);
    setPrintScope(document, 'panel');
    expect(root.getAttribute('data-print')).toBe('panel');
    expect(other.hasAttribute('data-print-target')).toBe(false);
    window.dispatchEvent(new Event('afterprint'));
    expect(root.hasAttribute('data-print')).toBe(false);
  });

  it('printWithScope: o alcance xa está posto en before e en print, e limpa en afterprint', async () => {
    const seen: (string | null)[] = [];
    const print = vi.spyOn(window, 'print').mockImplementation(() => {
      seen.push(root.getAttribute('data-print'));
    });
    await printWithScope(document, 'dialog', null, () => {
      seen.push(root.getAttribute('data-print'));
    });
    expect(print).toHaveBeenCalledOnce();
    expect(seen).toEqual(['dialog', 'dialog']);
    window.dispatchEvent(new Event('afterprint'));
    expect(root.hasAttribute('data-print')).toBe(false);
  });

  it('se before falla, desfai o alcance e non imprime', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    await expect(
      printWithScope(document, 'card', card, () => {
        throw new Error('sen imaxe');
      }),
    ).rejects.toThrow('sen imaxe');
    expect(print).not.toHaveBeenCalled();
    expect(root.hasAttribute('data-print')).toBe(false);
    expect(card.hasAttribute('data-print-target')).toBe(false);
  });
});
