// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { OpeningChecklistComponent } from './opening-checklist.component';
import { MunicipalitySection } from './section';

const LIST = '/api/v1/municipalities/naron/facilities';
const listUrl = (page = 0) => `${LIST}?page=${page}&size=100`;

const LEGAL = {
  legalEntityName: 'Concello de Narón',
  nif: 'P1505400J',
  address: 'Praza de Galicia, 1',
  generalEmail: 'info@naron.example',
  dpoEmail: 'dpd@naron.example',
};

/** Tarxeta do listado da xestión, só co que mira a card. */
type Summary = { id: string; status: string; missing: string[] } & Record<string, unknown>;

function summary(overrides: Partial<Summary> = {}): Summary {
  return {
    id: 'f1',
    name: 'Pavillón',
    status: 'ACTIVE',
    address: { street: 'Rúa do Alto', streetNumber: '12', postalCode: '15570', locality: 'Narón' },
    sports: [],
    cover: null,
    missing: [],
    version: 1,
    ...overrides,
  };
}

const page = (items: Summary[], totalItems = items.length, at = 0) => ({
  items,
  page: at,
  size: 100,
  totalItems,
});

describe('Concello › Xeral › Para abrir as reservas', () => {
  afterEach(() => backend().verify());

  async function render(legal: Partial<typeof LEGAL> = {}) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), MunicipalitySection] });
    TestBed.inject(CurrentMunicipality).set(municipality({ legal }));
    const fixture = TestBed.createComponent(OpeningChecklistComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const stable = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    };
    const item = (key: string) => el.querySelector<HTMLElement>(`[data-item="${key}"]`);
    return { el, stable, item };
  }

  it('sen nada: os dous pasos por facer, coas súas ligazóns; axe', async () => {
    const { el, stable, item } = await render({ nif: 'P1505400J' });
    expect(el.querySelector('furar-checklist')).toBeNull();
    (await expectRequest(listUrl())).flush(
      page([summary({ missing: ['USES'] }), summary({ id: 'f2', status: 'TEMPORARILY_CLOSED' })]),
    );
    await stable();
    expect(el.querySelector('h2')?.textContent).toBe('Para abrir as reservas');
    expect(el.querySelector('[data-count]')?.textContent?.trim()).toBe('0 de 2');
    expect(item('legal')?.textContent).toContain('Datos que usan os textos legais');
    expect(item('legal')?.querySelector('a')?.getAttribute('href')).toBe('/naron/xestion/concello/legal');
    expect(item('facility')?.textContent).toContain('Primeira instalación');
    expect(item('facility')?.querySelector('a')?.getAttribute('href')).toBe('/naron/xestion/instalacions?nova=1');
    await expectNoAxeViolations(el);
  });

  it('cos datos legais completos e sen instalación publicada: 1 de 2', async () => {
    const { el, stable, item } = await render(LEGAL);
    (await expectRequest(listUrl())).flush(page([]));
    await stable();
    expect(el.querySelector('[data-count]')?.textContent?.trim()).toBe('1 de 2');
    expect(item('legal')?.getAttribute('data-done')).toBe('true');
    expect(item('legal')?.querySelector('a')).toBeNull();
    expect(item('facility')?.getAttribute('data-done')).toBe('false');
  });

  it('busca a instalación publicada nas páxinas seguintes', async () => {
    const { stable, item } = await render();
    const drafts = Array.from({ length: 100 }, (_, i) => summary({ id: `d${i}`, missing: ['SCHEDULE'] }));
    (await expectRequest(listUrl())).flush(page(drafts, 101));
    (await expectRequest(listUrl(1))).flush(page([summary()], 101, 1));
    await stable();
    expect(item('facility')?.getAttribute('data-done')).toBe('true');
    expect(item('legal')?.getAttribute('data-done')).toBe('false');
  });

  it('co todo feito, desaparece', async () => {
    const { el, stable } = await render(LEGAL);
    (await expectRequest(listUrl())).flush(page([summary()]));
    await stable();
    expect(el.querySelector('furar-checklist')).toBeNull();
  });

  it('se falla o listado, non avisa de nada', async () => {
    const { el, stable } = await render();
    flushProblem(await expectRequest(listUrl()), 500, 'common.internal');
    await stable();
    expect(el.querySelector('furar-checklist')).toBeNull();
  });

  it('compacta, fóra da sección (a portada): os datos legais gardados e a folla da xestión antes de pintala', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality({ legal: LEGAL }));
    const fixture = TestBed.createComponent(OpeningChecklistComponent);
    fixture.componentRef.setInput('compact', true);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    (await expectRequest(listUrl())).flush(page([]));
    const link = document.querySelector('link#furar-management-css');
    expect(link).not.toBeNull();
    link?.dispatchEvent(new Event('load'));
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('furar-checklist[data-compact]')).not.toBeNull();
    });
    expect(el.querySelector('[data-next]')?.textContent).toBe('crea a primeira instalación');
    expect(el.querySelector('[data-count]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('paso 2 de 2');
    expect(el.querySelector('a[data-go]')?.getAttribute('href')).toBe('/naron/xestion/instalacions?nova=1');
  });
});
