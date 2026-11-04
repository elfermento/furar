// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../../testing/api';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { MunicipalityHomePage } from '../municipality-home.page';

const FIELDS = '/api/v1/municipalities/naron/custom-fields?target=MUNICIPALITY';
const LIST = '/api/v1/municipalities/naron/public/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/municipalities/naron/public/availability';
const MANAGEMENT_LIST = '/api/v1/municipalities/naron/facilities?page=0&size=100';

/** Sen o NIF: o primeiro paso de «Para abrir as reservas» está por facer. */
const LEGAL = {
  legalEntityName: 'Concello de Narón',
  address: 'Praza de Galicia, 1',
  generalEmail: 'info@naron.example',
  dpoEmail: 'dpd@naron.example',
};

async function render(roles: Parameters<typeof fakeSession>[0]) {
  TestBed.configureTestingModule({ providers: provideTestApi(fakeSession(roles)) });
  TestBed.inject(CurrentMunicipality).set(municipality({ legal: LEGAL }));
  const fixture = TestBed.createComponent(MunicipalityHomePage);
  await fixture.whenStable();
  (await expectRequest(FIELDS)).flush([]);
  (await expectRequest(AVAILABILITY)).flush({ date: '', today: '', now: '', page: 0, size: 12, items: [] });
  (await expectRequest(LIST)).flush([]);
  await fixture.whenStable();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

/** Deixa pasar o `@defer (on idle)` e as súas peticións. */
async function idle(fixture: Awaited<ReturnType<typeof render>>['fixture']) {
  await new Promise((resolve) => setTimeout(resolve, 50));
  await fixture.whenStable();
}

describe('portada do concello › pasos pendentes (maqueta 56)', () => {
  afterEach(() => backend().verify());

  for (const [who, roles] of [
    ['Xestión', { manager: 'naron' }],
    ['a plataforma', { platform: true }],
  ] as const) {
    it(`${who}: a lista de «Xeral» pregada enriba do título, co seguinte paso e «Ir»`, async () => {
      const { fixture, el } = await render(roles);
      (await expectRequest(MANAGEMENT_LIST)).flush({ items: [], page: 0, size: 100, totalItems: 0 });
      document.querySelector('link#furar-management-css')?.dispatchEvent(new Event('load'));
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(el.querySelector('furar-checklist[data-compact]')).not.toBeNull();
      });
      const main = el.querySelector('.f-hero__main')!;
      expect(main.firstElementChild?.tagName.toLowerCase()).toBe('furar-opening-checklist');
      expect(el.querySelector('[data-next]')?.textContent).toBe('completa os datos legais');
      expect(el.querySelector('[data-count]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('paso 1 de 2');
      expect(el.querySelector('a[data-go]')?.getAttribute('href')).toBe('/naron/xestion/concello/legal');
      expect(el.querySelector('button.f-checklist__toggle')?.getAttribute('aria-expanded')).toBe('false');
    });
  }

  for (const [who, roles] of [
    ['Recepción', { staff: 'naron' }],
    ['a veciñanza', {}],
  ] as const) {
    it(`${who}: nin a lista nin a petición da xestión`, async () => {
      const { fixture, el } = await render(roles);
      await idle(fixture);
      backend().expectNone(MANAGEMENT_LIST);
      expect(el.querySelector('furar-opening-checklist')).toBeNull();
    });
  }
});
