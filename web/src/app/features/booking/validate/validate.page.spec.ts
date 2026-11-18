// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushMatching, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { BASE, FACILITIES, FACILITIES_URL, detail, text } from '../../../../testing/manage-bookings';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ValidatePage } from './validate.page';

const URL = `${BASE}/bookings/validations`;

describe('Validar QR', () => {
  beforeEach(() => {
    // Luns 26 de outubro de 2026, 11:00 en Galicia.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-26T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    // «Actividades de hoxe» (debaixo do validador): sen sesións, non sae.
    flushMatching((r) => r.url.endsWith('/events/manage/sessions/today'), []);
    backend().verify();
  });

  async function render() {
    TestBed.configureTestingModule({ providers: [...provideTestApi()] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(ValidatePage);
    await fixture.whenStable();
    (await expectRequest((r) => r.url === FACILITIES_URL)).flush(FACILITIES);
    const el = fixture.nativeElement as HTMLElement;
    const settle = () => fixture.whenStable();
    const type = async (code: string) => {
      const input = el.querySelector<HTMLInputElement>('[data-validate-code]')!;
      input.value = code;
      input.dispatchEvent(new Event('input'));
      await settle();
    };
    /** Teclea o código enteiro e responde á lectura. */
    const read = async (code: string, body: object) => {
      await type(code);
      (await expectRequest(URL)).flush(body);
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-validate-outcome]')).not.toBeNull();
      });
    };
    const outcome = () => el.querySelector('[data-validate-outcome]');
    return { fixture, el, settle, type, read, outcome };
  }

  it('no ordenador só o código; ata completalo non chama á API e, completo, valida soa; axe', async () => {
    const { el, type, read, outcome } = await render();
    expect(text(el.querySelector('h1'))).toBe('Validar QR');
    expect(el.querySelector('[data-validate-mode]')).toBeNull();
    expect(el.querySelector('furar-qr-scanner')).toBeNull();
    expect(text(el.querySelector('label[for="validate-code"]'))).toBe('Código da reserva');

    await type('fur-7k2');
    expect(el.querySelector<HTMLInputElement>('[data-validate-code]')!.value).toBe('FUR-7K2');
    backend().expectNone(URL);

    await read('fur-7k2q', {
      outcome: 'VALIDATED',
      booking: detail({ attendance: 'USED', version: 4 }),
      attendanceAt: '2026-10-26T10:00:00Z',
    });
    // Sempre nesta orde: código → datos da reserva → aviso → botóns.
    const order = [
      '[data-validate-code]',
      '[data-validate-facility]',
      'furar-value-list',
      '[data-validate-outcome]',
      '[data-validate-next]',
    ].map((selector) => el.querySelector(selector)!);
    for (let i = 1; i < order.length; i++) {
      expect(order[i - 1].compareDocumentPosition(order[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
    expect(text(el.querySelector('[data-validate-facility]'))).toBe('Pista de pádel 1 Rúa Buxo, 16 · Narón');
    expect([...el.querySelectorAll('.f-value-list__row')].map((row) => text(row))).toEqual([
      'PersoaAna Rey',
      'UsoPista completa',
      'Díahoxe, luns 26 de outubro',
      'Hora19:00–20:00',
    ]);
    expect(outcome()?.getAttribute('data-tone')).toBe('success');
    expect(text(outcome())).toContain('Reserva validada');
    expect(text(el.querySelector('[data-validate-next]'))).toBe('Validar outra');
    expect(text(el.querySelector('[data-validate-undo]'))).toBe('Desfacer');
    await expectNoAxeViolations(el);
  });

  it('«Validar outra» baleira o código; «Desfacer» vai coa versión e volve ao principio', async () => {
    const { el, settle, read } = await render();
    const body = { outcome: 'VALIDATED', booking: detail({ attendance: 'USED', version: 4 }) };
    await read('FUR-7K2Q', body);
    el.querySelector<HTMLButtonElement>('[data-validate-next]')!.click();
    await settle();
    expect(el.querySelector('[data-validate-outcome]')).toBeNull();
    expect(el.querySelector<HTMLInputElement>('[data-validate-code]')!.value).toBe('');

    await read('FUR-7K2Q', body);
    el.querySelector<HTMLButtonElement>('[data-validate-undo]')!.click();
    const undo = await expectRequest(`${BASE}/bookings/b1/attendance`);
    expect(undo.request.method).toBe('DELETE');
    expect(undo.request.headers.get('If-Match')).toBe('"4"');
    undo.flush(detail({ attendance: 'NONE', version: 5 }));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-validate-outcome]')).toBeNull();
    });
    expect(el.querySelector<HTMLInputElement>('[data-validate-code]')!.value).toBe('');
  });

  it('fóra de prazo: aviso, motivo obrigatorio, «Validar igualmente» e «Non validar»; axe', async () => {
    const { el, settle, read, outcome } = await render();
    await read('FUR-7K2Q', {
      outcome: 'OUT_OF_WINDOW',
      booking: detail({ start: '2026-10-27T17:00:00Z', end: '2026-10-27T18:00:00Z' }),
    });
    expect(outcome()?.getAttribute('data-tone')).toBe('warn');
    expect(text(outcome())).toContain('Fóra do horario da reserva');
    expect(text(outcome())).toContain(
      'Esta reserva é para mañá ás 18:00. Só se valida soa desde 45 minutos antes ata 45 minutos despois.',
    );
    // Aviso → motivo → botóns.
    const reason = el.querySelector<HTMLTextAreaElement>('[data-validate-reason]')!;
    const force = el.querySelector<HTMLButtonElement>('[data-validate-force]')!;
    expect(outcome()!.compareDocumentPosition(reason) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(reason.compareDocumentPosition(force) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(text(force)).toBe('Validar igualmente');
    expect(text(el.querySelector('[data-validate-skip]'))).toBe('Non validar');

    // Sen motivo: o erro debaixo do campo, o foco nel e ningunha chamada.
    force.click();
    await settle();
    expect(text(el.querySelector('#validate-reason-error'))).toBe('Escribe o motivo para validala.');
    expect(reason.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(reason);
    backend().expectNone(URL);
    await expectNoAxeViolations(el);

    reason.value = ' Chegou antes: adestramento ';
    reason.dispatchEvent(new Event('input'));
    await settle();
    force.click();
    const forced = await expectRequest(URL);
    expect(forced.request.body).toEqual({ code: 'FUR-7K2Q', force: true, reason: 'Chegou antes: adestramento' });
    forced.flush({ outcome: 'VALIDATED', booking: detail({ attendance: 'USED', version: 4 }) });
    await vi.waitFor(async () => {
      await settle();
      expect(outcome()?.getAttribute('data-validate-outcome')).toBe('VALIDATED');
    });
    expect(el.querySelector('[data-validate-reason]')).toBeNull();
  });

  it('«Non validar» volve ao principio sen chamar á API', async () => {
    const { el, settle, read } = await render();
    await read('FUR-7K2Q', { outcome: 'OUT_OF_WINDOW', booking: detail() });
    el.querySelector<HTMLButtonElement>('[data-validate-skip]')!.click();
    await settle();
    expect(el.querySelector('[data-validate-outcome]')).toBeNull();
    backend().expectNone(URL);
  });

  it('xa validada (cando e quen), cancelada e inexistente: só o aviso, sen botóns', async () => {
    const { el, read, type, settle, outcome } = await render();
    await read('FUR-7K2Q', {
      outcome: 'ALREADY_VALIDATED',
      booking: detail({ attendance: 'USED' }),
      attendanceAt: '2026-10-26T09:02:00Z',
      attendanceBy: 'Antón Rei',
    });
    expect(outcome()?.getAttribute('data-tone')).toBe('info');
    expect(text(outcome())).toContain('Xa estaba validada');
    expect(text(outcome())).toContain('Hoxe ás 10:02, por Antón Rei.');
    expect(el.querySelector('.f-validate__acts')).toBeNull();

    await read('FUR-C4NC', {
      outcome: 'CANCELLED',
      booking: detail({ status: 'CANCELLED_BY_PERSON', cancelledAt: '2026-10-25T08:00:00Z', cancellable: false }),
    });
    expect(outcome()?.getAttribute('data-tone')).toBe('danger');
    expect(text(outcome())).toContain('Reserva cancelada');
    expect(text(outcome())).toContain('Cancelouna a persoa o 25 de outubro.');

    await read('FUR-C4NC', {
      outcome: 'CANCELLED',
      booking: detail({ status: 'CANCELLED_BY_MUNICIPALITY', cancelledAt: '2026-10-25T08:00:00Z' }),
    });
    expect(text(outcome())).toContain('Cancelouna o concello o 25 de outubro.');

    await type('FUR-ZZZZ');
    flushProblem(await expectRequest(URL), 404, 'booking.not-found');
    await vi.waitFor(async () => {
      await settle();
      expect(outcome()?.getAttribute('data-validate-outcome')).toBe('NOT_FOUND');
    });
    expect(text(outcome())).toContain('Non hai ningunha reserva con ese código');
    expect(el.querySelector('[data-validate-facility]')).toBeNull();
    expect(el.querySelector('.f-validate__acts')).toBeNull();
  });
});
