// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { CalendarPeriodComponent } from './calendar-period.component';

@Component({
  imports: [CalendarPeriodComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-calendar-period mode="DAY" date="2026-10-27" today="2026-10-27" label="martes, 27 de outubro" />
    <button type="button" data-after-bar>Fóra</button>
  `,
})
class HostComponent {}

describe('CalendarPeriodComponent', () => {
  it('a capa do calendario pecha ao saír da barra co tabulador, non ao moverse dentro dela', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const trigger = el.querySelector<HTMLButtonElement>('[data-period-label]')!;
    const pop = () => el.querySelector('[data-period-pop]');
    const leaveTo = async (next: Element | null) => {
      pop()!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: next }));
      await fixture.whenStable();
    };

    trigger.click();
    await fixture.whenStable();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    // Ás frechas da propia barra, ou sen destino (o día enfocado repíntase ao cambiar de mes): segue aberta.
    await leaveTo(el.querySelector('[data-period-next]'));
    await leaveTo(null);
    expect(pop()).not.toBeNull();
    // Fóra da barra: pecha (aberta taparía o calendario e Esc xa non chegaría a ela).
    await leaveTo(el.querySelector('[data-after-bar]'));
    expect(pop()).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });
});
