// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { preparePanelPrint, printWithChartImages, swapChartsForImages, useLightTheme } from './print-charts';

describe('gráficos como imaxes ao imprimir', () => {
  // jsdom non trae `adoptedStyleSheets`: unha lista simple fai as súas veces.
  let adopted: CSSStyleSheet[] = [];
  beforeAll(() => {
    Object.defineProperty(document, 'adoptedStyleSheets', {
      configurable: true,
      get: () => adopted,
      set: (sheets: CSSStyleSheet[]) => (adopted = sheets),
    });
  });
  afterAll(() => {
    delete (document as { adoptedStyleSheets?: unknown }).adoptedStyleSheets;
  });

  afterEach(() => {
    document.body.replaceChildren();
    document.documentElement.removeAttribute('data-print');
    vi.restoreAllMocks();
  });

  it('un furar-chart sen debuxar (ou que non é un gráfico) queda como estaba', () => {
    document.body.innerHTML = '<figure role="img" aria-label="X"><furar-chart><div></div></furar-chart></figure>';
    const restore = swapChartsForImages(document.body);
    expect(document.querySelector('img')).toBeNull();
    restore();
    // O canvas nunca se agocha na pantalla: a folla de impresión escolle a imaxe só no papel.
    expect(document.querySelector('furar-chart div')!.hasAttribute('hidden')).toBe(false);
  });

  it('se falla antes de imprimir, non imprime e desfai o alcance', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    await expect(
      printWithChartImages(document, 'panel', null, () => {
        throw new Error('sen render');
      }),
    ).rejects.toThrow('sen render');
    expect(print).not.toHaveBeenCalled();
    expect(document.documentElement.hasAttribute('data-print')).toBe(false);
  });

  it('render corre antes de imprimir; afterprint limpa o alcance', async () => {
    const order: string[] = [];
    vi.spyOn(window, 'print').mockImplementation(() => {
      order.push(`print:${document.documentElement.getAttribute('data-print')}`);
    });
    await printWithChartImages(document, 'panel', null, () => order.push('render'));
    expect(order).toEqual(['render', 'print:panel']);
    window.dispatchEvent(new Event('afterprint'));
    expect(document.documentElement.hasAttribute('data-print')).toBe(false);
  });

  it('cun tema escuro, o claro só para o papel (folla @media print), sen tocar a pantalla; cun claro, nada', () => {
    const style = document.documentElement.style;
    style.setProperty('--f-surface', '#25272a');
    style.setProperty('--f-ink', '#f2f2f2');
    style.setProperty('--f-primary', '#7cc4ff');
    const restore = useLightTheme(document);
    // A pantalla segue co tema escuro (sen pestanexo en claro).
    expect(style.getPropertyValue('--f-surface')).toBe('#25272a');
    const sheets = document.adoptedStyleSheets;
    expect(sheets).toHaveLength(1);
    const rule = sheets[0]!.cssRules[0] as CSSMediaRule;
    expect(rule.media.mediaText).toBe('print');
    const root = rule.cssRules[0] as CSSStyleRule;
    expect(root.style.getPropertyValue('--f-surface')).toBe('#ffffff');
    expect(root.style.getPropertyPriority('--f-surface')).toBe('important');
    expect(root.style.getPropertyValue('--f-ink')).toBe('#1a1a1a');
    // O primary do escuro non chega a 3:1 sobre branco: o do tema por defecto.
    expect(root.style.getPropertyValue('--f-primary')).toBe('#0b5c8e');
    restore();
    expect(document.adoptedStyleSheets).toHaveLength(0);
    for (const name of ['--f-surface', '--f-ink', '--f-primary']) style.removeProperty(name);
    style.setProperty('--f-surface', '#ffffff');
    useLightTheme(document)();
    expect(document.adoptedStyleSheets).toHaveLength(0);
    style.removeProperty('--f-surface');
  });

  it('preparePanelPrint: sen alcance pon o do panel e desfaise; con alcance (Exportar), nada', () => {
    const render = vi.fn();
    const undo = preparePanelPrint(document, render)!;
    expect(document.documentElement.getAttribute('data-print')).toBe('panel');
    expect(render).toHaveBeenCalledOnce();
    undo();
    expect(document.documentElement.hasAttribute('data-print')).toBe(false);
    document.documentElement.setAttribute('data-print', 'card');
    expect(preparePanelPrint(document, render)).toBeNull();
    expect(document.documentElement.getAttribute('data-print')).toBe('card');
  });
});
