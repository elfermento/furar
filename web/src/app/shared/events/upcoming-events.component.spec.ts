// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { publicActivity, publicEvent } from './public-event.fixture';
import { UpcomingEventsComponent } from './upcoming-events.component';

const LIST = '/api/v1/municipalities/naron/events';

describe('UpcomingEventsComponent: «Eventos e actividades» na portada', () => {
  afterEach(() => backend().verify());

  let found: boolean[] = [];

  async function render(events: object[]) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    const fixture = TestBed.createComponent(UpcomingEventsComponent);
    found = [];
    fixture.componentInstance.found.subscribe((some) => found.push(some));
    fixture.componentRef.setInput('slug', 'naron');
    (await expectRequest(LIST)).flush(events);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('sen eventos, non hai sección (e dío: a portada non ofrece «Mostrar: Eventos»)', async () => {
    const el = await render([]);
    expect(el.querySelector('[data-home-events]')).toBeNull();
    expect(found).toEqual([false]);
  });

  it('cun filtro: só os das instalacións que pasan e que se fan ese día (as actividades, entre o seu inicio e o seu fin)', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    const fixture = TestBed.createComponent(UpcomingEventsComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.componentRef.setInput('day', '2099-01-15');
    fixture.componentRef.setInput('counted', true);
    (await expectRequest(LIST)).flush([publicEvent(), publicActivity()]);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const ids = () => [...el.querySelectorAll('[data-event-card]')].map((c) => c.getAttribute('data-event-card'));
    expect(ids()).toEqual(['a1']);
    expect(el.querySelector('#eventos')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Eventos e actividades · 1');
    fixture.componentRef.setInput('day', '2099-12-20');
    fixture.componentRef.setInput('facilityIds', new Set(['f1']));
    await fixture.whenStable();
    expect(ids()).toEqual(['e1']);
    fixture.componentRef.setInput('facilityIds', new Set(['f9']));
    await fixture.whenStable();
    expect(el.querySelector('[data-home-events]')).toBeNull();
  });

  it('con eventos, dío ao cargar', async () => {
    await render([publicEvent()]);
    expect(found).toEqual([true]);
  });

  it('cada tarxeta: data (ou horario e datas), lugar, nome, ligazón á ficha e, no pé, o chip das prazas', async () => {
    const el = await render([
      publicActivity(),
      publicEvent(),
      publicEvent({ id: 'e2', free: 0 }),
      publicEvent({ id: 'e3', free: undefined }),
      publicEvent({ id: 'e4', free: 2 }),
    ]);
    const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
    expect(el.querySelector('#eventos')?.textContent?.trim()).toBe('Eventos e actividades');
    const cards = [...el.querySelectorAll('furar-media-card')];
    expect(cards).toHaveLength(5);
    const [activity, day, full, unlimited, last] = cards;
    expect(text(activity.querySelector('.f-media-card__when'))).toBe('Luns e mércores · 17:00');
    expect(text(activity.querySelector('[data-card-period]'))).toBe('Do 6 out. ao 24 xuño');
    expect(text(activity.querySelector('.f-media-card__place'))).toBe('Piscina municipal');
    expect(text(activity.querySelector('.f-media-card__foot [data-seats]'))).toBe('5 prazas libres');
    expect(text(day.querySelector('.f-media-card__when'))).toBe('20 de decembro · 11:00');
    expect(day.querySelector('[data-card-period]')).toBeNull();
    expect(text(day.querySelector('.f-media-card__place'))).toBe('Pista de atletismo');
    expect(day.querySelector('a')?.getAttribute('href')).toBe('/naron/eventos/e1');
    expect(text(day.querySelector('.f-media-card__foot [data-seats]'))).toBe('38 prazas libres');
    expect(full.querySelector('[data-seats]')?.getAttribute('data-tone')).toBe('muted');
    expect(text(full.querySelector('[data-seats]'))).toBe('Completa');
    expect(unlimited.querySelector('[data-seats]')).toBeNull();
    expect(text(last.querySelector('[data-seats]'))).toBe('Últimas 2 prazas');
  });

  it('na portada común, os de varios concellos nun só carrusel, por data, co concello; un que falla non quita os demais', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    const fixture = TestBed.createComponent(UpcomingEventsComponent);
    found = [];
    fixture.componentInstance.found.subscribe((some) => found.push(some));
    fixture.componentRef.setInput('municipalities', [
      { slug: 'naron', name: 'Narón' },
      { slug: 'cambre', name: 'Cambre' },
      { slug: 'ferrol', name: 'Ferrol' },
    ]);
    (await expectRequest(LIST)).flush([publicEvent()]);
    (await expectRequest('/api/v1/municipalities/cambre/events')).flush([
      publicActivity({ id: 'a-cambre', facility: undefined, place: 'Praia' }),
    ]);
    flushProblem(await expectRequest('/api/v1/municipalities/ferrol/events'), 500, 'common.internal');
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const cards = [...el.querySelectorAll('furar-media-card')];
    // A actividade de Cambre empeza antes.
    expect(cards.map((c) => c.querySelector('a')?.getAttribute('href'))).toEqual([
      '/cambre/eventos/a-cambre',
      '/naron/eventos/e1',
    ]);
    expect(cards.map((c) => c.querySelector('.f-media-card__place')?.textContent?.trim())).toEqual([
      'Praia · Cambre',
      'Pista de atletismo · Narón',
    ]);
    expect(found).toEqual([true]);
  });
});
