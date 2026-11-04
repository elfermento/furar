// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import { CalendarComponent } from './calendar.component';
import type { IsoDate } from './local-date';

@Component({
  imports: [CalendarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-calendar
      [(value)]="value"
      [min]="min()"
      [max]="max()"
      [today]="'2026-10-25'"
      [dateDisabled]="disabled"
      (picked)="picked.push($event)"
    />
  `,
})
class HostComponent {
  readonly value = signal<IsoDate | null>('2026-12-24');
  readonly min = signal<IsoDate | null>(null);
  readonly max = signal<IsoDate | null>(null);
  readonly picked: IsoDate[] = [];
  /** O Nadal pechado, para probar os días desactivados. */
  readonly disabled = (date: IsoDate) => date === '2026-12-25';
}

describe('furar-calendar', () => {
  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return fixture;
  }

  const el = (fixture: ComponentFixture<HostComponent>) => fixture.nativeElement as HTMLElement;
  const day = (fixture: ComponentFixture<HostComponent>, date: IsoDate) =>
    el(fixture).querySelector<HTMLElement>(`td[data-date="${date}"]`);
  const title = (fixture: ComponentFixture<HostComponent>) =>
    el(fixture).querySelector('.f-calendar__title')!.textContent!.trim();
  const tabStops = (fixture: ComponentFixture<HostComponent>) =>
    [...el(fixture).querySelectorAll<HTMLElement>('td[tabindex="0"]')].map((td) => td.dataset['date']);

  async function press(fixture: ComponentFixture<HostComponent>, key: string, shiftKey = false) {
    const target = document.activeElement as HTMLElement;
    const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  async function focusDay(fixture: ComponentFixture<HostComponent>, date: IsoDate) {
    day(fixture, date)!.focus();
    await fixture.whenStable();
  }

  it('anuncia o mes, nomea cada día coa data enteira e marca hoxe e o escollido', async () => {
    const fixture = await render();
    const grid = el(fixture).querySelector('table[role="grid"]')!;
    const titleEl = el(fixture).querySelector('.f-calendar__title')!;
    expect(title(fixture)).toBe('Decembro de 2026');
    expect(titleEl.getAttribute('aria-live')).toBe('polite');
    expect(grid.getAttribute('aria-labelledby')).toBe(titleEl.id);
    expect(grid.querySelectorAll('tbody tr')).toHaveLength(6);
    // Unha data simple: sen aria-multiselectable.
    expect(grid.hasAttribute('aria-multiselectable')).toBe(false);
    expect(day(fixture, '2026-12-24')!.getAttribute('aria-label')).toBe('xoves, 24 de decembro de 2026');
    expect(day(fixture, '2026-12-24')!.getAttribute('aria-selected')).toBe('true');
    expect(day(fixture, '2026-12-23')!.getAttribute('aria-selected')).toBe('false');
    expect(day(fixture, '2026-12-25')!.getAttribute('aria-disabled')).toBe('true');
    expect(tabStops(fixture)).toEqual(['2026-12-24']);
    const visible = [...grid.querySelectorAll('th span[aria-hidden="true"]')].map((s) => s.textContent!.trim());
    const spoken = [...grid.querySelectorAll('th .sr-only')].map((s) => s.textContent!.trim());
    expect(visible).toEqual(['L', 'M', 'M', 'X', 'V', 'S', 'D']);
    expect(spoken[0]).toBe('luns');
    expect(spoken[6]).toBe('domingo');
    await expectNoAxeViolations(el(fixture));
  });

  it('hoxe leva aria-current="date"', async () => {
    const fixture = await render();
    fixture.componentInstance.value.set(null);
    await fixture.whenStable();
    expect(title(fixture)).toBe('Outubro de 2026');
    expect(day(fixture, '2026-10-25')!.getAttribute('aria-current')).toBe('date');
    expect(tabStops(fixture)).toEqual(['2026-10-25']);
  });

  it('frechas, Inicio e Fin moven o día co foco dentro da semana e entre meses', async () => {
    const fixture = await render();
    await focusDay(fixture, '2026-12-24');
    await press(fixture, 'ArrowRight');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-25');
    await press(fixture, 'ArrowDown');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2027-01-01');
    expect(title(fixture)).toBe('Xaneiro de 2027');
    await press(fixture, 'ArrowUp');
    await press(fixture, 'ArrowLeft');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-24');
    await press(fixture, 'Home');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-21');
    await press(fixture, 'End');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-27');
    expect(tabStops(fixture)).toEqual(['2026-12-27']);
  });

  it('Re Páx e Av Páx cambian de mes; con Maiús, de ano', async () => {
    const fixture = await render();
    await focusDay(fixture, '2026-12-31');
    await press(fixture, 'PageDown');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2027-01-31');
    await press(fixture, 'PageDown');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2027-02-28');
    await press(fixture, 'PageUp', true);
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-02-28');
    expect(title(fixture)).toBe('Febreiro de 2026');
    await press(fixture, 'PageDown', true);
    expect(title(fixture)).toBe('Febreiro de 2027');
  });

  it('Intro e Espazo escollen; os días desactivados enfócanse pero non se escollen', async () => {
    const fixture = await render();
    const host = fixture.componentInstance;
    await focusDay(fixture, '2026-12-24');
    await press(fixture, 'ArrowLeft');
    const enter = await press(fixture, 'Enter');
    expect(enter.defaultPrevented).toBe(true);
    expect(host.value()).toBe('2026-12-23');
    await press(fixture, 'ArrowRight');
    await press(fixture, 'ArrowRight');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-25');
    await press(fixture, ' ');
    expect(host.value()).toBe('2026-12-23');
    day(fixture, '2026-12-25')!.click();
    day(fixture, '2026-12-28')!.click();
    await fixture.whenStable();
    expect(host.value()).toBe('2026-12-28');
    expect(host.picked).toEqual(['2026-12-23', '2026-12-28']);
  });

  it('min e max acoutan o teclado e as frechas do mes', async () => {
    const fixture = await render();
    fixture.componentInstance.min.set('2026-12-10');
    fixture.componentInstance.max.set('2027-01-05');
    await fixture.whenStable();
    expect(day(fixture, '2026-12-09')!.getAttribute('aria-disabled')).toBe('true');
    const [previous, next] = el(fixture).querySelectorAll<HTMLButtonElement>('.f-calendar__nav');
    expect(previous.getAttribute('aria-label')).toBe('Mes anterior');
    expect(previous.getAttribute('aria-disabled')).toBe('true');
    previous.click();
    await fixture.whenStable();
    expect(title(fixture)).toBe('Decembro de 2026');
    next.click();
    await fixture.whenStable();
    expect(title(fixture)).toBe('Xaneiro de 2027');
    expect(tabStops(fixture)).toEqual(['2027-01-05']);
    expect(next.getAttribute('aria-disabled')).toBe('true');
    await focusDay(fixture, '2027-01-05');
    await press(fixture, 'PageDown');
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2027-01-05');
    await press(fixture, 'PageUp', true);
    expect((document.activeElement as HTMLElement).dataset['date']).toBe('2026-12-10');
  });

  it('cambia o idioma en quente: mes, días e nomes por Intl', async () => {
    const fixture = await render();
    await TestBed.inject(LanguageService).use('es');
    await fixture.whenStable();
    expect(title(fixture)).toBe('Diciembre de 2026');
    expect(day(fixture, '2026-12-24')!.getAttribute('aria-label')).toBe('jueves, 24 de diciembre de 2026');
    const headers = [...el(fixture).querySelectorAll('th span[aria-hidden]')].map((s) => s.textContent!.trim());
    expect(headers).toEqual(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
    expect(el(fixture).querySelector('.f-calendar__nav')!.getAttribute('aria-label')).toBe('Mes anterior');
    expect(el(fixture).querySelectorAll('.f-calendar__nav')[1].getAttribute('aria-label')).toBe('Mes siguiente');
  });
});

@Component({
  imports: [CalendarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-calendar
    range
    [value]="from()"
    [rangeEnd]="to()"
    [today]="'2026-10-25'"
    (picked)="picked.push($event)"
  />`,
})
class RangeHostComponent {
  readonly from = signal<IsoDate | null>('2026-10-27');
  readonly to = signal<IsoDate | null>('2026-10-30');
  readonly picked: IsoDate[] = [];
}

describe('furar-calendar en modo rango', () => {
  it('marca os extremos e os días do medio; o clic só avisa (quen o usa decide o rango)', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl')] });
    const fixture = TestBed.createComponent(RangeHostComponent);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const cell = (date: IsoDate) => host.querySelector<HTMLElement>(`td[data-date="${date}"]`)!;
    expect(
      ['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-30', '2026-10-31'].map((d) =>
        cell(d).getAttribute('aria-selected'),
      ),
    ).toEqual(['false', 'true', 'true', 'true', 'false']);
    expect(cell('2026-10-28').hasAttribute('data-in-range')).toBe(true);
    expect(cell('2026-10-27').hasAttribute('data-in-range')).toBe(false);
    // Grella de varios escollidos; os extremos din «inicio» e «fin» no nome.
    expect(host.querySelector('table[role="grid"]')!.getAttribute('aria-multiselectable')).toBe('true');
    expect(cell('2026-10-27').getAttribute('aria-label')).toBe('martes, 27 de outubro de 2026, inicio');
    expect(cell('2026-10-30').getAttribute('aria-label')).toBe('venres, 30 de outubro de 2026, fin');
    expect(cell('2026-10-28').getAttribute('aria-label')).toBe('mércores, 28 de outubro de 2026');
    fixture.componentInstance.to.set('2026-10-27');
    await fixture.whenStable();
    expect(cell('2026-10-27').getAttribute('aria-label')).toBe('martes, 27 de outubro de 2026, inicio, fin');
    fixture.componentInstance.to.set('2026-10-30');
    await fixture.whenStable();
    cell('2026-10-31').click();
    await fixture.whenStable();
    expect(fixture.componentInstance.picked).toEqual(['2026-10-31']);
    expect(cell('2026-10-31').getAttribute('aria-selected')).toBe('false');
    await expectNoAxeViolations(host);
  });
});
