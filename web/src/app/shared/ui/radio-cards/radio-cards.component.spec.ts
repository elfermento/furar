// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { RadioCardOption } from './radio-cards.component';
import { RadioCardsComponent } from './radio-cards.component';

const ROLES: RadioCardOption<string>[] = [
  {
    value: 'MANAGER',
    label: 'Xestor',
    items: [
      { text: 'Reservas e cobros', included: true },
      { text: 'Indicadores', included: true },
    ],
  },
  {
    value: 'STAFF',
    label: 'Conserxe',
    items: [
      { text: 'Reservas e cobros', included: true },
      { text: 'Indicadores', included: false },
    ],
  },
];

@Component({
  imports: [ReactiveFormsModule, RadioCardsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span id="role-label">{{ 'Rol' }}</span>
    <furar-radio-cards id="form" labelledBy="role-label" [formControl]="role" [options]="roles" />
    <span id="free-label">{{ 'Outro' }}</span>
    <furar-radio-cards id="free" labelledBy="free-label" [options]="roles" (changed)="picked.set($event)" />
  `,
})
class HostComponent {
  readonly roles = ROLES;
  readonly role = new FormControl<string>('MANAGER', { nonNullable: true });
  readonly picked = signal<string | null>(null);
}

const text = (node: Element | null) => node?.textContent?.replace(/\s+/g, ' ').trim();

describe('furar-radio-cards', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const radios = (id: string) => [...el.querySelectorAll<HTMLInputElement>(`#${id} input[type="radio"]`)];
    return { fixture, el, radios };
  }

  it('un radiogroup co nome do rótulo; radios nativos co nome da tarxeta e a lista como descrición', async () => {
    const { el, radios } = await render();
    const group = el.querySelector('#form [role="radiogroup"]')!;
    expect(group.getAttribute('aria-labelledby')).toBe('role-label');
    const [manager, staff] = radios('form');
    // Mesmo name: unha parada de tabulador e frechas nativas.
    expect(manager.name).toBe(staff.name);
    expect(radios('free')[0].name).not.toBe(manager.name);
    expect(text(el.querySelector(`#${manager.getAttribute('aria-labelledby')}`))).toBe('Xestor');
    // O ✓ e o ✕ son decorativos: o si e o non van en texto.
    const items = [...el.querySelectorAll(`#${staff.getAttribute('aria-describedby')} li`)].map(text);
    expect(items).toEqual(['Si: Reservas e cobros', 'Non: Indicadores']);
    expect(el.querySelectorAll('#form .f-radio-card__mark[aria-hidden="true"]')).toHaveLength(4);
    expect(manager.checked).toBe(true);
    expect(manager.closest('.f-radio-card')?.hasAttribute('data-checked')).toBe(true);
    await expectNoAxeViolations(el);
  });

  it('picar na tarxeta escolle; segue o formulario (setValue e disable)', async () => {
    const { fixture, el, radios } = await render();
    const host = fixture.componentInstance;
    (radios('form')[1].closest('label') as HTMLLabelElement).click();
    await fixture.whenStable();
    expect(host.role.value).toBe('STAFF');
    expect(radios('form')[1].checked).toBe(true);
    expect(radios('form')[0].closest('.f-radio-card')?.hasAttribute('data-checked')).toBe(false);
    host.role.setValue('MANAGER');
    await fixture.whenStable();
    expect(radios('form')[0].checked).toBe(true);
    host.role.disable();
    await fixture.whenStable();
    expect(radios('form').every((r) => r.disabled)).toBe(true);
    expect(el.querySelector('#free input')?.hasAttribute('disabled')).toBe(false);
  });

  it('sen formulario, emite changed só se cambia', async () => {
    const { fixture, radios } = await render();
    radios('free')[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.picked()).toBe('STAFF');
    fixture.componentInstance.picked.set(null);
    radios('free')[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.picked()).toBeNull();
  });
});
