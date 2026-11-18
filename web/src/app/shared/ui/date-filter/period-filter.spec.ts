// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import type { IsoDate } from '../calendar/local-date';
import type { PeriodValue } from './period-filter';
import { periodOf, periodRange, presetRange } from './period-filter';
import { PeriodFilterComponent } from './period-filter.component';

describe('período dun panel de cifras', () => {
  const today = '2026-11-11' as IsoDate;

  it('os atallos son a semana ISO, o mes, o trimestre e o ano naturais (os da API)', () => {
    expect(presetRange('semana', today)).toEqual({ from: '2026-11-09', to: '2026-11-15' });
    expect(presetRange('mes', today)).toEqual({ from: '2026-11-01', to: '2026-11-30' });
    expect(presetRange('trimestre', today)).toEqual({ from: '2026-10-01', to: '2026-12-31' });
    expect(presetRange('ano', today)).toEqual({ from: '2026-01-01', to: '2026-12-31' });
    expect(presetRange('mes', '2028-02-10' as IsoDate)).toEqual({ from: '2028-02-01', to: '2028-02-29' });
  });

  it('un rango igual a un atallo é ese período; outro, «datas»', () => {
    expect(periodOf('2026-10-01' as IsoDate, '2026-12-31' as IsoDate, today)).toEqual({
      period: 'trimestre',
      from: null,
      to: null,
    });
    expect(periodOf('2026-10-05' as IsoDate, '2026-10-18' as IsoDate, today)).toEqual({
      period: 'datas',
      from: '2026-10-05',
      to: '2026-10-18',
    });
    expect(periodRange({ period: 'datas', from: '2026-10-05' as IsoDate, to: '2026-10-18' as IsoDate }, today)).toEqual(
      { from: '2026-10-05', to: '2026-10-18' },
    );
  });
});

@Component({
  imports: [PeriodFilterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-period-filter
      [value]="value()"
      today="2026-11-11"
      month="Novembro"
      [maxDays]="40"
      label="Período"
      (changed)="changes.push($event); value.set($event)"
    />
  `,
})
class HostComponent {
  readonly value = signal<PeriodValue>({ period: 'mes', from: null, to: null });
  readonly changes: PeriodValue[] = [];
}

describe('furar-period-filter', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const settle = () => fixture.whenStable();
    const chip = () => el.querySelector('.f-mdate__chip-open')?.textContent?.trim();
    const open = async () => {
      el.querySelector<HTMLButtonElement>('.f-mdate__chip-open')!.click();
      await settle();
    };
    return { fixture, el, settle, chip, open, host: fixture.componentInstance };
  }

  it('o chip leva o mes polo seu nome; un atallo e «Aplicar» cambian o período', async () => {
    const { el, settle, chip, open, host } = await render();
    expect(chip()).toBe('Novembro');
    await open();
    el.querySelector<HTMLButtonElement>('[data-preset="semana"]')!.click();
    await settle();
    expect(host.changes).toEqual([]);
    el.querySelector<HTMLButtonElement>('[data-date-apply]')!.click();
    await settle();
    expect(host.changes).toEqual([{ period: 'semana', from: null, to: null }]);
    expect(chip()).toBe('Esta semana');
  });

  it('un rango longo de máis avisa e non se aplica: «Aplicar» leva ao calendario; Esc volve ao de antes', async () => {
    const { el, settle, chip, open, host } = await render();
    await open();
    el.querySelector<HTMLButtonElement>('[data-preset="trimestre"]')!.click();
    await settle();
    expect(el.querySelector('[data-date-too-long]')?.textContent).toContain('40');
    const apply = el.querySelector<HTMLButtonElement>('[data-date-apply]')!;
    expect(apply.disabled).toBe(false);
    apply.click();
    await settle();
    expect(el.querySelector('[data-date-panel]')).not.toBeNull();
    expect(el.querySelector('furar-calendar')?.contains(document.activeElement)).toBe(true);
    expect(host.changes).toEqual([]);
    el.querySelector('[data-date-panel]')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    await settle();
    expect(host.changes).toEqual([]);
    expect(chip()).toBe('Novembro');
  });

  it('quitar o filtro volve ao mes en curso', async () => {
    const { el, settle, host } = await render();
    host.value.set({ period: 'ano', from: null, to: null });
    await settle();
    el.querySelector<HTMLButtonElement>('[data-date-clear]')!.click();
    await settle();
    expect(host.changes).toEqual([{ period: 'mes', from: null, to: null }]);
  });
});
