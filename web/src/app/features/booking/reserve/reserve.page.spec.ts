// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  backend,
  expectRequest,
  fakeSession,
  flushMatching,
  flushProblem,
  provideTestApi,
} from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { SELECT_OPTIONS, definition, municipality } from '../../../../testing/fixtures';
import { pickOption } from '../../../../testing/select';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BOOKINGS_URL,
  DAY,
  FACILITY_URL,
  NOTES,
  NOTES_URL,
  FIELDS_URL,
  availability,
  booking,
  facility,
  slot,
} from './reserve.fixture';
import { ReservePage } from './reserve.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

/** Os campos propios da reserva, se se pediron (con sesión): os indicados ou ningún. */
const flushFields = (definitions: object[] = []) =>
  flushMatching((r) => r.url === FIELDS_URL && r.params.get('target') === 'BOOKING', definitions);

describe('Páxina de reservar', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(url: string, session: ReturnType<typeof fakeSession> = fakeSession({})) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        provideRouter(
          [
            { path: 'naron/instalacions/:id/reservar', component: ReservePage },
            { path: 'naron/instalacions/:id', component: StubPage },
            { path: 'naron/reservas', component: StubPage },
          ],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    return { harness, el, settle };
  }

  async function renderForm(url: string, session?: ReturnType<typeof fakeSession>, fields: object[] = []) {
    const view = await render(url, session);
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields(fields);
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
    await vi.waitFor(async () => {
      await view.settle();
      expect(view.el.querySelector('[data-reserve-field="time"]')).not.toBeNull();
      if (url.includes('hora=')) expect(value(view.el, 'time')).not.toBe('Escoller');
    });
    return view;
  }

  const anonymous = () => ({ ...fakeSession({}), authenticated: () => false }) as never;
  const field = (el: HTMLElement, name: string) =>
    el.querySelector<HTMLButtonElement>(`[data-reserve-field="${name}"] .f-field-popover__trigger`)!;
  const value = (el: HTMLElement, name: string) =>
    field(el, name).querySelector('.f-field-popover__value')?.textContent?.trim();
  const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');
  const key = (target: Element, name: string) =>
    target.dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));

  it('sen sesión: «←», tarxeta da instalación, campos co escollido, total e «Continuar» coa nota do concello; axe', async () => {
    const { el } = await renderForm(`/naron/instalacions/f1/reservar?uso=u1&data=${DAY}`, anonymous());
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Reservar');
    const back = el.querySelector('[data-reserve-back]');
    expect(back?.getAttribute('href')).toBe('/naron/instalacions/f1');
    expect(back?.getAttribute('aria-label')).toBe('Volver á instalación');
    expect(el.querySelector('.f-reserve__facility')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    expect(el.querySelector('.f-reserve__facility')?.textContent).toContain('Rúa Buxo, 16 · Narón');
    expect(value(el, 'what')).toBe('Fútbol 7');
    expect(value(el, 'date')).toBe('mér 28 out');
    expect(value(el, 'time')).toBe('Escoller');
    expect(value(el, 'part')).toBe('Tanto me ten');
    expect(el.querySelector('[data-reserve-total]')?.textContent?.trim()).toBe('30,00\u00a0€');
    const go = el.querySelector<HTMLButtonElement>('[data-reserve-go]')!;
    expect(go.textContent?.trim()).toBe('Continuar');
    expect(go.disabled).toBe(true);
    // O texto de pago sae da API (nota do concello), nunca do código.
    expect(el.querySelector('[data-reserve-fine]')?.textContent?.trim()).toBe(
      'Pedirémosche entrar ao continuar · No mostrador, ao chegar',
    );
    await expectNoAxeViolations(el);
  });

  it('teclado: frecha abre, o foco vai dentro, Esc pecha e volve ao campo; os campos pasan sós ao seguinte', async () => {
    const { el, settle } = await renderForm(`/naron/instalacions/f1/reservar?uso=u1&data=${DAY}`);
    // Que: frecha abaixo abre o panel; escoller o mesmo uso pasa á Data, co foco no día escollido.
    key(field(el, 'what'), 'ArrowDown');
    await settle();
    expect(field(el, 'what').getAttribute('aria-expanded')).toBe('true');
    expect(dialog()?.contains(document.activeElement)).toBe(true);
    key(document.activeElement!, 'Escape');
    await settle();
    expect(dialog()).toBeNull();
    expect(document.activeElement).toBe(field(el, 'what'));

    key(field(el, 'what'), 'ArrowDown');
    await settle();
    document.querySelector<HTMLButtonElement>('.f-reserve__use[aria-pressed="true"]')!.click();
    await settle();
    expect(field(el, 'date').getAttribute('aria-expanded')).toBe('true');
    await vi.waitFor(() => expect(document.activeElement?.getAttribute('data-date')).toBe(DAY));

    // Data: frecha á dereita e Intro escollen o día seguinte e abren a Hora.
    key(document.activeElement!, 'ArrowRight');
    await settle();
    key(document.activeElement!, 'Enter');
    await settle();
    expect(query().get('data')).toBe('2026-10-29');
    expect(field(el, 'time').getAttribute('aria-expanded')).toBe('true');
    (await expectRequest((r) => r.url === AVAILABILITY_URL && r.params.get('date') === '2026-10-29')).flush(
      availability(),
    );
    await settle();
    // Hora: só as horas da API, coa duración (1 hora · 2 horas) e as partes libres.
    const slots = [...document.querySelectorAll<HTMLButtonElement>('[data-reserve-slot]')];
    expect(slots.map((b) => `${b.querySelector('b')?.textContent} ${b.querySelector('small')?.textContent}`)).toEqual([
      '17:00 1 libre',
      '18:00 2 libres',
      '19:00 1 libre',
    ]);
    expect(
      [...document.querySelectorAll('[role="radiogroup"] [role="radio"]')].map((b) => b.textContent?.trim()),
    ).toEqual(['1 hora', '2 horas']);
    slots[1].click();
    await settle();
    expect(query().get('hora')).toBe('18:00');
    expect(value(el, 'time')).toBe('18:00–19:00');
    // Parte: abre soa; «Metade sur» pecha e queda no campo.
    expect(field(el, 'part').getAttribute('aria-expanded')).toBe('true');
    const parts = [...document.querySelectorAll<HTMLButtonElement>('.f-reserve__part')];
    expect(parts.map((b) => b.textContent?.trim())).toEqual(['Metade norte', 'Metade sur', 'Tanto me ten']);
    parts[1].click();
    await settle();
    expect(dialog()).toBeNull();
    expect(query().get('parte')).toBe('p2');
    expect(value(el, 'part')).toBe('Metade sur');
    expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.textContent?.trim()).toBe('Confirmar reserva');
  });

  it('conta de traballo: aviso antes de reservar e «Confirmar reserva» desactivado e descrito por el; axe', async () => {
    const { el } = await renderForm(
      `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00&parte=p2`,
      fakeSession({ workAccount: true }),
    );
    const notice = el.querySelector<HTMLElement>('[data-reserve-work-account]')!;
    expect(notice.textContent).toContain('Esta é unha conta de traballo; para reservar usa a túa conta persoal.');
    const go = el.querySelector<HTMLButtonElement>('[data-reserve-go]')!;
    expect(go.disabled).toBe(true);
    expect(go.getAttribute('aria-describedby')).toBe(notice.id);
    await expectNoAxeViolations(el);
    // Unha conta persoal non ve o aviso.
    TestBed.resetTestingModule();
    const personal = await renderForm(
      `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00&parte=p2`,
      fakeSession({}),
    );
    expect(personal.el.querySelector('[data-reserve-work-account]')).toBeNull();
    expect(personal.el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(false);
  });

  it('409: aviso en role="status" coas tres horas, a Hora baleira; unha alternativa volve encher a Hora', async () => {
    const { el, settle } = await renderForm(`/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00`);
    expect(value(el, 'time')).toBe('18:00–19:00');
    el.querySelector<HTMLButtonElement>('[data-reserve-go]')!.click();
    flushProblem(await expectRequest(BOOKINGS_URL), 409, 'booking.slot-taken', {
      alternatives: [slot(1020, 1, ['p1']), slot(1140, 1, ['p2']), slot(1260, 1, ['p1'])],
    });
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(
      availability([slot(1020, 2, ['p1']), slot(1140, 1, ['p2']), slot(1260, 1, ['p1'])]),
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-status] furar-callout')).not.toBeNull();
    });
    const status = el.querySelector('[data-reserve-status]')!;
    expect(status.getAttribute('role')).toBe('status');
    expect(status.textContent).toContain('Alguén acaba de reservar as 18:00');
    const alts = [...status.querySelectorAll<HTMLButtonElement>('[data-reserve-alt]')];
    expect(alts.map((b) => b.textContent?.trim())).toEqual(['17:00', '19:00', '21:00']);
    expect(value(el, 'time')).toBe('Escoller');
    expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(true);
    await expectNoAxeViolations(el);
    alts[1].click();
    await settle();
    expect(value(el, 'time')).toBe('19:00–20:00');
    expect(el.querySelector('[data-reserve-status] furar-callout')).toBeNull();
  });

  it('campos propios da reserva: no mesmo paso, o obrigatorio antes de confirmar e os valores no POST', async () => {
    const level = definition({
      code: 'level',
      type: 'SELECT',
      label: { gl: 'Nivel', es: 'Nivel' },
      help: undefined,
      options: SELECT_OPTIONS,
      usages: [{ target: 'BOOKING', required: true, visibility: 'OWNER' }],
    });
    const { el, settle } = await renderForm(
      `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00`,
      undefined,
      [level],
    );
    const fields = el.querySelector('[data-reserve-custom-fields]')!;
    expect(fields.textContent).toContain('Nivel');
    el.querySelector<HTMLButtonElement>('[data-reserve-go]')!.click();
    await settle();
    backend().expectNone((r) => r.url === BOOKINGS_URL);
    await vi.waitFor(async () => {
      await settle();
      expect(fields.querySelector('.field-error')?.textContent).toContain('obrigatorio');
    });
    await pickOption(fields.querySelector('#reserve-cf-level')!, 'Ximnasio', settle);
    el.querySelector<HTMLButtonElement>('[data-reserve-go]')!.click();
    const post = await expectRequest(BOOKINGS_URL);
    expect(post.request.body).toMatchObject({ useId: 'u1', customFields: { level: 'gym' } });
    post.flush(booking({ customFields: { level: 'gym' } }));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-value="cf-level"]')?.textContent).toContain('Ximnasio');
    });
  });

  it('«Reserva confirmada»: foco no título, correo, valores, QR, prazo exacto en Europe/Madrid e accións; axe', async () => {
    const { el, settle } = await renderForm(`/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00&parte=p2`);
    const go = el.querySelector<HTMLButtonElement>('[data-reserve-go]')!;
    go.focus();
    go.click();
    go.click(); // dobre clic: unha soa reserva
    const post = await expectRequest(BOOKINGS_URL);
    await settle();
    expect(go.getAttribute('aria-busy')).toBe('true');
    expect(document.activeElement).toBe(go);
    go.click();
    await settle();
    backend().expectNone(BOOKINGS_URL);
    post.flush(booking());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-reserve-confirmed h1')).not.toBeNull();
    });
    const title = el.querySelector<HTMLElement>('furar-reserve-confirmed h1')!;
    expect(title.textContent?.trim()).toBe('Reserva confirmada');
    await vi.waitFor(() => expect(document.activeElement).toBe(title));
    expect(el.textContent).toContain('Enviámoscha a xestora@naron.example');
    const rows = [...el.querySelectorAll('furar-value-list [data-value]')].map((row) => [
      row.querySelector('dt')?.textContent?.trim(),
      row.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(rows).toEqual([
      ['Uso', 'Fútbol 7'],
      ['Día', 'mércores, 28 de outubro'],
      ['Hora', '18:00–19:00'],
      ['Parte', 'Metade sur'],
      ['Prezo', '30,00\u00a0€ · No mostrador, ao chegar'],
    ]);
    expect(el.querySelector('furar-qr svg')?.getAttribute('aria-label')).toContain('FUR-7K2Q');
    expect(el.querySelector('[data-done-code]')?.textContent?.trim()).toBe('FUR-7K2Q');
    expect(el.querySelector('[data-done-cancel]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Podes cancelala sen custo ata o luns, 26 de outubro ás 18:00',
    );
    expect(el.querySelector('[data-done-mine]')?.getAttribute('href')).toBe('/naron/reservas');
    expect(query().get('reserva')).toBe('b1');
    await expectNoAxeViolations(el);
  });

  it('recargar «?reserva=» pide a reserva á API', async () => {
    const { el, settle } = await render('/naron/instalacions/f1/reservar?reserva=b1');
    (await expectRequest(`${BOOKINGS_URL}/b1`)).flush(booking());
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields();
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-done-code]')?.textContent?.trim()).toBe('FUR-7K2Q');
    });
    backend().expectNone((r) => r.url === AVAILABILITY_URL);
  });

  it('modo cambio: «Cambiar a reserva» e «Confirmar o cambio»', async () => {
    const { el, settle } = await render(`/naron/instalacions/f1/reservar?cambiar=b1&uso=u1&data=${DAY}&hora=19:00`);
    (await expectRequest(`${BOOKINGS_URL}/b1`)).flush(booking());
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields();
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(false);
    });
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Cambiar a reserva');
    expect(el.querySelector('[data-reserve-go]')?.textContent?.trim()).toBe('Confirmar o cambio');
  });
});
