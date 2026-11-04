// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { withPopoverApi } from '../../../../testing/popover';
import { LanguageService } from '../../../core/i18n/language.service';
import { provideCdkGlobalStyles } from '../overlay/cdk-styles';
import { TimePickerComponent } from './time-picker.component';
import { formatMinutes, timeOptions } from './time-format';

@Component({
  imports: [ReactiveFormsModule, TimePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form">
      <furar-time-picker id="from" formControlName="from" [label]="'Luns, tramo 1, desde'" [min]="360" />
      <furar-time-picker id="to" formControlName="to" allowEndOfDay [label]="'Luns, tramo 1, ata'" [min]="1380" />
      <furar-time-picker id="empty" formControlName="empty" [label]="'Martes, tramo 1, desde'" />
      <button id="after" type="button">{{ 'Seguinte' }}</button>
    </form>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    from: new FormControl<number | null>(480),
    to: new FormControl<number | null>(1440),
    empty: new FormControl<number | null>(null),
  });
}

/** O selector dentro dun formulario que se pecha con Esc (bloqueos, usos, excepcións). */
@Component({
  imports: [ReactiveFormsModule, TimePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (keydown.escape)="formEscapes = formEscapes + 1">
      <furar-time-picker id="from" formControlName="from" [label]="'Desde'" />
    </form>
  `,
})
class InFormHostComponent {
  formEscapes = 0;
  readonly form = new FormGroup({ from: new FormControl<number | null>(480) });
}

/** Tecla con `key` e `keyCode` (o CDK aínda le keyCode). */
function key(target: Element, name: string, keyCode: number): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
  return event;
}

const KEYS = { enter: 13, escape: 27, space: 32, end: 35, home: 36, up: 38, down: 40, tab: 9 } as const;

describe('furar-time-picker', () => {
  let scrolled: Element[];

  beforeEach(() => {
    scrolled = [];
    Element.prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this);
    };
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(providers: unknown[] = []) {
    TestBed.configureTestingModule({ providers: [provideTestI18n(), ...(providers as [])] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const button = (fixture: ComponentFixture<HostComponent>, id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`#${id} button`)!;
  const listbox = () => document.querySelector<HTMLElement>('[role="listbox"]');
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];

  async function openWithClick(fixture: ComponentFixture<HostComponent>, id: string) {
    button(fixture, id).click();
    await fixture.whenStable();
  }

  it('o botón amosa a hora en texto e o nome leva o contexto e a hora', async () => {
    const fixture = await render();
    const from = button(fixture, 'from');
    expect(from.textContent?.trim()).toBe('8:00');
    expect(from.getAttribute('aria-label')).toBe('Luns, tramo 1, desde, 8:00');
    expect(from.getAttribute('aria-haspopup')).toBe('listbox');
    expect(from.getAttribute('aria-expanded')).toBe('false');
    expect(button(fixture, 'to').textContent?.trim()).toBe('24:00');
    expect(button(fixture, 'empty').getAttribute('aria-label')).toBe('Martes, tramo 1, desde, Escoller hora');
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('ao abrir: aria-expanded, lista cada 15 min co foco na escollida e desprazada ata ela', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'from');
    const from = button(fixture, 'from');
    expect(from.getAttribute('aria-expanded')).toBe('true');
    expect(from.getAttribute('aria-controls')).toBe(listbox()?.id);
    expect(listbox()?.getAttribute('aria-label')).toBe('Luns, tramo 1, desde');
    const all = options();
    expect(all[0].textContent?.trim()).toBe('6:00');
    expect(all[1].textContent?.trim()).toBe('6:15');
    expect(all.at(-1)?.textContent?.trim()).toBe('23:45');
    const selected = all.find((o) => o.getAttribute('aria-selected') === 'true');
    expect(selected?.textContent?.trim()).toBe('8:00');
    expect(scrolled).toContain(selected);
    expect(document.activeElement).toBe(selected);
    await expectNoAxeViolations(document.body);
  });

  it('teclado: frechas, Inicio e Fin moven; Intro escolle, pecha e devolve o foco ao botón', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'from');
    const list = listbox()!;
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('8:15');
    key(document.activeElement!, 'End', KEYS.end);
    await fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('23:45');
    key(document.activeElement!, 'Home', KEYS.home);
    await fixture.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('6:00');
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
    const event = key(document.activeElement!, 'Enter', KEYS.enter);
    await fixture.whenStable();
    expect(event.defaultPrevented).toBe(true);
    expect(fixture.componentInstance.form.controls.from.value).toBe(375);
    expect(list.isConnected).toBe(false);
    expect(document.activeElement).toBe(button(fixture, 'from'));
    expect(button(fixture, 'from').textContent?.trim()).toBe('6:15');
    expect(button(fixture, 'from').getAttribute('aria-expanded')).toBe('false');
    expect(fixture.componentInstance.form.controls.from.touched).toBe(true);
  });

  it('Esc pecha sen cambiar o valor e o foco volve ao botón', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'from');
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    key(document.activeElement!, 'Escape', KEYS.escape);
    await fixture.whenStable();
    expect(listbox()).toBeNull();
    expect(fixture.componentInstance.form.controls.from.value).toBe(480);
    expect(document.activeElement).toBe(button(fixture, 'from'));
  });

  it('Esc na lista dentro dun formulario só pecha a lista, aínda coa capa no formulario (Chrome)', async () => {
    const restore = withPopoverApi();
    try {
      TestBed.configureTestingModule({ providers: [provideTestI18n()] });
      const fixture = TestBed.createComponent(InFormHostComponent);
      await fixture.whenStable();
      const form = (fixture.nativeElement as HTMLElement).querySelector('form')!;
      const trigger = form.querySelector<HTMLButtonElement>('#from button')!;
      trigger.click();
      await fixture.whenStable();
      expect(form.contains(listbox())).toBe(true);
      const escape = key(document.activeElement!, 'Escape', KEYS.escape);
      await fixture.whenStable();
      expect(escape.defaultPrevented).toBe(true);
      expect(fixture.componentInstance.formEscapes).toBe(0);
      expect(listbox()).toBeNull();
      expect(document.activeElement).toBe(trigger);
      expect(fixture.componentInstance.form.controls.from.value).toBe(480);
    } finally {
      restore();
    }
  });

  it('frecha abaixo no botón abre; Tab pecha sen cambiar e deixa o foco no botón para seguir', async () => {
    const fixture = await render();
    const from = button(fixture, 'from');
    from.focus();
    const down = key(from, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
    expect(down.defaultPrevented).toBe(true);
    expect(listbox()).not.toBeNull();
    const tab = key(document.activeElement!, 'Tab', KEYS.tab);
    await fixture.whenStable();
    expect(tab.defaultPrevented).toBe(false);
    expect(listbox()).toBeNull();
    expect(document.activeElement).toBe(from);
    expect(fixture.componentInstance.form.controls.from.value).toBe(480);
  });

  it('picar unha opción escolle; picar fóra pecha sen cambiar', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'from');
    options()[4].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.form.controls.from.value).toBe(420);
    await openWithClick(fixture, 'from');
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#after')!.click();
    await fixture.whenStable();
    expect(listbox()).toBeNull();
    expect(fixture.componentInstance.form.controls.from.value).toBe(420);
  });

  it('allowEndOfDay ofrece 24:00 (1440) e min recorta o principio', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'to');
    expect(options().map((o) => o.textContent?.trim())).toEqual(['23:00', '23:15', '23:30', '23:45', '24:00']);
    key(document.activeElement!, 'Home', KEYS.home);
    key(document.activeElement!, ' ', KEYS.space);
    await fixture.whenStable();
    expect(fixture.componentInstance.form.controls.to.value).toBe(1380);
  });

  it('sen valor, abre na primeira hora; con formControlName segue setValue e disable', async () => {
    const fixture = await render();
    await openWithClick(fixture, 'empty');
    expect(document.activeElement?.textContent?.trim()).toBe('0:00');
    key(document.activeElement!, 'Escape', KEYS.escape);
    const form = fixture.componentInstance.form;
    form.controls.empty.setValue(1035);
    form.controls.from.disable();
    await fixture.whenStable();
    expect(button(fixture, 'empty').textContent?.trim()).toBe('17:15');
    expect(button(fixture, 'from').disabled).toBe(true);
    form.controls.empty.setValue(null);
    await fixture.whenStable();
    expect(button(fixture, 'empty').hasAttribute('data-empty')).toBe(true);
  });

  it('cambia de idioma en quente', async () => {
    const fixture = await render();
    await TestBed.inject(LanguageService).use('es');
    await fixture.whenStable();
    expect(button(fixture, 'empty').textContent?.trim()).toBe('Elegir hora');
    expect(button(fixture, 'from').getAttribute('aria-label')).toBe('Luns, tramo 1, desde, 8:00');
  });

  it('con provideCdkGlobalStyles o CDK non mete <style> (CSP style-src self)', async () => {
    const before = document.head.querySelectorAll('style').length;
    const fixture = await render([provideCdkGlobalStyles()]);
    await openWithClick(fixture, 'from');
    expect(listbox()).not.toBeNull();
    const added = [...document.head.querySelectorAll('style')].slice(before);
    expect(added.some((s) => s.textContent?.includes('cdk-overlay'))).toBe(false);
  });
});

describe('time-format', () => {
  it('formata minutos co idioma e 1440 como 24:00', () => {
    expect(formatMinutes(0, 'gl')).toBe('0:00');
    expect(formatMinutes(495, 'es')).toBe('8:15');
    expect(formatMinutes(1440, 'gl')).toBe('24:00');
  });

  it('opcións cada 15 min, con 24:00 só se se pide', () => {
    expect(timeOptions(0, 1440, false).length).toBe(96);
    expect(timeOptions(0, 1440, true).at(-1)).toBe(1440);
    expect(timeOptions(7, 60, false)).toEqual([15, 30, 45, 60]);
  });
});
