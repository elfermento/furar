// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../../../testing/deployment';
import { municipality } from '../../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import { addDays, localToday } from '../../../../shared/ui/calendar/local-date';
import { LIST_FACILITIES, LIST_SLOTS, availabilityPage } from './public-list.fixture';
import { PublicListPage } from './public-list.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const LIST = '/api/v1/municipalities/naron/public/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/municipalities/naron/public/availability';

describe('Listado público de instalacións', () => {
  afterEach(() => backend().verify());

  async function render(url = '/naron/instalacions', slots = LIST_SLOTS, extra: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        ...extra,
        provideRouter([
          { path: 'naron/instalacions', component: PublicListPage },
          { path: 'naron/instalacions/:id', component: StubPage },
          { path: 'naron/instalacions/:id/reservar', component: StubPage },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    const date = free.request.params.get('date') ?? '';
    free.flush(availabilityPage(date, slots));
    (await expectRequest(LIST)).flush(LIST_FACILITIES);
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('.f-plist__count')).not.toBeNull();
    });
    return { harness, el, date, settle };
  }

  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('furar-public-list-card')];
  const chips = (el: HTMLElement) => [...el.querySelectorAll<HTMLButtonElement>('.f-plist__filters button')];
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');

  it('buscador, chips cos deportes, recuento, Lista · Mapa e tarxetas con ocos e horas; pasa axe', async () => {
    const { el, date } = await render();
    expect(date).toBe(localToday());
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Instalacións');
    expect(el.querySelector('form[role="search"]')).not.toBeNull();
    // Os deportes das instalacións, sen repetir e en orde; ningún marcado.
    expect(chips(el).map((c) => [c.textContent?.trim(), c.getAttribute('aria-pressed')])).toEqual([
      ['Bádminton', 'false'],
      ['Fútbol 11', 'false'],
      ['Fútbol 7', 'false'],
      ['Fútbol sala', 'false'],
      ['Pádel', 'false'],
      ['Cuberta', 'false'],
    ]);
    expect(el.querySelector('.f-plist__filters')?.getAttribute('role')).toBe('group');
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('3 instalacións');
    const views = [...el.querySelectorAll<HTMLButtonElement>('[furarSegmented] button')];
    expect(views.map((b) => [b.textContent?.trim(), b.getAttribute('aria-pressed')])).toEqual([
      ['Lista', 'true'],
      ['Mapa', 'false'],
    ]);
    // Tarxeta con ocos: chip enriba da imaxe e as tres primeiras horas, á páxina de reservar.
    const [campo, padel, pavillon] = cards(el);
    expect(campo.querySelector('.f-media-card__image furar-tag')?.textContent?.trim()).toBe('2 ocos hoxe');
    const times = [...campo.querySelectorAll<HTMLAnchorElement>('.f-slot-links a')];
    expect(times.map((a) => a.textContent?.trim())).toEqual(['19:00', '21:00']);
    expect(times[0].getAttribute('href')).toBe(`/naron/instalacions/f-campo/reservar?data=${date}&hora=19:00`);
    expect(campo.querySelector('h3 a')?.getAttribute('href')).toBe('/naron/instalacions/f-campo');
    expect(padel.querySelectorAll('.f-slot-links a')).toHaveLength(3);
    expect(padel.querySelector('.f-slot-links a')?.textContent?.trim()).toBe('09:30');
    // Sen ocos: sen chip nin horas.
    expect(pavillon.querySelector('furar-tag[cardBadge]')).toBeNull();
    expect(pavillon.querySelector('.f-slot-links')).toBeNull();
    await expectNoAxeViolations(el);
  }, 20_000);

  it('os filtros viven no URL: deporte e día ao entrar, e os chips cámbiano', async () => {
    const tomorrow = addDays(localToday(), 1);
    const { el, date, settle } = await render(`/naron/instalacions?deporte=padel&data=${tomorrow}`);
    expect(date).toBe(tomorrow);
    expect(
      chips(el)
        .find((c) => c.textContent?.includes('Pádel'))
        ?.getAttribute('aria-pressed'),
    ).toBe('true');
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('1 instalación');
    expect(cards(el)[0].querySelector('furar-tag[cardBadge]')?.textContent?.trim()).toBe('4 ocos mañá');
    // O buscador abre co deporte e o día do URL.
    expect(el.querySelector<HTMLInputElement>('input[role="combobox"]')?.value).toBe('Pádel');
    expect(el.querySelector('.f-date-field__button')?.getAttribute('aria-label')).toBe('Día, Mañá');

    chips(el)
      .find((c) => c.textContent?.includes('Fútbol sala'))!
      .click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['padel', 'futsal']);
    });
    expect(query().get('data')).toBe(tomorrow);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('2 instalacións');

    chips(el)
      .find((c) => c.textContent?.includes('Pádel'))!
      .click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['futsal']);
    });
    expect(cards(el).map((c) => c.querySelector('h3')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
  });

  it('«Cuberta»: deixa só as que din que son cubertas, vive no URL e sae con «Quitar os filtros»', async () => {
    const { el, settle } = await render('/naron/instalacions?cuberta=si&deporte=padel');
    const covered = el.querySelector<HTMLButtonElement>('[data-covered]')!;
    expect(covered.getAttribute('aria-pressed')).toBe('true');
    expect(el.textContent).toContain('Ningunha instalación con estes filtros');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Quitar os filtros'))!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(3);
    });
    expect(query().has('cuberta')).toBe(false);
    el.querySelector<HTMLButtonElement>('[data-covered]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('cuberta')).toBe('si');
    });
    expect(cards(el).map((c) => c.querySelector('h3')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
  });

  it('sen o dato de cuberta en ningunha, non hai chip «Cuberta»', async () => {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), provideRouter([{ path: 'naron/instalacions', component: PublicListPage }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/instalacions');
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    free.flush(availabilityPage(free.request.params.get('date') ?? ''));
    (await expectRequest(LIST)).flush(LIST_FACILITIES.map((f) => ({ ...f, covered: undefined })));
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.querySelectorAll('furar-public-list-card')).toHaveLength(3);
    });
    expect(el.querySelector('[data-covered]')).toBeNull();
    expect(chips(el)).toHaveLength(5);
  });

  it('un deporte sen instalacións: aviso e «Quitar os filtros»', async () => {
    const { el, settle } = await render('/naron/instalacions?deporte=tenis');
    expect(el.textContent).toContain('Ningunha instalación con estes filtros');
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('0 instalacións');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Quitar os filtros'))!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(3);
    });
    expect(query().has('deporte')).toBe(false);
  });

  it('Mapa: unha marca por instalación, que leva á súa ficha; a lista é a alternativa', async () => {
    const { el, settle } = await render('/naron/instalacions?deporte=padel&deporte=football');
    [...el.querySelectorAll<HTMLButtonElement>('[furarSegmented] button')][1].click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('vista')).toBe('mapa');
    });
    // O punto vén no enderezo da lista: o mapa non pide as fichas (`verify` no `afterEach`).
    await vi.waitFor(
      async () => {
        await settle();
        expect(el.querySelectorAll('.leaflet-container .f-map__marker')).toHaveLength(2);
      },
      { timeout: 5000 },
    );
    expect(cards(el)).toHaveLength(0);
    const section = el.querySelector('.f-plist__map');
    expect(section?.getAttribute('aria-label')).toBe('Mapa das instalacións');
    expect(section?.textContent).toContain('vista Lista');
    expect(el.querySelector('.f-map__canvas')?.getAttribute('aria-hidden')).toBe('true');
    const markers = [...el.querySelectorAll<HTMLElement>('.f-map__marker')];
    expect(markers.map((m) => m.getAttribute('title')).sort()).toEqual([
      'Campo de fútbol 1 de O Alto',
      'Pista de pádel 1 de O Alto',
    ]);
    // Non son parada de tabulador.
    expect(markers.every((m) => !m.hasAttribute('tabindex'))).toBe(true);
    markers.find((m) => m.title.startsWith('Pista'))!.click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/instalacions/f-padel'));
  }, 20_000);

  it('sen mapa no despregue: sen Lista · Mapa e «vista=mapa» dá a lista', async () => {
    const { el } = await render('/naron/instalacions?vista=mapa', LIST_SLOTS, [provideDeploymentSettings(NO_MAP)]);
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(cards(el)).toHaveLength(3);
    await expectNoAxeViolations(el);
  });

  it('se falla a carga, erro con «Tentar de novo»; se fallan os ocos, tarxetas sen chip', async () => {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), provideRouter([{ path: 'naron/instalacions', component: PublicListPage }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/instalacions');
    const el = harness.routeNativeElement!;
    flushProblem(await expectRequest(AVAILABILITY), 500, 'common.internal');
    flushProblem(await expectRequest(LIST), 500, 'common.internal');
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.textContent).toContain('Non se puideron cargar as instalacións');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    (await expectRequest(LIST)).flush(LIST_FACILITIES);
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.querySelectorAll('furar-public-list-card')).toHaveLength(3);
    });
    expect(el.querySelector('furar-tag[cardBadge]')).toBeNull();
  });
});
