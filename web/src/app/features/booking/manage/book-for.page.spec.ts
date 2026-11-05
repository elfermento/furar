// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../../testing/api';
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

describe('Reservar no nome doutra persoa (maqueta 58)', () => {
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

    await TestBed.inject(Router).navigateByUrl(`/naron/xestion/reservas/nova?uso=u1&data=${DAY}&hora=18:00`);
    await settle();
    type(input(el, 'name'), 'Ana Pérez');
    type(input(el, 'email'), 'ana.perez@correo.example');
    await settle();
    el.querySelector<HTMLButtonElement>('[data-book-for-paid]')!.click();
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
    const notify = input(el, 'notify');
    expect(notify.checked).toBe(true);
    expect(notify.parentElement?.textContent).toContain('Avisar a Ana Pérez por correo');

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
});
