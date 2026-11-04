// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { withPopoverApi } from '../../../../testing/popover';
import type { SelectOption } from './select.component';
import { SELECT_POSITIONS, SelectComponent } from './select.component';

const LANGUAGES: SelectOption<string>[] = [
  { value: 'gl', label: 'Galego' },
  { value: 'es', label: 'Castelán' },
];
const PERIODS: SelectOption<string | null>[] = [
  { value: null, label: 'Sen límite' },
  { value: 'DAY', label: 'Ao día' },
  { value: 'WEEK', label: 'Á semana' },
  { value: 'MONTH', label: 'Ao mes' },
];

@Component({
  imports: [ReactiveFormsModule, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (keydown.escape)="formEscapes = formEscapes + 1">
      <label for="lang">{{ 'Idioma por defecto' }}</label>
      <furar-select
        id="lang-box"
        selectId="lang"
        formControlName="lang"
        [label]="'Idioma por defecto'"
        [options]="languages"
      />
      <furar-select
        id="period-box"
        formControlName="period"
        [label]="'Período'"
        [options]="periods"
        placeholder="Escoller"
      />
      <furar-select id="free-box" [label]="'Instalación'" [options]="languages" (changed)="picked.set($event)" />
      <button id="after" type="button">{{ 'Seguinte' }}</button>
    </form>
  `,
})
class HostComponent {
  formEscapes = 0;
  readonly languages = LANGUAGES;
  readonly periods = PERIODS;
  readonly picked = signal<string | null>(null);
  readonly form = new FormGroup({
    lang: new FormControl<string | null>('gl'),
    period: new FormControl<string | null>(null),
  });
}

/** Tecla con `key` e `keyCode` (o CDK aínda le keyCode). */
function key(target: Element, name: string, keyCode: number): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
  return event;
}

const KEYS = { enter: 13, escape: 27, space: 32, end: 35, home: 36, down: 40, tab: 9 } as const;

describe('furar-select', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const button = (fixture: ComponentFixture<HostComponent>, id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`#${id} button`)!;
  const listbox = () => document.querySelector<HTMLElement>('[role="listbox"]');
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];

  async function open(fixture: ComponentFixture<HostComponent>, id: string) {
    button(fixture, id).click();
    await fixture.whenStable();
  }

  it('o botón amosa o valor; o nome leva o rótulo e o valor; o <label for> apunta ao botón', async () => {
    const fixture = await render();
    const lang = button(fixture, 'lang-box');
    expect(lang.id).toBe('lang');
    expect(lang.textContent?.trim()).toBe('Galego');
    expect(lang.getAttribute('aria-label')).toBe('Idioma por defecto, Galego');
    expect(lang.getAttribute('aria-haspopup')).toBe('listbox');
    expect(lang.getAttribute('aria-expanded')).toBe('false');
    // null tamén é un valor con texto («Sen límite»).
    expect(button(fixture, 'period-box').textContent?.trim()).toBe('Sen límite');
    // Sen valor nin opción, o texto baleiro.
    expect(button(fixture, 'free-box').hasAttribute('data-empty')).toBe(true);
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('ao abrir: lista co nome do rótulo e o foco na escollida, co ✓', async () => {
    const fixture = await render();
    await open(fixture, 'lang-box');
    expect(button(fixture, 'lang-box').getAttribute('aria-expanded')).toBe('true');
    expect(button(fixture, 'lang-box').getAttribute('aria-controls')).toBe(listbox()?.id);
    expect(listbox()?.getAttribute('aria-label')).toBe('Idioma por defecto');
    expect(options().map((o) => o.textContent?.trim())).toEqual(['Galego', 'Castelán']);
    expect(document.activeElement).toBe(options()[0]);
    expect(options()[0].getAttribute('aria-selected')).toBe('true');
    await expectNoAxeViolations(document.body);
  });

  it('teclado: frechas, Inicio e Fin moven; Intro escolle, pecha e devolve o foco', async () => {
    const fixture = await render();
    await open(fixture, 'period-box');
    key(document.activeElement!, 'End', KEYS.end);
    await fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('Ao mes');
    key(document.activeElement!, 'Home', KEYS.home);
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
    const enter = key(document.activeElement!, 'Enter', KEYS.enter);
    await fixture.whenStable();
    expect(enter.defaultPrevented).toBe(true);
    expect(fixture.componentInstance.form.controls.period.value).toBe('DAY');
    expect(listbox()).toBeNull();
    expect(document.activeElement).toBe(button(fixture, 'period-box'));
    expect(button(fixture, 'period-box').textContent?.trim()).toBe('Ao día');
    expect(fixture.componentInstance.form.controls.period.touched).toBe(true);
  });

  it('Esc só pecha a lista, aínda dentro dun formulario que se pecha con Esc', async () => {
    const restore = withPopoverApi();
    try {
      const fixture = await render();
      await open(fixture, 'lang-box');
      key(document.activeElement!, 'ArrowDown', KEYS.down);
      const escape = key(document.activeElement!, 'Escape', KEYS.escape);
      await fixture.whenStable();
      expect(escape.defaultPrevented).toBe(true);
      expect(fixture.componentInstance.formEscapes).toBe(0);
      expect(listbox()).toBeNull();
      expect(fixture.componentInstance.form.controls.lang.value).toBe('gl');
      expect(document.activeElement).toBe(button(fixture, 'lang-box'));
    } finally {
      restore();
    }
  });

  it('frecha abaixo no botón abre; Tab pecha sen cambiar e deixa seguir', async () => {
    const fixture = await render();
    const lang = button(fixture, 'lang-box');
    lang.focus();
    const down = key(lang, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
    expect(down.defaultPrevented).toBe(true);
    expect(listbox()).not.toBeNull();
    const tab = key(document.activeElement!, 'Tab', KEYS.tab);
    await fixture.whenStable();
    expect(tab.defaultPrevented).toBe(false);
    expect(listbox()).toBeNull();
    expect(document.activeElement).toBe(lang);
  });

  it('picar escolle e emite changed sen formulario; picar fóra pecha sen cambiar', async () => {
    const fixture = await render();
    await open(fixture, 'free-box');
    options()[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.picked()).toBe('es');
    expect(button(fixture, 'free-box').textContent?.trim()).toBe('Castelán');
    await open(fixture, 'lang-box');
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#after')!.click();
    await fixture.whenStable();
    expect(listbox()).toBeNull();
    expect(fixture.componentInstance.form.controls.lang.value).toBe('gl');
  });

  it('segue setValue e disable do formulario', async () => {
    const fixture = await render();
    const form = fixture.componentInstance.form;
    form.controls.lang.setValue('es');
    form.controls.period.disable();
    await fixture.whenStable();
    expect(button(fixture, 'lang-box').textContent?.trim()).toBe('Castelán');
    expect(button(fixture, 'period-box').disabled).toBe(true);
  });

  it('a lista abre co ancho do campo como mínimo (no panel do CDK)', async () => {
    const fixture = await render();
    const lang = button(fixture, 'lang-box');
    lang.getBoundingClientRect = () => ({ width: 256 }) as DOMRect;
    await open(fixture, 'lang-box');
    const pane = listbox()!.closest<HTMLElement>('.cdk-overlay-pane')!;
    expect(pane.style.minWidth).toBe('256px');
    // Se non cabe aliñada á esquerda do campo, á dereita (dentro da pantalla).
    expect(SELECT_POSITIONS.map((p) => p.overlayX)).toEqual(['start', 'start', 'end', 'end']);
  });

  it('a lista enche o panel (select.css): non se queda co ancho do seu texto', () => {
    // O panel do CDK é flex en fila: sen medrar, a lista mediría o seu contido e non o mínimo.
    const css = readFileSync(resolve(process.cwd(), 'src/styles/components/select.css'), 'utf8');
    const list = css.slice(css.indexOf('.f-select__list {'), css.indexOf('}', css.indexOf('.f-select__list {')));
    expect(list).toContain('flex: 1 1 auto;');
    expect(list).toContain('max-width: calc(100vw - 2 * var(--spacing-gutter));');
  });
});
