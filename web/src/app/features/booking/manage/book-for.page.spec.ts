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
import { municipality } from '../../../../testing/fixtures';
import { detail } from '../../../../testing/manage-bookings';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BASE,
  DAY,
  FACILITY_URL,
  FIELDS_URL,
  NOTES,
  NOTES_URL,
  availability,
  facility,
  slot,
} from '../reserve/reserve.fixture';
import { BookForPage } from './book-for.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const FACILITIES = {
  items: [
    {
      id: 'f1',
      name: 'Campo de fútbol 1 de O Alto',
      status: 'ACTIVE',
      published: true,
      version: 1,
      missing: [],
      sports: [],
    },
    {
      id: 'f9',
      name: 'Pavillón pechado',
      status: 'TEMPORARILY_CLOSED',
      published: true,
      version: 1,
      missing: [],
      sports: [],
    },
  ],
  page: 0,
  size: 100,
  totalItems: 2,
};

/** Unha reserva que fixo o persoal para Ana, ás 18:00 na metade sur (a súa hora non se ofrece). */
const ANA = detail({
  id: 'b1',
  code: 'FUR-7K2Q',
  holderName: 'Ana Pérez',
  holderEmail: 'ana.perez@correo.example',
  holderPhone: '666 123 456',
  holderAccount: false,
  bookedBy: { name: 'Lucía Rey', role: 'STAFF' },
  facilityId: 'f1',
  facilityName: 'Campo de fútbol 1 de O Alto',
  useId: 'u1',
  useName: 'Fútbol 7',
  rentalMode: 'PARTS',
  partId: 'p2',
  start: '2026-10-28T17:00:00Z',
  end: '2026-10-28T18:00:00Z',
  version: 4,
});

describe('Reservar no nome doutra persoa', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(url: string) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({ staff: 'naron' })),
        provideRouter(
          [
            { path: 'naron/xestion/reservas', component: StubPage },
            { path: 'naron/xestion/reservas/nova', component: BookForPage },
            { path: 'naron/xestion/reservas/:id/cambiar', component: BookForPage },
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

  /** A instalación, as notas de pago, os campos propios e os ocos do día. */
  async function flushForm(slots = availability()) {
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(slots);
  }

  const input = (el: HTMLElement, name: string) => el.querySelector<HTMLInputElement>(`[data-book-for-${name}]`)!;
  const type = (target: HTMLInputElement, text: string) => {
    target.value = text;
    target.dispatchEvent(new Event('input'));
  };
  const url = () => TestBed.inject(Router).url;

  it('nova: o mesmo formulario ca a veciñanza, «Para quen?» e o pago; reserva e volve ao detalle; axe', async () => {
    const { el, settle } = await render(`/naron/xestion/reservas/nova?uso=u1&data=${DAY}`);
    (await expectRequest((r) => r.url === `${BASE}/facilities`)).flush(FACILITIES);
    await flushForm();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"]')).not.toBeNull();
    });
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Nova reserva');
    // Só as instalacións publicadas e abertas.
    expect(el.querySelector('[data-book-for-facility]')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    expect(el.querySelector('[data-book-for-who] legend')?.textContent?.trim()).toBe('Para quen?');
    expect(el.querySelector('[data-book-for-payment]')).not.toBeNull();
    expect(el.querySelector('[data-book-for-notify]')).toBeNull();
    // A instalación, no grupo de Que · Data · Hora.
    expect(
      el
        .querySelector('[data-book-for-facility]')
        ?.closest('.f-field-group')
        ?.querySelector('[data-reserve-field="time"]'),
    ).not.toBeNull();
    // Correo e teléfono, na mesma fila.
    const contact = el.querySelector('[data-book-for-who] [data-book-for-contact]')!;
    expect(contact.querySelector('[data-book-for-email]')).not.toBeNull();
    expect(contact.querySelector('[data-book-for-phone]')).not.toBeNull();

    await TestBed.inject(Router).navigateByUrl(`/naron/xestion/reservas/nova?uso=u1&data=${DAY}&hora=18:00`);
    await settle();
    type(input(el, 'name'), 'Ana Pérez');
    type(input(el, 'email'), 'ana.perez@correo.example');
    await settle();
    // «Pago»: o chip de estado abre un menú (Pendente · Pagada).
    el.querySelector<HTMLButtonElement>('[data-book-for-payment] furar-chip-menu button')!.click();
    await settle();
    [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')]
      .find((b) => b.textContent?.trim() === 'Pagada')!
      .click();
    await settle();
    const go = el.querySelector<HTMLButtonElement>('[data-book-for-go]')!;
    expect(go.textContent?.trim()).toBe('Reservar para Ana Pérez');
    await expectNoAxeViolations(el);

    go.click();
    const post = await expectRequest((r) => r.url === `${BASE}/bookings` && r.method === 'POST');
    expect(post.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(post.request.body).toEqual({
      facilityId: 'f1',
      useId: 'u1',
      start: '2026-10-28T17:00:00.000Z',
      slots: 1,
      holder: { name: 'Ana Pérez', email: 'ana.perez@correo.example' },
      paid: true,
      language: 'gl',
    });
    post.flush({ ...ANA, id: 'b7' });
    await vi.waitFor(() => expect(url()).toBe('/naron/xestion/reservas?reserva=b7'));
  });

  it('nova: sen nome non reserva e o erro vai ao campo', async () => {
    const { el, settle } = await render(`/naron/xestion/reservas/nova?uso=u1&data=${DAY}&hora=18:00`);
    (await expectRequest((r) => r.url === `${BASE}/facilities`)).flush(FACILITIES);
    await flushForm();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-book-for-go]')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('[data-book-for-go]')!.click();
    await settle();
    expect(input(el, 'name').getAttribute('aria-invalid')).toBe('true');
    expect(el.textContent).toContain('Este campo é obrigatorio.');
  });

  it('cambiar: o formulario relleno coa mesma hora; garda e avisa por correo', async () => {
    const { el, settle } = await render('/naron/xestion/reservas/b1/cambiar');
    (await expectRequest(`${BASE}/bookings/b1`)).flush(ANA);
    await flushForm();
    await vi.waitFor(async () => {
      await settle();
      expect(input(el, 'name')?.value).toBe('Ana Pérez');
    });
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Cambiar reserva');
    expect(input(el, 'phone').value).toBe('666 123 456');
    expect(el.querySelector('[data-book-for-before]')?.textContent).toContain('18:00–19:00');
    const notify = el.querySelector<HTMLButtonElement>('[data-book-for-notify] [role="switch"]')!;
    expect(notify.getAttribute('aria-checked')).toBe('true');
    expect(el.querySelector('[data-book-for-notify]')?.textContent).toContain('Avisar a Ana Pérez por correo');

    type(input(el, 'phone'), '600 000 000');
    await settle();
    el.querySelector<HTMLButtonElement>('[data-book-for-go]')!.click();
    const put = await expectRequest((r) => r.url === `${BASE}/bookings/b1` && r.method === 'PUT');
    expect(put.request.headers.get('If-Match')).toBe('"4"');
    expect(put.request.body).toEqual({
      useId: 'u1',
      start: '2026-10-28T17:00:00.000Z',
      slots: 1,
      partId: 'p2',
      holder: { name: 'Ana Pérez', email: 'ana.perez@correo.example', phone: '600 000 000' },
      paid: false,
      notifyPerson: true,
      customFields: {},
    });
    put.flush({ ...ANA, holderPhone: '600 000 000', version: 5 });
    await vi.waitFor(() => expect(url()).toBe('/naron/xestion/reservas?reserva=b1'));
  });
  describe('usos por prazas: «Prazas» e a reserva é do correo', () => {
    const SWIM = {
      id: 'u3',
      name: 'Natación libre',
      rentalMode: 'SEATS' as const,
      capacity: 20,
      parts: [],
      slotMinutes: 60,
      availability: 'ALWAYS' as const,
      periods: [],
      weekdays: 127,
      price: { kind: 'PAID' as const, amountCents: 300, per: 'PERSON' as const },
      rules: {},
      sports: [],
    };
    const HOLDER_DEPENDENTS_URL = `${BASE}/bookings/holder-dependents`;

    async function renderSwim() {
      const view = await render(`/naron/xestion/reservas/nova?uso=u3&data=${DAY}&hora=18:00`);
      (await expectRequest((r) => r.url === `${BASE}/facilities`)).flush(FACILITIES);
      (await expectRequest(NOTES_URL)).flush(NOTES);
      flushMatching((r) => r.url === FIELDS_URL, []);
      (await expectRequest(FACILITY_URL)).flush(facility({ uses: [SWIM] }));
      (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability([slot(1080, 1)]));
      await vi.waitFor(async () => {
        await view.settle();
        expect(view.el.querySelector('[data-book-for-seats]')).not.toBeNull();
      });
      return view;
    }
    const hint = (el: HTMLElement) => el.querySelector('[data-book-for-hint]')?.textContent?.trim();
    const options = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('[data-book-for-seats] [role="option"]')];

    it('con conta saen as súas persoas a cargo; acompañantes a man; o total leva as prazas; axe', async () => {
      const { el, settle } = await renderSwim();
      // Sen «Para quen» entre os campos da hora: no mostrador vai en «Prazas».
      expect(el.querySelector('[data-reserve-field="who"]')).toBeNull();
      expect(hint(el)).toBe('Sen correo: non recibe a confirmación');
      type(input(el, 'name'), 'Marta Vilar');
      type(input(el, 'email'), 'Marta.Vilar@exemplo.example.org');
      await settle();
      expect(hint(el)).toBe('A reserva queda a nome deste correo');
      backend().expectNone(HOLDER_DEPENDENTS_URL);

      // Ao saír do correo pídense as persoas a cargo da súa conta (no corpo, non no URL).
      input(el, 'email').dispatchEvent(new Event('blur'));
      const read = await expectRequest(HOLDER_DEPENDENTS_URL);
      expect(read.request.method).toBe('POST');
      expect(read.request.body).toEqual({ email: 'marta.vilar@exemplo.example.org' });
      read.flush([
        { id: 'd1', name: 'Uxía Vilar', birthYear: 2015 },
        { id: 'd2', name: 'Brais Vilar', birthYear: 2018 },
      ]);
      await vi.waitFor(async () => {
        await settle();
        expect(hint(el)).toBe('Ten conta: a reserva será súa');
      });
      expect(options(el).map((o) => o.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Marta Vilar',
        'Uxía Vilar 2015',
        'Brais Vilar 2018',
      ]);
      // A persoa ocupa praza sempre: non se desmarca.
      options(el)[0].click();
      options(el)[1].click();
      await settle();
      expect(options(el).map((o) => o.getAttribute('aria-selected'))).toEqual(['true', 'true', 'false']);

      el.querySelector<HTMLButtonElement>('[data-book-for-companion-add]')!.click();
      await settle();
      const row = el.querySelector('[data-book-for-companion]')!;
      type(row.querySelector<HTMLInputElement>('[data-companion-name]')!, 'Icía Pardo');
      await settle();
      expect(el.querySelector('[data-book-for-total]')?.textContent).toBe('9,00\u00a0€ · 3 prazas');
      await expectNoAxeViolations(el);

      // Un acompañante sen ano non se garda: o erro vai ao seu campo.
      el.querySelector<HTMLButtonElement>('[data-book-for-go]')!.click();
      await settle();
      backend().expectNone((r) => r.url === `${BASE}/bookings`);
      expect(row.querySelector('[data-companion-year]')?.getAttribute('aria-invalid')).toBe('true');
      type(row.querySelector<HTMLInputElement>('[data-companion-year]')!, '2016');
      await settle();

      el.querySelector<HTMLButtonElement>('[data-book-for-go]')!.click();
      const post = await expectRequest((r) => r.url === `${BASE}/bookings` && r.method === 'POST');
      expect(post.request.body).toMatchObject({
        useId: 'u3',
        holder: { name: 'Marta Vilar', email: 'Marta.Vilar@exemplo.example.org' },
        participants: { self: true, dependentIds: ['d1'], companions: [{ name: 'Icía Pardo', birthYear: 2016 }] },
      });
      post.flush(detail({ id: 'b9', rentalMode: 'SEATS', seats: 3 }));
      await vi.waitFor(() => expect(url()).toContain('/naron/xestion/reservas?reserva=b9'));
    });

    it('o correo escríbese antes de escoller o uso por prazas: as súas persoas pídense ao escollelo', async () => {
      const { el, settle } = await render(`/naron/xestion/reservas/nova?uso=u1&data=${DAY}`);
      (await expectRequest((r) => r.url === `${BASE}/facilities`)).flush(FACILITIES);
      (await expectRequest(NOTES_URL)).flush(NOTES);
      flushMatching((r) => r.url === FIELDS_URL, []);
      (await expectRequest(FACILITY_URL)).flush(facility({ uses: [...facility().uses, SWIM] }));
      (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-reserve-field="time"]')).not.toBeNull();
      });
      // Nun uso que non é por prazas non hai «Prazas» nin se consulta nada.
      expect(el.querySelector('[data-book-for-seats]')).toBeNull();
      type(input(el, 'name'), 'Marta Vilar');
      type(input(el, 'email'), 'marta.vilar@exemplo.example.org');
      input(el, 'email').dispatchEvent(new Event('blur'));
      await settle();
      backend().expectNone(HOLDER_DEPENDENTS_URL);

      await TestBed.inject(Router).navigateByUrl(`/naron/xestion/reservas/nova?uso=u3&data=${DAY}`);
      (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability([slot(1080, 1)]));
      const read = await expectRequest(HOLDER_DEPENDENTS_URL);
      expect(read.request.body).toEqual({ email: 'marta.vilar@exemplo.example.org' });
      read.flush([{ id: 'd1', name: 'Uxía Vilar', birthYear: 2015 }]);
      await vi.waitFor(async () => {
        await settle();
        expect(hint(el)).toBe('Ten conta: a reserva será súa');
        expect(options(el).map((o) => o.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
          'Marta Vilar',
          'Uxía Vilar 2015',
        ]);
      });

      // Corrixir o correo e saír del volve preguntar; o mesmo correo, non.
      input(el, 'email').dispatchEvent(new Event('blur'));
      await settle();
      backend().expectNone(HOLDER_DEPENDENTS_URL);
      type(input(el, 'email'), 'outra@exemplo.example.org');
      await settle();
      expect(options(el)).toHaveLength(1);
      input(el, 'email').dispatchEvent(new Event('blur'));
      (await expectRequest(HOLDER_DEPENDENTS_URL)).flush([]);
      await settle();
      expect(hint(el)).toBe('A reserva queda a nome deste correo');
    });

    it('quitar un acompañante xa escrito pregunta antes; demasiadas consultas dino sen romper o formulario', async () => {
      const { el, settle } = await renderSwim();
      el.querySelector<HTMLButtonElement>('[data-book-for-companion-add]')!.click();
      await settle();
      // Baleiro, quítase sen preguntar.
      el.querySelector<HTMLButtonElement>('[data-companion-remove] button')!.click();
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-book-for-companion]')).toBeNull();
      });
      el.querySelector<HTMLButtonElement>('[data-book-for-companion-add]')!.click();
      await settle();
      type(el.querySelector<HTMLInputElement>('[data-companion-name]')!, 'Icía Pardo');
      await settle();
      el.querySelector<HTMLButtonElement>('[data-companion-remove] button')!.click();
      await settle();
      const confirm = document.querySelector<HTMLElement>('furar-dialog-container')!;
      expect(confirm.textContent).toContain('Quitar a Icía Pardo?');
      confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-book-for-companion]')).toBeNull();
      });

      type(input(el, 'email'), 'xoan@exemplo.example.org');
      input(el, 'email').dispatchEvent(new Event('blur'));
      flushProblem(await expectRequest(HOLDER_DEPENDENTS_URL), 429, 'booking.dependents-rate-limited');
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-book-for-error]')?.textContent).toContain('Demasiadas consultas seguidas');
      });
      expect(hint(el)).toBe('A reserva queda a nome deste correo');
    });
  });
});
