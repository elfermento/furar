// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpRequest } from '@angular/common/http';
import type { TestRequest } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import type { BookingCalendarResponse } from './bookings-calendar.store';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { pickOption, selectText } from '../../../../testing/select';
import {
  BASE,
  FACILITIES,
  FACILITIES_URL,
  detail,
  dialog,
  row,
  text,
  useMonday,
} from '../../../../testing/manage-bookings';
import { BookingsCalendarPage } from './bookings-calendar.page';

@Component({ selector: 'furar-test-bookings', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const CALENDAR_URL = `${BASE}/bookings/calendar`;
const calendarFor =
  (view: string, date: string, facilityId: string | null = null) =>
  (r: HttpRequest<unknown>) =>
    r.url === CALENDAR_URL &&
    r.params.get('view') === view &&
    r.params.get('date') === date &&
    r.params.get('facilityId') === facilityId;

function calendar(overrides: Partial<BookingCalendarResponse> = {}): BookingCalendarResponse {
  const empty = { blocks: [], days: [], hours: [], openings: [] };
  return { view: 'DAY', from: '2026-10-26', to: '2026-10-26', ...empty, ...overrides };
}
const OPEN_F1 = { facilityId: 'f1', date: '2026-10-26', startMinute: 540, endMinute: 870 };
const ANA = row();
const BRAIS = row({
  id: 'b5',
  holderName: 'Brais Couce',
  facilityId: 'f2',
  facilityName: 'Campo de fútbol 1',
  useName: 'Campo enteiro',
  start: '2026-10-26T09:00:00Z',
  end: '2026-10-26T10:30:00Z',
  paymentStatus: 'PAID',
});
const CANCELLED = row({ id: 'b9', holderName: 'Cancelada', status: 'CANCELLED_BY_PERSON' });

async function render() {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(fakeSession()),
      provideRouter([
        { path: 'naron/xestion/calendario', component: BookingsCalendarPage },
        { path: 'naron/xestion/reservas', component: StubPage },
      ]),
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create('/naron/xestion/calendario');
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === FACILITIES_URL)).flush(FACILITIES);
  const day = calendar({ blocks: [ANA, BRAIS, CANCELLED], openings: [OPEN_F1] });
  (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(day);
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('[data-cal-block]')).not.toBeNull();
  });
  return { el, settle };
}

const blocks = (el: Element) => [...el.querySelectorAll<HTMLButtonElement>('.f-bcal__block')];
const label = (el: Element) => text(el.querySelector('[data-period-label]'));
const click = (el: Element | null) => (el as HTMLElement).click();

describe('Calendario das reservas (D2-16)', () => {
  useMonday();

  it('día de todas: columna por instalación, bloques con nome enteiro en orde de lectura; as canceladas non ocupan; axe', async () => {
    const { el } = await render();
    expect(text(el.querySelector('h1'))).toBe('Calendario');
    expect(el.querySelector('[data-calendar-back]')?.getAttribute('href')).toBe('/naron/xestion/reservas');
    expect(el.querySelector('[data-mode="DAY"]')?.getAttribute('aria-checked')).toBe('true');
    expect(selectText(el.querySelector('[data-calendar-facility]')!)).toBe('Todas as instalacións');
    expect(label(el)).toBe('luns, 26 de outubro');
    expect([...el.querySelectorAll('.f-bcal__head[data-column]')].map((h) => text(h))).toEqual([
      'Pista de pádel 1',
      'Campo de fútbol 1',
    ]);
    expect(blocks(el).map((b) => b.getAttribute('aria-label'))).toEqual([
      'Brais Couce, Campo enteiro, Campo de fútbol 1, 10:00–11:30, Pagada',
      'Ana Rey, Pista completa, Pista de pádel 1, 19:00–20:00, Pendente',
    ]);
    expect(text(blocks(el)[1])).toBe('Ana Rey Pista completa');
    expect(el.querySelector('[data-booking="b9"]')).toBeNull();
    // Horas de 9 a 23 (14 filas): Brais ás 10:00 vai unha fila por debaixo da cabeceira.
    expect(el.querySelectorAll('.f-bcal__hour')).toHaveLength(14);
    // Raiadas as horas sen tramo aberto: a pista abre de 9:00 a 14:30 (fóra, 8); o campo non abre (14).
    expect(el.querySelectorAll('.f-bcal__cell--closed')).toHaveLength(22);
    expect(blocks(el)[0].style.top).toBe('7.25rem');
    expect(el.querySelector('[data-period-prev]')?.getAttribute('aria-label')).toBe('Día anterior');
    await expectNoAxeViolations(el);
  });

  it('frechas e calendario en capa: calquera día, semana ou mes', async () => {
    const { el, settle } = await render();
    click(el.querySelector('[data-period-next]'));
    (await expectRequest(calendarFor('DAY', '2026-10-27'))).flush(calendar({ from: '2026-10-27', to: '2026-10-27' }));
    await settle();
    expect(label(el)).toBe('martes, 27 de outubro');
    expect(blocks(el)).toHaveLength(0);
    expect(text(el.querySelector('.f-bcal__main > [role="status"]'))).toBe('Non hai reservas neste período.');

    const trigger = el.querySelector<HTMLButtonElement>('[data-period-label]')!;
    trigger.click();
    await settle();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    const pop = el.querySelector('[data-period-pop]')!;
    expect(pop.querySelector('[aria-selected="true"]')?.getAttribute('data-date')).toBe('2026-10-27');
    // As frechas propias do mes: o ano pasado tamén vale.
    for (let i = 0; i < 12; i++) click(pop.querySelector('.f-calendar__nav'));
    await settle();
    click(pop.querySelector('[data-date="2025-10-15"]'));
    (await expectRequest(calendarFor('DAY', '2025-10-15'))).flush(calendar({ from: '2025-10-15', to: '2025-10-15' }));
    await settle();
    expect(el.querySelector('[data-period-pop]')).toBeNull();
    expect(label(el)).toBe('mércores, 15 de outubro');

    // Esc e picar fóra pechan a capa.
    trigger.click();
    await settle();
    el.querySelector('[data-period-pop]')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    await settle();
    expect(el.querySelector('[data-period-pop]')).toBeNull();
    trigger.click();
    await settle();
    el.querySelector('h1')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(el.querySelector('[data-period-pop]')).toBeNull();

    // Mes: ábrese nese mes sen día escollido; a semana marca os sete días.
    click(el.querySelector('[data-mode="MONTH"]'));
    (await expectRequest(calendarFor('MONTH', '2025-10-15'))).flush(
      calendar({ view: 'MONTH', from: '2025-10-01', to: '2025-10-31' }),
    );
    await settle();
    expect(label(el)).toBe('Outubro de 2025');
    trigger.click();
    await settle();
    expect(el.querySelector('[data-period-pop] [aria-selected="true"]')).toBeNull();
    expect(el.querySelector('[data-period-pop] [data-date="2025-10-01"]')).not.toBeNull();
    trigger.click();
    await settle();
    click(el.querySelector('[data-period-prev]'));
    (await expectRequest(calendarFor('MONTH', '2025-09-15'))).flush(
      calendar({ view: 'MONTH', from: '2025-09-01', to: '2025-09-30' }),
    );
    await settle();
    expect(label(el)).toBe('Setembro de 2025');
    expect(el.querySelector('[data-period-prev]')?.getAttribute('aria-label')).toBe('Mes anterior');

    click(el.querySelector('[data-mode="WEEK"]'));
    (await expectRequest(calendarFor('WEEK', '2025-09-15'))).flush(
      calendar({ view: 'WEEK', from: '2025-09-15', to: '2025-09-21' }),
    );
    await settle();
    expect(label(el)).toBe('Semana do 15 ao 21 de setembro');
    trigger.click();
    await settle();
    expect(
      [...el.querySelectorAll('[data-period-pop] [aria-selected="true"]')].map((d) => d.getAttribute('data-date')),
    ).toEqual(['2025-09-15', '2025-09-16', '2025-09-17', '2025-09-18', '2025-09-19', '2025-09-20', '2025-09-21']);
  });

  it('escoller unha instalación no día pasa á semana con bloques; «Todas» na semana, contas por hora', async () => {
    const { el, settle } = await render();
    await pickOption(el.querySelector('[data-calendar-facility]')!, 'Pista de pádel 1', settle);
    const wed = row({ id: 'b6', start: '2026-10-28T18:00:00Z', end: '2026-10-28T19:00:00Z', paymentStatus: 'NONE' });
    (await expectRequest(calendarFor('WEEK', '2026-10-26', 'f1'))).flush(
      calendar({ view: 'WEEK', from: '2026-10-26', to: '2026-11-01', blocks: [ANA, wed] }),
    );
    await settle();
    expect(el.querySelector('[data-mode="WEEK"]')?.getAttribute('aria-checked')).toBe('true');
    expect(label(el)).toBe('Semana do 26 de outubro ao 1 de novembro');
    const heads = [...el.querySelectorAll('.f-bcal__head[data-column]')];
    expect(heads.map((h) => text(h))).toEqual(['lun 26', 'mar 27', 'mér 28', 'xov 29', 'ven 30', 'sáb 31', 'dom 1']);
    expect(heads[0].classList).toContain('f-bcal__head--today');
    expect(blocks(el).map((b) => b.getAttribute('aria-label'))).toEqual([
      'Ana Rey, Pista completa, Pista de pádel 1, luns 26, 19:00–20:00, Pendente',
      'Ana Rey, Pista completa, Pista de pádel 1, mércores 28, 19:00–20:00, Gratis',
    ]);

    await pickOption(el.querySelector('[data-calendar-facility]')!, 'Todas as instalacións', settle);
    (await expectRequest(calendarFor('WEEK', '2026-10-26'))).flush(
      calendar({
        view: 'WEEK',
        from: '2026-10-26',
        to: '2026-11-01',
        hours: [
          { date: '2026-10-26', hour: 19, count: 3 },
          { date: '2026-10-27', hour: 10, count: 1 },
        ],
      }),
    );
    await settle();
    expect(el.querySelector('[data-mode="WEEK"]')?.getAttribute('aria-checked')).toBe('true');
    expect(blocks(el)).toHaveLength(0);
    const counts = [...el.querySelectorAll('.f-bcal__count')];
    expect(counts.map((c) => [text(c), c.getAttribute('aria-label')])).toEqual([
      ['3 reservas', '3 reservas, luns 26, 19:00'],
      ['1 reserva', '1 reserva, martes 27, 10:00'],
    ]);
    await expectNoAxeViolations(el);
  });

  it('vista rápida: por hora de inicio, ata catro, abre a tarxeta e «+N · Ver o día»; pecha con Esc, fóra e ao desprazar', async () => {
    const { el, settle } = await render();
    click(el.querySelector('[data-mode="MONTH"]'));
    (await expectRequest(calendarFor('MONTH', '2026-10-26'))).flush(
      calendar({ view: 'MONTH', from: '2026-10-01', to: '2026-10-31', days: [{ date: '2026-10-26', count: 5 }] }),
    );
    await settle();
    const day = el.querySelector<HTMLButtonElement>('[data-day="2026-10-26"]')!;
    // Nome: a data enteira e a conta en palabras (o número curto do móbil vai oculto).
    expect(text(day.querySelector('.sr-only'))).toBe('luns, 26 de outubro de 2026');
    expect(text(day.querySelector('.f-bcal__mtext'))).toBe('5 reservas');
    expect(day.querySelector('.f-bcal__mnum')?.getAttribute('aria-hidden')).toBe('true');
    expect(day.getAttribute('aria-current')).toBe('date');
    expect(text(el.querySelector('[data-day="2026-10-27"]'))).toContain('Libre');

    day.click();
    const at = (id: string, name: string, hour: number) =>
      row({ id, holderName: name, start: `2026-10-26T${hour}:00:00Z`, end: `2026-10-26T${hour + 1}:00:00Z` });
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(
      calendar({
        blocks: [at('p1', 'Uxía Fraga', 17), at('p2', 'Sara Lema', 16), at('p3', 'Iago Sixto', 16), ANA, BRAIS],
      }),
    );
    await settle();
    const peek = document.querySelector<HTMLElement>('[data-peek]')!;
    expect(peek.getAttribute('aria-label')).toBe('luns 26');
    expect(document.activeElement).toBe(peek);
    expect([...peek.querySelectorAll('.f-bcal__peek-time')].map((t) => text(t))).toEqual([
      '10:00–11:30',
      '17:00–18:00',
      '18:00–19:00',
    ]);
    expect(text(peek.querySelector('[data-peek-open="b5"]'))).toBe(
      'Brais Couce Campo de fútbol 1 · Campo enteiro Pagada',
    );
    expect(text(peek.querySelector('[data-peek-day]'))).toBe('+1 · Ver o día');
    await expectNoAxeViolations(peek);

    // Esc pecha e o foco volve ao día.
    peek.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();
    expect(document.activeElement).toBe(day);

    // Desprazar pecha; picar fóra tamén.
    day.click();
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [ANA] }));
    await settle();
    document.dispatchEvent(new Event('scroll'));
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();
    day.click();
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [] }));
    await settle();
    expect(text(document.querySelector('[data-peek]'))).toContain('Sen reservas.');
    expect(text(document.querySelector('[data-peek-day]'))).toBe('Ver o día');
    el.querySelector('h1')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();

    // Unha fila abre a tarxeta (2/3 e 1/3).
    day.click();
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [ANA] }));
    await settle();
    click(document.querySelector('[data-peek-open="b1"]'));
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();
    expect(el.querySelector('.f-bcal__two')?.hasAttribute('data-detail')).toBe(true);
    expect(text(el.querySelector('[data-detail-card] [data-detail-facility] b'))).toBe('Pista de pádel 1');

    // «Ver o día» leva á vista do día.
    day.click();
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [ANA] }));
    await settle();
    click(document.querySelector('[data-peek-day]'));
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [ANA] }));
    await settle();
    expect(el.querySelector('[data-mode="DAY"]')?.getAttribute('aria-checked')).toBe('true');
  });

  it('a conta dunha hora abre só as reservas desa hora; un erro queda na vista rápida', async () => {
    const { el, settle } = await render();
    click(el.querySelector('[data-mode="WEEK"]'));
    (await expectRequest(calendarFor('WEEK', '2026-10-26'))).flush(
      calendar({
        view: 'WEEK',
        from: '2026-10-26',
        to: '2026-11-01',
        hours: [{ date: '2026-10-26', hour: 19, count: 1 }],
      }),
    );
    await settle();
    const count = el.querySelector<HTMLButtonElement>('.f-bcal__count')!;
    count.click();
    (await expectRequest(calendarFor('DAY', '2026-10-26'))).flush(calendar({ blocks: [ANA, BRAIS] }));
    await settle();
    const peek = document.querySelector('[data-peek]')!;
    expect(peek.getAttribute('aria-label')).toBe('luns 26');
    expect([...peek.querySelectorAll('[data-peek-open]')].map((r) => r.getAttribute('data-peek-open'))).toEqual(['b1']);
    // Premer de novo pecha.
    count.click();
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();
    count.click();
    flushProblem(await expectRequest(calendarFor('DAY', '2026-10-26')), 500, 'common.unexpected');
    await settle();
    expect(text(document.querySelector('[data-peek]'))).toContain('Non se puideron cargar as reservas.');
    click(document.querySelector('[data-peek-close]'));
    await settle();
    expect(document.querySelector('[data-peek]')).toBeNull();
  });

  it('un bloque abre a tarxeta; picar de novo, Esc ou fóra péchaa; cobrar volve pedir o calendario', async () => {
    const { el, settle } = await render();
    const ana = () => el.querySelector<HTMLButtonElement>('[data-booking="b1"]')!;
    ana().click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await settle();
    expect(ana().getAttribute('aria-pressed')).toBe('true');
    const card = el.querySelector('[data-detail-card]')!;
    expect(text(card.querySelector('[data-detail-total]'))).toBe('Total 8,00 € Pendente de pago');

    click(card.querySelector('[data-payment="PAID"]'));
    (await expectRequest(`${BASE}/bookings/b1/payment`)).flush(
      detail({ paymentStatus: 'PAID', paymentTargets: ['PENDING'], version: 4 }),
    );
    let reload: TestRequest | undefined;
    await vi.waitFor(async () => {
      await settle();
      reload = backend().expectOne(calendarFor('DAY', '2026-10-26'));
    });
    reload!.flush(calendar({ blocks: [row({ paymentStatus: 'PAID', version: 4 }), BRAIS] }));
    backend().expectNone((r) => r.url.endsWith('/bookings/summary'));
    await vi.waitFor(async () => {
      await settle();
      expect(ana().getAttribute('aria-label')).toBe('Ana Rey, Pista completa, Pista de pádel 1, 19:00–20:00, Pagada');
    });
    expect(el.querySelector('[data-detail-card]')).not.toBeNull();

    ana().click();
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();

    ana().click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await settle();
    // Regresión (D2-17): Esc co foco no bloque que abriu o detalle tamén o pecha.
    ana().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();

    ana().click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await settle();
    el.querySelector('h1')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();
  });

  it('se non carga, aviso con «Tentar de novo»', async () => {
    const { el, settle } = await render();
    click(el.querySelector('[data-period-next]'));
    flushProblem(await expectRequest(calendarFor('DAY', '2026-10-27')), 500, 'common.unexpected');
    await settle();
    expect(text(el.querySelector('furar-status-state'))).toContain('Non se puido cargar o calendario');
    click(el.querySelector('furar-status-state button'));
    (await expectRequest(calendarFor('DAY', '2026-10-27'))).flush(calendar({ from: '2026-10-27', to: '2026-10-27' }));
    await settle();
    expect(el.querySelector('furar-calendar-grid')).not.toBeNull();
  });

  describe('no móbil', () => {
    let original: PropertyDescriptor | undefined;
    beforeEach(() => {
      original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: (query: string) => ({ matches: query.includes('max-width') }),
      });
    });
    afterEach(() => {
      if (original) Object.defineProperty(window, 'matchMedia', original);
      else Reflect.deleteProperty(window, 'matchMedia');
    });

    it('o día vai en lista por hora e o detalle nun diálogo; axe', async () => {
      const { el, settle } = await render();
      expect(el.querySelector('furar-calendar-grid')).toBeNull();
      const groups = [...el.querySelectorAll('.f-bcal__list-group')];
      expect(groups.map((g) => text(g.querySelector('h2')))).toEqual(['10:00–11:30', '19:00–20:00']);
      expect(text(groups[1].querySelector('.f-mrow'))).toBe(
        'Ana Rey Pista de pádel 1 · Pista completa Pendente de pago',
      );
      await expectNoAxeViolations(el);

      click(el.querySelector('.f-mrow[data-booking="b1"]'));
      (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
      await settle();
      expect(el.querySelector('[data-detail-card]')).toBeNull();
      await vi.waitFor(() => expect(dialog()?.querySelector('furar-managed-booking-detail')).not.toBeNull());
    });

    it('un día sen reservas dio que non hai', async () => {
      const { el, settle } = await render();
      click(el.querySelector('[data-period-next]'));
      (await expectRequest(calendarFor('DAY', '2026-10-27'))).flush(calendar({ from: '2026-10-27', to: '2026-10-27' }));
      await settle();
      expect(text(el.querySelector('.f-bcal__list'))).toBe('Non hai reservas neste período.');
    });
  });
});
