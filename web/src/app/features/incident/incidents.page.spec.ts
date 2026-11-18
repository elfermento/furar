// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { incident } from './incident.fixture';
import { IncidentsPage } from './incidents.page';

const LIST = '/api/v1/municipalities/naron/incidents';
const ITEMS = [
  incident({ id: 'i1' }),
  incident({ id: 'i2', status: 'IN_PROGRESS', type: 'CLEANING', facilityId: 'f2', facilityName: 'Pavillón' }),
  incident({ id: 'i3', status: 'RESOLVED', origin: 'STAFF', reporter: { name: undefined, email: undefined } }),
];

describe('IncidentsPage: incidencias da xestión', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/new-count'), { count: 1 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(items: object[] = ITEMS) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({ staff: 'naron' }))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(IncidentsPage);
    const el = fixture.nativeElement as HTMLElement;
    (await expectRequest(LIST)).flush(items);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    return { fixture, el };
  }

  it('a táboa: data, instalación, tipo, estado en chip de cor e quen avisou', async () => {
    const { el } = await render();
    const rows = [...el.querySelectorAll('[data-incidents-table] tbody tr')];
    expect(rows).toHaveLength(3);
    const cells = (row: Element) => [...row.querySelectorAll('td, th')].map((c) => c.textContent?.trim());
    expect(cells(rows[0]!)).toEqual(['11/11/2026', 'Pista de pádel 2', 'Avaría', 'Nova', 'Uxía Proba']);
    expect(rows[0]!.querySelector('furar-tag')?.getAttribute('data-tone')).toBe('danger');
    expect(rows[1]!.querySelector('furar-tag')?.getAttribute('data-tone')).toBe('info');
    expect(cells(rows[2]!)[4]).toBe('Persoal do concello');
    expect(el.querySelectorAll('[data-incident-card]')).toHaveLength(3);
    await expectNoAxeViolations(el);
  });

  it('a busca filtra no texto que se ve; sen resultados, dio', async () => {
    const { fixture, el } = await render();
    const search = el.querySelector<HTMLInputElement>('[data-incident-search]')!;
    search.value = 'pavillon';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(el.querySelectorAll('[data-incidents-table] tbody tr')).toHaveLength(1);
    search.value = 'non existe';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(el.querySelector('[data-incidents-empty]')?.textContent).toContain('Non hai incidencias con estes filtros.');
  });

  it('sen ningunha, o baleiro explica onde aparecerán', async () => {
    const { el } = await render([]);
    expect(el.querySelector('[data-incidents-empty]')?.textContent).toContain('aparecerá aquí');
  });
});
