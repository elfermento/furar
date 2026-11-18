// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CHART_ENGINE } from '../../../shared/ui/chart/chart-engine';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { useMonday } from '../../../../testing/manage-bookings';
import { platformResponse } from '../../../../testing/platform-indicators';
import { PlatformIndicatorsPage } from './platform-indicators.page';

const text = (e: Element | null | undefined) => (e?.textContent ?? '').replaceAll(' ', ' ').replace(/\s+/g, ' ').trim();

const URL = '/api/v1/indicators';

async function render(url = '/plataforma/indicadores') {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(fakeSession({ platform: true })),
      provideRouter([{ path: 'plataforma/indicadores', component: PlatformIndicatorsPage }]),
      provideLocationMocks(),
      { provide: CHART_ENGINE, useValue: () => new Promise(() => undefined) },
    ],
  });
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  const first = await expectRequest((r) => r.url.endsWith(URL));
  return { el, settle, harness, first };
}

const names = (el: Element) => [...el.querySelectorAll('[data-platform-table] tbody th')].map((th) => text(th));

describe('Indicadores da plataforma', () => {
  useMonday();

  it('o mes en curso: catro cifras sen persoas, unha fila por concello, sen ligazóns; axe', async () => {
    const { el, settle, first } = await render();
    expect(first.request.params.get('period')).toBe('MONTH');
    first.flush(platformResponse());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-stat-tile')).toHaveLength(4);
    });
    expect(text(el.querySelector('h1'))).toBe('Indicadores da plataforma');
    expect([...el.querySelectorAll('[data-kpi]')].map((k) => k.getAttribute('data-kpi'))).toEqual([
      'bookings',
      'occupancy',
      'reservedHours',
      'paidCents',
    ]);
    // Máis reservas primeiro, coa columna marcada; as cifras á dereita.
    expect(names(el)).toEqual(['Ferrol', 'Narón', 'A Capela']);
    expect(el.querySelector('[data-column="bookings"]')?.getAttribute('aria-sort')).toBe('descending');
    const ferrol = el.querySelector('[data-platform-table] tr[data-municipality="ferrol"]')!;
    expect([...ferrol.querySelectorAll('td')].map((td) => text(td))).toEqual([
      '2.105',
      '63 %',
      '2.388 h',
      '1.031',
      '15.420,50 €',
    ]);
    expect(ferrol.querySelector('td')?.classList).toContain('f-pindicators__num');
    // A fila non leva a ningunha parte.
    expect(el.querySelector('[data-platform-municipalities] a')).toBeNull();
    const card = el.querySelector('[data-platform-cards] [data-municipality="ferrol"]');
    expect(text(card?.querySelector('.f-pindicators__line'))).toBe('Ferrol 15.420,50 €');
    expect(text(card?.querySelector('.f-pindicators__summary'))).toBe(
      '2.105 reservas · 63 % de ocupación · 2.388 h · 1.031 persoas',
    );
    await expectNoAxeViolations(el);
  });

  it('cada cifra abre a súa evolución sumada', async () => {
    const { el, settle, first } = await render();
    first.flush(platformResponse());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-stat-tile')).toHaveLength(4);
    });
    el.querySelector<HTMLButtonElement>('[data-kpi="bookings"] button')!.click();
    await settle();
    const dialog = await vi.waitFor(() => {
      const found = document.querySelector<HTMLElement>('[role="dialog"]');
      if (!found) throw new Error('sen modal');
      return found;
    });
    expect(text(dialog.querySelector('h2'))).toMatch(/^Reservas/);
  });

  it('ordena desde a cabeceira e desde «Ordenar»', async () => {
    const { el, settle, first } = await render();
    first.flush(platformResponse());
    await vi.waitFor(async () => {
      await settle();
      expect(names(el)).toHaveLength(3);
    });
    el.querySelector<HTMLButtonElement>('[data-column="name"] .f-field-popover__trigger')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-column-filter="Concello"] [data-option="asc"]')!.click();
    await settle();
    expect(names(el)).toEqual(['A Capela', 'Ferrol', 'Narón']);
    expect(el.querySelector('[data-column="name"]')?.getAttribute('aria-sort')).toBe('ascending');
    expect(el.querySelector('[data-column="bookings"]')?.getAttribute('aria-sort')).toBeNull();

    el.querySelector<HTMLButtonElement>('[data-platform-sort] .f-field-popover__trigger')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-column-filter="Ordenar"] [data-option="paidCents"]')!.click();
    await settle();
    expect(names(el)).toEqual(['Ferrol', 'Narón', 'A Capela']);
    expect(el.querySelector('[data-column="paidCents"]')?.getAttribute('aria-sort')).toBe('descending');
  });

  it('o período vai no URL e pide de novo; un erro sae na páxina', async () => {
    const { settle, first } = await render('/plataforma/indicadores?periodo=ano');
    expect(first.request.params.get('period')).toBe('YEAR');
    flushProblem(first, 400, 'indicators.period-invalid', { params: { max: 366 } });
    await settle();
    expect(document.querySelector('furar-problem-alert')?.textContent).toMatch(/\S/);
  });

  it('sen ningún concello: as cifras a cero e un aviso no canto da táboa', async () => {
    const { el, settle, first } = await render();
    first.flush(platformResponse([]));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-platform-empty]')).not.toBeNull();
    });
    expect(el.querySelector('[data-platform-table]')).toBeNull();
  });

  async function loaded() {
    const view = await render();
    view.first.flush(platformResponse());
    await vi.waitFor(async () => {
      await view.settle();
      expect(names(view.el)).toHaveLength(3);
    });
    return view;
  }

  async function choose(el: Element, preset: string, settle: () => Promise<unknown>) {
    el.querySelector<HTMLButtonElement>('[data-period-filter] .f-mdate__chip-open')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>(`[data-period-filter] [data-preset="${preset}"]`)!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-period-filter] [data-date-apply]')!.click();
    await settle();
  }

  it('cambiar o período pide de novo e anúncioo; o mesmo período non pide nada', async () => {
    const { el, settle } = await loaded();
    expect(text(el.querySelector('[data-platform-indicators-status]'))).toBe('');
    await choose(el, 'mes', settle);
    backend().expectNone((r) => r.url.endsWith(URL));
    await choose(el, 'semana', settle);
    const next = await expectRequest((r) => r.url.endsWith(URL) && r.params.get('period') === 'WEEK');
    next.flush(platformResponse());
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-platform-indicators-status]'))).not.toBe('');
    });
  });

  it('Ctrl+P sen «Exportar»: beforeprint pon o alcance do panel e afterprint quítao', async () => {
    await loaded();
    const root = document.documentElement;
    window.dispatchEvent(new Event('beforeprint'));
    expect(root.getAttribute('data-print')).toBe('panel');
    window.dispatchEvent(new Event('afterprint'));
    expect(root.hasAttribute('data-print')).toBe(false);
  });

  it('Ctrl+P antes de que cheguen os datos non cambia nada', async () => {
    const { first } = await render();
    window.dispatchEvent(new Event('beforeprint'));
    expect(document.documentElement.hasAttribute('data-print')).toBe(false);
    window.dispatchEvent(new Event('afterprint'));
    first.flush(platformResponse());
  });

  it('Exportar CSV: o nome do ficheiro leva a plataforma e o período', async () => {
    const { el, settle } = await loaded();
    const files: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      files.push(this.download);
    });
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:x');
    globalThis.URL.revokeObjectURL = vi.fn();
    el.querySelector<HTMLButtonElement>('.f-export-menu__trigger')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-format="csv"]')!.click();
    await settle();
    expect(files).toHaveLength(1);
    expect(files[0]).toMatch(/^indicadores-plataforma-.+\.csv$/);
    click.mockRestore();
  });
});
