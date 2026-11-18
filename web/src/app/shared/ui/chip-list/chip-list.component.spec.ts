// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ChipItem } from './chip-list.component';
import { ChipListComponent } from './chip-list.component';

@Component({
  imports: [ChipListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label for="field">Deportes</label>
    <input #field id="field" type="text" />
    <furar-chip-list
      label="Deportes escollidos"
      [items]="items()"
      [status]="status()"
      [fallbackFocus]="field"
      (removed)="remove($event)"
    />
  `,
})
class HostComponent {
  readonly items = signal<ChipItem[]>([
    { key: 'basketball', label: 'Baloncesto' },
    { key: 'handball', label: 'Balonmán' },
    { key: 'c1', label: 'Bolo celta', outline: true },
  ]);
  readonly status = signal('');

  remove(index: number): void {
    const gone = this.items()[index];
    this.items.update((list) => list.filter((_, i) => i !== index));
    this.status.set(`Quitouse ${gone?.label}.`);
  }
}

describe('furar-chip-list', () => {
  let fixture: ComponentFixture<HostComponent>;

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: provideTestI18n(lang) });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const buttons = () => [...el().querySelectorAll<HTMLButtonElement>('[data-chip-remove]')];
  const labels = () => [...el().querySelectorAll('.f-chip')].map((chip) => chip.textContent?.trim());
  const status = () => el().querySelector('[role="status"]')?.textContent?.trim();
  const field = () => el().querySelector<HTMLInputElement>('#field')!;

  function press(target: Element, name: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    return event;
  }

  it('lista nomeada con cada chip e o seu «×» «Quitar …»; o marcado con bordo; sen fallos de axe', async () => {
    await render();
    const list = el().querySelector('ul.f-chip-list')!;
    expect(list.getAttribute('aria-label')).toBe('Deportes escollidos');
    expect(labels()).toEqual(['Baloncesto', 'Balonmán', 'Bolo celta']);
    expect(buttons().map((b) => b.getAttribute('aria-label'))).toEqual([
      'Quitar Baloncesto',
      'Quitar Balonmán',
      'Quitar Bolo celta',
    ]);
    expect(buttons().every((b) => b.type === 'button')).toBe(true);
    expect(el().querySelectorAll('.f-chip[data-outline]')).toHaveLength(1);
    expect(el().querySelector('[role="status"]')).not.toBeNull();
    await expectNoAxeViolations(el());
  });

  it('en castelán, «Quitar …» tamén', async () => {
    await render('es');
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(buttons()[0]?.getAttribute('aria-label')).toBe('Quitar Baloncesto');
  });

  it('ao quitar, o foco vai ao «×» seguinte (ao anterior no último) e, sen chips, ao campo; anúnciase', async () => {
    await render();
    buttons()[1]!.click();
    await settle();
    expect(labels()).toEqual(['Baloncesto', 'Bolo celta']);
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Quitar Bolo celta');
    expect(status()).toBe('Quitouse Balonmán.');

    buttons()[1]!.click();
    await settle();
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Quitar Baloncesto');

    buttons()[0]!.click();
    await settle();
    expect(el().querySelector('ul')).toBeNull();
    expect(document.activeElement).toBe(field());
    // A rexión educada segue aí para anunciar a última baixa.
    expect(status()).toBe('Quitouse Baloncesto.');
    await expectNoAxeViolations(el());
  });

  it('Supr e Retroceso nun «×» quítano; ← e → móvense entre os «×» e → no último volve ao campo', async () => {
    await render();
    buttons()[0]!.focus();
    expect(press(buttons()[0]!, 'ArrowRight').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(buttons()[1]);
    press(buttons()[1]!, 'ArrowLeft');
    expect(document.activeElement).toBe(buttons()[0]);
    press(buttons()[0]!, 'ArrowLeft');
    expect(document.activeElement).toBe(buttons()[0]);
    buttons()[2]!.focus();
    press(buttons()[2]!, 'ArrowRight');
    expect(document.activeElement).toBe(field());

    buttons()[0]!.focus();
    expect(press(buttons()[0]!, 'Delete').defaultPrevented).toBe(true);
    await settle();
    expect(labels()).toEqual(['Balonmán', 'Bolo celta']);
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Quitar Balonmán');

    press(buttons()[0]!, 'Backspace');
    await settle();
    expect(labels()).toEqual(['Bolo celta']);
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Quitar Bolo celta');

    // Outras teclas (e con modificadores) non fan nada.
    expect(press(buttons()[0]!, 'a').defaultPrevented).toBe(false);
    const shifted = new KeyboardEvent('keydown', { key: 'Delete', shiftKey: true, bubbles: true, cancelable: true });
    buttons()[0]!.dispatchEvent(shifted);
    await settle();
    expect(labels()).toEqual(['Bolo celta']);
  });
});
