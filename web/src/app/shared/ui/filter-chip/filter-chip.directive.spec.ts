// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { FilterChipDirective } from './filter-chip.directive';

@Component({
  imports: [FilterChipDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="group" aria-label="Pago">
      <button id="neutral" type="button" furarFilterChip [(pressed)]="court">{{ 'Pista 1' }}</button>
      <button id="pending" type="button" furarFilterChip="warn" [(pressed)]="pending">{{ 'Pendente' }}</button>
    </div>
  `,
})
class HostComponent {
  readonly court = signal(false);
  readonly pending = signal(true);
}

describe('[furarFilterChip]', () => {
  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      neutral: el.querySelector<HTMLButtonElement>('#neutral')!,
      pending: el.querySelector<HTMLButtonElement>('#pending')!,
    };
  }

  it('é un botón nativo con aria-pressed e ton opcional', async () => {
    const { neutral, pending } = await render();
    expect(neutral.type).toBe('button');
    expect(neutral.classList).toContain('f-filter-chip');
    expect(neutral.getAttribute('aria-pressed')).toBe('false');
    expect(neutral.hasAttribute('data-tone')).toBe(false);
    expect(pending.getAttribute('aria-pressed')).toBe('true');
    expect(pending.dataset['tone']).toBe('warn');
  });

  it('o clic (tamén Intro e Espazo, que nun botón nativo son clic) marca e desmarca', async () => {
    const { fixture, neutral, pending } = await render();
    neutral.click();
    pending.click();
    await fixture.whenStable();
    expect(neutral.getAttribute('aria-pressed')).toBe('true');
    expect(pending.getAttribute('aria-pressed')).toBe('false');
    expect(fixture.componentInstance.court()).toBe(true);
    expect(fixture.componentInstance.pending()).toBe(false);
  });

  it('pasa axe nos dous estados', async () => {
    const { fixture, neutral } = await render();
    await expectNoAxeViolations(fixture.nativeElement);
    neutral.click();
    await fixture.whenStable();
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
