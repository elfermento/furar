// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { definition, municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localToday } from '../../../shared/ui/calendar/local-date';
import { MunicipalityHomePage } from '../municipality-home.page';
import { FACILITIES } from './home.fixture';

const LIST = '/api/v1/municipalities/naron/public/facilities';
const FIELDS = '/api/v1/municipalities/naron/custom-fields?target=MUNICIPALITY';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/municipalities/naron/public/availability';

/** Ocos de hoxe: o campo con 2, o pádel con 1 e o pavillón sen ocos (sen chip). */
function availability(
  items = [
    { facilityId: 'f-campo', freeCount: 2, firstStarts: [] },
    { facilityId: 'f-padel', freeCount: 1, firstStarts: [] },
    { facilityId: 'f-pavillon', freeCount: 0, firstStarts: [] },
  ],
) {
  return { date: '2026-10-26', today: '2026-10-26', now: '2026-10-26T08:00:00Z', page: 0, size: 12, items };
}

async function render(customFields: Record<string, unknown> = {}) {
  TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({})) });
  TestBed.inject(CurrentMunicipality).set(municipality({ customFields }));
  const fixture = TestBed.createComponent(MunicipalityHomePage);
  await fixture.whenStable();
  const el = fixture.nativeElement as HTMLElement;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  return { fixture, el, settle };
}

describe('portada do concello', () => {
  afterEach(() => backend().verify());

  it('título co concello, buscador, collaxe, tarxetas e datos públicos; pasa axe', async () => {
    const { el, settle } = await render({ phone: '981 000 000' });
    expect(el.querySelector('h1')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Reserva a túa pista en Narón');
    expect(el.querySelector('furar-status-state[role="status"]')?.textContent).toContain('Cargando as instalacións');
    (await expectRequest(FIELDS)).flush([definition()]);
    const free = await expectRequest(AVAILABILITY);
    expect(free.request.params.get('date')).toBe(localToday());
    expect(free.request.params.get('size')).toBe('12');
    free.flush(availability());
    (await expectRequest(LIST)).flush(FACILITIES);
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-facility-card')).toHaveLength(3);
    });
    expect(el.querySelector('form[role="search"]')).not.toBeNull();
    // Collaxe coas portadas (foto ou debuxo), decorativo.
    const collage = el.querySelector('.f-collage');
    expect(collage?.getAttribute('aria-hidden')).toBe('true');
    expect(collage?.querySelectorAll('img')).toHaveLength(3);
    // As tarxetas van nun furar-carousel (tira no móbil, grella no escritorio).
    expect(el.querySelector('furar-carousel.f-rail ul')?.getAttribute('aria-label')).toBeTruthy();
    // Tarxeta: nome como título, enderezo, deportes en etiquetas soft e aviso de pechada.
    const first = el.querySelector('furar-facility-card');
    expect(first?.querySelector('h3')?.textContent?.trim()).toBe('Campo de fútbol 1 de O Alto');
    expect(first?.querySelector('.f-media-card__place')?.textContent).toContain('Rúa Buxo, 2');
    expect(
      [...(first?.querySelectorAll('furar-tag[data-look="soft"]') ?? [])].map((t) => t.textContent?.trim()),
    ).toEqual(['Fútbol 11', 'Fútbol 7']);
    expect(first?.textContent).toContain('Pechada temporalmente');
    // «N ocos hoxe» enriba da imaxe; sen ocos, sen chip.
    const badges = [...el.querySelectorAll('furar-facility-card')].map(
      (card) => card.querySelector('.f-media-card__image furar-tag[cardBadge]')?.textContent?.trim() ?? null,
    );
    expect(badges).toEqual(['2 ocos hoxe', '1 oco hoxe', null]);
    expect(first?.querySelector('furar-tag[cardBadge]')?.getAttribute('data-tone')).toBe('success');
    // Cada tarxeta leva á ficha pública (ligazón estirada no título).
    expect(first?.querySelector('h3 a')?.getAttribute('href')).toBe('/naron/instalacions/f-campo');
    expect(first?.querySelector('furar-media-card')?.hasAttribute('data-link')).toBe(true);
    // Sen fotos: o debuxo suxerido, co seu texto alternativo.
    const pavilion = [...el.querySelectorAll('furar-facility-card')][2];
    expect(pavilion.querySelector('img')?.getAttribute('src')).toContain('/drawings/sports_hall/thumb');
    expect(pavilion.querySelector('img')?.getAttribute('alt')).toBe('Debuxo dun pavillón polideportivo');
    expect(el.querySelector('#instalacions')?.tagName).toBe('H2');
    expect(el.querySelectorAll('.f-step')).toHaveLength(3);
    expect(el.querySelector('.f-steps h3')?.textContent).toContain('Busca o teu oco');
    expect([...el.querySelectorAll('.f-shot')].every((s) => s.getAttribute('aria-hidden') === 'true')).toBe(true);
    // As capturas levan os datos do concello: as súas instalacións coas fotos (1), a tarxeta de
    // reservar dunha delas (2) e un QR de verdade da reserva de exemplo (3).
    const [search, book, play] = [...el.querySelectorAll('.f-shot')];
    expect([...search.querySelectorAll('.f-shot__mini b')].map((b) => b.textContent?.trim())).toEqual(
      FACILITIES.map((f) => f.name),
    );
    expect(search.querySelector('.f-shot__mini img')?.getAttribute('src')).toBe(FACILITIES[0].cover.thumb);
    expect(book.querySelector('.f-shot__card b')?.textContent?.trim()).toBe(FACILITIES[0].name);
    expect(book.querySelector('.f-shot__thumb')?.getAttribute('src')).toBe(FACILITIES[0].cover.thumb);
    expect(play.querySelector('.f-shot__code')?.textContent?.trim()).toBe('FUR-X7K2');
    expect(play.querySelector('.f-shot__qr')?.getAttribute('viewBox')).toBe('0 0 21 21');
    expect(el.querySelector('dd')?.textContent).toContain('981 000 000');
    await expectNoAxeViolations(el);
  }, 20_000);

  it('sen instalacións publicadas: os exemplos coa etiqueta «Exemplo» e sen ligazón (maqueta 46)', async () => {
    const { el, settle } = await render();
    (await expectRequest(FIELDS)).flush([]);
    (await expectRequest(AVAILABILITY)).flush(availability([]));
    (await expectRequest(LIST)).flush([]);
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-example-card')).toHaveLength(4);
    });
    const cards = [...el.querySelectorAll('furar-example-card')];
    expect(cards.map((c) => c.querySelector('.f-media-card__title')?.textContent?.trim())).toEqual([
      'Campo de fútbol',
      'Pista de pádel',
      'Pavillón polideportivo',
      'Pista de atletismo',
    ]);
    expect(cards.every((c) => c.textContent?.includes('Exemplo'))).toBe(true);
    expect(el.querySelector('.f-rail a')).toBeNull();
    expect(el.querySelector('.f-hero__title')?.textContent).toContain('Narón');
    // O collaxe leva os debuxos de exemplo e a etiqueta na primeira foto.
    expect([...el.querySelectorAll('.f-collage img')].map((i) => i.getAttribute('src'))).toEqual([
      'examples/football_field.jpg',
      'examples/padel_court.jpg',
      'examples/sports_hall.jpg',
    ]);
    expect(el.querySelector('.f-collage [data-place="1"] .f-collage__badge')?.textContent?.trim()).toBe('Exemplo');
    expect(el.querySelector('.f-shot__mini b')?.textContent?.trim()).toBe('Campo de fútbol');
    await expectNoAxeViolations(el);
  });

  it('se falla a carga, erro con «Tentar de novo»', async () => {
    const { el, settle } = await render();
    (await expectRequest(FIELDS)).flush([]);
    // Se fallan os ocos, as tarxetas vense igual, sen chip.
    flushProblem(await expectRequest(AVAILABILITY), 500, 'common.internal');
    flushProblem(await expectRequest(LIST), 500, 'common.internal');
    await vi.waitFor(async () => {
      await settle();
      expect(el.textContent).toContain('Non se puideron cargar as instalacións');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))?.click();
    (await expectRequest(AVAILABILITY)).flush(availability([]));
    (await expectRequest(LIST)).flush(FACILITIES.slice(0, 1));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-facility-card')).toHaveLength(1);
    });
  });
});
