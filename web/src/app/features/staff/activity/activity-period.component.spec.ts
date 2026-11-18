// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ActivityPeriod } from './activity-period.component';
import { ActivityPeriodComponent } from './activity-period.component';

@Component({
  imports: [ActivityPeriodComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-activity-period [today]="'2026-10-25'" [from]="from" (changed)="changes.push($event)" />`,
})
class HostComponent {
  from: string | null = null;
  readonly changes: ActivityPeriod[] = [];
}

describe('furar-activity-period', () => {
  async function render(from: string | null = null) {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.from = from;
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const apply = el.querySelector<HTMLButtonElement>('[data-period-apply]')!;
    const button = (field: string) => el.querySelector<HTMLElement>(`[data-period-${field}] .f-date-field__button`)!;
    return { fixture, el, apply, button, host: fixture.componentInstance };
  }

  it('«Aplicar» sen días segue activo: o erro debaixo de cada campo e o foco no primeiro', async () => {
    const { fixture, el, apply, button, host } = await render();
    expect(apply.disabled).toBe(false);
    expect(el.querySelector('.f-field-error')).toBeNull();
    apply.click();
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.activeElement).toBe(button('from')));
    expect(el.querySelector('[data-period-from-error]')?.textContent?.trim()).toBe('Escolle o primeiro día.');
    expect(el.querySelector('[data-period-to-error]')?.textContent?.trim()).toBe('Escolle o último día.');
    expect(button('from').getAttribute('aria-invalid')).toBe('true');
    expect(button('from').getAttribute('aria-describedby')).toBe(el.querySelector('[data-period-from-error]')!.id);
    expect(host.changes).toEqual([]);
    await expectNoAxeViolations(el);
  });

  it('cun só día falta o outro: o erro e o foco van a «Ata»', async () => {
    const { fixture, el, apply, button, host } = await render('2026-10-20');
    apply.click();
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.activeElement).toBe(button('to')));
    expect(el.querySelector('[data-period-from-error]')).toBeNull();
    expect(el.querySelector('[data-period-to-error]')).not.toBeNull();
    expect(host.changes).toEqual([]);
  });
});
