// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { matchCard } from '../../../testing/open-matches';
import { OpenMatchesComponent } from './open-matches-section.component';

const LIST = '/api/v1/municipalities/naron/public/open-matches';
const CATALOG = '/api/v1/catalog/open-matches';
const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length });

describe('«Buscan xente para xogar»: as partidas nas portadas e na ficha (ADR-0040)', () => {
  afterEach(() => backend().verify());

  async function render(inputs: Record<string, unknown>, url: string, body: object | null, fail = false) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(OpenMatchesComponent);
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    const request = await expectRequest((r) => r.url === url);
    if (fail) flushProblem(request, 500, 'common.unexpected');
    else request.flush(body);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement, request };
  }

  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  it('cun filtro: só as das instalacións que pasan e dese día, co número no título', async () => {
    const { fixture, el } = await render(
      { slug: 'naron', facilityIds: new Set(['f1']), day: '2099-11-20', counted: true },
      LIST,
      page([
        matchCard(),
        matchCard({ id: 'm2', facilityId: 'f2' }),
        matchCard({ id: 'm3', start: '2099-11-21T19:00:00Z', end: '2099-11-21T20:00:00Z' }),
      ]),
    );
    expect([...el.querySelectorAll('[data-open-match]')].map((c) => c.getAttribute('data-open-match'))).toEqual(['m1']);
    expect(text(el.querySelector('h2'))).toBe('Buscan xente para xogar · 1');
    // Nada que pase: sen sección.
    fixture.componentRef.setInput('day', '2099-11-22');
    await fixture.whenStable();
    expect(el.querySelector('section')).toBeNull();
  });

  it('portada común cun concello escollido: só as del', async () => {
    const { el } = await render({ only: 'ares' }, CATALOG, {
      page: 0,
      size: 20,
      hasNext: false,
      municipalities: [
        { slug: 'naron', name: 'Narón', items: [matchCard()] },
        { slug: 'ares', name: 'Ares', items: [matchCard({ id: 'm9' })] },
      ],
    });
    expect([...el.querySelectorAll('[data-open-match]')].map((c) => c.getAttribute('data-open-match'))).toEqual(['m9']);
  });

  it('portada do concello: o carrusel coa tarxeta dos eventos, sen quen organiza e coas prazas no pé; leva á páxina; axe', async () => {
    const { el, request } = await render(
      { slug: 'naron' },
      LIST,
      page([
        matchCard(),
        matchCard({ id: 'm2', title: undefined, level: undefined, cover: undefined, total: 3, minimum: 4, maximum: 4 }),
      ]),
    );
    expect(request.request.params.get('facilityId')).toBeNull();
    expect(text(el.querySelector('h2'))).toBe('Buscan xente para xogar');
    expect(el.querySelector('section')?.className).toContain('f-home__section');
    expect(el.querySelector('furar-carousel')).not.toBeNull();
    expect(el.querySelector('furar-status-card')).toBeNull();
    const first = el.querySelector('furar-media-card[data-open-match="m1"]')!;
    expect(text(first.querySelector('.f-media-card__title'))).toBe('Pachanga de venres');
    expect(text(first.querySelector('.f-media-card__when'))).toBe('ven. 20 nov. · 20:00');
    expect(text(first.querySelector('.f-media-card__place'))).toBe('Pavillón da Gándara');
    expect([...first.querySelectorAll('.f-tag-list furar-tag')].map(text)).toEqual(['Fútbol sala', 'Nivel medio']);
    expect(first.querySelector('img')?.getAttribute('src')).toBe('/api/v1/municipalities/naron/drawings/futsal/thumb');
    expect(text(first)).not.toContain('Uxía Pereira');
    const seats = first.querySelector('.f-media-card__foot [data-seats]');
    expect(text(seats)).toBe('6 prazas libres');
    expect(seats?.getAttribute('data-tone')).toBe('success');
    expect(first.querySelector('a.f-media-card__link')?.getAttribute('href')).toBe('/naron/partidas/m1');
    // Sen título nin nivel: o uso fai de nome e non hai etiquetas; sen portada, sen imaxe.
    const second = el.querySelector('furar-media-card[data-open-match="m2"]')!;
    expect(text(second.querySelector('.f-media-card__title'))).toBe('Fútbol sala');
    expect(second.querySelector('.f-tag-list')).toBeNull();
    expect(second.querySelector('img')).toBeNull();
    expect(text(second.querySelector('[data-seats]'))).toBe('Última praza');
    expect(second.querySelector('[data-seats]')?.getAttribute('data-tone')).toBe('warn');
    await expectNoAxeViolations(el);
  });

  it('o pé das prazas, nos catro casos: libres, as últimas 2, a última e completa', async () => {
    const { el } = await render(
      { slug: 'naron' },
      LIST,
      page([
        matchCard({ id: 'a', total: 4 }),
        matchCard({ id: 'b', total: 8 }),
        matchCard({ id: 'c', total: 9 }),
        matchCard({ id: 'd', total: 10 }),
      ]),
    );
    const seats = [...el.querySelectorAll('[data-seats]')];
    expect(seats.map(text)).toEqual(['6 prazas libres', 'Últimas 2 prazas', 'Última praza', 'Completa']);
    expect(seats.map((s) => s.getAttribute('data-tone'))).toEqual(['success', 'warn', 'warn', 'muted']);
  });

  it('ficha da instalación: só as súas, en filas coma «Que se pode reservar»: nome ligado, día e hora co nivel e as prazas; axe', async () => {
    const { el, request } = await render(
      { slug: 'naron', facilityId: 'f1', variant: 'facility' },
      LIST,
      page([matchCard(), matchCard({ id: 'm2', title: undefined, level: undefined, total: 9 })]),
    );
    expect(request.request.params.get('facilityId')).toBe('f1');
    expect(el.querySelector('section')?.className).toBe('f-public__section');
    expect(el.querySelector('h2')?.className).not.toContain('f-home__heading');
    expect(el.querySelector('furar-carousel')).toBeNull();
    expect(el.querySelector('ul')?.className).toBe('f-public__uses');
    const row = el.querySelector('[data-open-match="m1"]')!;
    const link = row.querySelector('h3 a');
    expect(text(link)).toBe('Pachanga de venres');
    expect(link?.getAttribute('href')).toBe('/naron/partidas/m1');
    expect(text(row.querySelector('p'))).toBe('ven. 20 nov. · 20:00 · Nivel medio');
    expect(text(row)).not.toContain('Uxía Pereira');
    expect(text(row.querySelector('[data-seats]'))).toBe('6 prazas libres');
    expect(row.querySelector('[data-chip], .f-status-card__note')).toBeNull();
    const second = el.querySelector('[data-open-match="m2"]')!;
    expect(text(second.querySelector('h3'))).toBe('Fútbol sala');
    expect(text(second.querySelector('p'))).toBe('ven. 20 nov. · 20:00');
    expect(text(second.querySelector('[data-seats]'))).toBe('Última praza');
    await expectNoAxeViolations(el);
  });

  it('portada común: o mesmo carrusel, coa instalación e o concello; leva á páxina no seu concello', async () => {
    const { el } = await render({}, CATALOG, {
      page: 0,
      size: 20,
      hasNext: false,
      municipalities: [
        { slug: 'naron', name: 'Narón', items: [matchCard()] },
        {
          slug: 'ares',
          name: 'Ares',
          items: [matchCard({ id: 'm9', facilityName: 'Pista de tenis do Parque', total: 8 })],
        },
      ],
    });
    expect(el.querySelector('section')?.className).toContain('f-home__section');
    expect(el.querySelector('furar-carousel')).not.toBeNull();
    const card = el.querySelector('furar-media-card[data-open-match="m9"]')!;
    expect(text(card.querySelector('.f-media-card__place'))).toBe('Pista de tenis do Parque · Ares');
    expect(text(card)).not.toContain('Uxía Pereira');
    expect(text(card.querySelector('.f-media-card__foot [data-seats]'))).toBe('Últimas 2 prazas');
    expect(card.querySelector('a.f-media-card__link')?.getAttribute('href')).toBe('/ares/partidas/m9');
  });

  it('sen partidas a sección non existe; se falla a lectura, tampouco', async () => {
    const { el } = await render({ slug: 'naron' }, LIST, page([]));
    expect(el.querySelector('section')).toBeNull();
    TestBed.resetTestingModule();
    const failed = await render({ slug: 'naron' }, LIST, null, true);
    expect(failed.el.querySelector('section')).toBeNull();
  });
});
