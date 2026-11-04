// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import {
  backend,
  expectRequest,
  fakeSession,
  flushMatching,
  flushProblem,
  provideTestApi,
} from '../../../../testing/api';
import { municipality } from '../../../../testing/fixtures';
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
  wholeAvailability,
} from './reserve.fixture';
import { BOOKING_RETRY_DELAYS, ConfirmedBookingStore } from './confirmed-booking.store';
import { ReserveCustomFields } from './reserve-custom-fields';
import { ReserveStore } from './reserve.store';
import { UseAvailabilityStore } from './use-availability.store';

/** Os campos propios da reserva, se se pediron (con sesión): os indicados ou ningún. */
const flushFields = (definitions: object[] = []) =>
  flushMatching((r) => r.url === FIELDS_URL && r.params.get('target') === 'BOOKING', definitions);

describe('Estado da páxina de reservar', () => {
  afterEach(() => backend().verify());

  async function setup(url: string, session = fakeSession({})) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        provideRouter([]),
        ReserveStore,
        ReserveCustomFields,
        ConfirmedBookingStore,
        UseAvailabilityStore,
        { provide: BOOKING_RETRY_DELAYS, useValue: [0, 0] },
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    await TestBed.inject(Router).navigateByUrl(url);
    const store = TestBed.inject(ReserveStore);
    return { store, session };
  }

  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');
  const settle = async () => {
    TestBed.tick();
    await TestBed.inject(Router).navigated;
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
  };

  /** Carga a ficha e responde aos ocos do uso que se pida. */
  async function load(store: ReserveStore, slots = availability()) {
    const loading = store.load('f1');
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields();
    (await expectRequest(FACILITY_URL)).flush(facility());
    await loading;
    TestBed.tick();
    const request = await expectRequest((r) => r.url === AVAILABILITY_URL);
    request.flush(slots);
    await settle();
    return request;
  }

  it('URL → estado: uso, día, hora, dúas franxas e a parte; total e oco da API', async () => {
    const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00&franxas=2&parte=p2`);
    const request = await load(store);
    expect(request.request.params.get('useId')).toBe('u1');
    expect(request.request.params.get('date')).toBe(DAY);
    expect(store.use()?.name).toBe('Fútbol 7');
    expect(store.slots()).toBe(2);
    expect(store.chosen()?.time).toBe('18:00');
    expect(store.partId()).toBe('p2');
    expect(store.total()).toBe(6000);
  });

  it('estado → URL: cada escolla queda no URL e a parte que xa non está libre volve a «Tanto me ten»', async () => {
    const { store } = await setup(`/?uso=u1&data=${DAY}`);
    await load(store);
    store.selectTime('17:00');
    await settle();
    expect(query().get('hora')).toBe('17:00');
    store.selectPart('p1');
    await settle();
    expect(query().get('parte')).toBe('p1');
    store.selectTime('19:00');
    await settle();
    expect(query().get('hora')).toBe('19:00');
    expect(query().has('parte')).toBe(false);
    // A duración que xa non cabe a esa hora deixa a Hora baleira.
    store.selectDuration(2);
    await settle();
    expect(query().get('franxas')).toBe('2');
    expect(query().has('hora')).toBe(false);
    // Outro día pide os seus ocos e baleira a hora.
    store.selectTime('17:00');
    store.selectDate('2026-10-29');
    await settle();
    expect(query().get('data')).toBe('2026-10-29');
    expect(query().has('hora')).toBe(false);
    (await expectRequest((r) => r.url === AVAILABILITY_URL && r.params.get('date') === '2026-10-29')).flush(
      availability(),
    );
  });

  it('sen sesión, «Continuar» vai a Keycloak co URL que leva todo o escollido; ao volver, o mesmo paso', async () => {
    const anonymous = { ...fakeSession({}), authenticated: () => false };
    const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00&parte=p2`, anonymous as never);
    await load(store);
    await store.submit();
    const returnUrl = anonymous.login.mock.calls[0][0] as string;
    expect(returnUrl).toBe(`/?uso=u1&data=${DAY}&hora=18:00&parte=p2`);
    backend().expectNone(BOOKINGS_URL);

    // A volta de Keycloak: a mesma páxina, xa con sesión, co mesmo uso, día, hora e parte.
    TestBed.resetTestingModule();
    const back = await setup(returnUrl, fakeSession({}));
    await load(back.store);
    expect(back.store.authenticated()).toBe(true);
    expect(back.store.chosen()?.time).toBe('18:00');
    expect(back.store.partId()).toBe('p2');
  });

  it('con sesión, confirma co oco da API e pasa a «?reserva=»', async () => {
    const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00&parte=p2`);
    await load(store);
    const submitting = store.submit();
    const request = await expectRequest(BOOKINGS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      facilityId: 'f1',
      useId: 'u1',
      start: '2026-10-28T17:00:00.000Z',
      slots: 1,
      partId: 'p2',
    });
    request.flush(booking());
    await submitting;
    await settle();
    expect(TestBed.inject(ConfirmedBookingStore).booking()?.code).toBe('FUR-7K2Q');
    expect([...query().keys()]).toEqual(['reserva']);
    expect(query().get('reserva')).toBe('b1');
  });

  describe('Idempotency-Key (ADR-0025, punto 19)', () => {
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    const keyOf = (request: { request: { headers: { get(name: string): string | null } } }) =>
      request.request.headers.get('Idempotency-Key');
    const lost = (request: { error(event: ProgressEvent): void }) => request.error(new ProgressEvent('error'));

    it('cada confirmación leva unha clave UUID; sen resposta, reinténtase só coa mesma', async () => {
      const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
      await load(store);
      const submitting = store.submit();
      const first = await expectRequest(BOOKINGS_URL);
      const key = keyOf(first);
      expect(key).toMatch(UUID);
      lost(first);
      const second = await expectRequest(BOOKINGS_URL);
      expect(keyOf(second)).toBe(key);
      flushProblem(second, 503, 'common.busy');
      const third = await expectRequest(BOOKINGS_URL);
      expect(keyOf(third)).toBe(key);
      third.flush(booking());
      await submitting;
      await settle();
      expect(query().get('reserva')).toBe('b1');
    });

    it('se os reintentos tampouco responden, o erro queda á vista e confirmar o mesmo repite a clave', async () => {
      const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
      await load(store);
      const submitting = store.submit();
      const first = await expectRequest(BOOKINGS_URL);
      const key = keyOf(first);
      lost(first);
      lost(await expectRequest(BOOKINGS_URL));
      lost(await expectRequest(BOOKINGS_URL));
      await submitting;
      expect(store.submitError()?.status).toBe(0);
      backend().expectNone(BOOKINGS_URL);

      const again = store.submit();
      const retried = await expectRequest(BOOKINGS_URL);
      expect(keyOf(retried)).toBe(key);
      retried.flush(booking());
      await again;
    });

    it('unha resposta clara pecha a intención: a seguinte confirmación leva clave nova', async () => {
      const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
      await load(store);
      const submitting = store.submit();
      const first = await expectRequest(BOOKINGS_URL);
      flushProblem(first, 422, 'booking.cap-reached');
      await submitting;
      backend().expectNone(BOOKINGS_URL);

      const again = store.submit();
      const second = await expectRequest(BOOKINGS_URL);
      expect(keyOf(second)).toMatch(UUID);
      expect(keyOf(second)).not.toBe(keyOf(first));
      second.flush(booking());
      await again;
    });

    it('outro oco é outra intención: clave nova aínda que a anterior quedase sen resposta', async () => {
      const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
      await load(store);
      const submitting = store.submit();
      const first = await expectRequest(BOOKINGS_URL);
      lost(first);
      lost(await expectRequest(BOOKINGS_URL));
      lost(await expectRequest(BOOKINGS_URL));
      await submitting;

      store.selectTime('19:00');
      await settle();
      const again = store.submit();
      const other = await expectRequest(BOOKINGS_URL);
      expect(other.request.body).toEqual(expect.objectContaining({ start: '2026-10-28T18:00:00.000Z' }));
      expect(keyOf(other)).not.toBe(keyOf(first));
      other.flush(booking());
      await again;
    });
  });

  it('409: aviso coas tres horas da API, a Hora baleira e os ocos pedidos de novo', async () => {
    const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
    await load(store);
    const submitting = store.submit();
    flushProblem(await expectRequest(BOOKINGS_URL), 409, 'booking.slot-taken', {
      alternatives: [slot(1020, 1, ['p1']), slot(1140, 1, ['p2']), slot(1260, 1, ['p1']), slot(1320, 1, ['p1'])],
    });
    await submitting;
    expect(store.taken()).toEqual({ time: '18:00', alternatives: ['17:00', '19:00', '21:00'] });
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability([slot(1020, 2, ['p1'])]));
    await settle();
    expect(query().has('hora')).toBe(false);
    expect(store.chosen()).toBeNull();
    expect(store.submitError()).toBeNull();
    // Escoller unha alternativa quita o aviso.
    store.selectTime('17:00');
    expect(store.taken()).toBeNull();
  });

  it('outro erro (p. ex. límite por persoa) queda para o aviso de erro, sen tocar a hora', async () => {
    const { store } = await setup(`/?uso=u1&data=${DAY}&hora=18:00`);
    await load(store);
    const submitting = store.submit();
    flushProblem(await expectRequest(BOOKINGS_URL), 422, 'booking.cap-reached');
    await submitting;
    expect(store.submitError()?.translated).toBe('Xa chegaches ao máximo de reservas neste período.');
    expect(query().get('hora')).toBe('18:00');
  });

  it('o listado leva só día e hora: escolle o uso que ten esa hora libre', async () => {
    const { store } = await setup(`/?data=${DAY}&hora=19:30`);
    const loading = store.load('f1');
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields();
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL && r.params.get('useId') === 'u1')).flush(availability());
    (await expectRequest((r) => r.url === AVAILABILITY_URL && r.params.get('useId') === 'u2')).flush(
      wholeAvailability(),
    );
    await loading;
    await settle();
    expect(query().get('uso')).toBe('u2');
    (await expectRequest((r) => r.url === AVAILABILITY_URL && r.params.get('useId') === 'u2')).flush(
      wholeAvailability(),
    );
    await settle();
    expect(store.use()?.id).toBe('u2');
    expect(store.chosen()?.time).toBe('19:30');
  });

  it('modo cambio: abre co uso, día e parte da reserva e cambia con PUT …/slot e If-Match', async () => {
    const { store } = await setup('/?cambiar=b1');
    (await expectRequest(`${BOOKINGS_URL}/b1`)).flush(booking({ version: 3 }));
    await settle();
    expect(query().get('uso')).toBe('u1');
    expect(query().get('data')).toBe(DAY);
    expect(query().get('parte')).toBe('p2');
    expect(query().get('cambiar')).toBe('b1');
    await load(store);
    store.selectTime('19:00');
    await settle();
    const submitting = store.submit();
    const request = await expectRequest(`${BOOKINGS_URL}/b1/slot`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"3"');
    expect(request.request.body).toEqual({ useId: 'u1', start: '2026-10-28T18:00:00.000Z', slots: 1, partId: 'p2' });
    request.flush(booking({ start: '2026-10-28T18:00:00Z', end: '2026-10-28T19:00:00Z', version: 4 }));
    await submitting;
    await settle();
    expect([...query().keys()]).toEqual(['reserva']);
    backend().expectNone(BOOKINGS_URL);
  });
});
