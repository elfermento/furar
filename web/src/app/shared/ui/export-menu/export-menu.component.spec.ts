// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { ExportMenuComponent } from './export-menu.component';

@Component({
  imports: [ExportMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-export-menu [subject]="'Ocupación'" [pdfHint]="'Este gráfico'" [pdf]="pdf" [csv]="csv" />
    <button type="button" id="outside">{{ 'fóra' }}</button>
  `,
})
class HostComponent {
  readonly requested: string[] = [];
  /** O PDF espera ata `finishPdf` (como a impresión); o CSV é ao momento. */
  finishPdf: () => void = () => undefined;
  readonly pdf = () => {
    this.requested.push('pdf');
    return new Promise<void>((resolve) => (this.finishPdf = resolve));
  };
  readonly csv = () => this.requested.push('csv');
}

/** Tecla con `key` e `keyCode` (o CDK aínda le keyCode). */
function key(target: Element, name: string, keyCode: number): void {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
}

const KEYS = { enter: 13, escape: 27, down: 40, up: 38 } as const;

describe('furar-export-menu', () => {
  let fixture: ComponentFixture<HostComponent>;

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  }

  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  const trigger = () =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('furar-export-menu button')!;
  const menu = () => document.querySelector<HTMLElement>('[role="menu"]');
  const items = () => [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"]')];

  async function open() {
    trigger().focus();
    key(trigger(), 'ArrowDown', KEYS.down);
    await fixture.whenStable();
  }

  it('botón de menú (APG): nome con que se exporta, aria-haspopup e aria-expanded', async () => {
    await render();
    expect(trigger().textContent?.trim()).toBe('Exportar');
    expect(trigger().getAttribute('aria-label')).toBe('Exportar: Ocupación');
    expect(trigger().getAttribute('aria-haspopup')).toBe('menu');
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('abre co foco no primeiro (PDF), cada un coa súa axuda; as frechas moven', async () => {
    await render();
    await open();
    expect(trigger().getAttribute('aria-expanded')).toBe('true');
    expect(menu()?.getAttribute('aria-label')).toBe('Exportar');
    expect(items().map((item) => item.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      'PDF Este gráfico',
      'CSV Os datos, para unha folla de cálculo',
    ]);
    expect(document.activeElement).toBe(items()[0]);
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    expect(document.activeElement).toBe(items()[1]);
    key(document.activeElement!, 'ArrowUp', KEYS.up);
    expect(document.activeElement).toBe(items()[0]);
    await expectNoAxeViolations(menu()!);
  });

  it('Intro escolle: emite, pecha e devolve o foco ao botón', async () => {
    await render();
    await open();
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    key(items()[1]!, 'Enter', KEYS.enter);
    await fixture.whenStable();
    expect(fixture.componentInstance.requested).toEqual(['csv']);
    expect(menu()).toBeNull();
    expect(document.activeElement).toBe(trigger());

    await open();
    items()[0]!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.requested).toEqual(['csv', 'pdf']);
    expect(menu()).toBeNull();
    fixture.componentInstance.finishPdf();
  });

  it('mentres exporta: «Exportar» ocupado co foco e o nome intactos, e non abre outra vez', async () => {
    await render();
    await open();
    items()[0]!.click();
    await fixture.whenStable();
    expect(trigger().getAttribute('aria-busy')).toBe('true');
    expect(trigger().getAttribute('aria-disabled')).toBe('true');
    expect(trigger().disabled).toBe(false);
    expect(trigger().getAttribute('aria-label')).toBe('Exportar: Ocupación');
    expect(document.activeElement).toBe(trigger());
    trigger().click();
    await fixture.whenStable();
    expect(menu()).toBeNull();
    fixture.componentInstance.finishPdf();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(trigger().hasAttribute('aria-busy')).toBe(false);
    });
    expect(trigger().hasAttribute('aria-disabled')).toBe(false);
    expect(fixture.componentInstance.requested).toEqual(['pdf']);
  });

  it('Esc pecha sen emitir e devolve o foco; clic fóra pecha', async () => {
    await render();
    await open();
    key(document.activeElement!, 'Escape', KEYS.escape);
    await fixture.whenStable();
    expect(menu()).toBeNull();
    expect(document.activeElement).toBe(trigger());

    await open();
    const outside = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#outside')!;
    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await fixture.whenStable();
    expect(menu()).toBeNull();
    expect(fixture.componentInstance.requested).toEqual([]);
  });

  it('Esc pecha só o menú: non chega ao documento (o modal de debaixo segue aberto)', async () => {
    await render();
    await open();
    const reached: string[] = [];
    const listener = (event: KeyboardEvent) => reached.push(event.key);
    document.body.addEventListener('keydown', listener);
    try {
      key(document.activeElement!, 'Escape', KEYS.escape);
      await fixture.whenStable();
      expect(menu()).toBeNull();
      expect(reached).toEqual([]);
    } finally {
      document.body.removeEventListener('keydown', listener);
    }
  });

  it('en castelán, coa axuda por defecto do PDF', async () => {
    await render('es');
    await open();
    expect(items()[1]?.textContent).toContain('Los datos, para una hoja de cálculo');
    expect(items()[0]?.textContent).toContain('Este gráfico');
    await expectNoAxeViolations(menu()!);
  });
});
