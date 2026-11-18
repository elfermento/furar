// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { loadLazySymbols } from '../../shared/ui/icon/icon.component';
import { OpenDataPage } from './open-data.page';
import type { OpenDataSummary } from './open-data.store';

const URL = '/api/v1/municipalities/naron/open-data';
const ALL_URL = '/api/v1/catalog/open-data';

function summary(overrides: Partial<OpenDataSummary> = {}): OpenDataSummary {
  return {
    datasets: [
      { key: 'FACILITIES', updatedOn: '2026-11-10', formats: ['csv', 'json', 'geojson'] },
      { key: 'CLOSURES', formats: ['csv', 'json'] },
      { key: 'OCCUPANCY', updatedOn: '2026-11-11', formats: ['csv', 'json'] },
    ],
    occupancy: {
      availableFrom: '2025-11-11',
      availableTo: '2026-11-10',
      defaultFrom: '2026-10-01',
      defaultTo: '2026-10-31',
      maxMonths: 12,
      bytesPerDay: { csv: 140_000, json: 260_000 },
    },
    catalog: true,
    municipalities: 1,
    customFields: [{ code: 'pavimento', label: { gl: 'Pavimento', es: 'Pavimento' }, type: 'SELECT' }],
    ...overrides,
  };
}

describe('Datos abertos', () => {
  beforeAll(() => loadLazySymbols());
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-11T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(path = '/naron/datos-abertos', body: OpenDataSummary | 404 = summary()) {
    const all = !path.startsWith('/naron');
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideRouter([
          { path: 'naron/datos-abertos', component: OpenDataPage },
          { path: 'datos-abertos', component: OpenDataPage, data: { scope: 'all' } },
          { path: 'erro/:status', component: OpenDataPage },
        ]),
      ],
    });
    if (!all) TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(path);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    const request = await expectRequest(all ? ALL_URL : URL);
    if (body === 404) flushProblem(request, 404, 'opendata.not-published');
    else request.flush(body);
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-tabs, [data-open-data-empty]')).not.toBeNull();
    });
    return { harness, el, settle };
  }

  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  it('pestanas de liña, frase do conxunto, «Actualizado o…», pregables pechados e información ao final; axe', async () => {
    const { el } = await render();
    expect(text(el.querySelector('h1'))).toBe('Datos abertos');
    expect(text(el.querySelector('furar-page-header'))).toContain('O Concello de Narón publica aquí');
    expect([...el.querySelectorAll('[role="tab"]')].map((t) => text(t))).toEqual([
      'Instalacións',
      'Peches',
      'Ocupación',
    ]);
    expect(text(el.querySelector('[data-dataset-description]'))).toContain('As instalacións deportivas');
    expect(text(el.querySelector('[data-updated]'))).toMatch(/Actualizado o 10 de novembro de 2026|10 nov/);
    const folds = [...el.querySelectorAll('furar-disclosure .f-disclosure__toggle')];
    expect(folds.map((b) => b.getAttribute('aria-expanded'))).toEqual(['false', 'false']);
    expect(el.querySelector('furar-date-filter')).toBeNull();
    expect(el.querySelector('[data-portals]')).not.toBeNull();
    expect(text(el.querySelector('[data-reuse]'))).toContain('Citando o Concello de Narón');
    await expectNoAxeViolations(el);
  });

  it('«Columnas» das instalacións: dúas táboas e os campos propios; os pregables son independentes', async () => {
    const { el, settle } = await render();
    const [details, columns] = [...el.querySelectorAll<HTMLButtonElement>('.f-disclosure__toggle')];
    columns.click();
    await settle();
    expect(details.getAttribute('aria-expanded')).toBe('false');
    expect(columns.getAttribute('aria-expanded')).toBe('true');
    const tables = [...el.querySelectorAll('[data-columns]')];
    expect(tables.map((t) => t.getAttribute('data-columns'))).toEqual(['facility', 'use']);
    expect(text(tables[0])).toContain('campo_pavimento');
    expect(text(tables[0].querySelector('tbody tr th, tbody tr td'))).toBe('id');
  });

  it('sen «Actualizado» sen data; «Peches» no URL', async () => {
    const { el, settle } = await render();
    el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[1].click();
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(Router).url).toBe('/naron/datos-abertos?conxunto=peches');
    });
    expect(text(el.querySelector('[data-dataset-description]'))).toContain('Os días nos que');
    expect(el.querySelector('[data-updated]')).toBeNull();
  });

  it('Ocupación: o filtro «Datas» co último mes enteiro e os erros sen abrir o diálogo', async () => {
    const { el, settle } = await render('/naron/datos-abertos?conxunto=ocupacion');
    expect(el.querySelector('[role="tab"][aria-selected="true"]')?.textContent?.trim()).toBe('Ocupación');
    expect(text(el.querySelector('[data-date-chip]'))).toContain('Do 01/10 ao 31/10');
    el.querySelector<HTMLButtonElement>('[data-date-clear]')!.click();
    await settle();
    el.querySelector<HTMLButtonElement>('[data-download]')!.click();
    await settle();
    expect(text(el.querySelector('[data-dates-error]'))).toBe('Escolle o primeiro e o último día.');
    expect(dialog()).toBeNull();
  });

  it('a descarga pide o ficheiro coas datas e o idioma; 429 queda no diálogo', async () => {
    const { el, settle } = await render('/naron/datos-abertos?conxunto=ocupacion');
    el.querySelector<HTMLButtonElement>('[data-download]')!.click();
    await settle();
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    expect(text(dialog()!.querySelector('[data-download-size]'))).toContain('MB');
    expect(dialog()!.querySelectorAll('input[type="radio"]').length).toBe(2);
    dialog()!.querySelector<HTMLButtonElement>('[data-download-confirm]')!.click();
    const request = await expectRequest((r) => r.url.endsWith('/open-data/occupancy.csv'));
    expect(request.request.params.get('desde')).toBe('2026-10-01');
    expect(request.request.params.get('ata')).toBe('2026-10-31');
    expect(request.request.params.get('lang')).toBe('gl');
    request.flush(new Blob(['x']), { status: 429, statusText: 'Too Many' });
    await vi.waitFor(async () => {
      await settle();
      expect(text(dialog()?.querySelector('[data-download-error]'))).toContain('Agora hai moitas descargas');
    });
  });

  it('Instalacións: diálogo con tres formatos e descarga do CSV', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-download]')!.click();
    await settle();
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    expect(dialog()!.querySelectorAll('input[type="radio"]').length).toBe(3);
    expect(dialog()!.querySelector('[data-download-size]')).toBeNull();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    URL_create();
    dialog()!.querySelector<HTMLButtonElement>('[data-download-confirm]')!.click();
    const request = await expectRequest((r) => r.url.endsWith('/open-data/facilities.csv'));
    request.flush(new Blob(['id;nome']), {
      headers: { 'Content-Disposition': 'attachment; filename="naron-instalacions.csv"' },
    });
    await vi.waitFor(() => expect(click).toHaveBeenCalled());
  });

  it('a de todos: columna «concello», sen «Últimos 12 meses» e co «?» nas datas', async () => {
    const { el, settle } = await render(
      '/datos-abertos?conxunto=ocupacion',
      summary({ municipalities: 2, customFields: [], occupancy: { ...summary().occupancy, maxMonths: 1 } }),
    );
    expect(text(el.querySelector('furar-page-header'))).toContain('Os concellos desta web');
    expect(el.querySelector('furar-help-tip')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('.f-mdate__chip-open')!.click();
    await settle();
    expect([...el.querySelectorAll('[data-preset]')].map((b) => b.getAttribute('data-preset'))).toEqual([
      'yesterday',
      'lastMonth',
    ]);
    el.querySelectorAll<HTMLButtonElement>('.f-disclosure__toggle')[1].click();
    await settle();
    expect(text(el.querySelector('[data-columns] tbody tr th, [data-columns] tbody tr td'))).toBe('concello');
  });

  it('a de todos sen concellos: estado baleiro sen pestanas', async () => {
    const { el } = await render('/datos-abertos', summary({ municipalities: 0, catalog: false }));
    expect(text(el.querySelector('[data-open-data-empty]'))).toContain('Aínda ningún concello');
    expect(el.querySelector('furar-tabs')).toBeNull();
  });
});

/** jsdom non trae URL.createObjectURL: abonda cun enderezo falso. */
function URL_create(): void {
  const url = window.URL as unknown as { createObjectURL?: unknown; revokeObjectURL?: unknown };
  url.createObjectURL = () => 'blob:x';
  url.revokeObjectURL = () => undefined;
}
