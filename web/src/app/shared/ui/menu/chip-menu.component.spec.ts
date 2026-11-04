// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ChipMenuOption } from './chip-menu.component';
import { ChipMenuComponent } from './chip-menu.component';
import { MENU } from './menu.directives';

type Status = 'ACTIVE' | 'CLOSED' | 'WITHDRAWN';

@Component({
  imports: [ChipMenuComponent, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-chip-menu
      [label]="'Estado'"
      [options]="options"
      [value]="value()"
      [disabled]="disabled()"
      (selected)="requested.push($event)"
    />
    <button type="button" id="actions" [furarMenuTrigger]="actions">{{ 'Accións' }}</button>
    <ng-template #actions>
      <div furarMenu [attr.aria-label]="'Accións'">
        <button type="button" furarMenuItem (triggered)="requested.push('EDIT')">{{ 'Editar' }}</button>
        <button type="button" furarMenuItem disabled>{{ 'Duplicar' }}</button>
      </div>
    </ng-template>
  `,
})
class HostComponent {
  readonly options: ChipMenuOption<Status>[] = [
    { value: 'ACTIVE', label: 'Activa', tone: 'success' },
    { value: 'CLOSED', label: 'Pechada temporalmente', tone: 'warn' },
    { value: 'WITHDRAWN', label: 'De baixa', tone: 'muted' },
  ];
  readonly value = signal<Status>('ACTIVE');
  readonly disabled = signal(false);
  readonly requested: string[] = [];
}

/** Tecla con `key` e `keyCode` (o CDK aínda le keyCode). */
function key(target: Element, name: string, keyCode: number): void {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
}

const KEYS = { enter: 13, escape: 27, down: 40, up: 38, end: 35 } as const;

describe('furar-chip-menu e [furarMenu]', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  });

  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  const chip = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('furar-chip-menu button')!;
  const items = () => [...document.querySelectorAll<HTMLElement>('[role="menu"] [role^="menuitem"]')];

  async function openWithKeyboard(trigger: HTMLElement) {
    trigger.focus();
    key(trigger, 'ArrowDown', KEYS.down);
    await fixture.whenStable();
  }

  it('o chip é un furar-tag dentro dun botón co nome completo e o estado en texto', async () => {
    const button = chip();
    expect(button.getAttribute('aria-label')).toBe('Estado: Activa. Cambiar');
    expect(button.getAttribute('aria-haspopup')).toBe('menu');
    expect(button.getAttribute('aria-expanded')).toBe('false');
    const tag = button.querySelector('furar-tag')!;
    expect(tag.textContent?.trim()).toBe('Activa');
    expect(tag.getAttribute('data-tone')).toBe('success');

    fixture.componentInstance.value.set('WITHDRAWN');
    await fixture.whenStable();
    expect(button.getAttribute('aria-label')).toBe('Estado: De baixa. Cambiar');
    expect(tag.getAttribute('data-tone')).toBe('muted');
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('frechas abren co foco dentro; o menú ten opcións únicas coa marcada en aria-checked', async () => {
    await openWithKeyboard(chip());
    const menu = document.querySelector<HTMLElement>('[role="menu"]')!;
    expect(chip().getAttribute('aria-expanded')).toBe('true');
    expect(menu.getAttribute('aria-label')).toBe('Estado');
    expect(items().map((item) => item.getAttribute('role'))).toEqual([
      'menuitemradio',
      'menuitemradio',
      'menuitemradio',
    ]);
    expect(items().map((item) => item.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false']);
    expect(items().map((item) => item.textContent?.trim())).toEqual(['Activa', 'Pechada temporalmente', 'De baixa']);
    expect(items()[2]!.querySelector('.f-tag-swatch')?.getAttribute('aria-hidden')).toBe('true');
    expect(document.activeElement).toBe(items()[0]);
    await expectNoAxeViolations(menu);

    key(document.activeElement!, 'ArrowDown', KEYS.down);
    expect(document.activeElement).toBe(items()[1]);
    key(document.activeElement!, 'End', KEYS.end);
    expect(document.activeElement).toBe(items()[2]);
  });

  it('Intro escolle: emite o pedido, pecha e devolve o foco ao chip', async () => {
    await openWithKeyboard(chip());
    key(document.activeElement!, 'ArrowDown', KEYS.down);
    const closed = items()[1]!;
    key(closed, 'Enter', KEYS.enter);
    await fixture.whenStable();
    expect(fixture.componentInstance.requested).toEqual(['CLOSED']);
    expect(document.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(chip());
  });

  it('escoller a opción xa marcada non emite; Esc pecha sen cambiar e devolve o foco', async () => {
    await openWithKeyboard(chip());
    items()[0]!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.requested).toEqual([]);

    await openWithKeyboard(chip());
    key(document.activeElement!, 'Escape', KEYS.escape);
    await fixture.whenStable();
    expect(document.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(chip());
    expect(fixture.componentInstance.requested).toEqual([]);
  });

  it('desactivado non abre', async () => {
    fixture.componentInstance.disabled.set(true);
    await fixture.whenStable();
    expect(chip().disabled).toBe(true);
  });

  it('[furarMenuItem]: accións con role menuitem, desactivadas con aria-disabled', async () => {
    const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#actions')!;
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu');
    await openWithKeyboard(trigger);
    expect(items().map((item) => item.getAttribute('role'))).toEqual(['menuitem', 'menuitem']);
    expect(items()[1]!.getAttribute('aria-disabled')).toBe('true');
    items()[0]!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.requested).toEqual(['EDIT']);
  });
});
