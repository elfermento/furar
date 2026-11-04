// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../../testing/deployment';
import { municipality } from '../../../../testing/fixtures';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { loadLazySymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS as SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { PublicFacilityPage } from './public-facility.page';
import type { PublicFacility } from './public-facility.store';

const URL = '/api/v1/municipalities/naron/public/facilities/f1';
const NOTES_URL = '/api/v1/municipalities/naron/payment-notes';
const NOTES = {
  payment: { gl: 'No mostrador, ao chegar', es: 'En el mostrador, al llegar' },
  paymentDefault: true,
  refund: { gl: 'O concello dirache como cha devolve', es: 'El ayuntamiento te dirá cómo te lo devuelve' },
  refundDefault: true,
};
const RULES = {
  minAdvanceHours: 2,
  maxAdvanceDays: 7,
  maxSlots: 2,
  capCount: 2,
  capPeriod: 'WEEK',
  cancelHours: 24,
} as const;
type Use = PublicFacility['uses'][number];

/** Un tramo de `date` en minutos; os instantes, en UTC (abonda para os tests). */
function slot(date: string, start: number, end: number) {
  const at = (minute: number) => new Date(new Date(`${date}T00:00:00Z`).getTime() + minute * 60_000).toISOString();
  return { startMinute: start, endMinute: end, startsAt: at(start), endsAt: at(end) };
}

const DATES = ['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'];

function detail(overrides: Partial<PublicFacility> = {}): PublicFacility {
  return {
    id: 'f1',
    name: 'Campo de fútbol 1 de O Alto',
    address: {
      street: 'Rúa Buxo',
      streetNumber: '16',
      postalCode: '15572',
      locality: 'Narón',
      latitude: 43.51,
      longitude: -8.19,
    },
    description: { gl: 'Céspede artificial.', es: 'Césped artificial.' },
    temporarilyClosed: false,
    bookable: true,
    closures: [],
    images: [
      {
        kind: 'DRAWING',
        id: 'football_field',
        alt: { gl: 'Debuxo do campo de fútbol', es: 'Dibujo del campo de fútbol' },
        width: 1200,
        height: 800,
        large: '/x/large',
        thumb: '/x/thumb',
      },
    ],
    uses: [
      {
        id: 'u1',
        name: 'Fútbol 7',
        rentalMode: 'PARTS',
        parts: [
          { id: 'p1', name: 'Metade norte' },
          { id: 'p2', name: 'Metade sur' },
        ],
        slotMinutes: 60,
        availability: 'ALWAYS',
        periods: [],
        weekdays: 127,
        price: { kind: 'NONE', per: 'BOOKING' },
        rules: {},
      },
      {
        id: 'u2',
        name: 'Campo enteiro',
        rentalMode: 'WHOLE',
        parts: [],
        slotMinutes: 90,
        availability: 'ALWAYS',
        periods: [],
        weekdays: 127,
        price: { kind: 'NONE', per: 'BOOKING' },
        rules: {},
      },
    ],
    amenities: ['CHANGING_ROOMS', 'PARKING'],
    phone: '981 38 21 00',
    email: 'deportes@naron.example',
    openingHours: DATES.map((date, i) => ({
      date,
      slots: i < 5 ? [slot(date, 540, 840), slot(date, 960, 1380)] : i === 5 ? [slot(date, 540, 1260)] : [],
    })),
    ...overrides,
  };
}

describe('Ficha pública da instalación', () => {
  afterEach(() => backend().verify());

  async function render(response: PublicFacility | 'missing' = detail(), providers: unknown[] = []) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), ...providers] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(PublicFacilityPage);
    fixture.componentRef.setInput('id', 'f1');
    const el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    (await expectRequest(NOTES_URL)).flush(NOTES);
    const request = await expectRequest(URL);
    if (response === 'missing') flushProblem(request, 404, 'facility.not-found');
    else request.flush(response);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('h1')).not.toBeNull();
    });
    return { fixture, el };
  }

  it('nome, imaxe, descrición, usos sen prezo, servizos e mapa; tarxeta con enderezo, horario e contacto', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/naron/instalacions');
    expect(el.querySelector('.f-public__image')?.getAttribute('alt')).toBe('Debuxo do campo de fútbol');
    expect(el.textContent).toContain('Céspede artificial.');
    const uses = [...el.querySelectorAll('[data-public-uses] li')].map((li) => [
      li.querySelector('h3')?.textContent?.trim(),
      li.querySelector('p')?.textContent?.trim(),
    ]);
    expect(uses).toEqual([
      ['Fútbol 7', 'Metade norte ou Metade sur · 60 minutos'],
      ['Campo enteiro', 'Enteira · 90 minutos'],
    ]);
    expect(el.querySelector('[data-public-uses]')?.textContent).not.toContain('€');
    expect([...el.querySelectorAll('.f-public__amenities li')].map((li) => li.textContent?.trim())).toEqual([
      'Vestiarios',
      'Aparcadoiro',
    ]);
    expect(el.querySelector('furar-map')).not.toBeNull();
    expect(el.querySelector('[data-public-address]')?.textContent).toBe('Rúa Buxo, 16 · 15572 Narón');
    const links = [...el.querySelectorAll<HTMLAnchorElement>('.f-public-card__link')];
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['tel:981382100', 'mailto:deportes@naron.example']);
    // Sen prezos, normas nin normas da instalación: non hai «Antes de reservar».
    expect(el.querySelector('[data-public-before]')).toBeNull();
    // «Reservar» ao pé da tarxeta fixa, cara á páxina de reservar.
    const reserve = el.querySelector('aside furar-public-facility-card [data-public-reserve]');
    expect(reserve?.textContent?.trim()).toBe('Reservar');
    expect(reserve?.getAttribute('href')).toBe('/naron/instalacions/f1/reservar');
    await expectNoAxeViolations(el);
  });

  it('sen mapa no despregue: sen «Como chegar»; o enderezo, na tarxeta', async () => {
    const { el } = await render(detail(), [provideDeploymentSettings(NO_MAP)]);
    expect(el.querySelector('furar-map')).toBeNull();
    expect(el.querySelector('#public-map')).toBeNull();
    expect(el.querySelector('[data-public-address]')?.textContent).toBe('Rúa Buxo, 16 · 15572 Narón');
    await expectNoAxeViolations(el);
  });

  it('o horario: hoxe só e en negriña; os días seguidos co mesmo horario, xuntos', async () => {
    const { el } = await render();
    const terms = [...el.querySelectorAll('[data-public-week] dt')].map((dt) => dt.textContent?.trim());
    expect(terms).toEqual(['Hoxe, luns', 'Martes a venres', 'Sábado', 'Domingo']);
    expect(el.querySelector('[data-public-week] dt')?.classList).toContain('f-public-card__today');
    const hours = [...el.querySelectorAll('[data-public-week] dd')].map((dd) =>
      [...dd.querySelectorAll('span')].map((s) => s.textContent?.trim()),
    );
    expect(hours).toEqual([
      ['9:00 – 14:00', '16:00 – 23:00'],
      ['9:00 – 14:00', '16:00 – 23:00'],
      ['9:00 – 21:00'],
      ['Pechado'],
    ]);
    expect(el.querySelector('[data-public-now]')?.textContent).toMatch(/Aberto agora|Pechado agora/);
  });

  it('pechada temporalmente: etiqueta ao lado do nome; en castelán, os textos e o idioma da descrición', async () => {
    const { fixture, el } = await render(detail({ temporarilyClosed: true }));
    expect(el.querySelector('.f-public__title furar-tag')?.textContent).toContain('Pechada temporalmente');
    TestBed.inject(TranslocoService).setActiveLang('es');
    await fixture.whenStable();
    expect(el.textContent).toContain('Qué se puede reservar');
  });

  it('se non se ve (borrador, de baixa ou doutro concello): «non está dispoñible» como h1', async () => {
    const { el } = await render('missing');
    expect(el.querySelector('h1')?.textContent).toContain('Esta instalación non está dispoñible');
    expect(el.querySelector('furar-status-state button')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('erro ao cargar: «Tentar de novo» volve pedir', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(PublicFacilityPage);
    fixture.componentRef.setInput('id', 'f1');
    const el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushProblem(await expectRequest(URL), 500, 'common.internal');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('h1')?.textContent).toContain('Non se puido cargar a instalación');
    });
    el.querySelector<HTMLButtonElement>('furar-status-state button')!.click();
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(URL)).flush(detail({ description: undefined, phone: undefined, email: undefined }));
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('h1')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    });
    expect(el.querySelector('#public-description')).toBeNull();
    expect(el.textContent).not.toContain('Contacto');
  });

  function priced(overrides: Partial<Use>[]): PublicFacility {
    const base = detail().uses;
    return detail({ uses: overrides.map((o, i) => ({ ...base[i % base.length], id: `u${i + 1}`, ...o })) });
  }
  const before = (el: HTMLElement) =>
    [...el.querySelectorAll<HTMLElement>('[data-public-before] li')].map((li) => [
      li.dataset['before'],
      li.querySelector('b')?.textContent?.trim(),
      [...li.querySelectorAll('span.block')].map((s) => s.textContent?.trim()),
    ]);

  it('prezo en cada fila: importe, por persoa nas prazas, «Gratis» en chip e nada sen prezo', async () => {
    const { el } = await render(
      priced([
        { name: 'Fútbol 7', price: { kind: 'PAID', amountCents: 3000, per: 'BOOKING' } },
        { name: 'Campo enteiro', price: { kind: 'FREE', per: 'BOOKING' } },
        {
          name: 'Ximnasio',
          rentalMode: 'SEATS',
          capacity: 20,
          price: { kind: 'PAID', amountCents: 350, per: 'PERSON' },
        },
        { name: 'Pista', price: { kind: 'NONE', per: 'BOOKING' } },
      ]),
    );
    const prices = [...el.querySelectorAll('[data-public-uses] li')].map(
      (li) => li.querySelector('[data-use-price]')?.textContent?.replace(/\s+/g, ' ').trim() ?? null,
    );
    expect(prices).toEqual(['30,00 €', 'Gratis', '3,50 € por persoa', null]);
    expect(el.querySelector('[data-public-uses] furar-tag')?.getAttribute('data-tone')).toBe('info');
    await expectNoAxeViolations(el);
  });

  it('«Antes de reservar»: só as normas con valor, coa súa icona, o pago e as normas da instalación', async () => {
    const { el, fixture } = await render({
      ...priced([
        { slotMinutes: 60, rules: { ...RULES }, price: { kind: 'PAID', amountCents: 3000, per: 'BOOKING' } },
        { slotMinutes: 60, rules: { ...RULES }, price: { kind: 'PAID', amountCents: 6000, per: 'BOOKING' } },
      ]),
      houseRules: { gl: 'Calzado de herba artificial.', es: 'Calzado de hierba artificial.' },
    });
    expect(before(el)).toEqual([
      ['advance', 'Cando reservar', ['De 2 horas a 7 días antes']],
      ['duration', 'Duración', ['Ata 2 horas seguidas']],
      ['cap', 'Límite', ['2 reservas por semana']],
      ['cancel', 'Cancelación', ['Gratis ata 24 horas antes']],
      ['payment', 'Pago', ['No mostrador, ao chegar']],
    ]);
    // Estas iconas non son do marco: chegan co chunk de lazy-symbols.
    await loadLazySymbols();
    await fixture.whenStable();
    const icons = [...el.querySelectorAll('[data-public-before] li furar-icon path')].map((p) => p.getAttribute('d'));
    expect(icons).toEqual([
      SYMBOLS['calendar-month'],
      SYMBOLS.hourglass,
      SYMBOLS['event-repeat'],
      SYMBOLS['event-busy'],
      SYMBOLS.euro,
    ]);
    expect(el.querySelector('[data-house-rules] h3')?.textContent).toContain('Normas da instalación');
    expect(el.querySelector('[data-house-rules] p')?.textContent).toBe('Calzado de herba artificial.');
    // O pago sae unha soa vez na pantalla.
    expect(el.textContent?.split('No mostrador, ao chegar')).toHaveLength(2);
    await expectNoAxeViolations(el);
    await TestBed.inject(LanguageService).use('es');
    await fixture.whenStable();
    expect(before(el)[4]).toEqual(['payment', 'Pago', ['En el mostrador, al llegar']]);
  });

  it('sen normas, «Antes de reservar» só leva o pago; sen usos de pago, nin iso', async () => {
    const { el } = await render(priced([{ price: { kind: 'PAID', amountCents: 3000, per: 'BOOKING' } }, {}]));
    expect(before(el)).toEqual([['payment', 'Pago', ['No mostrador, ao chegar']]]);
  });

  it('se os usos din cousas distintas, unha liña por uso co seu nome', async () => {
    const { el } = await render(
      priced([
        { name: 'Fútbol 7', slotMinutes: 60, rules: { maxSlots: 2 } },
        { name: 'Campo enteiro', slotMinutes: 90, rules: { maxSlots: 1 } },
      ]),
    );
    expect(before(el)).toEqual([
      ['duration', 'Duración', ['Fútbol 7: Ata 2 horas seguidas', 'Campo enteiro: Ata 1 h 30 min seguidas']],
    ]);
  });

  it('«Próximos peches» baixo a tarxeta: datas, todo o día ou as horas e o motivo ou o festivo', async () => {
    const { el } = await render(
      detail({
        closures: [
          { from: '2026-11-10', to: '2026-11-10', startMinute: 600, endMinute: 720, reason: 'Mantemento' },
          { from: '2026-12-08', to: '2026-12-08', holiday: { gl: 'Inmaculada Concepción', es: 'Inmaculada' } },
          { from: '2026-12-12', to: '2026-12-14', reason: 'Torneo de Nadal' },
          { from: '2026-12-31', to: '2027-01-02' },
        ],
      }),
    );
    const card = el.querySelector<HTMLElement>('[data-public-closures]')!;
    expect(card.querySelector('h2')?.textContent).toContain('Próximos peches');
    expect([...card.querySelectorAll('li')].map((li) => li.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '10 nov. 10:00 – 12:00 Mantemento',
      '8 dec. Pechado Inmaculada Concepción',
      '12–14 dec. Pechado Torneo de Nadal',
      '31 dec. – 2 xan. Pechado',
    ]);
    await expectNoAxeViolations(el);
  });

  it('sen peches próximos non se pinta a lista', async () => {
    const { el } = await render(detail({ closures: [] }));
    expect(el.querySelector('[data-public-closures]')).toBeNull();
  });

  it('sen reserva por internet, a tarxeta non leva «Reservar»', async () => {
    const { el } = await render(detail({ bookable: false }));
    expect(el.querySelector('[data-public-reserve]')).toBeNull();
    expect([...el.querySelectorAll('a, button')].some((n) => n.textContent?.includes('Reservar'))).toBe(false);
  });
});
