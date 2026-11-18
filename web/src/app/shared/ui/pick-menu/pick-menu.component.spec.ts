// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { DialogService } from '../dialog/dialog.service';
import type { PickMenuOption, PickSheetRequest } from './pick-menu.component';
import { PickMenuComponent } from './pick-menu.component';
import { openPickSheet } from './pick-sheet.component';

const OPTIONS: PickMenuOption[] = [
  { id: 'ares', label: 'Ares', detail: 'Recepción' },
  { id: 'naron', label: 'Narón', detail: 'Xestión' },
];

@Component({
  imports: [PickMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-pick-menu
    label="Concello"
    icon="location"
    [options]="options"
    [sheet]="sheet()"
    (picked)="picked.set($event)"
    (sheetRequested)="request.set($event)"
  />`,
})
class HostComponent {
  readonly options = OPTIONS;
  readonly sheet = signal(false);
  readonly picked = signal<string | null>(null);
  readonly request = signal<PickSheetRequest | null>(null);
}

const text = (el: Element | null | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim();

describe('furar-pick-menu', () => {
  afterEach(() => document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = '')));

  async function render(sheet = false) {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.sheet.set(sheet);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('escritorio: un menú co nome e o detalle de cada opción; escoller emite o id; pasa axe', async () => {
    const { fixture, el } = await render();
    const button = el.querySelector<HTMLButtonElement>('button')!;
    expect(text(button)).toBe('Concello');
    expect(button.getAttribute('aria-haspopup')).toBe('menu');
    await expectNoAxeViolations(el);
    button.click();
    await fixture.whenStable();
    const menu = document.querySelector<HTMLElement>('.f-pick-menu__menu')!;
    expect(menu.getAttribute('aria-label')).toBe('Concello');
    const items = [...menu.querySelectorAll<HTMLElement>('[role="menuitem"]')];
    expect(items.map((i) => text(i))).toEqual(['Ares Recepción', 'Narón Xestión']);
    await expectNoAxeViolations(menu);
    items[1]!.click();
    expect(fixture.componentInstance.picked()).toBe('naron');
  });

  it('móbil: sen menú; pide a folla co título, as opcións e o botón', async () => {
    const { fixture, el } = await render(true);
    const button = el.querySelector<HTMLButtonElement>('button')!;
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    button.click();
    await fixture.whenStable();
    expect(document.querySelector('.f-pick-menu__menu')).toBeNull();
    expect(fixture.componentInstance.request()).toEqual({ heading: 'Concello', options: OPTIONS, from: button });
  });

  it('a folla: unha fila por opción; escoller pecha e devolve o id; pasa axe', async () => {
    const { fixture, el } = await render(true);
    const chosen = openPickSheet(TestBed.inject(DialogService), {
      heading: 'Concello',
      options: OPTIONS,
      from: el.querySelector('button')!,
    });
    await fixture.whenStable();
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
    expect(text(dialog.querySelector('h2'))).toBe('Concello');
    const rows = [...dialog.querySelectorAll<HTMLButtonElement>('[data-option]')];
    expect(rows.map((r) => text(r))).toEqual(['Ares Recepción', 'Narón Xestión']);
    await expectNoAxeViolations(dialog);
    rows[0]!.click();
    await expect(chosen).resolves.toBe('ares');
  });
});
