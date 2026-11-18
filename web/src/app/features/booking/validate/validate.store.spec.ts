// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { BASE, detail } from '../../../../testing/manage-bookings';
import { cameraOffered } from './qr-reader';
import { dayOfMonth, isBookingCode, relativeDay, typedCode, weekdayAndDay } from './validate';
import { ValidateStore } from './validate.store';

const URL = `${BASE}/bookings/validations`;
const validated = (overrides = {}) => ({
  outcome: 'VALIDATED',
  booking: detail({ attendance: 'USED', version: 4 }),
  attendanceAt: '2026-10-26T18:02:00Z',
  attendanceBy: 'Antón Rei',
  ...overrides,
});

describe('ValidateStore', () => {
  let store: ValidateStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), ValidateStore] });
    store = TestBed.inject(ValidateStore);
  });
  afterEach(() => backend().verify());

  it('ler un código valida soa: manda só o código e garda o resultado', async () => {
    const read = store.read('naron', 'FUR-7K2Q');
    expect(store.state()).toBe('loading');
    const post = await expectRequest(URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ code: 'FUR-7K2Q' });
    post.flush(validated());
    await read;
    expect(store.state()).toBe('ready');
    expect(store.result()?.outcome).toBe('VALIDATED');
  });

  it('un código que non existe (404) non é un erro; outro fallo si, co seu texto', async () => {
    let read = store.read('naron', 'FUR-ZZZZ');
    flushProblem(await expectRequest(URL), 404, 'booking.not-found');
    await read;
    expect(store.state()).toBe('notFound');
    expect(store.result()).toBeNull();
    expect(store.problem()).toBeNull();

    read = store.read('naron', 'FUR-7K2Q');
    flushProblem(await expectRequest(URL), 403, 'auth.forbidden');
    await read;
    expect(store.state()).toBe('error');
    expect(store.problem()?.code).toBe('auth.forbidden');
  });

  it('só vale a última lectura: a resposta dun código anterior descártase', async () => {
    const first = store.read('naron', 'FUR-AAAA');
    const second = store.read('naron', 'FUR-7K2Q');
    const [old, latest] = backend().match((r) => r.url === URL);
    latest.flush(validated());
    old.flush({ ...validated(), outcome: 'CANCELLED' });
    await Promise.all([first, second]);
    expect(store.result()?.outcome).toBe('VALIDATED');
  });

  it('«Validar igualmente» manda o motivo; se falla, o resultado de antes queda e o erro lánzase', async () => {
    const read = store.read('naron', 'FUR-7K2Q');
    (await expectRequest(URL)).flush({ outcome: 'OUT_OF_WINDOW', booking: detail() });
    await read;

    let force = store.force('naron', 'FUR-7K2Q', 'Chegou antes');
    const refused = await expectRequest(URL);
    expect(refused.request.body).toEqual({ code: 'FUR-7K2Q', force: true, reason: 'Chegou antes' });
    flushProblem(refused, 422, 'booking.validation-reason-required');
    await expect(force).rejects.toMatchObject({ code: 'booking.validation-reason-required' });
    expect(store.result()?.outcome).toBe('OUT_OF_WINDOW');

    force = store.force('naron', 'FUR-7K2Q', 'Chegou antes');
    (await expectRequest(URL)).flush(validated());
    await force;
    expect(store.result()?.outcome).toBe('VALIDATED');
  });

  it('«Desfacer» vai coa versión que se ve e volve ao principio', async () => {
    const read = store.read('naron', 'FUR-7K2Q');
    (await expectRequest(URL)).flush(validated());
    await read;

    const undo = store.undo('naron');
    const request = await expectRequest(`${BASE}/bookings/b1/attendance`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    request.flush(detail({ attendance: 'NONE', version: 5 }));
    await undo;
    expect(store.state()).toBe('idle');
    expect(store.result()).toBeNull();
  });

  it('as instalacións son para a miniatura: se non chegan, sen ela', async () => {
    const load = store.loadFacilities('naron');
    (await expectRequest(`${BASE}/facilities?page=0&size=100`)).flush(null, { status: 500, statusText: 'x' });
    await load;
    expect(store.facilities()).toEqual([]);
  });
});

describe('Validar QR: códigos, datas e cámara', () => {
  it('o código tecléase en maiúsculas e sen espazos; só o enteiro se manda', () => {
    expect(typedCode('  fur-7k2q ')).toBe('FUR-7K2Q');
    expect(isBookingCode('FUR-7K2Q')).toBe(true);
    for (const partial of ['', 'FUR-7K2', 'FUR-7K2QX', '7K2Q', 'https://exemplo.example.org/FUR-7K2Q', 'fur-7k2q']) {
      expect(isBookingCode(partial)).toBe(false);
    }
  });

  it('«9 de novembro», «luns 9 de novembro» e «hoxe» ou «mañá»', () => {
    expect(dayOfMonth('2026-11-09', 'gl')).toBe('9 de novembro');
    expect(dayOfMonth('2026-11-24', 'es')).toBe('24 de noviembre');
    expect(weekdayAndDay('2026-11-09', 'gl')).toBe('luns 9 de novembro');
    const names = { today: 'hoxe', tomorrow: 'mañá' };
    expect(relativeDay('2026-11-09', '2026-11-09', names)).toBe('hoxe');
    expect(relativeDay('2026-11-10', '2026-11-09', names)).toBe('mañá');
    expect(relativeDay('2026-11-11', '2026-11-09', names)).toBeNull();
  });

  it('a cámara só se ofrece no móbil: getUserMedia, punteiro táctil e pantalla estreita', () => {
    const view = (touch: boolean, narrow: boolean, media = true) =>
      ({
        navigator: media ? { mediaDevices: { getUserMedia: () => Promise.reject(new Error('x')) } } : {},
        matchMedia: (query: string) => ({ matches: query.includes('pointer') ? touch : narrow }),
      }) as unknown as Window & typeof globalThis;
    expect(cameraOffered(view(true, true))).toBe(true);
    expect(cameraOffered(view(false, true))).toBe(false);
    expect(cameraOffered(view(true, false))).toBe(false);
    expect(cameraOffered(view(true, true, false))).toBe(false);
    expect(cameraOffered(null)).toBe(false);
  });
});
