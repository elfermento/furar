// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { withPopoverApi } from '../../../../testing/popover';
import { IconComponent } from '../icon/icon.component';
import { MultiSelectComponent } from './multi-select.component';
import type { SelectOption } from './select.component';

const SPORTS: SelectOption<string>[] = [
  { value: 'basketball', label: 'Baloncesto' },
  { value: 'padel', label: 'Pádel' },
  { value: ':covered', label: 'Cuberta' },
];

@Component({
  imports: [MultiSelectComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (keydown.escape)="formEscapes = formEscapes + 1">
      <furar-multi-select [label]="'Deporte'" [options]="sports" [value]="value()" (changed)="value.set($event)">
        <furar-icon furarMultiSelectIcon name="filter" />
      </furar-multi-select>
      <button id="outside" type="button">{{ 'Fóra' }}</button>
    </form>
  `,
})
class HostComponent {
  formEscapes = 0;
  readonly sports = SPORTS;
  readonly value = signal<readonly string[]>([]);
}

function key(target: Element, name: string, keyCode: number): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
  return event;
}

describe('furar-multi-select', () => {
  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const button = (fixture: ComponentFixture<HostComponent>) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('furar-multi-select button')!;
  const listbox = () => document.querySelector<HTMLElement>('[role="listbox"]');
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];

  it('pechado só se ve o botón co rótulo; a lista aparece ao pulsalo, co foco nela', async () => {
    const fixture = await render();
    expect(button(fixture).textContent?.trim()).toBe('Deporte');
    expect(button(fixture).getAttribute('aria-label')).toBe('Deporte');
    expect(button(fixture).getAttribute('aria-haspopup')).toBe('listbox');
    expect(button(fixture).getAttribute('aria-expanded')).toBe('false');
    expect(listbox()).toBeNull();

    button(fixture).click();
    await fixture.whenStable();

    expect(button(fixture).getAttribute('aria-expanded')).toBe('true');
    expect(listbox()?.getAttribute('aria-label')).toBe('Deporte');
    expect(listbox()?.getAttribute('aria-multiselectable')).toBe('true');
    expect(options().map((o) => o.textContent?.trim())).toEqual(['Baloncesto', 'Pádel', 'Cuberta']);
    expect(listbox()?.contains(document.activeElement)).toBe(true);
    await expectNoAxeViolations(document.body);
  });

  it('marca varias sen pecharse; o botón di a opción, ou cantas, e o nome accesible, cales', async () => {
    const fixture = await render();
    button(fixture).click();
    await fixture.whenStable();

    options()[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toEqual(['padel']);
    expect(listbox()).not.toBeNull();
    expect(options()[1].getAttribute('aria-selected')).toBe('true');
    expect(button(fixture).textContent?.trim()).toBe('Pádel');
    expect(button(fixture).getAttribute('aria-label')).toBe('Deporte, Pádel');

    options()[2].click();
    await fixture.whenStable();
    expect([...fixture.componentInstance.value()].sort()).toEqual([':covered', 'padel']);
    expect(button(fixture).textContent?.trim()).toBe('2 filtros');
    expect(button(fixture).getAttribute('aria-label')).toBe('Deporte, Pádel, Cuberta');

    options()[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toEqual([':covered']);
    expect(button(fixture).textContent?.trim()).toBe('Cuberta');
  });

  it('péchase picando fóra e con Esc, que devolve o foco ao botón sen pechar o formulario', async () => {
    const restore = withPopoverApi();
    try {
      const fixture = await render();
      button(fixture).click();
      await fixture.whenStable();
      (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#outside')!.click();
      document.body.click();
      await fixture.whenStable();
      expect(listbox()).toBeNull();

      button(fixture).click();
      await fixture.whenStable();
      const escape = key(document.activeElement!, 'Escape', 27);
      await fixture.whenStable();
      expect(escape.defaultPrevented).toBe(true);
      expect(listbox()).toBeNull();
      expect(document.activeElement).toBe(button(fixture));
      expect(fixture.componentInstance.formEscapes).toBe(0);
    } finally {
      restore();
    }
  });
});
