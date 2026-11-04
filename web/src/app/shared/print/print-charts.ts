// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { DerivedTokens } from '../../core/theme/theme-tokens';
import { TOKEN_VARIABLES } from '../../core/theme/theme-tokens';
import { printTheme } from '../ui/chart/chart-tokens';
import { chartImage } from '../ui/chart/chart.component';
import type { PrintScope } from './print-scope';
import { printWithScope, setPrintScope } from './print-scope';

/** Marca das imaxes postas para imprimir (a folla de impresión e a limpeza búscanas por ela). */
const PRINT_IMAGE = 'data-print-image';

/**
 * Pon en cada `furar-chart` de `root` un `<img>` da súa imaxe en claro (o papel é branco, aínda
 * que se vexa en escuro) co `alt` do seu `<figure role="img">` (título e conclusión; sen figura,
 * como a minigráfica dunha cifra, decorativa). Un canvas non sempre sae no PDF e, se sae, co tema
 * da pantalla: a imaxe sae sempre igual. A folla de impresión (print.css) mostra a imaxe e agocha o
 * canvas só no papel: na pantalla non cambia nada mentres o diálogo está aberto.
 */
export function swapChartsForImages(root: ParentNode): () => void {
  const undo: (() => void)[] = [];
  for (const chart of root.querySelectorAll('furar-chart')) {
    const src = chartImage(chart, 'light');
    if (!src || !chart.firstElementChild) continue;
    const img = chart.ownerDocument.createElement('img');
    img.src = src;
    img.alt = chart.closest('[role="img"]')?.getAttribute('aria-label') ?? '';
    img.className = 'f-chart__print';
    img.setAttribute(PRINT_IMAGE, '');
    chart.appendChild(img);
    undo.push(() => img.remove());
  }
  return () => {
    for (const step of undo.splice(0)) step();
  };
}

/**
 * Cun tema escuro, os tokens do tema claro (`printTheme`) só para o papel: unha folla construída
 * (CSSOM, permitida pola CSP) con `@media print` e `!important`, que gaña ao tema que ThemeService
 * pon en `style` de `<html>`. O texto claro sobre papel branco non se le, e a pantalla non pestanexa
 * en claro mentres se imprime. Devolve a función que a quita.
 */
export function useLightTheme(document: Document): () => void {
  const root = document.documentElement;
  const view = document.defaultView;
  const read = (name: string) => view?.getComputedStyle(root).getPropertyValue(name).trim() ?? '';
  const light = printTheme(read(TOKEN_VARIABLES.surface), read(TOKEN_VARIABLES.primary));
  if (!light || !view?.CSSStyleSheet || !Array.isArray(document.adoptedStyleSheets)) return () => undefined;
  const tokens = (Object.entries(TOKEN_VARIABLES) as [keyof DerivedTokens, string][])
    .map(([key, name]) => `${name}: ${light[key]} !important;`)
    .join(' ');
  const sheet = new view.CSSStyleSheet();
  sheet.replaceSync(`@media print { :root { ${tokens} } }`);
  document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet];
  return () => {
    document.adoptedStyleSheets = document.adoptedStyleSheets.filter((s) => s !== sheet);
  };
}

/**
 * Prepara o PDF do panel cando a persoa imprime co navegador (Ctrl+P) sen pasar por «Exportar»:
 * se non hai alcance, pon `panel`, as imaxes dos gráficos e o tema claro para o papel. Devolve a
 * función que o desfai (en `afterprint`) ou `null` se xa había un alcance (o de «Exportar»).
 */
export function preparePanelPrint(document: Document, render?: () => void): (() => void) | null {
  if (document.documentElement.hasAttribute('data-print')) return null;
  const scope = setPrintScope(document, 'panel');
  render?.();
  const images = swapChartsForImages(document.body);
  const theme = useLightTheme(document);
  return () => {
    theme();
    images();
    scope();
  };
}

/**
 * Imprime co alcance (`printWithScope`), cos gráficos del cambiados por imaxes en claro e, cun
 * tema escuro, co tema claro (`useLightTheme`). Antes,
 * `render` (p. ex. `detectChanges`) para que a cabeceira impresa xa leve a hora de agora. As imaxes
 * e o tema quítanse en `afterprint` ou se algo falla antes de imprimir.
 */
export async function printWithChartImages(
  document: Document,
  scope: PrintScope,
  target?: Element | null,
  render?: () => void,
): Promise<void> {
  const view = document.defaultView;
  let restore: () => void = () => undefined;
  const done = () => {
    view?.removeEventListener('afterprint', done);
    restore();
  };
  view?.addEventListener('afterprint', done);
  try {
    await printWithScope(document, scope, target, () => {
      render?.();
      const images = swapChartsForImages(target ?? document.body);
      const theme = useLightTheme(document);
      restore = () => {
        theme();
        images();
      };
    });
  } catch (error) {
    done();
    throw error;
  }
}
