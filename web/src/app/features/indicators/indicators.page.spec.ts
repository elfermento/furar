// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import type { HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { CHART_ENGINE } from '../../shared/ui/chart/chart-engine';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import {
  emptyPanel,
  filterOptions,
  inProgressPanel,
  naronPanel,
  withoutIndicatorReviews,
} from '../../../testing/indicators';
import { BASE, useMonday } from '../../../testing/manage-bookings';
import { selectText } from '../../../testing/select';
import { IndicatorsPage } from './indicators.page';

/** Texto visible nunha liña, cos espazos que non parten coma espazos. */
const text = (e: Element | null | undefined) =>
  (e?.textContent ?? '').replaceAll('\u00a0', ' ').replace(/\s+/g, ' ').trim();

const PANEL_URL = `${BASE}/indicators`;
const FILTERS_URL = `${BASE}/indicators/filters`;

const panelFor =
  (period: string, extra: Record<string, string | string[] | null> = {}) =>
  (r: HttpRequest<unknown>) =>
    r.url === PANEL_URL &&
    r.params.get('period') === period &&
    Object.entries(extra).every(([key, value]) =>
      Array.isArray(value) ? r.params.getAll(key)?.join() === value.join() : r.params.get(key) === value,
    );

async function render(url = '/naron/xestion/indicadores', panel = naronPanel()) {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(fakeSession()),
      provideRouter([{ path: 'naron/xestion/indicadores', component: IndicatorsPage }]),
      provideLocationMocks(),
      // O motor dos gráficos non chega nunca: aquí só importan o texto, as táboas e a URL.
      { provide: CHART_ENGINE, useValue: () => new Promise(() => undefined) },
    ],
  });
  withoutIndicatorReviews();
  TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === FILTERS_URL)).flush(filterOptions());
  const first = await expectRequest((r) => r.url === PANEL_URL);
  first.flush(panel);
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('furar-stat-tile')).not.toBeNull();
  });
  return { el, settle, harness, first };
}

/** O texto do chip do período (`furar-period-filter`), nunha lista coma antes o segmentado. */
const pressed = (el: Element) =>
  [...el.querySelectorAll('[data-period-filter] [data-date-chip] .f-mdate__chip-open')].map((b) => text(b));
/** Abre o chip do período, escolle un atallo e aplica. */
async function choosePeriod(el: Element, preset: string, settle: () => Promise<unknown>): Promise<void> {
  el.querySelector<HTMLButtonElement>('[data-period-filter] .f-mdate__chip-open')!.click();
  await settle();
  el.querySelector<HTMLButtonElement>(`[data-period-filter] [data-preset="${preset}"]`)!.click();
  await settle();
  el.querySelector<HTMLButtonElement>('[data-period-filter] [data-date-apply]')!.click();
  await settle();
}
const conclusion = (el: Element, id: string) =>
  text(el.querySelector(`[data-chart="${id}"] .f-chart-card__conclusion`));

describe('Indicadores', () => {
  useMonday();

  it('o mes en curso: cinco cifras, seis gráficos coas súas frases, cabeceira impresa; axe', async () => {
    const { el, first } = await render();
    expect(first.request.params.get('period')).toBe('MONTH');
    expect(text(el.querySelector('h1'))).toBe('Indicadores');
    expect(pressed(el)).toEqual(['Outubro']);
    expect(selectText(el.querySelector('[data-facility]')!)).toBe('Todas as instalacións');
    expect(selectText(el.querySelector('[data-use]')!)).toBe('Todos os usos');
    expect([...el.querySelectorAll('furar-stat-tile')].map((t) => t.getAttribute('data-kpi'))).toEqual([
      'bookings',
      'occupancy',
      'reservedHours',
      'people',
      'paidCents',
      'rating',
    ]);
    expect(text(el.querySelector('[data-kpi="occupancy"] .f-stat-tile__change'))).toBe('↑ +5 p.p. respecto a setembro');
    expect(el.querySelectorAll('furar-chart-card')).toHaveLength(6);
    expect(conclusion(el, 'occupancy')).toBe('Pista de pádel 2 de O Alto é a máis chea: 71 % das súas horas abertas.');
    expect(conclusion(el, 'people')).toBe('187 en outubro, o máximo dos últimos 6 meses.');
    expect(el.querySelector('[data-chart="peak"] furar-heat-legend')).not.toBeNull();
    expect(text(el.querySelector('.f-print-header'))).toBe(
      'Concello de Narón · Outubro de 2026 · Todas as instalacións · Todos os usos · xerado o 26/10/2026, 11:00',
    );
    // A primeira carga non se anuncia; só o resultado dun filtro.
    expect(text(el.querySelector('[data-indicators-status]'))).toBe('');
    await expectNoAxeViolations(el);
  });

  it('o período en curso: a variación compara ata hoxe, dío nunha liña e a táboa marca o tramo en curso; axe', async () => {
    const { el, settle } = await render('/naron/xestion/indicadores', inProgressPanel());
    expect(text(el.querySelector('[data-kpi="bookings"] .f-stat-tile__value'))).toBe('412');
    expect(text(el.querySelector('[data-kpi="bookings"] .f-stat-tile__change'))).toBe(
      '↑ +10 % ata hoxe, fronte ao mesmo tramo de setembro',
    );
    expect(text(el.querySelector('[data-kpi="occupancy"] .f-stat-tile__change'))).toBe(
      '↑ +2 p.p. ata hoxe, fronte ao mesmo tramo de setembro',
    );
    el.querySelector<HTMLButtonElement>('[data-chart="daily"] .f-chart-card__toggle')!.click();
    await settle();
    const rows = [...el.querySelectorAll('[data-chart="daily"] tbody th[scope="row"]')].map((r) => text(r));
    expect(rows.at(-1)).toBe('26/10/2026 – 31/10/2026 (en curso)');
    expect(rows.filter((r) => r.includes('en curso'))).toHaveLength(1);
    await expectNoAxeViolations(el);
  });

  it('URL ↔ filtros: o URL pide o panel, cada filtro vai ao URL, anúnciase e volver atrás restaura', async () => {
    const { el, settle } = await render('/naron/xestion/indicadores?periodo=trimestre&instalacion=f-p1&uso=Partido', {
      ...naronPanel(),
      period: {
        kind: 'QUARTER',
        from: '2026-10-01',
        to: '2026-12-31',
        evolutionUnit: 'QUARTER',
        breakdownUnit: 'WEEK',
      },
    });
    backend().verify();
    // Sen arranque da app, o router escoita o «atrás» do navegador só se llo din.
    TestBed.inject(Router).setUpLocationChangeListener();
    expect(pressed(el)).toEqual(['Trimestre']);
    expect(selectText(el.querySelector('[data-facility]')!)).toBe('Pista de pádel 1 de O Alto');
    expect(selectText(el.querySelector('[data-use]')!)).toBe('Partido');

    await choosePeriod(el, 'ano', settle);
    const year = await expectRequest(panelFor('YEAR', { facilityId: 'f-p1', useId: ['u-p1'] }));
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/indicadores?periodo=ano&instalacion=f-p1&uso=Partido');
    year.flush({
      ...naronPanel(),
      period: { kind: 'YEAR', from: '2026-01-01', to: '2026-12-31', evolutionUnit: 'YEAR', breakdownUnit: 'MONTH' },
    });
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-indicators-status]'))).toBe(
        'Indicadores actualizados: ano 2026 · Pista de pádel 1 de O Alto · Partido.',
      );
    });
    expect(pressed(el)).toEqual(['Ano']);

    TestBed.inject(Location).back();
    await settle();
    (await expectRequest(panelFor('QUARTER', { facilityId: 'f-p1', useId: ['u-p1'] }))).flush(naronPanel());
    await settle();
    expect(pressed(el)).toEqual(['Trimestre']);
  });

  it('rango no calendario do chip: «Aplicar» leva o rango ao URL; o chip dío', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-period-filter] .f-mdate__chip-open')!.click();
    await settle();
    // Os atallos: Esta semana · Este mes (o posto) · Trimestre · Ano.
    const presets = [...el.querySelectorAll('[data-period-filter] [data-preset]')];
    expect(presets.map((b) => [text(b), b.getAttribute('aria-pressed')])).toEqual([
      ['Esta semana', 'false'],
      ['Este mes', 'true'],
      ['Trimestre', 'false'],
      ['Ano', 'false'],
    ]);
    el.querySelector<HTMLElement>('[data-period-filter] [data-date="2026-10-05"]')!.click();
    await settle();
    el.querySelector<HTMLElement>('[data-period-filter] [data-date="2026-10-18"]')!.click();
    await settle();
    // Sen aplicar, o URL non cambia.
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/indicadores');
    el.querySelector<HTMLButtonElement>('[data-period-filter] [data-date-apply]')!.click();
    await settle();
    (await expectRequest(panelFor('CUSTOM', { from: '2026-10-05', to: '2026-10-18' }))).flush(naronPanel());
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/indicadores?periodo=datas&desde=2026-10-05&ata=2026-10-18');
    await settle();
    expect(pressed(el)).toEqual(['Do 05/10 ao 18/10']);
  });

  it('un rango a medias e pechado con Esc volve ao período de antes, sen petición', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-period-filter] .f-mdate__chip-open')!.click();
    await settle();
    el.querySelector<HTMLElement>('[data-period-filter] [data-date="2026-10-05"]')!.click();
    await settle();
    el.querySelector('[data-period-filter] [data-date-panel]')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    await settle();
    backend().verify();
    expect(TestBed.inject(Router).url).toBe('/naron/xestion/indicadores');
    expect(pressed(el)).toEqual(['Outubro']);
  });

  it('sen reservas (Ferrol): as cifras a cero e un estado baleiro, sen erros; axe', async () => {
    const { el } = await render(undefined, emptyPanel());
    expect(el.querySelector('furar-problem-alert [role="alert"]')?.textContent?.trim()).toBe('');
    expect(text(el.querySelector('[data-indicators-empty] .f-status__title'))).toBe('Sen reservas neste período');
    expect(el.querySelectorAll('furar-chart-card')).toHaveLength(0);
    expect(text(el.querySelector('[data-kpi="bookings"] .f-stat-tile__change'))).toBe('Sen datos do período anterior');
    await expectNoAxeViolations(el);
  });

  it('un filtro sen reservas anúncioo: «Indicadores actualizados: … Sen reservas neste período.»', async () => {
    const { el, settle } = await render();
    await choosePeriod(el, 'ano', settle);
    (await expectRequest(panelFor('YEAR'))).flush({
      ...emptyPanel(),
      period: { kind: 'YEAR', from: '2026-01-01', to: '2026-12-31', evolutionUnit: 'YEAR', breakdownUnit: 'MONTH' },
    });
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-indicators-status]'))).toBe(
        'Indicadores actualizados: ano 2026 · Todas as instalacións · Todos os usos. Sen reservas neste período.',
      );
    });
    expect(el.querySelector('[data-indicators-status]')?.getAttribute('role')).toBe('status');
  });

  it('Ctrl+P sen «Exportar»: beforeprint pon o alcance do panel e afterprint quítao', async () => {
    const { settle } = await render();
    await settle();
    const root = document.documentElement;
    window.dispatchEvent(new Event('beforeprint'));
    expect(root.getAttribute('data-print')).toBe('panel');
    window.dispatchEvent(new Event('afterprint'));
    expect(root.hasAttribute('data-print')).toBe(false);
    // Co alcance xa posto por «Exportar» (unha tarxeta), non o cambia.
    root.setAttribute('data-print', 'card');
    window.dispatchEvent(new Event('beforeprint'));
    expect(root.getAttribute('data-print')).toBe('card');
    root.removeAttribute('data-print');
  });

  it('un erro da API sae traducido', async () => {
    const { el, settle } = await render();
    await choosePeriod(el, 'semana', settle);
    flushProblem(await expectRequest(panelFor('WEEK')), 400, 'indicators.filter-invalid');
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('furar-problem-alert'))).toContain('Referencia do erro: trace-1');
    });
  });

  it('Exportar CSV do panel e dun gráfico: os nomes de ADR-0026', async () => {
    const { el, settle } = await render();
    const names: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download);
    });
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const csv = async (scope: Element) => {
      scope.querySelector<HTMLButtonElement>('.f-export-menu__trigger')!.click();
      await settle();
      document.querySelector<HTMLButtonElement>('[data-format="csv"]')!.click();
      await settle();
    };
    await csv(el.querySelector('furar-indicator-filters')!);
    await csv(el.querySelector('[data-chart="revenue"]')!);
    expect(names).toEqual(['indicadores-naron-2026-10.csv', 'indicadores-naron-2026-10-ingresos.csv']);
    click.mockRestore();
  });
});
